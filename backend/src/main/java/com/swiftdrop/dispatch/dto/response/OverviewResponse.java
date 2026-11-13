package com.swiftdrop.dispatch.dto.response;

import com.swiftdrop.dispatch.model.CourierAvailability;
import com.swiftdrop.settings.model.FailedDeliveryAction;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The Operations Overview: today's numbers (Sri Lanka time) and the busiest couriers. The definitions
 * are in docs/dispatch.md. Day-on-day deltas and merchant totals are not stored, so not returned.
 *
 * @param date            today, Sri Lanka time
 * @param defaultAction   the System Settings default for a failed delivery
 * @param courierWorkload at most 5 active couriers, busiest first
 */
public record OverviewResponse(
        LocalDate date,
        FailedDeliveryAction defaultAction,
        Kpis kpis,
        TodayStatus todayStatus,
        List<CourierLoad> courierWorkload) {

    /**
     * @param unassignedParcels CREATED parcels without an open assignment (never below 0)
     * @param activeDeliveries  PICKED_UP + IN_TRANSIT + OUT_FOR_DELIVERY parcels
     * @param needsDecision     FAILED_ATTEMPT parcels waiting for retry or close
     */
    public record Kpis(long unassignedParcels, long activeDeliveries, long couriersOnline, long couriersOnBreak,
                       long couriersTotal, long completedToday, long failedToday, long needsDecision) {
    }

    /** @param inTransit PICKED_UP + IN_TRANSIT parcels */
    public record TodayStatus(long completed, long inTransit, long outForDelivery, long failed) {
    }

    /** @param pending open assignments not started yet (ASSIGNED) */
    public record CourierLoad(UUID courierId, String name, long todaysTotal, long completed, long inProgress,
                              long pending, CourierAvailability availability) {
    }
}
