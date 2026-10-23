package com.energymonitor.device.adapter.in.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import com.energymonitor.device.infrastructure.JwtKeyedTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;

/**
 * The subscriptions wire up and the service starts even with no broker to reach: the adapters
 * keep retrying in the background instead of failing the start. Nothing listens on port 9, so no
 * running broker or service is ever disturbed.
 */
@SpringBootTest(properties = {
        "mqtt.inbound.enabled=true",
        "mqtt.broker-url=tcp://127.0.0.1:9",
        "mqtt.connection-timeout=1"
})
@DisplayName("MQTT connectivity wiring")
class MqttConnectivityConfigurationTest extends JwtKeyedTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("one subscription per topic, and the handler")
    void wiresBothSubscriptions() {
        var adapters = context.getBeansOfType(MqttPahoMessageDrivenChannelAdapter.class).values();

        assertThat(adapters).hasSize(2);
        assertThat(adapters).extracting(adapter -> adapter.getTopic()[0])
                .containsExactlyInAnyOrder("energy-monitor/devices/+/telemetry",
                        "energy-monitor/devices/+/status");
        assertThat(context.getBean(ConnectivityMqttConsumer.class)).isNotNull();
    }
}
