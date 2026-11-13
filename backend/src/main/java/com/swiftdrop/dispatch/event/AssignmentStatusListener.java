package com.swiftdrop.dispatch.event;

import com.swiftdrop.common.messaging.event.ParcelStatusChangedEvent;
import com.swiftdrop.dispatch.service.AssignmentLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Reads parcel status events from {@link DispatchQueueConfig#QUEUE} and updates the parcel's open
 * assignment ({@link AssignmentLifecycleService}). A database error is thrown on, so the message is
 * retried and then dead-lettered (spring.rabbitmq.listener.simple in application.yml).
 */
@Component
@RequiredArgsConstructor
public class AssignmentStatusListener {

    private final AssignmentLifecycleService lifecycle;

    @RabbitListener(queues = DispatchQueueConfig.QUEUE)
    public void onStatusChanged(ParcelStatusChangedEvent event) {
        lifecycle.apply(event);
    }
}
