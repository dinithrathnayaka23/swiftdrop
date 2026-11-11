package com.swiftdrop.merchant.service;

import com.swiftdrop.merchant.config.MerchantApiKeyProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Creates and checks merchant API keys such as {@code sd_3f9a1c0b7d2e4f68_<43 characters>}: the
 * "sd" prefix, a public key ID and a 256-bit random secret.
 *
 * <p>The key ID is stored in plain text so a key is found with one indexed look-up. Of the key
 * itself only an HMAC-SHA256 keyed with the server pepper is stored. A slow password hash such as
 * BCrypt isn't needed: the secret is random rather than chosen by a person, so it can't be guessed
 * even from a fast hash, and BCrypt would add about 100 ms to every order API call.
 */
@Component
class ApiKeyCodec {

    static final String PREFIX = "sd";
    private static final int KEY_ID_BYTES = 8;
    private static final int SECRET_BYTES = 32;
    private static final Pattern KEY_ID = Pattern.compile("[0-9a-f]{" + KEY_ID_BYTES * 2 + "}");
    private static final Pattern SECRET = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec pepper;

    ApiKeyCodec(MerchantApiKeyProperties properties) {
        this.pepper = new SecretKeySpec(properties.pepper().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    /** A new key. The raw key is shown to the merchant once; only {@link #hash} of it is stored. */
    NewKey generate() {
        String keyId = HexFormat.of().formatHex(randomBytes(KEY_ID_BYTES));
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(SECRET_BYTES));
        return new NewKey(keyId, PREFIX + "_" + keyId + "_" + secret);
    }

    /** The key ID of a well-formed key; empty for anything else, so junk never reaches the database. */
    Optional<String> keyIdOf(String rawKey) {
        if (rawKey == null) {
            return Optional.empty();
        }
        // The secret may itself contain "_", so split into at most three parts.
        String[] parts = rawKey.split("_", 3);
        if (parts.length != 3 || !PREFIX.equals(parts[0])
                || !KEY_ID.matcher(parts[1]).matches() || !SECRET.matcher(parts[2]).matches()) {
            return Optional.empty();
        }
        return Optional.of(parts[1]);
    }

    String hash(String rawKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(pepper);
            return HexFormat.of().formatHex(mac.doFinal(rawKey.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 not available", ex);
        }
    }

    /** Constant-time comparison, so the response time doesn't reveal how much of a hash matched. */
    boolean matches(String rawKey, String storedHash) {
        return MessageDigest.isEqual(hash(rawKey).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] randomBytes(int count) {
        byte[] bytes = new byte[count];
        random.nextBytes(bytes);
        return bytes;
    }

    record NewKey(String keyId, String rawKey) {
    }
}
