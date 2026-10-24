package com.energymonitor.device.adapter.in.mqtt;

import com.energymonitor.device.adapter.in.mqtt.dto.StatusMessage;
import com.energymonitor.device.adapter.in.mqtt.dto.TelemetryMessage;
import com.energymonitor.device.application.port.in.ReceiveDeviceReport;
import com.energymonitor.device.application.port.in.ReceiveDeviceReport.Outcome;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Keeps the connectivity of the devices from what they publish.
 *
 * <ul>
 *   <li>Every message on {@code energy-monitor/devices/+/telemetry} means the device is online and
 *       refreshes its {@code last_seen} with the server clock (the reading itself is stored by
 *       energy-monitor-backend, which subscribes to the same topic).</li>
 *   <li>The retained {@code online} / {@code offline} messages of
 *       {@code energy-monitor/devices/+/status}, including the last will the broker publishes when
 *       a device drops without saying goodbye.</li>
 * </ul>
 *
 * <p>A message that cannot be trusted or parsed is dropped and logged; the subscription goes on.
 */
public class ConnectivityMqttConsumer {

    private static final Logger log = LoggerFactory.getLogger(ConnectivityMqttConsumer.class);

    private final ObjectMapper objectMapper;
    private final ReceiveDeviceReport reports;

    public ConnectivityMqttConsumer(ObjectMapper objectMapper, ReceiveDeviceReport reports) {
        this.objectMapper = objectMapper;
        this.reports = reports;
    }

    @ServiceActivator(inputChannel = MqttConnectivityConfiguration.TELEMETRY_CHANNEL)
    public void handleTelemetry(Message<?> message) {
        Optional<String> topicDevice = MqttTopics.deviceId(message);
        if (topicDevice.isEmpty()) {
            log.warn("Ignoring telemetry on unexpected topic");
            return;
        }
        String deviceId = topicDevice.get();
        try {
            TelemetryMessage telemetry = objectMapper.readValue(message.getPayload().toString(),
                    TelemetryMessage.class);
            if (telemetry.deviceId() != null && !telemetry.deviceId().equals(deviceId)) {
                log.warn("Ignoring telemetry on the topic of {} that claims to be {}", deviceId,
                        telemetry.deviceId());
                return;
            }
            if (reports.online(deviceId, telemetry.rssi()) == Outcome.UNKNOWN_DEVICE) {
                log.warn("Ignoring telemetry of unknown device {}", deviceId);
            }
        } catch (JacksonException e) {
            log.warn("Failed to parse telemetry of device {}: {}", deviceId, e.getOriginalMessage());
        } catch (RuntimeException e) {
            log.warn("Failed to process telemetry of device {}: {}", deviceId, e.getMessage());
        }
    }

    @ServiceActivator(inputChannel = MqttConnectivityConfiguration.STATUS_CHANNEL)
    public void handleStatus(Message<?> message) {
        Optional<String> topicDevice = MqttTopics.deviceId(message);
        if (topicDevice.isEmpty()) {
            log.warn("Ignoring status on unexpected topic");
            return;
        }
        String deviceId = topicDevice.get();
        String payload = message.getPayload().toString();
        if (payload.isBlank()) {
            // An empty retained message only clears the topic.
            return;
        }
        try {
            StatusMessage status = objectMapper.readValue(payload, StatusMessage.class);
            if (status.deviceId() != null && !status.deviceId().equals(deviceId)) {
                log.warn("Ignoring status on the topic of {} that claims to be {}", deviceId,
                        status.deviceId());
                return;
            }
            Outcome outcome;
            if ("online".equalsIgnoreCase(status.status())) {
                outcome = reports.online(deviceId, status.rssi());
            } else if ("offline".equalsIgnoreCase(status.status())) {
                outcome = reports.offline(deviceId);
            } else {
                log.warn("Ignoring status '{}' of device {}", status.status(), deviceId);
                return;
            }
            if (outcome == Outcome.UNKNOWN_DEVICE) {
                log.warn("Ignoring status of unknown device {}", deviceId);
            }
        } catch (JacksonException e) {
            log.warn("Failed to parse status of device {}: {}", deviceId, e.getOriginalMessage());
        } catch (RuntimeException e) {
            log.warn("Failed to process status of device {}: {}", deviceId, e.getMessage());
        }
    }
}
