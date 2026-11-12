package com.swiftdrop.assistant.config;

import com.swiftdrop.assistant.llm.LlmEndpoint;
import com.swiftdrop.common.ratelimit.RateLimit;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Settings for the AI delivery assistant, under {@code swiftdrop.assistant}. Every value has a
 * default here. Secrets come only from the environment (or the git-ignored .env in the dev profile).
 *
 * @param provider   the main model: {@code gemini}, {@code openai}, {@code openrouter}, {@code grok},
 *                   or {@code rules} (the default: no key, works offline)
 * @param apiKey     never logged ({@link #toString()} hides it)
 * @param baseUrl    blank for the provider's default, see {@link #resolvedBaseUrl()}
 * @param openrouter tried when the main model fails (e.g. its free quota is used up); its model
 *                   defaults to {@code openrouter/free}
 * @param grok       tried when OpenRouter fails too
 * @param rateLimit  questions per signed-in customer
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
        RateLimit rateLimit,
        Fallback openrouter,
        Fallback grok) {

    public static final String RULES_PROVIDER = "rules";
    public static final String GEMINI_PROVIDER = "gemini";
    public static final String OPENAI_PROVIDER = "openai";
    public static final String OPENROUTER_PROVIDER = "openrouter";
    public static final String GROK_PROVIDER = "grok";

    static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/openai/";
    static final String OPENAI_BASE_URL = "https://api.openai.com/v1";
    static final String OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1";
    static final String GROK_BASE_URL = "https://api.x.ai/v1";
    /** OpenRouter's free router: picks a free model that supports the request's features (tool calling). */
    static final String OPENROUTER_DEFAULT_MODEL = "openrouter/free";

    @ConstructorBinding
    public AssistantProperties {
        provider = provider == null || provider.isBlank() ? RULES_PROVIDER : provider.trim().toLowerCase(Locale.ROOT);
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
        openrouter = openrouter == null ? Fallback.NONE : openrouter;
        grok = grok == null ? Fallback.NONE : grok;
    }

    /** Settings with only the main model, no fallbacks. */
    public AssistantProperties(String provider, String apiKey, String model, String baseUrl, Duration timeout,
                               int maxMessageChars, int maxHistoryMessages, int maxReplyTokens, RateLimit rateLimit) {
        this(provider, apiKey, model, baseUrl, timeout, maxMessageChars, maxHistoryMessages, maxReplyTokens,
                rateLimit, null, null);
    }

    /** A fallback model; used only when its key is set (and a model, unless the provider has a default). */
    public record Fallback(String apiKey, String model, String baseUrl) {

        static final Fallback NONE = new Fallback(null, null, null);

        public Fallback {
            apiKey = apiKey == null ? "" : apiKey.trim();
            model = model == null ? "" : model.trim();
            baseUrl = baseUrl == null ? "" : baseUrl.trim();
        }

        @Override
        public String toString() {
            return "Fallback[apiKey=" + (apiKey.isEmpty() ? "" : "****") + ", model=" + model + ", baseUrl=" + baseUrl
                    + "]";
        }
    }

    /** The main model's configured base URL, or the provider's default; blank when neither is known. */
    public String resolvedBaseUrl() {
        return baseUrl.isEmpty() ? defaultBaseUrl(provider) : baseUrl;
    }

    /**
     * The models to try, in order: the main one, then OpenRouter, then Grok. Each is included only
     * when fully configured. Empty means basic mode.
     */
    public List<LlmEndpoint> languageModels() {
        List<LlmEndpoint> models = new ArrayList<>();
        if (!RULES_PROVIDER.equals(provider) && !apiKey.isEmpty() && !model.isEmpty() && !resolvedBaseUrl().isEmpty()) {
            models.add(new LlmEndpoint(provider, apiKey, model, resolvedBaseUrl(), timeout));
        }
        addFallback(models, OPENROUTER_PROVIDER, openrouter, OPENROUTER_DEFAULT_MODEL);
        addFallback(models, GROK_PROVIDER, grok, "");
        return List.copyOf(models);
    }

    /** True when at least one real model is configured; otherwise the rule-based assistant answers. */
    public boolean usesLanguageModel() {
        return !languageModels().isEmpty();
    }

    private void addFallback(List<LlmEndpoint> models, String name, Fallback fallback, String defaultModel) {
        String model = fallback.model().isEmpty() ? defaultModel : fallback.model();
        if (!fallback.apiKey().isEmpty() && !model.isEmpty()) {
            String url = fallback.baseUrl().isEmpty() ? defaultBaseUrl(name) : fallback.baseUrl();
            models.add(new LlmEndpoint(name, fallback.apiKey(), model, url, timeout));
        }
    }

    private static String defaultBaseUrl(String provider) {
        return switch (provider) {
            case GEMINI_PROVIDER -> GEMINI_BASE_URL;
            case OPENAI_PROVIDER -> OPENAI_BASE_URL;
            case OPENROUTER_PROVIDER -> OPENROUTER_BASE_URL;
            case GROK_PROVIDER -> GROK_BASE_URL;
            default -> "";
        };
    }

    @Override
    public String toString() {
        return "AssistantProperties[provider=" + provider + ", apiKey=" + (apiKey.isEmpty() ? "" : "****")
                + ", model=" + model + ", baseUrl=" + baseUrl + ", timeout=" + timeout
                + ", maxMessageChars=" + maxMessageChars + ", maxHistoryMessages=" + maxHistoryMessages
                + ", maxReplyTokens=" + maxReplyTokens + ", rateLimit=" + rateLimit
                + ", openrouter=" + openrouter + ", grok=" + grok + "]";
    }
}
