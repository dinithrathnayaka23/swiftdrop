package com.swiftdrop.merchant.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.merchant.dto.response.MerchantOrderDetailResponse;
import com.swiftdrop.merchant.dto.response.MerchantOrderSummaryResponse;
import com.swiftdrop.merchant.service.MerchantAccessService;
import com.swiftdrop.merchant.service.MerchantOrderService;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The dashboard counts, orders list and order details of the merchant portal. Approved merchants only. */
@RestController
@RequestMapping(ApiPaths.API_V1 + "/merchant/orders")
@PreAuthorize("hasRole('MERCHANT')")
@RequiredArgsConstructor
public class MerchantOrderController {

    private final MerchantAccessService merchantAccessService;
    private final MerchantOrderService orderService;

    @GetMapping("/counts")
    public Map<ParcelStatusCode, Long> counts(@AuthenticationPrincipal AuthenticatedUser user) {
        return orderService.counts(merchantIdOf(user));
    }

    @GetMapping
    public PagedModel<MerchantOrderSummaryResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "10") int size) {
        return new PagedModel<>(orderService.list(merchantIdOf(user), page, size));
    }

    /** 404 when the order doesn't exist or belongs to someone else. */
    @GetMapping("/{trackingNumber}")
    public MerchantOrderDetailResponse get(@AuthenticationPrincipal AuthenticatedUser user,
                                           @PathVariable String trackingNumber) {
        return orderService.get(merchantIdOf(user), trackingNumber);
    }

    private UUID merchantIdOf(AuthenticatedUser user) {
        return merchantAccessService.requireApprovedMerchant(UUID.fromString(user.userId()));
    }
}
