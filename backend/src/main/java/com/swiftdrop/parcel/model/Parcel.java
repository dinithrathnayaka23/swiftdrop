package com.swiftdrop.parcel.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * A parcel booked by a customer or created through the merchant API. Table {@code parcels}.
 *
 * <p>References to other modules (customer, merchant, pricing rule) are kept as IDs, not entity
 * associations, so modules stay independent.
 */
@Entity
@Table(name = "parcels")
@Getter
@Setter
@NoArgsConstructor
public class Parcel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "parcel_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tracking_number", nullable = false, updatable = false, length = 40)
    private String trackingNumber;

    /** {@code users.user_id} of the booking customer (user module); {@code null} for merchant orders. */
    @Column(name = "customer_user_id")
    private UUID customerUserId;

    /** {@code merchants.merchant_id} (merchant module); {@code null} for customer bookings. */
    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(name = "external_order_reference", length = 100)
    private String externalOrderReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pickup_address_id", nullable = false)
    private Address pickupAddress;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dropoff_address_id", nullable = false)
    private Address dropoffAddress;

    @Column(name = "weight_kg", nullable = false, precision = 8, scale = 3)
    private BigDecimal weightKg;

    @Column(name = "length_cm", precision = 8, scale = 2)
    private BigDecimal lengthCm;

    @Column(name = "width_cm", precision = 8, scale = 2)
    private BigDecimal widthCm;

    @Column(name = "height_cm", precision = 8, scale = 2)
    private BigDecimal heightCm;

    @Column(name = "content_description")
    private String contentDescription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "current_status_code", nullable = false)
    private ParcelStatus currentStatus;

    /** {@code pricing_rules.pricing_rule_id} used for the quote (config module). */
    @Column(name = "pricing_rule_id")
    private UUID pricingRuleId;

    @Column(name = "quoted_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal quotedFee;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "fee_currency", nullable = false, length = 3)
    private String feeCurrency = "LKR";

    @Column(name = "delivery_instructions")
    private String deliveryInstructions;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
