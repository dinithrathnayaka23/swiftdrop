package com.swiftdrop.merchant.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.merchant.dto.AuthenticatedMerchant;
import com.swiftdrop.merchant.dto.request.CreateOrderRequest;
import com.swiftdrop.merchant.dto.response.OrderResponse;
import com.swiftdrop.merchant.service.OrderApiService;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import jakarta.validation.Valid;
import java.net.URI;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Merchant Orders", description = "Order creation and tracking for approved merchants")
@SecurityRequirement(name = "apiKeyAuth")
@RequiredArgsConstructor
public class OrderApiController {

    private final OrderApiService orderApiService;

    @PostMapping
    @Operation(
            summary = "Create a merchant delivery order",
            description = "Creates a parcel order for the authenticated merchant. The response contains no personal address or contact data.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            mediaType = "application/json",
                            examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                                    value = """
                                            {
                                              "externalOrderReference": "STORE-10042",
                                              "pickupAddress": {
                                                "addressLine1": "10 Main Street",
                                                "city": "Colombo",
                                                "recipientName": "SwiftDrop Warehouse",
                                                "phone": "+94112223344"
                                              },
                                              "dropoffAddress": {
                                                "addressLine1": "25 Lake Road",
                                                "city": "Kandy",
                                                "recipientName": "Jane Customer",
                                                "phone": "+94771234567"
                                              },
                                              "parcelType": "BOX",
                                              "weightKg": 2.0,
                                              "description": "Order contents"
                                            }
                                            """))),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "201",
                            description = "Order created",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                                            value = """
                                                    {
                                                      "trackingNumber": "SD123456789",
                                                      "externalOrderReference": "STORE-10042",
                                                      "status": "CREATED",
                                                      "quotedFee": 850.00,
                                                      "currency": "LKR",
                                                      "createdAt": "2026-11-13T07:27:36Z"
                                                    }
                                                    """))),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "400", description = "Invalid order request"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "401", description = "Missing or invalid API key")
            })
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal AuthenticatedMerchant merchant) {
        OrderResponse created = orderApiService.createOrder(merchant.merchantId(), request);
        return ResponseEntity.created(URI.create(ApiPaths.ORDERS + "/" + created.trackingNumber()))
                .body(created);
    }

    @GetMapping("/{trackingNumber}")
    @Operation(summary = "Get a merchant order", description = "Returns a merchant-owned order without personal delivery details.")
    public OrderResponse get(
            @PathVariable String trackingNumber,
            @AuthenticationPrincipal AuthenticatedMerchant merchant) {
        return orderApiService.getOrder(merchant.merchantId(), trackingNumber);
    }

    @GetMapping("/{trackingNumber}/status")
    @Operation(summary = "Get order status", description = "Returns the current status for a merchant-owned order.")
    public ParcelStatusCode status(
            @PathVariable String trackingNumber,
            @AuthenticationPrincipal AuthenticatedMerchant merchant) {
        return orderApiService.getStatus(merchant.merchantId(), trackingNumber);
    }
}
