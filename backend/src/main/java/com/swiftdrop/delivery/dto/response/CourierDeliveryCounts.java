package com.swiftdrop.delivery.dto.response;

/**
 * The cards above Today's Deliveries, worked out from the parcels' statuses.
 *
 * @param total      all open deliveries in the list
 * @param assigned   not started yet (still CREATED, waiting for pickup)
 * @param inProgress picked up and on the way (PICKED_UP, IN_TRANSIT or OUT_FOR_DELIVERY)
 */
public record CourierDeliveryCounts(int total, int assigned, int inProgress) {
}
