package com.swiftdrop.merchant.service;

import com.swiftdrop.common.messaging.RabbitConfig;
import com.swiftdrop.common.messaging.event.ParcelStatusChangedEvent;
import com.swiftdrop.merchant.model.MerchantWebhook;
import com.swiftdrop.merchant.model.WebhookDelivery;
import com.swiftdrop.merchant.model.WebhookDeliveryStatus;
import com.swiftdrop.merchant.config.WebhookRetryProperties;
import com.swiftdrop.merchant.repository.MerchantWebhookRepository;
import com.swiftdrop.merchant.repository.WebhookDeliveryRepository;
import com.swiftdrop.parcel.model.Parcel;
import com.swiftdrop.parcel.repository.ParcelRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Delivers parcel status events to the owning merchant's configured webhook.
 *
 * <p>The delivery row is written before the network call so every attempted event is observable.
 * The consumer does not throw for an endpoint failure: the delivery result is persisted and the
 * broker message is acknowledged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookDispatcher {

    private final MerchantWebhookRepository webhookRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final ParcelRepository parcelRepository;
    private final WebhookDeliverySender deliverySender;
    private final JsonMapper jsonMapper;
    private final WebhookRetryProperties retryProperties;

    @RabbitListener(queues = RabbitConfig.WEBHOOK_QUEUE)
    @Transactional
    public void onStatusChanged(ParcelStatusChangedEvent event) {
        if (event == null || event.merchantId() == null || event.statusHistoryId() == null
                || event.parcelId() == null || event.trackingNumber() == null) {
            log.warn("Ignoring an incomplete merchant webhook status event");
            return;
        }

        MerchantWebhook webhook = webhookRepository
                .findFirstByMerchantIdAndActiveTrueOrderByCreatedAtAsc(event.merchantId())
                .filter(candidate -> subscribesTo(candidate, event))
                .orElse(null);
        if (webhook == null || deliveryRepository.existsByWebhookIdAndStatusHistoryId(webhook.getId(),
                event.statusHistoryId())) {
            return;
        }

        Parcel parcel = parcelRepository.findWithDetailsById(event.parcelId()).orElse(null);
        if (parcel == null) {
            log.warn("Ignoring webhook event {} because parcel {} was not found", event.eventId(), event.parcelId());
            return;
        }

        String payload = payload(event, parcel);
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setWebhook(webhook);
        delivery.setParcelId(event.parcelId());
        delivery.setStatusHistoryId(event.statusHistoryId());
        delivery.setEventType(event.eventType());
        delivery.setPayloadJson(payload);
        delivery.setDeliveryStatus(WebhookDeliveryStatus.PENDING);
        delivery = deliveryRepository.save(delivery);

        deliver(webhook, delivery, payload);
        deliveryRepository.save(delivery);
    }

    void retry(WebhookDelivery delivery) {
        deliver(delivery.getWebhook(), delivery, delivery.getPayloadJson());
        deliveryRepository.save(delivery);
    }

    private void deliver(MerchantWebhook webhook, WebhookDelivery delivery, String payload) {
        WebhookDeliverySender.AttemptResult result = deliverySender.send(webhook, payload);
        delivery.setLastAttemptAt(result.attemptedAt());
        delivery.setLastHttpStatus(result.httpStatus());
        delivery.setLastResponseExcerpt(result.responseExcerpt());
        if (result.successful()) {
            delivery.setDeliveryStatus(WebhookDeliveryStatus.DELIVERED);
            delivery.setDeliveredAt(result.attemptedAt());
            delivery.setNextRetryAt(null);
            delivery.setLastErrorMessage(null);
        } else {
            String error = result.errorMessage() != null
                    ? result.errorMessage() : "Webhook endpoint returned HTTP " + result.httpStatus();
            scheduleFailure(delivery, error, result.attemptedAt());
            if (result.errorMessage() != null) {
                log.warn("Webhook delivery {} failed: {}", delivery.getId(), result.errorMessage());
            }
        }
    }

    private void scheduleFailure(WebhookDelivery delivery, String error, Instant attemptedAt) {
        int nextRetryCount = delivery.getRetryCount() + 1;
        delivery.setRetryCount(nextRetryCount);
        delivery.setLastErrorMessage(error);
        if (nextRetryCount > retryProperties.backoff().size()) {
            delivery.setDeliveryStatus(WebhookDeliveryStatus.FAILED);
            delivery.setNextRetryAt(null);
            return;
        }
        delivery.setDeliveryStatus(WebhookDeliveryStatus.PENDING);
        delivery.setNextRetryAt(attemptedAt.plus(retryProperties.backoff().get(nextRetryCount - 1)));
    }

    private String payload(ParcelStatusChangedEvent event, Parcel parcel) {
        try {
            return jsonMapper.writeValueAsString(new WebhookPayload(event.eventId(), event.eventType(),
                    event.trackingNumber(), parcel.getExternalOrderReference(), event.previousStatus(),
                    event.newStatus(), event.occurredAt()));
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Unable to serialize webhook payload", ex);
        }
    }

    private static boolean subscribesTo(MerchantWebhook webhook, ParcelStatusChangedEvent event) {
        List<String> eventTypes = webhook.getEventTypes();
        return eventTypes != null && eventTypes.stream().anyMatch(type ->
                type.equalsIgnoreCase(event.eventType())
                        || type.equalsIgnoreCase(RabbitConfig.PARCEL_STATUS_CHANGED_KEY));
    }

    private record WebhookPayload(UUID eventId, String type, String trackingNumber,
                                  String externalOrderReference, String previousStatus, String newStatus,
                                  Instant occurredAt) {
    }
}
