package com.swiftdrop.realtime.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.common.messaging.RabbitConfig;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

class RealtimeQueueConfigTest {

    private final RealtimeQueueConfig config = new RealtimeQueueConfig();

    @Test
    void queueIsTemporaryAndNamedForThisModule() {
        Queue queue = config.realtimeStatusQueue();

        assertThat(queue.getName()).startsWith(RealtimeQueueConfig.QUEUE_NAME_PREFIX);
        assertThat(queue.isDurable()).isFalse();
        assertThat(queue.isExclusive()).isTrue();
        assertThat(queue.isAutoDelete()).isTrue();
    }

    @Test
    void receivesEveryParcelStatusEvent() {
        var exchange = new TopicExchange(RabbitConfig.PARCEL_EVENTS_EXCHANGE);

        Binding binding = config.realtimeStatusBinding(exchange);

        assertThat(binding.getExchange()).isEqualTo(RabbitConfig.PARCEL_EVENTS_EXCHANGE);
        assertThat(binding.getRoutingKey()).isEqualTo(RabbitConfig.PARCEL_STATUS_BINDING);
        assertThat(binding.getDestination()).startsWith(RealtimeQueueConfig.QUEUE_NAME_PREFIX);
    }
}
