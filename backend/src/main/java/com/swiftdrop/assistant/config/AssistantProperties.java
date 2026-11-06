package com.swiftdrop.assistant.config;

import com.swiftdrop.common.ratelimit.RateLimit;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the AI delivery assistant, under {@code swiftdrop.assistant}. Every value has a
 * default here, so nothing has to be added to the shared application.yml. Secrets come only from
 * the environment: {@code SWIFTDROP_ASSISTANT_API_KEY}.
 *
 * @param provider         which language model client to use. {@code rules} (the default) needs no
 *                         key and answers from simple rules, so demos and CI work offline.
 * @param apiKey           the provider's API key; never logged ({@link #toString()} hides it)
 * @param model            the provider's model name
 * @param baseUrl          the provider's API base URL; blank for the provider's default
 * @param timeout          how long one model call may take
 * @param maxMessageChars  longest message a user may send
 * @param maxHistoryMessages earlier messages sent back to the model with each question
 * @param maxReplyTokens   longest reply the model may write
 * @param rateLimit        questions per client IP (or per signed-in user)
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

    /** True when a real language model is configured; otherwise the rule-based fallback answers. */
    public boolean usesLanguageModel() {
        return !RULES_PROVIDER.equals(provider) && !apiKey.isEmpty();
    }

    /** Hides the API key, so logging these settings never leaks it. */
    @Override
    public String toString() {
        return "AssistantProperties[provider=" + provider + ", apiKey=" + (apiKey.isEmpty() ? "" : "****")
                + ", model=" + model + ", baseUrl=" + baseUrl + ", timeout=" + timeout
                + ", maxMessageChars=" + maxMessageChars + ", maxHistoryMessages=" + maxHistoryMessages
                + ", maxReplyTokens=" + maxReplyTokens + ", rateLimit=" + rateLimit + "]";
    }
}
