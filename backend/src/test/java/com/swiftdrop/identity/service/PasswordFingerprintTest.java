package com.swiftdrop.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.common.security.Role;
import com.swiftdrop.user.dto.UserAccount;
import com.swiftdrop.user.model.AccountStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PasswordFingerprintTest {

    private static UserAccount user(String passwordHash, String googleSubject) {
        return new UserAccount(UUID.randomUUID(), "N", "n@example.com", passwordHash, googleSubject, Role.CUSTOMER,
                AccountStatus.ACTIVE, true);
    }

    @Test
    void isAShortStableHexDigest() {
        String fingerprint = PasswordFingerprint.of("$2a$10$hash");

        assertThat(fingerprint).hasSize(24).matches("[0-9a-f]+");
        assertThat(PasswordFingerprint.of("$2a$10$hash")).isEqualTo(fingerprint);
    }

    @Test
    void changesWhenThePasswordHashChanges() {
        assertThat(PasswordFingerprint.of("$2a$10$old")).isNotEqualTo(PasswordFingerprint.of("$2a$10$new"));
    }

    @Test
    void doesNotContainTheHash() {
        assertThat(PasswordFingerprint.of("$2a$10$secret-hash")).doesNotContain("secret");
    }

    @Test
    void passwordAccountsUseTheirHashAndGoogleOnlyAccountsTheirGoogleId() {
        assertThat(PasswordFingerprint.of(user("$2a$hash", "sub-1"))).isEqualTo(PasswordFingerprint.of("$2a$hash"));
        assertThat(PasswordFingerprint.of(user(null, "sub-1"))).isEqualTo(PasswordFingerprint.of("google:sub-1"));
    }
}
