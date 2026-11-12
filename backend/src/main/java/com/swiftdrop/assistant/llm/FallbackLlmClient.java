package com.swiftdrop.assistant.llm;

import com.swiftdrop.assistant.config.AssistantProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * The client the assistant uses: the language model when one is configured, otherwise the rules.
 * When the model fails (quota used up, timeout, server error, unreadable reply) that round is
 * answered by the rules, and the model is left alone for {@link #PAUSE_AFTER_FAILURE} instead of
 * being retried.
 */
@Slf4j
@Primary
@Component
public class FallbackLlmClient implements LlmClient {

    static final Duration PAUSE_AFTER_FAILURE = Duration.ofSeconds(60);

    private final LlmClient model;
    private final RuleBasedLlmClient rules;
    private final Supplier<Instant> clock;
    private volatile Instant modelPausedUntil = Instant.MIN;

    @Autowired
    public FallbackLlmClient(AssistantProperties properties, RuleBasedLlmClient rules, JsonMapper json) {
        this(properties.usesLanguageModel() ? OpenAiCompatibleLlmClient.create(properties, json) : null, rules,
                Instant::now);
        log.info("Assistant mode: {}", model == null ? "rules (no language model configured)"
                : properties.provider() + " model " + properties.model());
    }

    FallbackLlmClient(LlmClient model, RuleBasedLlmClient rules, Supplier<Instant> clock) {
        this.model = model;
        this.rules = rules;
        this.clock = clock;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        if (model == null || clock.get().isBefore(modelPausedUntil)) {
            return rules.complete(request);
        }
        try {
            return model.complete(request);
        } catch (LlmUnavailableException ex) {
            return fallBack(ex.getMessage(), request);
        } catch (RuntimeException ex) {
            return fallBack(ex.getClass().getSimpleName(), request);
        }
    }

    private LlmResponse fallBack(String reason, LlmRequest request) {
        modelPausedUntil = clock.get().plus(PAUSE_AFTER_FAILURE);
        log.warn("Assistant model unavailable ({}); answering in basic mode for the next {} s", reason,
                PAUSE_AFTER_FAILURE.toSeconds());
        return rules.complete(request);
    }
}
