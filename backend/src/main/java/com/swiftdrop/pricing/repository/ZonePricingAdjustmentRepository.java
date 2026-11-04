package com.swiftdrop.pricing.repository;

import com.swiftdrop.pricing.model.ZonePricingAdjustment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZonePricingAdjustmentRepository extends JpaRepository<ZonePricingAdjustment, UUID> {

    List<ZonePricingAdjustment> findByPricingRule_Id(UUID pricingRuleId);

    Optional<ZonePricingAdjustment> findByPricingRule_IdAndPickupZoneIdAndDeliveryZoneId(
            UUID pricingRuleId, UUID pickupZoneId, UUID deliveryZoneId);
}