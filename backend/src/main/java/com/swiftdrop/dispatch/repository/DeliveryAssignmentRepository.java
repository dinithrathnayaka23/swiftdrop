package com.swiftdrop.dispatch.repository;

import com.swiftdrop.dispatch.model.AssignmentStatus;
import com.swiftdrop.dispatch.model.DeliveryAssignment;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /** Whether the parcel has an open assignment (to anyone). */
    boolean existsByParcelIdAndEndedAtIsNull(UUID parcelId);

    /**
     * The parcel's open assignment, row-locked until the transaction ends, so a reassignment, an
     * unassignment and the status listener can't change it at the same time.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from DeliveryAssignment a where a.parcelId = :parcelId and a.endedAt is null")
    Optional<DeliveryAssignment> findOpenByParcelIdForUpdate(@Param("parcelId") UUID parcelId);

    /** All of a courier's assignments (open and ended). */
    Page<DeliveryAssignment> findByCourier_Id(UUID courierId, Pageable pageable);

    /** A courier's open assignments. */
    Page<DeliveryAssignment> findByCourier_IdAndEndedAtIsNull(UUID courierId, Pageable pageable);

    /** A courier's ended assignments. */
    Page<DeliveryAssignment> findByCourier_IdAndEndedAtIsNotNull(UUID courierId, Pageable pageable);

    // ---- Operations ----------------------------------------------------------------------------

    /** Open assignments with this status (e.g. IN_PROGRESS = active deliveries), with their couriers. */
    @EntityGraph(attributePaths = "courier")
    Page<DeliveryAssignment> findByEndedAtIsNullAndAssignmentStatus(AssignmentStatus status, Pageable pageable);

    long countByEndedAtIsNullAndAssignmentStatus(AssignmentStatus status);

    /** Assignments that ended with this status since {@code since} (e.g. COMPLETED or FAILED today). */
    long countByAssignmentStatusAndEndedAtGreaterThanEqual(AssignmentStatus status, Instant since);

    /**
     * Active couriers by today's workload (open + completed since {@code startOfDay}), busiest first;
     * ties by courier age (oldest first) so the cut-off is stable. Pass a page size for the limit.
     */
    @Query("""
            select new com.swiftdrop.dispatch.repository.CourierDayLoad(c.id, c.userId, c.availabilityStatus,
                sum(case when a.endedAt is null
                              and a.assignmentStatus = com.swiftdrop.dispatch.model.AssignmentStatus.ASSIGNED
                         then 1 else 0 end),
                sum(case when a.endedAt is null
                              and a.assignmentStatus = com.swiftdrop.dispatch.model.AssignmentStatus.IN_PROGRESS
                         then 1 else 0 end),
                sum(case when a.assignmentStatus = com.swiftdrop.dispatch.model.AssignmentStatus.COMPLETED
                              and a.endedAt >= :startOfDay
                         then 1 else 0 end))
            from Courier c left join DeliveryAssignment a on a.courier = c
            where c.active = true
            group by c.id, c.userId, c.availabilityStatus, c.createdAt
            order by sum(case when (a.endedAt is null
                                    and a.assignmentStatus in (com.swiftdrop.dispatch.model.AssignmentStatus.ASSIGNED,
                                                               com.swiftdrop.dispatch.model.AssignmentStatus.IN_PROGRESS))
                                   or (a.assignmentStatus = com.swiftdrop.dispatch.model.AssignmentStatus.COMPLETED
                                       and a.endedAt >= :startOfDay)
                              then 1 else 0 end) desc,
                     c.createdAt asc, c.id asc
            """)
    List<CourierDayLoad> findBusiestActiveCouriers(@Param("startOfDay") Instant startOfDay, Pageable limit);

    /** The FAILED assignments (failed attempts) of these parcels, with their couriers, in one query. */
    @Query("""
            select a from DeliveryAssignment a join fetch a.courier
            where a.parcelId in :parcelIds
              and a.assignmentStatus = com.swiftdrop.dispatch.model.AssignmentStatus.FAILED
            """)
    List<DeliveryAssignment> findFailedByParcelIds(@Param("parcelIds") Collection<UUID> parcelIds);

    /** The courier account of each of these parcels' open assignment; parcels without one are left out. */
    @Query("""
            select new com.swiftdrop.dispatch.repository.OpenAssignmentCourier(a.parcelId, c.userId)
            from DeliveryAssignment a join a.courier c
            where a.parcelId in :parcelIds and a.endedAt is null
            """)
    List<OpenAssignmentCourier> findOpenCouriersByParcelIds(@Param("parcelIds") Collection<UUID> parcelIds);

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
