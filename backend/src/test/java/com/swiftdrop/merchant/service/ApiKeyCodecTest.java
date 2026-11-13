package com.swiftdrop.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.merchant.config.MerchantApiKeyProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ApiKeyCodecTest {

    static final String PEPPER = "test-only-api-key-pepper-at-least-32-bytes-long";

    private final ApiKeyCodec codec = new ApiKeyCodec(new MerchantApiKeyProperties(PEPPER, 0));

    @Test
    void generatedKeyCarriesItsPublicKeyId() {
        ApiKeyCodec.NewKey key = codec.generate();

        assertThat(key.rawKey()).matches("sd_[0-9a-f]{16}_[A-Za-z0-9_-]{43}");
        assertThat(codec.keyIdOf(key.rawKey())).contains(key.keyId());
    }

    @Test
    void everyKeyIsDifferent() {
        assertThat(codec.generate().rawKey()).isNotEqualTo(codec.generate().rawKey());
    }

    @Test
    void storedHashIsNotTheKeyAndOnlyMatchesTheSameKey() {
        String rawKey = codec.generate().rawKey();
        String hash = codec.hash(rawKey);

        assertThat(hash).isNotEqualTo(rawKey).doesNotContain(rawKey.substring(20));
        assertThat(codec.matches(rawKey, hash)).isTrue();
        assertThat(codec.matches(codec.generate().rawKey(), hash)).isFalse();
    }

    @Test
    void hashDependsOnTheServerPepper() {
        String rawKey = codec.generate().rawKey();
        ApiKeyCodec otherServer = new ApiKeyCodec(new MerchantApiKeyProperties(PEPPER + "-other", 0));

        assertThat(otherServer.hash(rawKey)).isNotEqualTo(codec.hash(rawKey));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "not-a-key",
            "pk_3f9a1c0b7d2e4f68_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ",
            "sd_3F9A1C0B7D2E4F68_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ",
            "sd_3f9a1c0b7d2e_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ",
            "sd_3f9a1c0b7d2e4f68_tooShort",
            "sd_3f9a1c0b7d2e4f68_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ!"
    })
    void malformedKeysHaveNoKeyId(String rawKey) {
        assertThat(codec.keyIdOf(rawKey)).isEmpty();
    }
}
