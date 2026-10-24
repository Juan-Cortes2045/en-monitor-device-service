package com.energymonitor.device.infrastructure;

import com.energymonitor.device.application.port.in.DetectInactiveDevices;
import com.energymonitor.device.application.port.in.RecordDeviceConnectivity;
import com.energymonitor.device.application.usecase.DeviceConnectivityService;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs {@link DeviceConnectivityService} in one transaction per call.
 *
 * <p>A change of state writes a row in {@code device_status_log} and, through the event, its
 * message in {@code device_outbox}. One without the other must never stay committed: a lost
 * connection nobody is told about, or an alert for an outage that was rolled back. The service
 * stays framework-free; this decorator is what the MQTT consumer and the inactivity check get.
 */
public class TransactionalDeviceConnectivity implements RecordDeviceConnectivity, DetectInactiveDevices {

    private final DeviceConnectivityService target;
    private final TransactionTemplate transaction;

    public TransactionalDeviceConnectivity(DeviceConnectivityService target, TransactionTemplate transaction) {
        this.target = target;
        this.transaction = transaction;
    }

    @Override
    public void recordOnline(String deviceId, Integer signalStrength) {
        transaction.executeWithoutResult(status -> target.recordOnline(deviceId, signalStrength));
    }

    @Override
    public void recordOffline(String deviceId) {
        transaction.executeWithoutResult(status -> target.recordOffline(deviceId));
    }

    @Override
    public int markInactiveDevicesOffline() {
        Integer marked = transaction.execute(status -> target.markInactiveDevicesOffline());
        return marked == null ? 0 : marked;
    }
}
