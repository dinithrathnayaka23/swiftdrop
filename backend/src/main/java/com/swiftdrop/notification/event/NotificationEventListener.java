package com.swiftdrop.notification.event;

import com.swiftdrop.common.messaging.RabbitConfig;
import com.swiftdrop.common.messaging.event.ParcelStatusChangedEvent;
import com.swiftdrop.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@link ParcelStatusChangedEvent} from {@code swiftdrop.notification.parcel-events}.
 * Triggers in-app notification creation, email dispatch, and SMS alerts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @RabbitListener(queues = RabbitConfig.NOTIFICATION_QUEUE)
    public void onParcelStatusChanged(ParcelStatusChangedEvent event) {
        log.info("Received parcel status event: parcelId={}, newStatus={}, eventId={}",
                event.parcelId(), event.newStatus(), event.eventId());
        try {
            notificationService.processParcelStatusChange(event);
        } catch (Exception ex) {
            log.error("Failed to process notification for status change event {}", event.eventId(), ex);
            // Re-throwing allows Spring RabbitMQ to route message to Dead-Letter Queue (DLQ) if retry limit is reached
            throw ex;
        }
    }
}

