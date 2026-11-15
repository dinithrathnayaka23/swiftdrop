package com.swiftdrop.delivery.dto.response;

/**
 * The cards above Today's Deliveries.
 *
 * @param total          all open deliveries in the list
 * @param assigned       not started yet (still CREATED, waiting for pickup)
 * @param inProgress     picked up and on the way (PICKED_UP, IN_TRANSIT or OUT_FOR_DELIVERY)
 * @param completedToday delivered by this courier since midnight (admin's time zone); failed attempts
 *                       don't count, as in dispatch's workload numbers
 */
public record CourierDeliveryCounts(int total, int assigned, int inProgress, int completedToday) {
}
