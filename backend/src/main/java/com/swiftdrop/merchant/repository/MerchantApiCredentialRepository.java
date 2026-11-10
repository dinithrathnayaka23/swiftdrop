package com.swiftdrop.merchant.repository;

import com.swiftdrop.merchant.model.MerchantApiCredential;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantApiCredentialRepository extends JpaRepository<MerchantApiCredential, UUID> {

    /** Loads the merchant too: authentication always checks its approval status. */
    @Query("select c from MerchantApiCredential c join fetch c.merchant where c.keyIdentifier = :keyIdentifier")
    Optional<MerchantApiCredential> findWithMerchantByKeyIdentifier(@Param("keyIdentifier") String keyIdentifier);

    /** Keys that still work: not revoked and not past their expiry. */
    @Query("select count(c) from MerchantApiCredential c where c.merchant.id = :merchantId"
            + " and c.revokedAt is null and (c.expiresAt is null or c.expiresAt > :now)")
    long countActive(@Param("merchantId") UUID merchantId, @Param("now") Instant now);
}
