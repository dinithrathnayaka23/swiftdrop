package com.swiftdrop.common.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for parcel events.
 *
 * <pre>
 *   publisher (parcel/dispatch) --"parcel.status.changed"--> [swiftdrop.parcel.events] (topic)
 *        |-- parcel.status.# --> swiftdrop.notification.parcel-events  (notification module)
 *        |-- parcel.status.# --> swiftdrop.webhook.parcel-events       (merchant webhooks)
 *   rejected messages --> [swiftdrop.parcel.events.dlx] --> swiftdrop.parcel.events.dlq
 * </pre>
 *
 * Spring Boot applies the JSON converter below to RabbitTemplate and @RabbitListener automatically.
 */
@Configuration
public class RabbitConfig {

    public static final String PARCEL_EVENTS_EXCHANGE = "swiftdrop.parcel.events";
    public static final String PARCEL_STATUS_CHANGED_KEY = "parcel.status.changed";
    public static final String PARCEL_STATUS_BINDING = "parcel.status.#";

    public static final String NOTIFICATION_QUEUE = "swiftdrop.notification.parcel-events";
    public static final String WEBHOOK_QUEUE = "swiftdrop.webhook.parcel-events";

    public static final String DEAD_LETTER_EXCHANGE = "swiftdrop.parcel.events.dlx";
    public static final String DEAD_LETTER_QUEUE = "swiftdrop.parcel.events.dlq";
    public static final String DEAD_LETTER_KEY = "dead";

    @Bean
    public TopicExchange parcelEventsExchange() {
        return new TopicExchange(PARCEL_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Queue notificationQueue() {
        return consumerQueue(NOTIFICATION_QUEUE);
    }

    @Bean
    public Queue webhookQueue() {
        return consumerQueue(WEBHOOK_QUEUE);
    }

    @Bean
    public Binding notificationBinding() {
        return BindingBuilder.bind(notificationQueue()).to(parcelEventsExchange()).with(PARCEL_STATUS_BINDING);
    }

    @Bean
    public Binding webhookBinding() {
        return BindingBuilder.bind(webhookQueue()).to(parcelEventsExchange()).with(PARCEL_STATUS_BINDING);
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(DEAD_LETTER_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    private static Queue consumerQueue(String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_KEY)
                .build();
    }
}
