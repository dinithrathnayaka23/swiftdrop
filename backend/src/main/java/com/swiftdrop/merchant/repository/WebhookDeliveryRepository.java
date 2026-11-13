package com.swiftdrop.merchant.repository;

import com.swiftdrop.merchant.model.WebhookDelivery;
import com.swiftdrop.merchant.model.WebhookDeliveryStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    List<WebhookDelivery> findByWebhookMerchantId(UUID merchantId);

    boolean existsByWebhookIdAndStatusHistoryId(UUID webhookId, UUID statusHistoryId);

    List<WebhookDelivery> findTop100ByDeliveryStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            WebhookDeliveryStatus deliveryStatus, Instant now);
}
