package com.swiftdrop.merchant.service;

import com.swiftdrop.merchant.model.WebhookDelivery;
import com.swiftdrop.merchant.model.WebhookDeliveryStatus;
import com.swiftdrop.merchant.repository.WebhookDeliveryRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Retries pending webhook deliveries whose next backoff delay has elapsed. */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookRetryJob {

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookDispatcher dispatcher;

    @Scheduled(fixedDelayString = "${WEBHOOK_RETRY_POLL_INTERVAL:30000}")
    @Transactional
    public void retryDueDeliveries() {
        for (WebhookDelivery delivery : deliveryRepository
                .findTop100ByDeliveryStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                        WebhookDeliveryStatus.PENDING, Instant.now())) {
            try {
                dispatcher.retry(delivery);
            } catch (RuntimeException ex) {
                log.warn("Webhook retry {} could not be processed: {}", delivery.getId(),
                        ex.getClass().getSimpleName());
            }
        }
    }
}
