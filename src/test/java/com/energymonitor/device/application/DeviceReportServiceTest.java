package com.energymonitor.device.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.energymonitor.device.application.port.in.ReceiveDeviceReport.Outcome;
import com.energymonitor.device.application.port.in.RecordDeviceConnectivity;
import com.energymonitor.device.application.port.out.DevicePersistencePort;
import com.energymonitor.device.application.usecase.DeviceReportService;
import com.energymonitor.device.domain.model.Device;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Device reports from the broker")
class DeviceReportServiceTest {

    private static final Device DEVICE = Device.create("DEV0000001", "Medidor", "appl000001", null, null,
            Instant.parse("2026-10-01T00:00:00Z"), "EM204", "device-api-key-123456");

    private final DevicePersistencePort devices = mock(DevicePersistencePort.class);
    private final RecordDeviceConnectivity connectivity = mock(RecordDeviceConnectivity.class);
    private final DeviceReportService service = new DeviceReportService(devices, connectivity);

    @Test
    @DisplayName("a registered device online is recorded with its signal")
    void onlineRecorded() {
        given(devices.findById("DEV0000001")).willReturn(Optional.of(DEVICE));

        assertThat(service.online("DEV0000001", -58)).isEqualTo(Outcome.RECORDED);
        verify(connectivity).recordOnline("DEV0000001", -58);
    }

    @Test
    @DisplayName("a registered device offline is recorded")
    void offlineRecorded() {
        given(devices.findById("DEV0000001")).willReturn(Optional.of(DEVICE));

        assertThat(service.offline("DEV0000001")).isEqualTo(Outcome.RECORDED);
        verify(connectivity).recordOffline("DEV0000001");
    }

    @Test
    @DisplayName("an unknown device writes no status row")
    void unknownDevice() {
        given(devices.findById(anyString())).willReturn(Optional.empty());

        assertThat(service.online("DEV0000099", -60)).isEqualTo(Outcome.UNKNOWN_DEVICE);
        assertThat(service.offline("DEV0000099")).isEqualTo(Outcome.UNKNOWN_DEVICE);
        assertThat(service.online(null, null)).isEqualTo(Outcome.UNKNOWN_DEVICE);
        verify(connectivity, never()).recordOnline(anyString(), any());
        verify(connectivity, never()).recordOffline(anyString());
    }
}
