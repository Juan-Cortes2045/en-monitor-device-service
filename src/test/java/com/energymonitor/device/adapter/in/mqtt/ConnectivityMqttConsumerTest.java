package com.energymonitor.device.adapter.in.mqtt;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.energymonitor.device.application.port.in.ReceiveDeviceReport;
import com.energymonitor.device.application.port.in.ReceiveDeviceReport.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * Connectivity from the broker: the topic, not the payload, identifies the device; a message that
 * cannot be trusted or parsed is dropped without stopping the subscription.
 *
 * <p>Plain Mockito test: the consumer is built by hand with a real {@code JsonMapper} and a
 * mocked use case, so no Spring context, broker or database is needed.
 */
@ExtendWith(MockitoExtension.class)
class ConnectivityMqttConsumerTest {

    private static final String DEVICE_ID = "DEV0000001";
    private static final String TELEMETRY = "energy-monitor/devices/" + DEVICE_ID + "/telemetry";
    private static final String STATUS = "energy-monitor/devices/" + DEVICE_ID + "/status";

    @Mock
    private ReceiveDeviceReport reports;

    private ConnectivityMqttConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ConnectivityMqttConsumer(JsonMapper.builder().build(), reports);
    }

    private static Message<String> message(String topic, String payload) {
        return MessageBuilder.withPayload(payload).setHeader(MqttHeaders.RECEIVED_TOPIC, topic).build();
    }

    private static String telemetry(String deviceId) {
        String id = deviceId == null ? "" : "\"deviceId\":\"" + deviceId + "\",";
        return "{" + id + "\"dateTime\":\"2026-10-13T20:17:03Z\",\"voltage\":231.4,\"current\":1.234,"
                + "\"activePower\":285.5,\"storedEnergy\":12.847,\"rssi\":-58,\"sequence\":42}";
    }

    private static String status(String deviceId, String state) {
        return "{\"deviceId\":\"" + deviceId + "\",\"deviceCode\":\"EM204\",\"status\":\"" + state
                + "\",\"rssi\":-61,\"ip\":\"192.168.1.40\",\"firmwareVersion\":\"1.3.8\",\"uptimeSeconds\":120}";
    }

    @Test
    @DisplayName("telemetry marks the device online with its signal")
    void telemetryMarksOnline() {
        when(reports.online(DEVICE_ID, -58)).thenReturn(Outcome.RECORDED);

        consumer.handleTelemetry(message(TELEMETRY, telemetry(DEVICE_ID)));

        verify(reports).online(DEVICE_ID, -58);
    }

    @Test
    @DisplayName("telemetry without a deviceId field trusts the topic")
    void telemetryWithoutIdTrustsTopic() {
        when(reports.online(DEVICE_ID, -58)).thenReturn(Outcome.RECORDED);

        consumer.handleTelemetry(message(TELEMETRY, telemetry(null)));

        verify(reports).online(DEVICE_ID, -58);
    }

    @Test
    @DisplayName("telemetry claiming another device on this topic is dropped")
    void telemetryOfAnotherDeviceDropped() {
        consumer.handleTelemetry(message(TELEMETRY, telemetry("DEV0000002")));

        verifyNoInteractions(reports);
    }

    @Test
    @DisplayName("telemetry of an unknown device only logs")
    void telemetryOfUnknownDevice() {
        when(reports.online(DEVICE_ID, -58)).thenReturn(Outcome.UNKNOWN_DEVICE);

        assertThatCode(() -> consumer.handleTelemetry(message(TELEMETRY, telemetry(DEVICE_ID))))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"energy-monitor/devices/telemetry", "other/" + DEVICE_ID + "/x/y/z"})
    @DisplayName("a message on an unexpected topic is dropped")
    void unexpectedTopicDropped(String topic) {
        consumer.handleTelemetry(message(topic, telemetry(DEVICE_ID)));
        consumer.handleStatus(message(topic, status(DEVICE_ID, "online")));

        verifyNoInteractions(reports);
    }

    @Test
    @DisplayName("a message without a topic is dropped")
    void missingTopicDropped() {
        consumer.handleTelemetry(MessageBuilder.withPayload(telemetry(DEVICE_ID)).build());

        verifyNoInteractions(reports);
    }

    @Test
    @DisplayName("malformed telemetry is dropped without breaking the subscription")
    void malformedTelemetryDropped() {
        assertThatCode(() -> consumer.handleTelemetry(message(TELEMETRY, "{not json")))
                .doesNotThrowAnyException();

        verifyNoInteractions(reports);
    }

    @Test
    @DisplayName("a failing use case does not break the subscription")
    void failingUseCaseSwallowed() {
        when(reports.online(anyString(), any())).thenThrow(new IllegalStateException("database down"));

        assertThatCode(() -> consumer.handleTelemetry(message(TELEMETRY, telemetry(DEVICE_ID))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an online status marks the device online with its signal")
    void onlineStatus() {
        when(reports.online(DEVICE_ID, -61)).thenReturn(Outcome.RECORDED);

        consumer.handleStatus(message(STATUS, status(DEVICE_ID, "online")));

        verify(reports).online(DEVICE_ID, -61);
    }

    @Test
    @DisplayName("an offline status, as the last will, marks the device offline")
    void offlineStatus() {
        when(reports.offline(DEVICE_ID)).thenReturn(Outcome.RECORDED);

        consumer.handleStatus(message(STATUS, status(DEVICE_ID, "OFFLINE")));

        verify(reports).offline(DEVICE_ID);
    }

    @Test
    @DisplayName("an empty retained status only clears the topic")
    void emptyStatusIgnored() {
        consumer.handleStatus(message(STATUS, ""));

        verifyNoInteractions(reports);
    }

    @Test
    @DisplayName("an unknown status value is dropped")
    void unknownStatusDropped() {
        consumer.handleStatus(message(STATUS, status(DEVICE_ID, "rebooting")));

        verifyNoInteractions(reports);
    }

    @Test
    @DisplayName("a status claiming another device on this topic is dropped")
    void statusOfAnotherDeviceDropped() {
        consumer.handleStatus(message(STATUS, status("DEV0000002", "online")));

        verifyNoInteractions(reports);
    }

    @Test
    @DisplayName("a malformed status is dropped")
    void malformedStatusDropped() {
        assertThatCode(() -> consumer.handleStatus(message(STATUS, "[]")))
                .doesNotThrowAnyException();

        verifyNoInteractions(reports);
    }
}
