package com.energymonitor.device.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.energymonitor.device.adapter.out.messaging.DeviceMessageTypes;
import com.energymonitor.device.adapter.out.persistence.repository.DeviceOutboxRepository;
import com.energymonitor.device.application.command.LinkDeviceCommand;
import com.energymonitor.device.application.port.in.AuthorizeBrokerAccess;
import com.energymonitor.device.application.port.in.LinkDevice;
import com.energymonitor.device.application.port.in.ListHomeDevices;
import com.energymonitor.device.application.port.out.DeviceHomePersistencePort;
import com.energymonitor.device.application.port.out.HomeLookupPort;
import com.energymonitor.device.application.port.out.HomeLookupPort.HomeAccess;
import com.energymonitor.device.application.result.LinkedDeviceResult;
import com.energymonitor.device.infrastructure.JwtKeyedTest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * A home deleted in the backend unlinks its modules here, against the real schema.
 * {@code @Transactional}: everything joins the test's transaction, which is rolled back, so no row
 * is left and the after-commit MQTT command is never sent. The home rights are mocked.
 */
@SpringBootTest
@Transactional
@DisplayName("Home deleted, end to end in the service")
class HomeDeletedConsumerIntegrationTest extends JwtKeyedTest {

    private static final String OWNER = "hdu0000001";
    private static final String HOME = "hdh0000001";

    @Autowired
    private HomeDeletedConsumer consumer;
    @Autowired
    private LinkDevice linkDevice;
    @Autowired
    private ListHomeDevices listHomeDevices;
    @Autowired
    private DeviceHomePersistencePort deviceHomes;
    @Autowired
    private AuthorizeBrokerAccess broker;
    @Autowired
    private DeviceOutboxRepository outbox;

    @MockitoBean
    private HomeLookupPort homes;

    private LinkedDeviceResult fridge;
    private LinkedDeviceResult washer;

    @BeforeEach
    void linkTwoModules() {
        given(homes.access(anyString(), anyString())).willReturn(new HomeAccess(true, true, true));
        fridge = link("Nevera", "appl000001");
        washer = link("Lavadora", "appl000004");
    }

    @Test
    @DisplayName("unlinks every module of the home, revokes their keys and announces each one")
    void unlinksEveryModule() {
        consumer.on(homeDeleted());

        assertThat(listHomeDevices.list(OWNER, HOME)).isEmpty();
        assertThat(deviceHomes.findByDeviceId(fridge.device().idDevice())).isEmpty();
        assertThat(broker.authenticate(fridge.device().deviceCode(), fridge.apiKey()))
                .as("the old key no longer connects").isFalse();
        assertThat(broker.authenticate(washer.device().deviceCode(), washer.apiKey())).isFalse();
        assertThat(unlinkedMessages()).isEqualTo(2);
    }

    @Test
    @DisplayName("a second delivery of the same message changes nothing")
    void secondDeliveryIsHarmless() {
        consumer.on(homeDeleted());
        consumer.on(homeDeleted());

        assertThat(unlinkedMessages()).isEqualTo(2);
    }

    private LinkedDeviceResult link(String name, String applianceTypeId) {
        // Six characters at most, like a real module code; random so a leftover row never clashes.
        String code = "H" + UUID.randomUUID().toString().replace("-", "").substring(0, 5).toUpperCase();
        return linkDevice.link(new LinkDeviceCommand(OWNER, HOME, code, name, applianceTypeId, null));
    }

    private long unlinkedMessages() {
        return outbox.findAll().stream()
                .filter(m -> m.getMessageType().equals(DeviceMessageTypes.DEVICE_UNLINKED))
                .filter(m -> m.getPayload().contains(fridge.device().idDevice())
                        || m.getPayload().contains(washer.device().idDevice()))
                .count();
    }

    private static Message homeDeleted() {
        MessageProperties properties = new MessageProperties();
        properties.setType("home.deleted");
        properties.setMessageId("hmo0000001");
        String body = "{\"homeId\":\"" + HOME + "\",\"deletedBy\":\"" + OWNER
                + "\",\"deletedAt\":\"2026-10-23T15:00:00Z\"}";
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }
}
