package com.energymonitor.device.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;

@DisplayName("Home events queues")
class HomeEventsMessagingConfigurationTest {

    private final Declarables declarables = new HomeEventsMessagingConfiguration()
            .homeEventQueues("energy-monitor.home", "device-service.dlx");

    @Test
    @DisplayName("the durable queue takes home.deleted from the home exchange")
    void bindsHomeDeleted() {
        Binding binding = declarables.getDeclarablesByType(Binding.class).stream()
                .filter(b -> b.getDestination().equals(HomeEventsMessagingConfiguration.HOME_DELETED_QUEUE))
                .findFirst().orElseThrow();

        assertThat(binding.getExchange()).isEqualTo("energy-monitor.home");
        assertThat(binding.getRoutingKey()).isEqualTo("home.deleted");
    }

    @Test
    @DisplayName("a message that keeps failing goes to its dead-letter queue")
    void deadLetters() {
        Queue queue = declarables.getDeclarablesByType(Queue.class).stream()
                .filter(q -> q.getName().equals(HomeEventsMessagingConfiguration.HOME_DELETED_QUEUE))
                .findFirst().orElseThrow();

        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", "device-service.dlx")
                .containsEntry("x-dead-letter-routing-key", HomeEventsMessagingConfiguration.HOME_DELETED_QUEUE);
        assertThat(declarables.getDeclarablesByType(Queue.class)).extracting(Queue::getName)
                .contains(HomeEventsMessagingConfiguration.HOME_DELETED_QUEUE + ".dlq");
    }
}
