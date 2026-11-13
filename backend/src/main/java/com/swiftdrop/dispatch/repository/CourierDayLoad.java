package com.swiftdrop.dispatch.repository;

import com.swiftdrop.dispatch.model.CourierAvailability;
import java.util.UUID;

/**
 * One courier's work today, for the Operations Overview.
 *
 * @param pending        open assignments not started yet (ASSIGNED)
 * @param inProgress     open assignments being delivered (IN_PROGRESS)
 * @param completedToday assignments COMPLETED since local midnight
 */
public record CourierDayLoad(UUID courierId, UUID userId, CourierAvailability availability, long pending,
                             long inProgress, long completedToday) {

    /** Open plus completed today, like {@code CourierWorkload.todaysTotal}. */
    public long todaysTotal() {
        return pending + inProgress + completedToday;
    }
}
