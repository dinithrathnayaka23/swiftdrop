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
    void usesALanguageModelOnlyWithAKnownProviderAKeyAndAModel() {
        assertThat(new AssistantProperties("gemini", "secret", "flash-lite", null, null, 0, 0, 0, null)
                .usesLanguageModel()).isTrue();
        assertThat(new AssistantProperties("gemini", " ", "flash-lite", null, null, 0, 0, 0, null)
                .usesLanguageModel()).isFalse();
        assertThat(new AssistantProperties("gemini", "secret", "", null, null, 0, 0, 0, null)
                .usesLanguageModel()).isFalse();
        assertThat(new AssistantProperties("rules", "secret", "flash-lite", null, null, 0, 0, 0, null)
                .usesLanguageModel()).isFalse();
        assertThat(new AssistantProperties("other", "secret", "m", null, null, 0, 0, 0, null)
                .usesLanguageModel()).isFalse();
        assertThat(new AssistantProperties("other", "secret", "m", "https://llm.example/v1", null, 0, 0, 0, null)
                .usesLanguageModel()).isTrue();
    }

    @Test
    void defaultsTheBaseUrlPerProvider() {
        assertThat(withProviderAndKey("gemini", "k").resolvedBaseUrl()).isEqualTo(AssistantProperties.GEMINI_BASE_URL);
        assertThat(withProviderAndKey("OpenAI", "k").resolvedBaseUrl()).isEqualTo(AssistantProperties.OPENAI_BASE_URL);
        assertThat(new AssistantProperties("gemini", "k", null, " https://proxy.example/v1 ", null, 0, 0, 0, null)
                .resolvedBaseUrl()).isEqualTo("https://proxy.example/v1");
    }

    @Test
    void toStringNeverShowsTheApiKey() {
        assertThat(withProviderAndKey("some-provider", "sk-very-secret").toString())
                .doesNotContain("sk-very-secret")
                .contains("apiKey=****");
    }
}
