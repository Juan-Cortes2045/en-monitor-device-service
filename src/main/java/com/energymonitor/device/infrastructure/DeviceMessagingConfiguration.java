package com.energymonitor.device.infrastructure;

import com.energymonitor.device.adapter.out.messaging.DeviceOutboxPublisher;
import com.energymonitor.device.adapter.out.messaging.DeviceOutboxRelay;
import com.energymonitor.device.adapter.out.messaging.RabbitDeviceOutboxPublisher;
import com.energymonitor.device.adapter.out.persistence.repository.DeviceOutboxRepository;
import java.time.Clock;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * The broker side of the outbox: the exchange this service publishes to, and the relay.
 *
 * <p>Only the exchange is declared here. The queues belong to the consumers, which declare and
 * bind their own; until one exists for a message type, {@link RabbitDeviceOutboxPublisher} gets
 * the message back and the relay keeps it pending.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DeviceMessagingProperties.class)
public class DeviceMessagingConfiguration {

    @Bean
    public TopicExchange deviceExchange(DeviceMessagingProperties properties) {
        return new TopicExchange(properties.exchange(), true, false);
    }

    /** A template of its own, mandatory, so a message no queue takes comes back. */
    @Bean
    public DeviceOutboxPublisher deviceOutboxPublisher(ConnectionFactory connections,
                                                       DeviceMessagingProperties properties) {
        return new RabbitDeviceOutboxPublisher(new RabbitTemplate(connections), properties.exchange(),
                properties.confirmTimeout());
    }

    @Bean
    public DeviceOutboxRelay deviceOutboxRelay(DeviceOutboxRepository outbox, DeviceOutboxPublisher publisher,
                                               DeviceMessagingProperties properties, Clock clock) {
        return new DeviceOutboxRelay(outbox, publisher, clock, properties.batchSize());
    }

    /** Runs the relay on a fixed delay, so a slow run never overlaps the next one. */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(name = "messaging.relay-enabled", havingValue = "true", matchIfMissing = true)
    static class RelaySchedule implements SchedulingConfigurer {

        private final DeviceOutboxRelay relay;
        private final DeviceMessagingProperties properties;

        RelaySchedule(DeviceOutboxRelay relay, DeviceMessagingProperties properties) {
            this.relay = relay;
            this.properties = properties;
        }

        @Override
        public void configureTasks(ScheduledTaskRegistrar registrar) {
            registrar.addFixedDelayTask(relay::relay, properties.pollInterval());
        }
    }
}
