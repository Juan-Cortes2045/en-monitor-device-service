package com.energymonitor.device.application.port.in;

import com.energymonitor.device.application.result.DeviceSummary;
import java.util.List;
import java.util.Optional;

/**
 * Use case: the other services read devices. No user is involved: the caller is a trusted
 * service, which applies its own rules to what it shows.
 */
public interface QueryDevices {

    /**
     * @param deviceId the device
     * @return the device with the home it is linked to, empty when it does not exist
     */
    Optional<DeviceSummary> find(String deviceId);

    /**
     * @param homeId the home
     * @return the devices currently linked to it, empty when it has none
     */
    List<DeviceSummary> listByHome(String homeId);
}
