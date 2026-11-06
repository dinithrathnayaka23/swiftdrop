package com.swiftdrop.realtime.config;

import com.swiftdrop.common.messaging.RabbitConfig;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Base64UrlNamingStrategy;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The live-tracking queue: a copy of every parcel status event for the server-sent events push.
 *
 * <pre>
 *   [swiftdrop.parcel.events] --parcel.status.#--> swiftdrop.realtime.&lt;random&gt;  (this module)
 * </pre>
 *
 * <p>Unlike the notification and webhook queues (in {@link RabbitConfig}), this queue is
 * <b>temporary</b>: non-durable, exclusive and deleted when the backend stops. Live pushes only
 * keep open screens fresh, so there is nothing to replay after a restart (browsers reload the
 * current state over REST when they reconnect). Each backend instance gets its own queue, so
 * every instance sees every event if the backend is ever scaled out.
 *
 * <p>The listener reads it with {@code @RabbitListener(queues = "#{realtimeStatusQueue.name}")}.
 */
@Configuration
public class RealtimeQueueConfig {

    public static final String QUEUE_NAME_PREFIX = "swiftdrop.realtime.";

    @Bean
    public Queue realtimeStatusQueue() {
        return new AnonymousQueue(new Base64UrlNamingStrategy(QUEUE_NAME_PREFIX));
    }

    @Bean
    public Binding realtimeStatusBinding(TopicExchange parcelEventsExchange) {
        return BindingBuilder.bind(realtimeStatusQueue())
                .to(parcelEventsExchange)
                .with(RabbitConfig.PARCEL_STATUS_BINDING);
    }
}
