package com.energymonitor.device.application.port.in;

/**
 * Use case: a device was heard from on the broker, by telemetry or by its status topic.
 *
 * <p>Only a registered device is recorded. The broker only lets a registered device publish, but
 * a device unlinked and deleted meanwhile, or a retained message left from before, may still
 * arrive.
 */
public interface ReceiveDeviceReport {

    /** What happened to a report. */
    enum Outcome {
        RECORDED,
        UNKNOWN_DEVICE
    }

    /**
     * The device published telemetry or an {@code online} status.
     *
     * @param deviceId       the device, from the topic
     * @param signalStrength RSSI in dBm, may be null
     * @return whether it was recorded
     */
    Outcome online(String deviceId, Integer signalStrength);

    /**
     * The device announced it is going away, or the broker published its last will.
     *
     * @param deviceId the device, from the topic
     * @return whether it was recorded
     */
    Outcome offline(String deviceId);
}
