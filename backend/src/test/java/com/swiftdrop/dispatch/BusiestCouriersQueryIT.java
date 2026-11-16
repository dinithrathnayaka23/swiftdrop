package com.swiftdrop.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.IntegrationTestBase;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.dispatch.model.CourierAvailability;
import com.swiftdrop.dispatch.repository.CourierDayLoad;
import com.swiftdrop.dispatch.repository.DeliveryAssignmentRepository;
import com.swiftdrop.parcel.dto.NewAddress;
import com.swiftdrop.parcel.dto.NewParcel;
import com.swiftdrop.parcel.model.ParcelType;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.user.dto.NewUser;
import com.swiftdrop.user.model.AccountStatus;
import com.swiftdrop.user.service.UserService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The overview's workload query on real data: only today's work counts (open, or completed since
 * midnight), however much older history a courier has, and couriers with no work today stay in.
 */
class BusiestCouriersQueryIT extends IntegrationTestBase {

    @Autowired private DeliveryAssignmentRepository assignmentRepository;
    @Autowired private ParcelService parcelService;
    @Autowired private UserService userService;
    @Autowired private JdbcTemplate jdbc;

    /** A fixed "midnight" between yesterday's and today's rows below. */
    private static final Instant START_OF_DAY = Instant.now().minusSeconds(3600);

    /** Couriers made here; their hand-made assignments are deleted after each test. */
    private final List<UUID> couriers = new ArrayList<>();

    /**
     * The rows inserted here (e.g. IN_PROGRESS on a parcel that is still CREATED) are not states the app
     * produces; remove them so later tests sharing the database see consistent data.
     */
    @AfterEach
    void removeTheHandMadeAssignments() {
        for (UUID courier : couriers) {
            jdbc.update("DELETE FROM delivery_assignments WHERE courier_id = ?", courier);
            jdbc.update("UPDATE couriers SET is_active = FALSE, availability_status = 'OFFLINE' WHERE courier_id = ?",
                    courier);
        }
        couriers.clear();
    }

    private UUID newUser(Role role) {
        return userService.createUser(NewUser.withPassword("Ramesh Perera",
                role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.swiftdrop.local", "+94771234567", "x",
                role, AccountStatus.ACTIVE)).id();
    }

    /** An active courier row for a new COURIER account. */
    private UUID newCourier(UUID userId) {
        UUID courierId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO couriers (courier_id, user_id, vehicle_type, vehicle_registration, availability_status,
                                      is_active, created_at)
                VALUES (?, ?, 'Motorbike', ?, 'ONLINE', TRUE, now())""",
                courierId, userId, "IT-" + courierId.toString().substring(0, 8).toUpperCase());
        couriers.add(courierId);
        return courierId;
    }

    private UUID newParcel(UUID customerId) {
        NewAddress address = new NewAddress("Kasun Perera", "+94771234567", "12 Galle Road", null, "Kandy", "Central",
                null, null);
        return parcelService.createParcel(new NewParcel(customerId, null, null, address, address, ParcelType.PARCEL,
                BigDecimal.ONE, null, null, null, null, null, null, BigDecimal.TEN)).id();
    }

    /** One assignment row; {@code endedAt} null keeps it open. */
    private void assignment(UUID courierId, UUID byUserId, String status, Instant assignedAt, Instant endedAt) {
        jdbc.update("""
                INSERT INTO delivery_assignments (parcel_id, courier_id, assigned_by_user_id, assignment_status,
                                                  assigned_at, ended_at)
                VALUES (?, ?, ?, ?, ?, ?)""",
                newParcel(byUserId), courierId, byUserId, status, Timestamp.from(assignedAt),
                endedAt == null ? null : Timestamp.from(endedAt));
    }

    private CourierDayLoad loadOf(UUID courierId) {
        // A large limit so this courier is on the list whatever other tests left in the database.
        return assignmentRepository.findBusiestActiveCouriers(START_OF_DAY, PageRequest.of(0, 10_000)).stream()
                .filter(load -> load.courierId().equals(courierId)).findFirst().orElseThrow();
    }

    @Test
    void oldHistoryDoesNotCountAndTodaysWorkDoes() {
        UUID userId = newUser(Role.COURIER);
        UUID customer = newUser(Role.CUSTOMER);
        UUID courier = newCourier(userId);
        Instant yesterday = START_OF_DAY.minusSeconds(86_400);
        // Old history: completed and failed before midnight, plus one reassigned.
        for (int i = 0; i < 5; i++) {
            assignment(courier, customer, "COMPLETED", yesterday, yesterday.plusSeconds(600));
        }
        assignment(courier, customer, "FAILED", yesterday, yesterday.plusSeconds(900));
        assignment(courier, customer, "REASSIGNED", yesterday, yesterday.plusSeconds(60));
        assignment(courier, customer, "COMPLETED", yesterday, START_OF_DAY.minusSeconds(1)); // a second too early
        // Today: one completed (exactly at midnight counts), one in progress, one not started.
        assignment(courier, customer, "COMPLETED", yesterday, START_OF_DAY);
        assignment(courier, customer, "IN_PROGRESS", START_OF_DAY.plusSeconds(60), null);
        assignment(courier, customer, "ASSIGNED", START_OF_DAY.plusSeconds(120), null);

        CourierDayLoad load = loadOf(courier);

        assertThat(load.userId()).isEqualTo(userId);
        assertThat(load.availability()).isEqualTo(CourierAvailability.ONLINE);
        assertThat(load.completedToday()).isEqualTo(1);
        assertThat(load.inProgress()).isEqualTo(1);
        assertThat(load.pending()).isEqualTo(1);
        assertThat(load.todaysTotal()).isEqualTo(3);
    }

    @Test
    void aCourierWithOnlyOldHistoryIsStillListedWithNothingToday() {
        UUID customer = newUser(Role.CUSTOMER);
        UUID courier = newCourier(newUser(Role.COURIER));
        Instant lastWeek = START_OF_DAY.minusSeconds(7 * 86_400);
        assignment(courier, customer, "COMPLETED", lastWeek, lastWeek.plusSeconds(600));
        assignment(courier, customer, "COMPLETED", lastWeek, lastWeek.plusSeconds(900));

        CourierDayLoad load = loadOf(courier);

        assertThat(load.todaysTotal()).isZero();
        assertThat(load.completedToday()).isZero();
    }
}
