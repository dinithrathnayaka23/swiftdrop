package com.swiftdrop.dispatch.event;

import com.swiftdrop.common.messaging.RabbitConfig;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The dispatch module's copy of every parcel status event, used to keep assignments in step with
 * the parcel ({@link AssignmentStatusListener}).
 *
 * <pre>
 *   [swiftdrop.parcel.events] --parcel.status.#--> swiftdrop.dispatch.parcel-events  (this module)
 *   rejected messages --> [swiftdrop.parcel.events.dlx] --> swiftdrop.parcel.events.dlq
 * </pre>
 *
 * <p>Durable, with the same dead-letter arguments as the notification and webhook queues in
 * {@link RabbitConfig}: closing assignments must survive a restart, and a message that keeps
 * failing goes to the shared dead-letter queue.
 */
@Configuration
public class DispatchQueueConfig {

    public static final String QUEUE = "swiftdrop.dispatch.parcel-events";

    @Bean
    public Queue dispatchStatusQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(RabbitConfig.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RabbitConfig.DEAD_LETTER_KEY)
                .build();
    }

    @Bean
    public Binding dispatchStatusBinding(TopicExchange parcelEventsExchange) {
        return BindingBuilder.bind(dispatchStatusQueue())
                .to(parcelEventsExchange)
                .with(RabbitConfig.PARCEL_STATUS_BINDING);
    }
}
