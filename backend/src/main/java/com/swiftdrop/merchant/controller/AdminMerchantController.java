package com.swiftdrop.merchant.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.merchant.dto.request.RejectMerchantRequest;
import com.swiftdrop.merchant.dto.response.MerchantResponse;
import com.swiftdrop.merchant.model.MerchantApprovalStatus;
import com.swiftdrop.merchant.service.MerchantService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Merchant approval for dispatchers/admins. */
@RestController
@RequestMapping(ApiPaths.API_V1 + "/admin/merchants")
@PreAuthorize("hasRole('DISPATCHER_ADMIN')")
@RequiredArgsConstructor
public class AdminMerchantController {

    private final MerchantService merchantService;

    /** Merchants by approval status (default: PENDING, oldest first). */
    @GetMapping
    public List<MerchantResponse> list(
            @RequestParam(defaultValue = "PENDING") MerchantApprovalStatus status) {
        return merchantService.listByStatus(status);
    }

    @PostMapping("/{merchantId}/approve")
    public MerchantResponse approve(@PathVariable UUID merchantId,
                                    @AuthenticationPrincipal AuthenticatedUser admin) {
        return merchantService.approve(merchantId, UUID.fromString(admin.userId()));
    }

    @PostMapping("/{merchantId}/reject")
    public MerchantResponse reject(@PathVariable UUID merchantId,
                                   @Valid @RequestBody RejectMerchantRequest request,
                                   @AuthenticationPrincipal AuthenticatedUser admin) {
        return merchantService.reject(merchantId, UUID.fromString(admin.userId()), request.reason());
    }
}
