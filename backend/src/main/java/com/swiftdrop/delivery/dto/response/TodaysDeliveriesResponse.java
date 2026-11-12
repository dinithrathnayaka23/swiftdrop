package com.swiftdrop.delivery.dto.response;

import java.util.List;

/** Today's Deliveries: the counts and the courier's open deliveries, oldest assignment first. */
public record TodaysDeliveriesResponse(CourierDeliveryCounts counts, List<CourierDeliveryItemResponse> deliveries) {
}
