package com.swiftdrop.assistant.llm;

import java.time.Duration;

/**
 * One OpenAI-compatible chat model to call.
 *
 * @param name    the provider, for logs (e.g. "gemini", "openrouter")
 * @param baseUrl the API base; requests go to {@code <baseUrl>/chat/completions}
 */
public record LlmEndpoint(String name, String apiKey, String model, String baseUrl, Duration timeout) {

    @Override
    public String toString() {
        return "LlmEndpoint[name=" + name + ", model=" + model + ", baseUrl=" + baseUrl + "]";
    }
}
