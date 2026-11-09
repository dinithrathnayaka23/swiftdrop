package com.swiftdrop.dispatch.repository;

import com.swiftdrop.dispatch.model.DeliveryAssignment;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Private to the dispatch module. An assignment is <em>open</em> while {@code ended_at} is null; it
 * ends (ended_at set) when it is completed, reassigned or cancelled.
 */
public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignment, UUID> {

    /** Ids of the parcels that have an open assignment. */
    @Query("select a.parcelId from DeliveryAssignment a where a.endedAt is null")
    List<UUID> findOpenParcelIds();

    /** Whether the parcel has an open assignment to the courier whose account is {@code courierUserId}. */
    boolean existsByParcelIdAndCourier_UserIdAndEndedAtIsNull(UUID parcelId, UUID courierUserId);

    /** The parcels in the courier's open assignments, oldest assignment first. */
    @Query("""
            select a.parcelId from DeliveryAssignment a
            where a.courier.userId = :courierUserId and a.endedAt is null
            order by a.assignedAt
            """)
    List<UUID> findOpenParcelIdsByCourierUserId(@Param("courierUserId") UUID courierUserId);

    /**
     * Per courier and status: open ASSIGNED and IN_PROGRESS assignments, plus the assignments
     * COMPLETED since {@code startOfDay}. Couriers with none are missing from the result.
     */
    @Query("""
            select new com.swiftdrop.dispatch.repository.WorkloadCount(a.courier.id, a.assignmentStatus, count(a))
            from DeliveryAssignment a
            where a.courier.id in :courierIds
              and ((a.endedAt is null
                    and a.assignmentStatus in (com.swiftdrop.dispatch.model.AssignmentStatus.ASSIGNED,
                                               com.swiftdrop.dispatch.model.AssignmentStatus.IN_PROGRESS))
                   or (a.assignmentStatus = com.swiftdrop.dispatch.model.AssignmentStatus.COMPLETED
                       and a.endedAt >= :startOfDay))
            group by a.courier.id, a.assignmentStatus
            """)
    List<WorkloadCount> countWorkload(@Param("courierIds") Collection<UUID> courierIds,
                                      @Param("startOfDay") Instant startOfDay);
}
