package com.energymonitor.device.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.energymonitor.device.application.port.in.AuthorizeBrokerAccess.Access;
import com.energymonitor.device.application.port.out.DevicePersistencePort;
import com.energymonitor.device.application.usecase.BrokerAccessService;
import com.energymonitor.device.domain.model.Device;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BrokerAccessServiceTest {

    private static final Device DEVICE = Device.create("DEV0000001", "Medidor", "appl000001", null, null,
            Instant.parse("2026-10-01T00:00:00Z"), "EM204", "device-api-key-123456");

    private final DevicePersistencePort devices = new DevicePersistencePort() {
        @Override
        public void save(Device device) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Device> findById(String deviceId) {
            return DEVICE.idDevice().equals(deviceId) ? Optional.of(DEVICE) : Optional.empty();
        }

        @Override
        public Optional<Device> findByDeviceCode(String deviceCode) {
            return DEVICE.deviceCode().equals(deviceCode) ? Optional.of(DEVICE) : Optional.empty();
        }
    };

    private final BrokerAccessService service = new BrokerAccessService(devices,
            "em-device-service", "service-secret", "em-measurement-service", "measurement-secret");

    @Test
    void deviceLogsInWithItsCodeAndApiKey() {
        assertTrue(service.authenticate("EM204", "device-api-key-123456"));
        assertFalse(service.authenticate("EM204", "wrong"));
        assertFalse(service.authenticate("EM999", "device-api-key-123456"));
        assertFalse(service.authenticate("EM204", ""));
        assertFalse(service.authenticate(null, null));
    }

    @Test
    void theStoredHashIsNotAPassword() {
        // Somebody who read the table holds only the hash; the broker hashes what it receives,
        // so the hash itself does not log in.
        assertFalse(service.authenticate("EM204", DEVICE.apiKeyHash()));
    }

    @Test
    void theRetiredBackendUserNoLongerLogsIn() {
        // energy-monitor-backend stopped being a broker client: the readings are the measurement
        // service's. Its old user is just an unknown name now.
        assertFalse(service.authenticate("em-backend", "backend-secret"));
        assertFalse(service.authorize("em-backend", "energy-monitor/devices/+/telemetry", Access.SUBSCRIBE));
    }

    @Test
    void serviceLogsInWithItsConfiguredCredentials() {
        assertTrue(service.authenticate("em-device-service", "service-secret"));
        assertFalse(service.authenticate("em-device-service", "measurement-secret"));
        assertFalse(service.authenticate("em-measurement-service", "service-secret"));
    }

    @Test
    void platformClientWithoutConfiguredPasswordIsRefused() {
        BrokerAccessService unconfigured = new BrokerAccessService(devices, "em-device-service", "",
                "em-measurement-service", "");
        assertFalse(unconfigured.authenticate("em-measurement-service", "anything"));
        assertFalse(unconfigured.authenticate("em-device-service", "anything"));
    }

    @Test
    void measurementServiceLogsInWithItsConfiguredCredentials() {
        assertTrue(service.authenticate("em-measurement-service", "measurement-secret"));
        assertFalse(service.authenticate("em-measurement-service", "device-api-key-123456"));
    }

    @Test
    void devicePublishesOnlyOnItsOwnTopics() {
        assertTrue(service.authorize("EM204", "energy-monitor/devices/DEV0000001/telemetry", Access.WRITE));
        assertTrue(service.authorize("EM204", "energy-monitor/devices/DEV0000001/status", Access.WRITE));

        assertFalse(service.authorize("EM204", "energy-monitor/devices/DEV0000002/telemetry", Access.WRITE));
        assertFalse(service.authorize("EM204", "energy-monitor/devices/DEV0000001/other", Access.WRITE));
        assertFalse(service.authorize("EM204", "energy-monitor/devices/DEV0000001/telemetry", Access.READ));
        assertFalse(service.authorize("EM204", "energy-monitor/devices/+/telemetry", Access.SUBSCRIBE));
        assertFalse(service.authorize("EM204", "#", Access.SUBSCRIBE));
    }

    @Test
    void measurementServiceOnlyReadsTheTelemetryOfEveryDevice() {
        assertTrue(service.authorize("em-measurement-service", "energy-monitor/devices/+/telemetry", Access.SUBSCRIBE));
        assertTrue(service.authorize("em-measurement-service", "energy-monitor/devices/DEV0000001/telemetry", Access.READ));

        assertFalse(service.authorize("em-measurement-service", "energy-monitor/devices/+/status", Access.SUBSCRIBE));
        assertFalse(service.authorize("em-measurement-service", "energy-monitor/devices/DEV0000001/status", Access.READ));
        assertFalse(service.authorize("em-measurement-service", "energy-monitor/devices/DEV0000001/telemetry", Access.WRITE));
        assertFalse(service.authorize("em-measurement-service", "energy-monitor/devices/DEV0000001/command", Access.WRITE));
        assertFalse(service.authorize("em-measurement-service", "energy-monitor/devices/DEV0000001/command", Access.READ));
        assertFalse(service.authorize("em-measurement-service", "energy-monitor/devices/#", Access.SUBSCRIBE));
        assertFalse(service.authorize("em-measurement-service", "#", Access.SUBSCRIBE));
    }

    @Test
    void serviceReadsEveryDeviceAndOnlyWritesCommands() {
        assertTrue(service.authorize("em-device-service", "energy-monitor/devices/+/telemetry", Access.SUBSCRIBE));
        assertTrue(service.authorize("em-device-service", "energy-monitor/devices/+/status", Access.SUBSCRIBE));
        assertTrue(service.authorize("em-device-service", "energy-monitor/devices/DEV0000001/status", Access.READ));

        assertTrue(service.authorize("em-device-service", "energy-monitor/devices/DEV0000001/command", Access.WRITE));
        assertFalse(service.authorize("em-device-service", "energy-monitor/devices/+/command", Access.WRITE));
        assertFalse(service.authorize("em-device-service", "energy-monitor/devices/DEV0000001/telemetry", Access.WRITE));
        assertFalse(service.authorize("em-device-service", "#", Access.SUBSCRIBE));
    }

    @Test
    void deviceReadsOnlyItsOwnCommands() {
        assertTrue(service.authorize("EM204", "energy-monitor/devices/DEV0000001/command", Access.SUBSCRIBE));
        assertTrue(service.authorize("EM204", "energy-monitor/devices/DEV0000001/command", Access.READ));

        assertFalse(service.authorize("EM204", "energy-monitor/devices/DEV0000002/command", Access.SUBSCRIBE));
        assertFalse(service.authorize("EM204", "energy-monitor/devices/DEV0000001/command", Access.WRITE));
    }

    @Test
    void unknownClientGetsNothing() {
        assertFalse(service.authorize("intruder", "energy-monitor/devices/DEV0000001/telemetry", Access.WRITE));
    }
}
