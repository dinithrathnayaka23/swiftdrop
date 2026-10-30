package com.swiftdrop.config.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Base pricing used for fee estimates, valid for a time window. Table {@code pricing_rules}. */
@Entity
@Table(name = "pricing_rules")
@Getter
@Setter
@NoArgsConstructor
public class PricingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "pricing_rule_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "rule_name", nullable = false, length = 120)
    private String ruleName;

    @Column(name = "base_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseFee;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "LKR";

    @Column(name = "included_weight_kg", nullable = false, precision = 8, scale = 3)
    private BigDecimal includedWeightKg = BigDecimal.ZERO;

    @Column(name = "additional_fee_per_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal additionalFeePerKg = BigDecimal.ZERO;

    @Column(name = "max_volume_cm3", precision = 12, scale = 2)
    private BigDecimal maxVolumeCm3;

    @Column(name = "oversize_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal oversizeFee = BigDecimal.ZERO;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** {@code users.user_id} of the admin who created the rule (user module). */
    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
