package com.swiftdrop.assistant.config;

import com.swiftdrop.common.ratelimit.RateLimit;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the AI delivery assistant, under {@code swiftdrop.assistant}. Every value has a
 * default here, so nothing has to be added to the shared application.yml. Secrets come only from
 * the environment: {@code SWIFTDROP_ASSISTANT_API_KEY}.
 *
 * @param provider           {@code gemini}, {@code openai} or {@code rules} (the default: no key, works offline)
 * @param apiKey             never logged ({@link #toString()} hides it)
 * @param baseUrl            blank for the provider's default, see {@link #resolvedBaseUrl()}
 * @param maxHistoryMessages earlier messages sent back to the model with each question
 * @param rateLimit          questions per signed-in customer
 */
@ConfigurationProperties(prefix = "swiftdrop.assistant")
public record AssistantProperties(
        String provider,
        String apiKey,
        String model,
        String baseUrl,
        Duration timeout,
        int maxMessageChars,
        int maxHistoryMessages,
        int maxReplyTokens,
        RateLimit rateLimit) {

    public static final String RULES_PROVIDER = "rules";
    public static final String GEMINI_PROVIDER = "gemini";
    public static final String OPENAI_PROVIDER = "openai";

    static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/openai/";
    static final String OPENAI_BASE_URL = "https://api.openai.com/v1";

    public AssistantProperties {
        provider = provider == null || provider.isBlank() ? RULES_PROVIDER : provider.trim().toLowerCase();
        apiKey = apiKey == null ? "" : apiKey.trim();
        model = model == null ? "" : model.trim();
        baseUrl = baseUrl == null ? "" : baseUrl.trim();
        if (timeout == null || !timeout.isPositive()) {
            timeout = Duration.ofSeconds(30);
        }
        if (maxMessageChars <= 0) {
            maxMessageChars = 1000;
        }
        if (maxHistoryMessages <= 0) {
            maxHistoryMessages = 10;
        }
        if (maxReplyTokens <= 0) {
            maxReplyTokens = 600;
        }
        if (rateLimit == null) {
            rateLimit = new RateLimit(20, Duration.ofMinutes(1));
        }
    }

    /** The configured base URL, or the provider's default; blank when neither is known. */
    public String resolvedBaseUrl() {
        if (!baseUrl.isEmpty()) {
            return baseUrl;
        }
        return switch (provider) {
            case GEMINI_PROVIDER -> GEMINI_BASE_URL;
            case OPENAI_PROVIDER -> OPENAI_BASE_URL;
            default -> "";
        };
    }

    /** True when a real model is fully configured; otherwise the rule-based assistant answers. */
    public boolean usesLanguageModel() {
        return !RULES_PROVIDER.equals(provider) && !apiKey.isEmpty() && !model.isEmpty()
                && !resolvedBaseUrl().isEmpty();
    }

    @Override
    public String toString() {
        return "AssistantProperties[provider=" + provider + ", apiKey=" + (apiKey.isEmpty() ? "" : "****")
                + ", model=" + model + ", baseUrl=" + baseUrl + ", timeout=" + timeout
                + ", maxMessageChars=" + maxMessageChars + ", maxHistoryMessages=" + maxHistoryMessages
                + ", maxReplyTokens=" + maxReplyTokens + ", rateLimit=" + rateLimit + "]";
    }
}
