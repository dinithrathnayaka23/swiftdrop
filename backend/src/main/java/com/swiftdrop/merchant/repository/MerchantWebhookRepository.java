package com.swiftdrop.merchant.repository;

import com.swiftdrop.merchant.model.MerchantWebhook;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantWebhookRepository extends JpaRepository<MerchantWebhook, UUID> {

    Optional<MerchantWebhook> findFirstByMerchantIdOrderByCreatedAtAsc(UUID merchantId);

    Optional<MerchantWebhook> findFirstByMerchantIdAndActiveTrueOrderByCreatedAtAsc(UUID merchantId);
}
