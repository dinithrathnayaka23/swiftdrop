package com.swiftdrop.assistant.llm;

/**
 * The language model could not answer this round. The message is a short reason for the logs
 * (for example "HTTP 429" or "timeout"); it never holds message text or the API key.
 */
public class LlmUnavailableException extends RuntimeException {

    public LlmUnavailableException(String reason) {
        super(reason);
    }

    public LlmUnavailableException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
