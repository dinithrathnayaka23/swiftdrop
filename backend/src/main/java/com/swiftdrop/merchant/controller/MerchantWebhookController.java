package com.swiftdrop.merchant.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.merchant.dto.request.UpdateWebhookSettingsRequest;
import com.swiftdrop.merchant.dto.response.WebhookSettingsResponse;
import com.swiftdrop.merchant.dto.response.WebhookDeliveryResponse;
import com.swiftdrop.merchant.dto.response.WebhookTestResponse;
import com.swiftdrop.merchant.model.WebhookDeliveryStatus;
import com.swiftdrop.merchant.service.MerchantAccessService;
import com.swiftdrop.merchant.service.WebhookDeliveryService;
import com.swiftdrop.merchant.service.WebhookSettingsService;
import jakarta.validation.Valid;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Webhook settings for the currently authenticated, approved merchant. */
@RestController
@RequestMapping(ApiPaths.API_V1 + "/merchant/webhook")
@PreAuthorize("hasRole('MERCHANT')")
@Tag(name = "Merchant Webhooks", description = "Configure signed status webhooks and inspect delivery attempts")
@RequiredArgsConstructor
public class MerchantWebhookController {

    private final MerchantAccessService merchantAccessService;
    private final WebhookSettingsService webhookSettingsService;
    private final WebhookDeliveryService webhookDeliveryService;

    @GetMapping
    @Operation(summary = "Get webhook settings", description = "Returns the approved merchant's webhook settings. The signing secret is never returned after creation or rotation.")
    public WebhookSettingsResponse get(@AuthenticationPrincipal AuthenticatedUser user) {
        return webhookSettingsService.get(merchantId(user));
    }

    @PutMapping
    @Operation(
            summary = "Update webhook settings",
            description = "Updates the endpoint URL, subscribed event types, and active state. Endpoints must use HTTPS; private hosts are allowed only in development when configured.")
    public WebhookSettingsResponse update(@Valid @RequestBody UpdateWebhookSettingsRequest request,
                                           @AuthenticationPrincipal AuthenticatedUser user) {
        return webhookSettingsService.update(merchantId(user), request);
    }

    @PostMapping("/rotate-secret")
    @Operation(summary = "Rotate webhook signing secret",
            description = "Generates a new signing secret and returns it once. Existing deliveries signed with the previous secret cannot be replayed after rotation.")
    public WebhookSettingsResponse rotateSecret(@AuthenticationPrincipal AuthenticatedUser user) {
        return webhookSettingsService.rotateSecret(merchantId(user));
    }

    @GetMapping("/deliveries")
    @Operation(summary = "List webhook deliveries", description = "Returns the authenticated merchant's webhook deliveries, newest first, optionally filtered by delivery status.")
    public Page<WebhookDeliveryResponse> deliveries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) WebhookDeliveryStatus status,
            @AuthenticationPrincipal AuthenticatedUser user) {
        PageableValidator.validate(page, size);
        return webhookDeliveryService.list(merchantId(user), status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")
                        .and(Sort.by(Sort.Direction.DESC, "id"))));
    }

    @PostMapping("/deliveries/{deliveryId}/retry")
    @Operation(summary = "Retry a failed webhook delivery", description = "Queues a failed delivery for another attempt. Only deliveries owned by the authenticated merchant can be retried.")
    public WebhookDeliveryResponse retry(@PathVariable UUID deliveryId,
                                         @AuthenticationPrincipal AuthenticatedUser user) {
        return webhookDeliveryService.retryFailed(merchantId(user), deliveryId);
    }

    @PostMapping("/test")
    @Operation(
            summary = "Send a test webhook",
            description = """
                    Sends a sample signed test event to the configured endpoint.

                    Verify a received webhook by computing HMAC-SHA256 over
                    `X-SwiftDrop-Timestamp + "." + raw request body` using the signing secret,
                    encoding the digest as lowercase hexadecimal, and comparing it with
                    `X-SwiftDrop-Signature`. Reject requests with an old timestamp to prevent replay attacks.
                    """)
    public WebhookTestResponse test(@AuthenticationPrincipal AuthenticatedUser user) {
        return webhookDeliveryService.sendTest(merchantId(user));
    }

    private UUID merchantId(AuthenticatedUser user) {
        return merchantAccessService.requireApprovedMerchant(UUID.fromString(user.userId()));
    }

    private static final class PageableValidator {
        private PageableValidator() {
        }

        static void validate(int page, int size) {
            if (page < 0 || size < 1 || size > 100) {
                throw new BadRequestException("Page must be non-negative and size must be between 1 and 100");
            }
        }
    }
}
