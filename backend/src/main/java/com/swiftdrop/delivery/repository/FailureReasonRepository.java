package com.swiftdrop.delivery.repository;

import com.swiftdrop.delivery.model.FailureReason;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Private to the delivery module. */
public interface FailureReasonRepository extends JpaRepository<FailureReason, String> {

    /** The reasons offered to couriers, in display order. */
    List<FailureReason> findByActiveTrueOrderByDisplayOrder();

    /** An active reason by its code. */
    Optional<FailureReason> findByCodeAndActiveTrue(String code);
}
