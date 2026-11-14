package com.swiftdrop.merchant.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swiftdrop.common.security.SecurityConfig;
import com.swiftdrop.common.security.SecurityErrorHandler;
import com.swiftdrop.common.security.jwt.JwtService;
import com.swiftdrop.merchant.dto.AuthenticatedMerchant;
import com.swiftdrop.merchant.dto.request.CreateOrderRequest;
import com.swiftdrop.merchant.dto.response.OrderResponse;
import com.swiftdrop.merchant.exception.MerchantNotApprovedException;
import com.swiftdrop.merchant.model.MerchantApprovalStatus;
import com.swiftdrop.merchant.service.MerchantApiKeyService;
import com.swiftdrop.merchant.service.OrderApiService;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderApiController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class, OrderApiControllerWebMvcTest.SecurityTestConfig.class})
class OrderApiControllerWebMvcTest {

    @TestConfiguration
    static class SecurityTestConfig {

        @Bean
        MerchantApiKeyService merchantApiKeyService() {
            return mock(MerchantApiKeyService.class);
        }

        @Bean
        JwtService jwtService() {
            return mock(JwtService.class);
        }

        @Bean
        OrderApiService orderApiService() {
            return mock(OrderApiService.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MerchantApiKeyService merchantApiKeyService;

    @Autowired
    private OrderApiService orderApiService;

    private final UUID merchantId = UUID.randomUUID();

    private String validBody() {
        return """
                {
                  "externalOrderReference": "ORD-1001",
                  "pickupAddress": {
                    "contactName": "BrightMart",
                    "contactPhone": "0771234567",
                    "addressLine1": "12 Main Street",
                    "city": "Colombo",
                    "district": "Colombo"
                  },
                  "dropoffAddress": {
                    "contactName": "Recipient",
                    "contactPhone": "0771234568",
                    "addressLine1": "45 Lake Road",
                    "city": "Kandy",
                    "district": "Kandy"
                  },
                  "parcelType": "PARCEL",
                  "weightKg": 2.5
                }
                """;
    }

    private void authenticateKey() {
        when(merchantApiKeyService.authenticate("valid-key"))
                .thenReturn(Optional.of(new AuthenticatedMerchant(merchantId, UUID.randomUUID(), "key-123")));
    }

    @Test
    void validOrderReturns201AndLocation() throws Exception {
        authenticateKey();
        when(orderApiService.createOrder(
                org.mockito.ArgumentMatchers.eq(merchantId),
                org.mockito.ArgumentMatchers.any(CreateOrderRequest.class)))
                .thenReturn(new OrderResponse(
                        "SD7KQ2MX9TRB4H", "ORD-1001", ParcelStatusCode.CREATED,
                        new java.math.BigDecimal("650.00"), "LKR", java.time.Instant.now()));

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-API-Key", "valid-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/orders/SD7KQ2MX9TRB4H"))
                .andExpect(jsonPath("$.trackingNumber").value("SD7KQ2MX9TRB4H"));
    }

    @Test
    void invalidBodyReturns400() throws Exception {
        authenticateKey();

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-API-Key", "valid-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void missingKeyReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void unapprovedMerchantReturns403() throws Exception {
        when(merchantApiKeyService.authenticate("pending-key"))
                .thenThrow(MerchantNotApprovedException.forStatus(MerchantApprovalStatus.PENDING));

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-API-Key", "pending-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_PENDING_APPROVAL"));
    }
}
