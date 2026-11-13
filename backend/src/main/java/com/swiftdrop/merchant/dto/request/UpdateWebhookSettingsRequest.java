package com.swiftdrop.merchant.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateWebhookSettingsRequest(
        @NotBlank @Size(max = 2048) String endpointUrl,
        @NotNull @Size(min = 1, max = 20) List<@NotBlank @Size(max = 50) String> eventTypes,
        boolean active) {
}
