package com.swiftdrop.merchant.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Webhook settings. The signing secret is populated only by create and rotate operations. */
public record WebhookSettingsResponse(
        UUID webhookId,
        String endpointUrl,
        List<String> eventTypes,
        boolean active,
        String signingSecret,
        Instant createdAt) {
}
