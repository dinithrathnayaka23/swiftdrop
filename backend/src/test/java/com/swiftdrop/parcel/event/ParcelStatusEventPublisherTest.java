package com.swiftdrop.parcel.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.swiftdrop.common.messaging.RabbitConfig;
import com.swiftdrop.common.messaging.event.ParcelStatusChangedEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class ParcelStatusEventPublisherTest {

    @Mock private RabbitTemplate rabbitTemplate;
    @InjectMocks private ParcelStatusEventPublisher publisher;

    private static ParcelStatusChangedEvent event() {
        return new ParcelStatusChangedEvent(UUID.randomUUID(), ParcelStatusChangedEvent.EVENT_TYPE,
                ParcelStatusChangedEvent.SCHEMA_VERSION, UUID.randomUUID(), "SD7KQ2MX9TRB4H", null, "CREATED",
                "PICKED_UP", 2, UUID.randomUUID(), UUID.randomUUID(), Instant.now());
    }

    @Test
    void sendsTheEventToTheParcelEventsExchange() {
        ParcelStatusChangedEvent event = event();

        publisher.publish(event);

        verify(rabbitTemplate).convertAndSend(RabbitConfig.PARCEL_EVENTS_EXCHANGE,
                RabbitConfig.PARCEL_STATUS_CHANGED_KEY, event);
    }

    @Test
    void aBrokerOutageDoesNotFailTheCallerBecauseTheChangeIsAlreadySaved() {
        doThrow(new AmqpConnectException(new RuntimeException("down")))
                .when(rabbitTemplate).convertAndSend(anyString(), eq(RabbitConfig.PARCEL_STATUS_CHANGED_KEY),
                        any(Object.class));

        assertThatCode(() -> publisher.publish(event())).doesNotThrowAnyException();
    }
}
