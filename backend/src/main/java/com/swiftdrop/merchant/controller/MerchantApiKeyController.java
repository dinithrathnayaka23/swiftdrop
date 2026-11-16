package com.swiftdrop.merchant.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.merchant.dto.response.ApiKeyResponse;
import com.swiftdrop.merchant.dto.response.GeneratedApiKeyResponse;
import com.swiftdrop.merchant.service.MerchantAccessService;
import com.swiftdrop.merchant.service.MerchantApiKeyService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The API credentials panel of the merchant portal. Every call goes through the approved-only gate
 * and is scoped to the signed-in user's own merchant.
 */
@RestController
@RequestMapping(ApiPaths.API_V1 + "/merchant/api-keys")
@PreAuthorize("hasRole('MERCHANT')")
@RequiredArgsConstructor
public class MerchantApiKeyController {

    private final MerchantAccessService merchantAccessService;
    private final MerchantApiKeyService apiKeyService;

    /** All keys of the merchant, newest first, including revoked and expired ones. */
    @GetMapping
    public List<ApiKeyResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return apiKeyService.list(merchantIdOf(user));
    }

    /** The response holds the raw key; it is shown only this once. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GeneratedApiKeyResponse generate(@AuthenticationPrincipal AuthenticatedUser user) {
        return apiKeyService.generate(merchantIdOf(user));
    }

    @PostMapping("/{apiCredentialId}/revoke")
    public ApiKeyResponse revoke(@PathVariable UUID apiCredentialId,
                                 @AuthenticationPrincipal AuthenticatedUser user) {
        return apiKeyService.revoke(merchantIdOf(user), apiCredentialId);
    }

    /** Revokes the key and returns its replacement, raw key included. */
    @PostMapping("/{apiCredentialId}/rotate")
    @ResponseStatus(HttpStatus.CREATED)
    public GeneratedApiKeyResponse rotate(@PathVariable UUID apiCredentialId,
                                          @AuthenticationPrincipal AuthenticatedUser user) {
        return apiKeyService.rotate(merchantIdOf(user), apiCredentialId);
    }

    private UUID merchantIdOf(AuthenticatedUser user) {
        return merchantAccessService.requireApprovedMerchant(UUID.fromString(user.userId()));
    }
}
