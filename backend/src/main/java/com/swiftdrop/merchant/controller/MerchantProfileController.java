package com.swiftdrop.merchant.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.merchant.dto.request.UpdateMerchantProfileRequest;
import com.swiftdrop.merchant.dto.response.MerchantProfileResponse;
import com.swiftdrop.merchant.service.MerchantAccessService;
import com.swiftdrop.merchant.service.MerchantProfileService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The business profile page of the merchant portal. Approved merchants only. */
@RestController
@RequestMapping(ApiPaths.API_V1 + "/merchant/profile")
@PreAuthorize("hasRole('MERCHANT')")
@RequiredArgsConstructor
public class MerchantProfileController {

    private final MerchantAccessService merchantAccessService;
    private final MerchantProfileService profileService;

    @GetMapping
    public MerchantProfileResponse get(@AuthenticationPrincipal AuthenticatedUser user) {
        return profileService.get(merchantIdOf(user));
    }

    @PutMapping
    public MerchantProfileResponse update(@Valid @RequestBody UpdateMerchantProfileRequest request,
                                          @AuthenticationPrincipal AuthenticatedUser user) {
        return profileService.update(merchantIdOf(user), request);
    }

    private UUID merchantIdOf(AuthenticatedUser user) {
        return merchantAccessService.requireApprovedMerchant(UUID.fromString(user.userId()));
    }
}
