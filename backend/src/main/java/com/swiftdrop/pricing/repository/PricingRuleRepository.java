package com.swiftdrop.pricing.repository;

import com.swiftdrop.pricing.model.PricingRule;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PricingRuleRepository extends JpaRepository<PricingRule, UUID> {

    /** Active rules whose time window includes {@code at}, newest first. */
    @Query("""
            select r from PricingRule r
            where r.active = true
              and r.effectiveFrom <= :at
              and (r.effectiveTo is null or r.effectiveTo > :at)
            order by r.effectiveFrom desc
            """)
    List<PricingRule> findActiveAt(@Param("at") Instant at);
}