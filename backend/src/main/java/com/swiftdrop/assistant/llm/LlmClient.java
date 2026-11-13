package com.swiftdrop.assistant.llm;

/**
 * A chat model that may ask for tools. One call is one round: the reply is either final text or
 * a list of tool calls, which the caller runs and sends back in the next {@link LlmRequest}.
 */
public interface LlmClient {

    /**
     * @throws LlmUnavailableException when the model can't answer (no key, timeout, quota used up,
     *                                 server error or a reply that can't be read)
     */
    LlmResponse complete(LlmRequest request);
}
