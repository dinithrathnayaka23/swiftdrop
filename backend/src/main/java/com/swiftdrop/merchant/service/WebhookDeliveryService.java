package com.swiftdrop.merchant.service;

import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.merchant.dto.response.WebhookDeliveryResponse;
import com.swiftdrop.merchant.dto.response.WebhookTestResponse;
import com.swiftdrop.merchant.model.MerchantWebhook;
import com.swiftdrop.merchant.model.WebhookDelivery;
import com.swiftdrop.merchant.model.WebhookDeliveryStatus;
import com.swiftdrop.merchant.repository.MerchantWebhookRepository;
import com.swiftdrop.merchant.repository.WebhookDeliveryRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class WebhookDeliveryService {

    private final WebhookDeliveryRepository deliveryRepository;
    private final MerchantWebhookRepository webhookRepository;
    private final WebhookDispatcher dispatcher;
    private final WebhookDeliverySender deliverySender;
    private final JsonMapper jsonMapper;

    @Transactional(readOnly = true)
    public Page<WebhookDeliveryResponse> list(UUID merchantId, WebhookDeliveryStatus status, Pageable pageable) {
        Page<WebhookDelivery> deliveries = status == null
                ? deliveryRepository.findByWebhookMerchantId(merchantId, pageable)
                : deliveryRepository.findByWebhookMerchantIdAndDeliveryStatus(merchantId, status, pageable);
        return deliveries.map(WebhookDeliveryService::toResponse);
    }

    @Transactional
    public WebhookDeliveryResponse retryFailed(UUID merchantId, UUID deliveryId) {
        WebhookDelivery delivery = ownedDelivery(merchantId, deliveryId);
        if (delivery.getDeliveryStatus() != WebhookDeliveryStatus.FAILED) {
            throw new ConflictException("Only failed webhook deliveries can be retried");
        }
        dispatcher.retry(delivery);
        return toResponse(delivery);
    }

    @Transactional(readOnly = true)
    public WebhookTestResponse sendTest(UUID merchantId) {
        MerchantWebhook webhook = webhookRepository.findFirstByMerchantIdAndActiveTrueOrderByCreatedAtAsc(merchantId)
                .orElseThrow(() -> new NotFoundException("Active webhook settings have not been configured"));
        String payload = testPayload();
        WebhookDeliverySender.AttemptResult result = deliverySender.send(webhook, payload);
        String error = result.errorMessage();
        if (!result.successful() && error == null) {
            error = "Webhook endpoint returned HTTP " + result.httpStatus();
        }
        return new WebhookTestResponse(result.successful(), result.httpStatus(), result.responseExcerpt(), error);
    }

    private WebhookDelivery ownedDelivery(UUID merchantId, UUID deliveryId) {
        WebhookDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new NotFoundException("Webhook delivery not found"));
        if (!merchantId.equals(delivery.getWebhook().getMerchant().getId())) {
            throw new NotFoundException("Webhook delivery not found");
        }
        return delivery;
    }

    private String testPayload() {
        try {
            return jsonMapper.writeValueAsString(new TestWebhookPayload(
                    UUID.randomUUID(), "test", Instant.now()));
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Unable to serialize webhook test payload", ex);
        }
    }

    private static WebhookDeliveryResponse toResponse(WebhookDelivery delivery) {
        return new WebhookDeliveryResponse(delivery.getId(), delivery.getParcelId(), delivery.getEventType(),
                delivery.getDeliveryStatus(), delivery.getRetryCount(), delivery.getLastAttemptAt(),
                delivery.getLastHttpStatus(), delivery.getLastResponseExcerpt(), delivery.getLastErrorMessage(),
                delivery.getNextRetryAt(), delivery.getDeliveredAt(), delivery.getCreatedAt());
    }

    private record TestWebhookPayload(UUID eventId, String type, Instant occurredAt) {
    }
}
