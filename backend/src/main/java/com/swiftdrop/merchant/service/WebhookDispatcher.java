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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
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

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final int RESPONSE_EXCERPT_LENGTH = 1000;

    private final MerchantWebhookRepository webhookRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final ParcelRepository parcelRepository;
    private final WebhookSecretService secretService;
    private final JsonMapper jsonMapper;
    private final WebhookRetryProperties retryProperties;

    private final RestClient restClient = createRestClient();

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
        Instant attemptedAt = Instant.now();
        String timestamp = Long.toString(attemptedAt.getEpochSecond());
        delivery.setLastAttemptAt(attemptedAt);
        try {
            HttpResult result = restClient.post()
                    .uri(URI.create(webhook.getEndpointUrl()))
                    .header("X-SwiftDrop-Signature", signature(timestamp, payload,
                            secretService.decrypt(webhook.getSigningSecretEncrypted())))
                    .header("X-SwiftDrop-Timestamp", timestamp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .exchange((request, response) -> new HttpResult(response.getStatusCode().value(),
                            response.getBody() == null ? null
                                    : excerpt(new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8))));
            delivery.setLastHttpStatus(result.status());
            delivery.setLastResponseExcerpt(result.responseExcerpt());
            if (result.status() >= 200 && result.status() < 300) {
                delivery.setDeliveryStatus(WebhookDeliveryStatus.DELIVERED);
                delivery.setDeliveredAt(attemptedAt);
                delivery.setNextRetryAt(null);
                delivery.setLastErrorMessage(null);
            } else {
                scheduleFailure(delivery, "Webhook endpoint returned HTTP " + result.status(), attemptedAt);
            }
        } catch (RestClientException | IllegalArgumentException | IllegalStateException ex) {
            scheduleFailure(delivery, "Webhook request failed: " + ex.getClass().getSimpleName(), attemptedAt);
            log.warn("Webhook delivery {} failed: {}", delivery.getId(), ex.getClass().getSimpleName());
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

    private static String signature(String timestamp, String body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to sign webhook payload", ex);
        }
    }

    private static RestClient createRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(REQUEST_TIMEOUT);
        requestFactory.setReadTimeout(REQUEST_TIMEOUT);
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    private static String excerpt(String response) {
        return response == null || response.length() <= RESPONSE_EXCERPT_LENGTH
                ? response : response.substring(0, RESPONSE_EXCERPT_LENGTH);
    }

    private record HttpResult(int status, String responseExcerpt) {
    }

    private record WebhookPayload(UUID eventId, String type, String trackingNumber,
                                  String externalOrderReference, String previousStatus, String newStatus,
                                  Instant occurredAt) {
    }
}
