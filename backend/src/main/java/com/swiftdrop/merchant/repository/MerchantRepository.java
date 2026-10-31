package com.swiftdrop.merchant.repository;

import com.swiftdrop.merchant.model.Merchant;
import com.swiftdrop.merchant.model.MerchantApprovalStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    @Query("select count(m) > 0 from Merchant m where lower(m.registrationNo) = lower(:registrationNo)")
    boolean existsByRegistrationNo(@Param("registrationNo") String registrationNo);

    List<Merchant> findByApprovalStatusOrderByCreatedAtAsc(MerchantApprovalStatus approvalStatus);
}
