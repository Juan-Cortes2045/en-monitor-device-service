package com.energymonitor.device.adapter.in.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.energymonitor.device.application.port.in.ReceiveDeviceReport;
import com.energymonitor.device.application.port.in.ReceiveDeviceReport.Outcome;
import com.energymonitor.device.infrastructure.JwtKeyedTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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

    @MockitoBean
    private ReceiveDeviceReport reports;

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

    @Test
    @DisplayName("each subscription hands its messages to the handler")
    void deliversToTheHandler() {
        given(reports.online("DEV0000001", -60)).willReturn(Outcome.RECORDED);
        given(reports.offline("DEV0000001")).willReturn(Outcome.RECORDED);

        // What Paho receives goes to the adapter's output channel; a channel nobody subscribed
        // to would throw here, and Paho drops the connection on that.
        adapter("energy-monitor/devices/+/telemetry").getOutputChannel().send(
                message("energy-monitor/devices/DEV0000001/telemetry", "{\"rssi\":-60}"));
        adapter("energy-monitor/devices/+/status").getOutputChannel().send(
                message("energy-monitor/devices/DEV0000001/status", "{\"status\":\"offline\"}"));

        verify(reports).online("DEV0000001", -60);
        verify(reports).offline("DEV0000001");
    }

    private MqttPahoMessageDrivenChannelAdapter adapter(String topic) {
        return context.getBeansOfType(MqttPahoMessageDrivenChannelAdapter.class).values().stream()
                .filter(adapter -> adapter.getTopic()[0].equals(topic))
                .findFirst()
                .orElseThrow();
    }

    private static org.springframework.messaging.Message<String> message(String topic, String payload) {
        return MessageBuilder.withPayload(payload).setHeader(MqttHeaders.RECEIVED_TOPIC, topic).build();
    }
}
