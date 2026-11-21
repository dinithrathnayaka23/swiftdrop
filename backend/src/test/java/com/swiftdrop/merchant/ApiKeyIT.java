package com.swiftdrop.merchant;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Merchant API keys end to end against the real database: create in the portal, use on the order
 * API (directly and through an OAuth token), revoke, rotate, the active-key limit and ownership.
 * An unknown tracking number answers 404 while a key is accepted and 401 once it isn't.
 */
class ApiKeyIT extends MerchantIntegrationTestBase {

    private static final String KEYS = "/api/v1/merchant/api-keys";

    private SignedInMerchant merchant;

    @BeforeEach
    void signIn() {
        merchant = approvedMerchant("BrightMart");
    }

    private JsonNode listedKey(String apiCredentialId) throws Exception {
        for (JsonNode key : ok(get(KEYS, merchant.token()), 200)) {
            if (key.get("apiCredentialId").asString().equals(apiCredentialId)) return key;
        }
        throw new AssertionError("Key " + apiCredentialId + " is not listed");
    }

    private HttpResponse<String> revoke(SignedInMerchant owner, String apiCredentialId) throws Exception {
        return post(KEYS + "/" + apiCredentialId + "/revoke", "", owner.token());
    }

    private HttpResponse<String> rotate(SignedInMerchant owner, String apiCredentialId) throws Exception {
        return post(KEYS + "/" + apiCredentialId + "/rotate", "", owner.token());
    }

    private HttpResponse<String> tokenFor(JsonNode key) throws Exception {
        return oauthToken(Map.of("grant_type", "client_credentials", "client_id", key.get("keyId").asString(),
                "client_secret", key.get("apiKey").asString()));
    }

    @Test
    void aKeyIsShownOnceWorksOnTheOrderApiAndStopsWorkingWhenRevoked() throws Exception {
        JsonNode created = createApiKey(merchant);
        String apiKey = created.get("apiKey").asString();
        String id = created.get("apiCredentialId").asString();
        assertThat(apiKey).startsWith("sd_" + created.get("keyId").asString() + "_");

        // Listed by its public key id only: neither the key nor its hash ever comes back.
        HttpResponse<String> list = get(KEYS, merchant.token());
        assertThat(list.body()).doesNotContain(apiKey).doesNotContain("apiKey").doesNotContain("Hash");
        assertThat(listedKey(id).get("status").asString()).isEqualTo("ACTIVE");
        assertThat(listedKey(id).get("lastUsedAt").isNull()).isTrue();

        assertThat(withApiKey(UNKNOWN_ORDER, null, apiKey).statusCode()).isEqualTo(404);
        assertThat(listedKey(id).get("lastUsedAt").isNull()).isFalse();
        String storedHash = jdbc.queryForObject(
                "SELECT api_key_hash FROM merchant_api_credentials WHERE api_credential_id = ?::uuid", String.class,
                id);
        assertThat(storedHash).isNotEqualTo(apiKey).doesNotContain(apiKey);

        JsonNode revoked = ok(revoke(merchant, id), 200);
        assertThat(revoked.get("status").asString()).isEqualTo("REVOKED");
        assertError(withApiKey(UNKNOWN_ORDER, null, apiKey), 401, "UNAUTHORIZED");

        // Revoking again keeps the first revocation time (stored to the microsecond).
        assertThat(Instant.parse(ok(revoke(merchant, id), 200).get("revokedAt").asString()))
                .isEqualTo(Instant.parse(revoked.get("revokedAt").asString()).truncatedTo(ChronoUnit.MICROS));
    }

    @Test
    void rotatingReplacesTheKeyInOneStep() throws Exception {
        JsonNode old = createApiKey(merchant);
        String oldId = old.get("apiCredentialId").asString();

        JsonNode replacement = ok(rotate(merchant, oldId), 201);

        assertThat(replacement.get("apiCredentialId").asString()).isNotEqualTo(oldId);
        assertThat(withApiKey(UNKNOWN_ORDER, null, old.get("apiKey").asString()).statusCode()).isEqualTo(401);
        assertThat(withApiKey(UNKNOWN_ORDER, null, replacement.get("apiKey").asString()).statusCode())
                .isEqualTo(404);
        assertThat(listedKey(oldId).get("status").asString()).isEqualTo("REVOKED");
        assertError(rotate(merchant, oldId), 409, "CONFLICT");
    }

    @Test
    void aMerchantHoldsAtMostTwoActiveKeysAndRotatingWorksAtTheLimit() throws Exception {
        JsonNode first = createApiKey(merchant);
        createApiKey(merchant);

        assertError(post(KEYS, "", merchant.token()), 409, "CONFLICT");
        ok(rotate(merchant, first.get("apiCredentialId").asString()), 201);
        assertError(post(KEYS, "", merchant.token()), 409, "CONFLICT");

        ok(revoke(merchant, listedActiveKeyId()), 200);
        createApiKey(merchant);
    }

    private String listedActiveKeyId() throws Exception {
        for (JsonNode key : ok(get(KEYS, merchant.token()), 200)) {
            if (key.get("status").asString().equals("ACTIVE")) return key.get("apiCredentialId").asString();
        }
        throw new AssertionError("No active key");
    }

    @Test
    void anotherMerchantsKeyCanNeitherBeSeenNorChanged() throws Exception {
        JsonNode mine = createApiKey(merchant);
        String id = mine.get("apiCredentialId").asString();
        SignedInMerchant other = approvedMerchant("Other Shop");

        assertThat(get(KEYS, other.token()).body()).doesNotContain(id);
        assertError(revoke(other, id), 404, "NOT_FOUND");
        assertError(rotate(other, id), 404, "NOT_FOUND");
        assertThat(withApiKey(UNKNOWN_ORDER, null, mine.get("apiKey").asString()).statusCode()).isEqualTo(404);
    }

    @Test
    void missingMalformedAndMadeUpKeysAreRefused() throws Exception {
        JsonNode real = createApiKey(merchant);
        String madeUp = real.get("apiKey").asString().substring(0, real.get("apiKey").asString().length() - 4)
                + "AAAA";

        assertError(get(UNKNOWN_ORDER), 401, "UNAUTHORIZED");
        assertError(withApiKey(UNKNOWN_ORDER, null, "not-a-key"), 401, "UNAUTHORIZED");
        assertError(withApiKey(UNKNOWN_ORDER, null, madeUp), 401, "UNAUTHORIZED");
        // A portal sign-in token is not an order API token.
        assertError(withAccessToken(UNKNOWN_ORDER, null, merchant.token()), 401, "UNAUTHORIZED");
    }

    @Test
    void anOAuthTokenWorksUntilItsKeyIsRevoked() throws Exception {
        JsonNode key = createApiKey(merchant);

        JsonNode token = ok(tokenFor(key), 200);
        assertThat(token.get("token_type").asString()).isEqualTo("Bearer");
        assertThat(token.get("expires_in").asLong()).isEqualTo(900);
        String accessToken = token.get("access_token").asString();
        assertThat(withAccessToken(UNKNOWN_ORDER, null, accessToken).statusCode()).isEqualTo(404);

        ok(revoke(merchant, key.get("apiCredentialId").asString()), 200);

        // The token was issued before the revocation and hasn't expired, but its key no longer works.
        assertError(withAccessToken(UNKNOWN_ORDER, null, accessToken), 401, "UNAUTHORIZED");
        assertError(tokenFor(key), 401, "UNAUTHORIZED");
    }

    @Test
    void anOAuthTokenStopsWorkingWhenTheMerchantIsSuspended() throws Exception {
        String accessToken = ok(tokenFor(createApiKey(merchant)), 200).get("access_token").asString();

        setMerchantStatus(merchant.merchantId(), "SUSPENDED");

        assertError(withAccessToken(UNKNOWN_ORDER, null, accessToken), 403, "ACCOUNT_DISABLED");
    }

    @Test
    void theTokenEndpointRefusesBadClientCredentials() throws Exception {
        JsonNode key = createApiKey(merchant);
        JsonNode other = createApiKey(approvedMerchant("Other Shop"));
        String secret = key.get("apiKey").asString();

        assertError(oauthToken(Map.of("grant_type", "client_credentials", "client_id",
                other.get("keyId").asString(), "client_secret", secret)), 401, "UNAUTHORIZED");
        assertError(oauthToken(Map.of("grant_type", "password", "client_id", key.get("keyId").asString(),
                "client_secret", secret)), 401, "UNAUTHORIZED");
        assertError(oauthToken(Map.of("grant_type", "client_credentials", "client_id",
                key.get("keyId").asString())), 401, "UNAUTHORIZED");
    }
}
