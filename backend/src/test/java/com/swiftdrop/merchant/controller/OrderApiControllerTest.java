package com.swiftdrop.merchant.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swiftdrop.merchant.dto.AuthenticatedMerchant;
import com.swiftdrop.merchant.dto.request.CreateOrderRequest;
import com.swiftdrop.merchant.dto.response.OrderResponse;
import com.swiftdrop.merchant.service.OrderApiService;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class OrderApiControllerTest {

    @Mock
    private OrderApiService orderApiService;

    @InjectMocks
    private OrderApiController controller;

    private final UUID merchantId = UUID.randomUUID();
    private final AuthenticatedMerchant merchant = new AuthenticatedMerchant(merchantId, UUID.randomUUID());

    private OrderResponse response() {
        return new OrderResponse(
                "SD7KQ2MX9TRB4H", "ORD-1001", ParcelStatusCode.CREATED,
                new BigDecimal("650.00"), "LKR", Instant.parse("2026-11-12T10:00:00Z"));
    }

    @Test
    void createUsesAuthenticatedMerchantAndReturns201WithLocation() {
        CreateOrderRequest request = org.mockito.Mockito.mock(CreateOrderRequest.class);
        when(orderApiService.createOrder(merchantId, request)).thenReturn(response());

        ResponseEntity<OrderResponse> result = controller.create(request, merchant);

        assertThat(result.getStatusCode().value()).isEqualTo(201);
        assertThat(result.getHeaders().getLocation().toString())
                .isEqualTo("/api/v1/orders/SD7KQ2MX9TRB4H");
        assertThat(result.getBody()).isEqualTo(response());
        verify(orderApiService).createOrder(merchantId, request);
    }

    @Test
    void getUsesAuthenticatedMerchantAndTrackingNumber() {
        when(orderApiService.getOrder(merchantId, "SD7KQ2MX9TRB4H")).thenReturn(response());

        assertThat(controller.get("SD7KQ2MX9TRB4H", merchant)).isEqualTo(response());
    }

    @Test
    void statusUsesAuthenticatedMerchantAndTrackingNumber() {
        when(orderApiService.getStatus(merchantId, "SD7KQ2MX9TRB4H"))
                .thenReturn(ParcelStatusCode.IN_TRANSIT);

        assertThat(controller.status("SD7KQ2MX9TRB4H", merchant))
                .isEqualTo(ParcelStatusCode.IN_TRANSIT);
    }
}
