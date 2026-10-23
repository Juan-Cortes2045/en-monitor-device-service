package com.energymonitor.device.application.usecase;

import com.energymonitor.device.application.port.in.ReceiveDeviceReport;
import com.energymonitor.device.application.port.in.RecordDeviceConnectivity;
import com.energymonitor.device.application.port.out.DevicePersistencePort;

/**
 * Records the connectivity of a device heard from on the broker, once it is known to be
 * registered: a status row must always belong to an existing device.
 *
 * <p>This service contains no Spring annotations.
 */
public class DeviceReportService implements ReceiveDeviceReport {

    private final DevicePersistencePort devices;
    private final RecordDeviceConnectivity connectivity;

    public DeviceReportService(DevicePersistencePort devices, RecordDeviceConnectivity connectivity) {
        this.devices = devices;
        this.connectivity = connectivity;
    }

    @Override
    public Outcome online(String deviceId, Integer signalStrength) {
        if (!registered(deviceId)) {
            return Outcome.UNKNOWN_DEVICE;
        }
        connectivity.recordOnline(deviceId, signalStrength);
        return Outcome.RECORDED;
    }

    @Override
    public Outcome offline(String deviceId) {
        if (!registered(deviceId)) {
            return Outcome.UNKNOWN_DEVICE;
        }
        connectivity.recordOffline(deviceId);
        return Outcome.RECORDED;
    }

    private boolean registered(String deviceId) {
        return deviceId != null && devices.findById(deviceId).isPresent();
    }
}
