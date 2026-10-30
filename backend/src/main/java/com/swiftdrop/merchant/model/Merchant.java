package com.swiftdrop.merchant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** A merchant business and its approval state. Table {@code merchants}. */
@Entity
@Table(name = "merchants")
@Getter
@Setter
@NoArgsConstructor
public class Merchant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "business_name", nullable = false, length = 180)
    private String businessName;

    @Column(name = "registration_no", length = 80)
    private String registrationNo;

    @Column(name = "contact_email", nullable = false, length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", nullable = false, length = 25)
    private String contactPhone;

    @Column(name = "business_address", nullable = false)
    private String businessAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    private MerchantApprovalStatus approvalStatus = MerchantApprovalStatus.PENDING;

    /** {@code users.user_id} of the MERCHANT user who registered the business (user module). */
    @Column(name = "registered_by_user_id", nullable = false, updatable = false)
    private UUID registeredByUserId;

    /** {@code users.user_id} of the admin who approved/rejected it (user module). */
    @Column(name = "reviewed_by_user_id")
    private UUID reviewedByUserId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
