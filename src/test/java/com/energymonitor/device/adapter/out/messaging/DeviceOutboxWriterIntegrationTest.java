package com.energymonitor.device.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.energymonitor.device.adapter.out.persistence.entity.DeviceOutboxEntity;
import com.energymonitor.device.adapter.out.persistence.repository.DeviceOutboxRepository;
import com.energymonitor.device.application.command.LinkDeviceCommand;
import com.energymonitor.device.application.command.UnlinkDeviceCommand;
import com.energymonitor.device.application.port.in.LinkDevice;
import com.energymonitor.device.application.port.in.ReceiveDeviceReport;
import com.energymonitor.device.application.port.in.UnlinkDevice;
import com.energymonitor.device.application.port.out.HomeLookupPort;
import com.energymonitor.device.application.port.out.HomeLookupPort.HomeAccess;
import com.energymonitor.device.infrastructure.JwtKeyedTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The device messages are written in the transaction of the change they announce, against the
 * real schema. {@code @Transactional}: everything joins the test's transaction, which is rolled
 * back, so no row is left and the after-commit MQTT command is never sent. The test profile also
 * stores the rows already discarded. The home rights are mocked: no backend is reached.
 */
@SpringBootTest
@Transactional
@DisplayName("Device outbox")
class DeviceOutboxWriterIntegrationTest extends JwtKeyedTest {

    private static final String OWNER = "dou0000001";
    private static final String HOME = "doh0000001";

    @Autowired
    private LinkDevice linkDevice;
    @Autowired
    private UnlinkDevice unlinkDevice;
    @Autowired
    private ReceiveDeviceReport reports;
    @Autowired
    private DeviceOutboxRepository outbox;
    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private HomeLookupPort homes;

    private String deviceId;

    @BeforeEach
    void link() {
        given(homes.access(anyString(), anyString())).willReturn(new HomeAccess(true, true, true));
        // Six characters at most, like a real module code; random so a leftover row never clashes.
        String code = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 5).toUpperCase();
        deviceId = linkDevice.link(new LinkDeviceCommand(OWNER, HOME, code, "Nevera", "appl000001",
                "Cocina")).device().idDevice();
    }

    @Test
    @DisplayName("linking writes device.linked with the home, the name and the owner")
    void linkWritesTheMessage() {
        List<DeviceOutboxEntity> messages = messagesFor(deviceId);

        assertThat(messages).extracting(DeviceOutboxEntity::getMessageType)
                .containsExactly(DeviceMessageTypes.DEVICE_LINKED);
        JsonNode payload = json.readTree(messages.getFirst().getPayload());
        assertThat(payload.get("homeId").asString()).isEqualTo(HOME);
        assertThat(payload.get("deviceName").asString()).isEqualTo("Nevera");
        assertThat(payload.get("linkedBy").asString()).isEqualTo(OWNER);
        assertThat(messages.getFirst().getDiscardedAt()).as("the test profile never publishes").isNotNull();
    }

    @Test
    @DisplayName("unlinking writes device.unlinked with the home it left")
    void unlinkWritesTheMessage() {
        unlinkDevice.unlink(new UnlinkDeviceCommand(OWNER, HOME, deviceId));

        assertThat(messagesFor(deviceId)).extracting(DeviceOutboxEntity::getMessageType)
                .containsExactly(DeviceMessageTypes.DEVICE_LINKED, DeviceMessageTypes.DEVICE_UNLINKED);
    }

    @Test
    @DisplayName("going offline writes device.connectivity-lost, and coming back restored, with the home")
    void connectivityWritesTheMessages() {
        reports.online(deviceId, -58);
        reports.offline(deviceId);
        reports.online(deviceId, -60);

        List<DeviceOutboxEntity> messages = messagesFor(deviceId);
        assertThat(messages).extracting(DeviceOutboxEntity::getMessageType)
                .containsExactly(DeviceMessageTypes.DEVICE_LINKED, DeviceMessageTypes.CONNECTIVITY_LOST,
                        DeviceMessageTypes.CONNECTIVITY_RESTORED);
        assertThat(json.readTree(messages.get(1).getPayload()).get("homeId").asString()).isEqualTo(HOME);
    }

    @Test
    @DisplayName("the first report of a device announces nothing: it was never offline")
    void firstOnlineAnnouncesNothing() {
        reports.online(deviceId, -58);

        assertThat(messagesFor(deviceId)).extracting(DeviceOutboxEntity::getMessageType)
                .containsExactly(DeviceMessageTypes.DEVICE_LINKED);
    }

    private List<DeviceOutboxEntity> messagesFor(String id) {
        return outbox.findAll().stream()
                .filter(message -> message.getPayload().contains("\"" + id + "\""))
                .sorted((a, b) -> a.getOccurredAt().compareTo(b.getOccurredAt()))
                .toList();
    }
}
