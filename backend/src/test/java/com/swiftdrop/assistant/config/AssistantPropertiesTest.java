package com.swiftdrop.assistant.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AssistantPropertiesTest {

    private static AssistantProperties withProviderAndKey(String provider, String apiKey) {
        return new AssistantProperties(provider, apiKey, null, null, null, 0, 0, 0, null);
    }

    @Test
    void defaultsToTheOfflineRuleBasedAssistant() {
        var properties = withProviderAndKey(null, null);

        assertThat(properties.provider()).isEqualTo(AssistantProperties.RULES_PROVIDER);
        assertThat(properties.apiKey()).isEmpty();
        assertThat(properties.usesLanguageModel()).isFalse();
        assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.maxMessageChars()).isPositive();
        assertThat(properties.maxHistoryMessages()).isPositive();
        assertThat(properties.maxReplyTokens()).isPositive();
        assertThat(properties.rateLimit()).isNotNull();
    }

    @Test
    void usesALanguageModelOnlyWithAProviderAndAKey() {
        assertThat(withProviderAndKey("some-provider", "secret").usesLanguageModel()).isTrue();
        assertThat(withProviderAndKey("some-provider", " ").usesLanguageModel()).isFalse();
        assertThat(withProviderAndKey("rules", "secret").usesLanguageModel()).isFalse();
    }

    @Test
    void toStringNeverShowsTheApiKey() {
        assertThat(withProviderAndKey("some-provider", "sk-very-secret").toString())
                .doesNotContain("sk-very-secret")
                .contains("apiKey=****");
    }
}
