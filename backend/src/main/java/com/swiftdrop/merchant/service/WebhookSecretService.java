package com.swiftdrop.merchant.service;

import com.swiftdrop.merchant.config.MerchantWebhookProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/** Generates webhook signing secrets and encrypts them before persistence. */
@Service
public class WebhookSecretService {

    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int SECRET_LENGTH = 32;

    private final SecretKeySpec encryptionKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public WebhookSecretService(MerchantWebhookProperties properties) {
        try {
            byte[] key = MessageDigest.getInstance("SHA-256")
                    .digest(properties.secretKey().getBytes(StandardCharsets.UTF_8));
            this.encryptionKey = new SecretKeySpec(key, "AES");
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to initialize webhook secret encryption", ex);
        }
    }

    public GeneratedSecret generate() {
        byte[] secret = new byte[SECRET_LENGTH];
        secureRandom.nextBytes(secret);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        return new GeneratedSecret(encoded, encrypt(encoded));
    }

    public String decrypt(String encryptedValue) {
        try {
            byte[] encrypted = Base64.getDecoder().decode(encryptedValue);
            if (encrypted.length <= IV_LENGTH) {
                throw new IllegalArgumentException("Encrypted webhook secret is too short");
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[encrypted.length - IV_LENGTH];
            System.arraycopy(encrypted, 0, iv, 0, IV_LENGTH);
            System.arraycopy(encrypted, IV_LENGTH, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Unable to decrypt webhook secret", ex);
        }
    }

    private String encrypt(String value) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to encrypt webhook secret", ex);
        }
    }

    public record GeneratedSecret(String value, String encryptedValue) {
    }
}
