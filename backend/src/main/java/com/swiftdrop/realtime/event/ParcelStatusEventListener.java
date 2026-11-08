package com.swiftdrop.realtime.event;

import com.swiftdrop.common.messaging.event.ParcelStatusChangedEvent;
import com.swiftdrop.realtime.dto.LiveStatusUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Reads parcel status events from this module's own queue ({@code RealtimeQueueConfig}) and
 * announces each new one inside the app as a {@link LiveStatusUpdate}, for the open live streams.
 *
 * <p>Duplicates and out-of-order events are dropped ({@link RecentStatusEvents}). Nothing here
 * throws for a bad event: a live push is only a hint, so a failure must not retry or block the
 * queue.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParcelStatusEventListener {

    private final RecentStatusEvents recentEvents;
    private final ApplicationEventPublisher eventPublisher;

    @RabbitListener(queues = "#{realtimeStatusQueue.name}")
    public void onStatusChanged(ParcelStatusChangedEvent event) {
        if (event == null || event.eventId() == null || event.parcelId() == null || event.trackingNumber() == null
                || event.newStatus() == null) {
            log.warn("Ignoring an incomplete parcel status event");
            return;
        }
        if (!recentEvents.firstTimeAndNewest(event)) {
            log.debug("Skipping status event {} for parcel {}: already handled or older", event.eventId(),
                    event.parcelId());
            return;
        }
        eventPublisher.publishEvent(LiveStatusUpdate.from(event));
    }
}
