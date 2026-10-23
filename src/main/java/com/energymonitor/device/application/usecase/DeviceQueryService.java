package com.energymonitor.device.application.usecase;

import com.energymonitor.device.application.port.in.QueryDevices;
import com.energymonitor.device.application.port.out.DeviceHomePersistencePort;
import com.energymonitor.device.application.port.out.DevicePersistencePort;
import com.energymonitor.device.application.result.DeviceSummary;
import com.energymonitor.device.domain.model.Device;
import com.energymonitor.device.domain.model.DeviceHome;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads devices for energy-monitor-backend: by id, with the home they are linked to, and by home.
 */
@Service
@Transactional(readOnly = true)
public class DeviceQueryService implements QueryDevices {

    private final DevicePersistencePort devices;
    private final DeviceHomePersistencePort deviceHomes;

    public DeviceQueryService(DevicePersistencePort devices, DeviceHomePersistencePort deviceHomes) {
        this.devices = devices;
        this.deviceHomes = deviceHomes;
    }

    @Override
    public Optional<DeviceSummary> find(String deviceId) {
        if (deviceId == null) {
            return Optional.empty();
        }
        return devices.findById(deviceId).map(device -> summary(device,
                deviceHomes.findByDeviceId(deviceId).map(DeviceHome::homeId).orElse(null)));
    }

    @Override
    public List<DeviceSummary> listByHome(String homeId) {
        if (homeId == null) {
            return List.of();
        }
        return deviceHomes.findByHomeId(homeId).stream()
                .map(link -> devices.findById(link.deviceId()))
                .flatMap(Optional::stream)
                .map(device -> summary(device, homeId))
                .toList();
    }

    private static DeviceSummary summary(Device device, String homeId) {
        return new DeviceSummary(device.idDevice(), device.name(), device.applianceTypeId(),
                device.location(), device.description(), device.installationDate(),
                device.deviceCode(), homeId);
    }
}
