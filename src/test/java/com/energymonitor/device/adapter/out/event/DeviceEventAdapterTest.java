package com.energymonitor.device.adapter.out.event;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.energymonitor.device.adapter.out.messaging.DeviceMessagePayloads.ConnectivityPayload;
import com.energymonitor.device.adapter.out.messaging.DeviceMessagePayloads.DeviceLinkedPayload;
import com.energymonitor.device.adapter.out.messaging.DeviceMessagePayloads.DeviceUnlinkedPayload;
import com.energymonitor.device.adapter.out.messaging.DeviceMessageTypes;
import com.energymonitor.device.adapter.out.messaging.DeviceOutboxWriter;
import com.energymonitor.device.api.DeviceConnectivityLost;
import com.energymonitor.device.api.DeviceConnectivityRestored;
import com.energymonitor.device.api.DeviceLinked;
import com.energymonitor.device.api.DeviceUnlinked;
import com.energymonitor.device.application.port.out.DeviceHomePersistencePort;
import com.energymonitor.device.domain.model.DeviceHome;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

@DisplayName("Device event adapter")
class DeviceEventAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-23T15:00:00Z");

    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final DeviceOutboxWriter outbox = mock(DeviceOutboxWriter.class);
    private final DeviceHomePersistencePort deviceHomes = mock(DeviceHomePersistencePort.class);
    private final DeviceEventAdapter adapter = new DeviceEventAdapter(publisher, outbox, deviceHomes);

    @Test
    @DisplayName("a lost connection goes to memory and to the outbox with the device's home")
    void connectivityLost() {
        given(deviceHomes.findByDeviceId("DEV0000001"))
                .willReturn(Optional.of(new DeviceHome("DEV0000001", "hom0000001")));
        var event = new DeviceConnectivityLost("DEV0000001", NOW);

        adapter.publish(event);

        verify(publisher).publishEvent(event);
        verify(outbox).write(DeviceMessageTypes.CONNECTIVITY_LOST,
                new ConnectivityPayload("DEV0000001", "hom0000001", NOW), NOW);
    }

    @Test
    @DisplayName("a restored connection of a device linked to no home carries no home")
    void connectivityRestoredWithoutHome() {
        given(deviceHomes.findByDeviceId("DEV0000001")).willReturn(Optional.empty());
        var event = new DeviceConnectivityRestored("DEV0000001", NOW);

        adapter.publish(event);

        verify(publisher).publishEvent(event);
        verify(outbox).write(DeviceMessageTypes.CONNECTIVITY_RESTORED,
                new ConnectivityPayload("DEV0000001", null, NOW), NOW);
    }

    @Test
    @DisplayName("a link goes to the outbox with its home, name and owner")
    void linked() {
        var event = new DeviceLinked("DEV0000001", "hom0000001", "Nevera", "use0000001", NOW);

        adapter.publish(event);

        verify(publisher).publishEvent(event);
        verify(outbox).write(DeviceMessageTypes.DEVICE_LINKED,
                new DeviceLinkedPayload("DEV0000001", "hom0000001", "Nevera", "use0000001", NOW), NOW);
    }

    @Test
    @DisplayName("an unlink goes to the outbox with the home it left")
    void unlinked() {
        var event = new DeviceUnlinked("DEV0000001", "hom0000001", NOW);

        adapter.publish(event);

        verify(publisher).publishEvent(event);
        verify(outbox).write(DeviceMessageTypes.DEVICE_UNLINKED,
                new DeviceUnlinkedPayload("DEV0000001", "hom0000001", NOW), NOW);
    }
}
