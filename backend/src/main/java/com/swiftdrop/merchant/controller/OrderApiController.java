package com.swiftdrop.merchant.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.merchant.dto.AuthenticatedMerchant;
import com.swiftdrop.merchant.dto.request.CreateOrderRequest;
import com.swiftdrop.merchant.dto.response.OrderResponse;
import com.swiftdrop.merchant.service.OrderApiService;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Merchant order API endpoints authenticated with a merchant API key. */
@RestController
@RequestMapping(ApiPaths.ORDERS)
@RequiredArgsConstructor
public class OrderApiController {

    private final OrderApiService orderApiService;

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal AuthenticatedMerchant merchant) {
        OrderResponse created = orderApiService.createOrder(merchant.merchantId(), request);
        return ResponseEntity.created(URI.create(ApiPaths.ORDERS + "/" + created.trackingNumber()))
                .body(created);
    }

    @GetMapping("/{trackingNumber}")
    public OrderResponse get(
            @PathVariable String trackingNumber,
            @AuthenticationPrincipal AuthenticatedMerchant merchant) {
        return orderApiService.getOrder(merchant.merchantId(), trackingNumber);
    }

    @GetMapping("/{trackingNumber}/status")
    public ParcelStatusCode status(
            @PathVariable String trackingNumber,
            @AuthenticationPrincipal AuthenticatedMerchant merchant) {
        return orderApiService.getStatus(merchant.merchantId(), trackingNumber);
    }
}
