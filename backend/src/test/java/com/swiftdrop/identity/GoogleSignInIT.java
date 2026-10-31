package com.swiftdrop.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.swiftdrop.IntegrationTestBase;
import com.swiftdrop.common.exception.UnauthorizedException;
import com.swiftdrop.identity.exception.GoogleSignInUnavailableException;
import com.swiftdrop.identity.security.GoogleIdTokenVerifier;
import com.swiftdrop.identity.security.GoogleIdTokenVerifier.GoogleIdentity;
import com.swiftdrop.notification.service.EmailService;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Google sign-in against the real schema. The Google token check itself is unit-tested in
 * GoogleIdTokenVerifierTest; here the verifier is mocked to return a chosen Google account.
 */
class GoogleSignInIT extends IntegrationTestBase {

    @MockitoBean
    private GoogleIdTokenVerifier googleVerifier;

    @MockitoBean
    private EmailService emailService;

    @Autowired
    private JsonMapper json;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void configTellsTheBrowserWhetherGoogleIsAvailable() throws Exception {
        when(googleVerifier.isEnabled()).thenReturn(true);
        when(googleVerifier.clientId()).thenReturn("abc.apps.googleusercontent.com");
        JsonNode enabled = json.readTree(get("/api/v1/auth/google/config").body());
        assertThat(enabled.get("enabled").asBoolean()).isTrue();
        assertThat(enabled.get("clientId").asString()).isEqualTo("abc.apps.googleusercontent.com");

        when(googleVerifier.isEnabled()).thenReturn(false);
        assertThat(json.readTree(get("/api/v1/auth/google/config").body()).get("enabled").asBoolean()).isFalse();
    }

    @Test
    void newGoogleUserBecomesACustomerAndCanRefresh() throws Exception {
        String subject = "sub-" + UUID.randomUUID();
        String email = uniqueEmail("google");
        when(googleVerifier.verify("credential-1")).thenReturn(new GoogleIdentity(subject, email, "Kamal Silva"));

        HttpResponse<String> response = post("/api/v1/auth/google", """
                {"credential":"credential-1","rememberMe":true}""");
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode auth = json.readTree(response.body());
        assertThat(auth.get("user").get("displayName").asString()).isEqualTo("Kamal Silva");
        assertThat(auth.get("user").get("role").asString()).isEqualTo("CUSTOMER");
        assertThat(auth.get("rememberMe").asBoolean()).isTrue();

        var row = jdbcTemplate.queryForMap("SELECT password_hash, google_subject, account_status FROM users WHERE email = ?",
                email);
        assertThat(row.get("password_hash")).isNull();
        assertThat(row.get("google_subject")).isEqualTo(subject);
        assertThat(row.get("account_status")).isEqualTo("ACTIVE");

        // Google-only accounts keep working sessions (no password to fingerprint).
        HttpResponse<String> refreshed = postWithSession("/api/v1/auth/refresh", sessionCookie(response));
        assertThat(refreshed.statusCode()).isEqualTo(200);

        // Signing in again finds the same account rather than creating another.
        assertThat(post("/api/v1/auth/google", """
                {"credential":"credential-1"}""").statusCode()).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE email = ?", Long.class, email))
                .isEqualTo(1L);

        // No password, so the password form can't be used.
        assertThat(post("/api/v1/auth/login", """
                {"email":"%s","password":"anything123"}""".formatted(email)).statusCode()).isEqualTo(401);
    }

    @Test
    void existingPasswordAccountIsLinkedByEmail() throws Exception {
        String email = uniqueEmail("link");
        post("/api/v1/auth/register/customer", """
                {"fullName":"Saman Fernando","email":"%s","phone":"+94712345678","password":"parcel2026"}"""
                .formatted(email));
        verifyEmail(email); // the owner proved the address, so Google may be linked to it
        String subject = "sub-" + UUID.randomUUID();
        when(googleVerifier.verify("credential-2")).thenReturn(new GoogleIdentity(subject, email.toUpperCase(), null));

        HttpResponse<String> response = post("/api/v1/auth/google", """
                {"credential":"credential-2"}""");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).get("user").get("displayName").asString()).isEqualTo("Saman Fernando");
        assertThat(jdbcTemplate.queryForObject("SELECT google_subject FROM users WHERE email = ?", String.class, email))
                .isEqualTo(subject);

        // The password still works too.
        assertThat(post("/api/v1/auth/login", """
                {"email":"%s","password":"parcel2026"}""".formatted(email)).statusCode()).isEqualTo(200);
    }

    @Test
    void unverifiedAccountRegisteredBySomeoneElseIsTakenOverByTheRealOwner() throws Exception {
        // An attacker registers the victim's address with their own password and never confirms it.
        String email = uniqueEmail("squatted");
        post("/api/v1/auth/register/customer", """
                {"fullName":"Attacker","email":"%s","phone":"+94712345678","password":"attacker2026"}"""
                .formatted(email));
        assertThat(post("/api/v1/auth/login", """
                {"email":"%s","password":"attacker2026"}""".formatted(email)).statusCode()).isEqualTo(403);

        // The real owner signs in with Google for that address.
        String subject = "sub-" + UUID.randomUUID();
        when(googleVerifier.verify("owner")).thenReturn(new GoogleIdentity(subject, email, "Real Owner"));
        assertThat(post("/api/v1/auth/google", """
                {"credential":"owner"}""").statusCode()).isEqualTo(200);

        // The attacker's password is gone; the account belongs to the Google owner, email verified.
        var row = jdbcTemplate.queryForMap(
                "SELECT password_hash, google_subject, email_verified_at FROM users WHERE email = ?", email);
        assertThat(row.get("password_hash")).isNull();
        assertThat(row.get("google_subject")).isEqualTo(subject);
        assertThat(row.get("email_verified_at")).isNotNull();
        assertThat(post("/api/v1/auth/login", """
                {"email":"%s","password":"attacker2026"}""".formatted(email)).statusCode()).isEqualTo(401);
    }

    @Test
    void emailLinkedToAnotherGoogleAccountIsRefused() throws Exception {
        String email = uniqueEmail("taken");
        when(googleVerifier.verify("first")).thenReturn(new GoogleIdentity("sub-" + UUID.randomUUID(), email, "A"));
        when(googleVerifier.verify("second")).thenReturn(new GoogleIdentity("sub-" + UUID.randomUUID(), email, "B"));
        assertThat(post("/api/v1/auth/google", """
                {"credential":"first"}""").statusCode()).isEqualTo(200);

        assertThat(post("/api/v1/auth/google", """
                {"credential":"second"}""").statusCode()).isEqualTo(401);
    }

    @Test
    void pendingMerchantIsStillBlocked() throws Exception {
        String email = uniqueEmail("merchant");
        post("/api/v1/auth/register/merchant", """
                {"businessName":"Lanka Traders","registrationNo":"PV%08d","contactPersonName":"Ruwan Jayasuriya",
                 "email":"%s","phone":"+94712345678","businessAddress":"12 Galle Road, Colombo 03",
                 "password":"merchant2026"}""".formatted(Math.abs(UUID.randomUUID().hashCode()) % 100_000_000, email));
        when(googleVerifier.verify("credential-3")).thenReturn(new GoogleIdentity("sub-" + UUID.randomUUID(), email, "Ruwan"));

        HttpResponse<String> response = post("/api/v1/auth/google", """
                {"credential":"credential-3"}""");
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(json.readTree(response.body()).get("code").asString()).isEqualTo("ACCOUNT_PENDING_APPROVAL");
        // Nothing was linked: the transaction rolled back.
        assertThat(jdbcTemplate.queryForObject("SELECT google_subject FROM users WHERE email = ?", String.class, email))
                .isNull();
    }

    @Test
    void verifierErrorsMapToClearResponses() throws Exception {
        when(googleVerifier.verify("bad")).thenThrow(new UnauthorizedException("Google sign-in failed. Please try again."));
        when(googleVerifier.verify("off")).thenThrow(new GoogleSignInUnavailableException());

        assertThat(post("/api/v1/auth/google", """
                {"credential":"bad"}""").statusCode()).isEqualTo(401);
        HttpResponse<String> unavailable = post("/api/v1/auth/google", """
                {"credential":"off"}""");
        assertThat(unavailable.statusCode()).isEqualTo(503);
        assertThat(json.readTree(unavailable.body()).get("code").asString()).isEqualTo("GOOGLE_SIGN_IN_UNAVAILABLE");
        assertThat(post("/api/v1/auth/google", """
                {"credential":""}""").statusCode()).isEqualTo(400);
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }
}
