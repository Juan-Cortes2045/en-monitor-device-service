package com.energymonitor.device.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.energymonitor.device.application.port.out.DeviceHomePersistencePort;
import com.energymonitor.device.application.port.out.DevicePersistencePort;
import com.energymonitor.device.application.result.DeviceSummary;
import com.energymonitor.device.application.usecase.DeviceQueryService;
import com.energymonitor.device.domain.model.Device;
import com.energymonitor.device.domain.model.DeviceHome;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Devices read by the other services")
class DeviceQueryServiceTest {

    private static final Instant LINKED = Instant.parse("2026-10-01T10:00:00Z");
    private static final Device FRIDGE = Device.create("DEV0000001", "Nevera", "appl000001", "Cocina", null,
            LINKED, "EM204", "device-api-key-123456");

    private final DevicePersistencePort devices = mock(DevicePersistencePort.class);
    private final DeviceHomePersistencePort deviceHomes = mock(DeviceHomePersistencePort.class);
    private final DeviceQueryService service = new DeviceQueryService(devices, deviceHomes);

    @Test
    @DisplayName("a linked device comes with its home")
    void linkedDevice() {
        given(devices.findById("DEV0000001")).willReturn(Optional.of(FRIDGE));
        given(deviceHomes.findByDeviceId("DEV0000001"))
                .willReturn(Optional.of(new DeviceHome("DEV0000001", "hom0000001")));

        assertThat(service.find("DEV0000001")).contains(new DeviceSummary("DEV0000001", "Nevera",
                "appl000001", "Cocina", null, LINKED, "EM204", "hom0000001"));
    }

    @Test
    @DisplayName("an unlinked device comes without a home")
    void unlinkedDevice() {
        given(devices.findById("DEV0000001")).willReturn(Optional.of(FRIDGE));
        given(deviceHomes.findByDeviceId("DEV0000001")).willReturn(Optional.empty());

        assertThat(service.find("DEV0000001")).get().extracting(DeviceSummary::homeId).isNull();
    }

    @Test
    @DisplayName("an unknown device, or none asked, is empty")
    void unknownDevice() {
        given(devices.findById("DEV0000099")).willReturn(Optional.empty());

        assertThat(service.find("DEV0000099")).isEmpty();
        assertThat(service.find(null)).isEmpty();
    }

    @Test
    @DisplayName("the devices of a home, skipping a link whose device is gone")
    void devicesOfHome() {
        given(deviceHomes.findByHomeId("hom0000001")).willReturn(List.of(
                new DeviceHome("DEV0000001", "hom0000001"), new DeviceHome("DEV0000002", "hom0000001")));
        given(devices.findById("DEV0000001")).willReturn(Optional.of(FRIDGE));
        given(devices.findById("DEV0000002")).willReturn(Optional.empty());

        assertThat(service.listByHome("hom0000001")).extracting(DeviceSummary::idDevice, DeviceSummary::homeId)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("DEV0000001", "hom0000001"));
        assertThat(service.listByHome(null)).isEmpty();
    }
}
