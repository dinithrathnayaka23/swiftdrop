package com.swiftdrop.dispatch.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.swiftdrop.common.messaging.RabbitConfig;
import com.swiftdrop.common.messaging.event.ParcelStatusChangedEvent;
import com.swiftdrop.dispatch.service.AssignmentLifecycleService;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

class DispatchQueueConfigTest {

    private final DispatchQueueConfig config = new DispatchQueueConfig();

    @Test
    void queueIsDurableAndDeadLettersLikeTheOtherConsumers() {
        Queue queue = config.dispatchStatusQueue();

        assertThat(queue.getName()).isEqualTo("swiftdrop.dispatch.parcel-events");
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.isExclusive()).isFalse();
        assertThat(queue.isAutoDelete()).isFalse();
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", RabbitConfig.DEAD_LETTER_EXCHANGE)
                .containsEntry("x-dead-letter-routing-key", RabbitConfig.DEAD_LETTER_KEY);
    }

    @Test
    void receivesEveryParcelStatusEvent() {
        Binding binding = config.dispatchStatusBinding(new TopicExchange(RabbitConfig.PARCEL_EVENTS_EXCHANGE));

        assertThat(binding.getExchange()).isEqualTo(RabbitConfig.PARCEL_EVENTS_EXCHANGE);
        assertThat(binding.getRoutingKey()).isEqualTo(RabbitConfig.PARCEL_STATUS_BINDING);
        assertThat(binding.getDestination()).isEqualTo(DispatchQueueConfig.QUEUE);
    }

    @Test
    void theListenerReadsThisQueueAndHandsEachEventOn() throws Exception {
        Method method = AssignmentStatusListener.class.getMethod("onStatusChanged", ParcelStatusChangedEvent.class);
        assertThat(method.getAnnotation(RabbitListener.class).queues()).containsExactly(DispatchQueueConfig.QUEUE);

        AssignmentLifecycleService lifecycle = Mockito.mock(AssignmentLifecycleService.class);
        ParcelStatusChangedEvent event = new ParcelStatusChangedEvent(null, null, 1, null, null, null, null, null, 1,
                null, null, null);
        new AssignmentStatusListener(lifecycle).onStatusChanged(event);
        verify(lifecycle).apply(event);
    }
}
