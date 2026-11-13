package com.swiftdrop.merchant.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.merchant.dto.request.UpdateWebhookSettingsRequest;
import com.swiftdrop.merchant.dto.response.WebhookSettingsResponse;
import com.swiftdrop.merchant.service.MerchantAccessService;
import com.swiftdrop.merchant.service.WebhookSettingsService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Webhook settings for the currently authenticated, approved merchant. */
@RestController
@RequestMapping(ApiPaths.API_V1 + "/merchant/webhook")
@PreAuthorize("hasRole('MERCHANT')")
@RequiredArgsConstructor
public class MerchantWebhookController {

    private final MerchantAccessService merchantAccessService;
    private final WebhookSettingsService webhookSettingsService;

    @GetMapping
    public WebhookSettingsResponse get(@AuthenticationPrincipal AuthenticatedUser user) {
        return webhookSettingsService.get(merchantId(user));
    }

    @PutMapping
    public WebhookSettingsResponse update(@Valid @RequestBody UpdateWebhookSettingsRequest request,
                                           @AuthenticationPrincipal AuthenticatedUser user) {
        return webhookSettingsService.update(merchantId(user), request);
    }

    @PostMapping("/rotate-secret")
    public WebhookSettingsResponse rotateSecret(@AuthenticationPrincipal AuthenticatedUser user) {
        return webhookSettingsService.rotateSecret(merchantId(user));
    }

    private UUID merchantId(AuthenticatedUser user) {
        return merchantAccessService.requireApprovedMerchant(UUID.fromString(user.userId()));
    }
}
