package com.swiftdrop.merchant.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.merchant.dto.request.MerchantOrderFilter;
import com.swiftdrop.merchant.dto.response.MerchantOrderDetailResponse;
import com.swiftdrop.merchant.dto.response.MerchantOrderSummaryResponse;
import com.swiftdrop.merchant.service.MerchantAccessService;
import com.swiftdrop.merchant.service.MerchantOrderService;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.service.ReceiptService.ReceiptDocument;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    /**
     * The merchant's orders, newest first. Every filter is optional and they combine: {@code search}
     * (tracking number, order reference, recipient or city), {@code status}, and the creation days
     * {@code from} and {@code to} ({@code yyyy-MM-dd}, both included). Bad values answer 400.
     */
    @GetMapping
    public PagedModel<MerchantOrderSummaryResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                                         @RequestParam(required = false) String search,
                                                         @RequestParam(required = false) String status,
                                                         @RequestParam(required = false) String from,
                                                         @RequestParam(required = false) String to,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "10") int size) {
        return new PagedModel<>(orderService.list(merchantIdOf(user),
                new MerchantOrderFilter(search, status, from, to), page, size));
    }

    /** 404 when the order doesn't exist or belongs to someone else. */
    @GetMapping("/{trackingNumber}")
    public MerchantOrderDetailResponse get(@AuthenticationPrincipal AuthenticatedUser user,
                                           @PathVariable String trackingNumber) {
        return orderService.get(merchantIdOf(user), trackingNumber);
    }

    /** The order's receipt as a PDF download; 404 when the order isn't this merchant's. */
    @GetMapping("/{trackingNumber}/receipt")
    public ResponseEntity<byte[]> receipt(@AuthenticationPrincipal AuthenticatedUser user,
                                          @PathVariable String trackingNumber) {
        ReceiptDocument receipt = orderService.receipt(merchantIdOf(user), trackingNumber);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(receipt.fileName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(receipt.pdf());
    }

    private UUID merchantIdOf(AuthenticatedUser user) {
        return merchantAccessService.requireApprovedMerchant(UUID.fromString(user.userId()));
    }
}
