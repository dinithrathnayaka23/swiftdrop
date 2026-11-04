package com.swiftdrop.parcel.event;

import com.swiftdrop.common.messaging.RabbitConfig;
import com.swiftdrop.common.messaging.event.ParcelStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends {@link ParcelStatusChangedEvent}s to RabbitMQ once the status change is committed, so
 * consumers never see a change that was rolled back.
 *
 * <p>If the broker is down the change stays saved and the failure is logged; the status history
 * still holds the change. A transactional outbox (report section 4.1) would close this gap later.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParcelStatusEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(ParcelStatusChangedEvent event) {
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.PARCEL_EVENTS_EXCHANGE,
                    RabbitConfig.PARCEL_STATUS_CHANGED_KEY, event);
        } catch (AmqpException e) {
            log.error("Could not publish status event {} for parcel {}: {}", event.eventId(), event.parcelId(),
                    e.getClass().getSimpleName());
        }
    }
}
