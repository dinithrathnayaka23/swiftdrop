package com.swiftdrop.assistant.llm;

import com.swiftdrop.assistant.config.AssistantProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * The client the assistant uses: the configured models in order (main, then OpenRouter, then Grok),
 * and the offline rules when none can answer. A model that fails (quota used up, timeout, server
 * error, unreadable reply) is skipped for {@link #PAUSE_AFTER_FAILURE} instead of being retried, so
 * the next one answers straight away.
 */
@Slf4j
@Primary
@Component
public class FallbackLlmClient implements LlmClient {

    static final Duration PAUSE_AFTER_FAILURE = Duration.ofSeconds(60);

    /** A model in the chain. */
    record Model(String name, LlmClient client) {
    }

    private final List<Model> models;
    private final RuleBasedLlmClient rules;
    private final Supplier<Instant> clock;
    private final AtomicReferenceArray<Instant> pausedUntil;

    @Autowired
    public FallbackLlmClient(AssistantProperties properties, RuleBasedLlmClient rules, JsonMapper json) {
        this(properties.languageModels().stream()
                        .map(endpoint -> new Model(endpoint.name() + " " + endpoint.model(),
                                OpenAiCompatibleLlmClient.create(endpoint, json)))
                        .toList(),
                rules, Instant::now);
        log.info("Assistant mode: {}", models.isEmpty() ? "rules (no language model configured)"
                : models.stream().map(Model::name).collect(Collectors.joining(" -> ")) + " -> rules");
    }

    FallbackLlmClient(List<Model> models, RuleBasedLlmClient rules, Supplier<Instant> clock) {
        this.models = List.copyOf(models);
        this.rules = rules;
        this.clock = clock;
        this.pausedUntil = new AtomicReferenceArray<>(models.size());
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        for (int i = 0; i < models.size(); i++) {
            Instant paused = pausedUntil.get(i);
            if (paused != null && clock.get().isBefore(paused)) {
                continue;
            }
            Model model = models.get(i);
            long started = System.nanoTime();
            try {
                LlmResponse response = model.client().complete(request);
                log.info("Assistant model {} answered in {} ms", model.name(), (System.nanoTime() - started) / 1_000_000);
                return response;
            } catch (LlmUnavailableException ex) {
                pause(i, model, ex.getMessage());
            } catch (RuntimeException ex) {
                pause(i, model, ex.getClass().getSimpleName());
            }
        }
        return rules.complete(request);
    }

    private void pause(int index, Model model, String reason) {
        pausedUntil.set(index, clock.get().plus(PAUSE_AFTER_FAILURE));
        log.warn("Assistant model {} unavailable ({}); skipping it for {} s", model.name(), reason,
                PAUSE_AFTER_FAILURE.toSeconds());
    }
}
