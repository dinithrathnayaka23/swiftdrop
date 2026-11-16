package com.swiftdrop.assistant.tools;

import java.util.Objects;
import java.util.UUID;

/**
 * Who is asking, taken from the request and the sign-in token, never from the model.
 *
 * @param customerId the signed-in customer
 * @param clientIp   for per-IP limits shared with public tracking
 */
public record ToolContext(UUID customerId, String clientIp) {

    public ToolContext {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(clientIp, "clientIp");
    }
}
