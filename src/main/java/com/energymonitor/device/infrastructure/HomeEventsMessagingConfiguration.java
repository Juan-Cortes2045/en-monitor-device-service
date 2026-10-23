package com.energymonitor.device.infrastructure;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The queue through which this service hears that energy-monitor-backend deleted a home, so the
 * modules linked to it are unlinked and told to offer Bluetooth again.
 *
 * <p>Durable, and dead-lettered to {@value #HOME_DELETED_QUEUE}{@code .dlq} once a message has
 * failed every retry. Declaring it is also what lets the backend's outbox publish
 * {@code home.deleted}: until a queue is bound the message comes back and waits.
 */
@Configuration(proxyBeanMethods = false)
public class HomeEventsMessagingConfiguration {

    public static final String HOME_DELETED_QUEUE = "device-service.device.home-deleted";

    @Bean
    public Declarables homeEventQueues(
            @Value("${messaging.home-exchange}") String homeExchange,
            @Value("${messaging.dead-letter-exchange}") String deadLetterExchange) {
        TopicExchange home = new TopicExchange(homeExchange, true, false);
        DirectExchange deadLetters = new DirectExchange(deadLetterExchange, true, false);
        Queue homeDeleted = QueueBuilder.durable(HOME_DELETED_QUEUE)
                .deadLetterExchange(deadLetterExchange)
                .deadLetterRoutingKey(HOME_DELETED_QUEUE)
                .build();
        Queue homeDeletedDead = QueueBuilder.durable(HOME_DELETED_QUEUE + ".dlq").build();
        return new Declarables(home, deadLetters, homeDeleted, homeDeletedDead,
                BindingBuilder.bind(homeDeleted).to(home).with("home.deleted"),
                BindingBuilder.bind(homeDeletedDead).to(deadLetters).with(HOME_DELETED_QUEUE));
    }
}
