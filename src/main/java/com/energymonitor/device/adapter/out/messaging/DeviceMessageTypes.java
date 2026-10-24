package com.energymonitor.device.adapter.out.messaging;

/**
 * The messages this service publishes, named by their routing key on the
 * {@code energy-monitor.device} topic exchange.
 *
 * <p>These names are a contract with the services that consume them: renaming one silently
 * stops its delivery, because the consumer's binding no longer matches.
 */
public final class DeviceMessageTypes {

    /** A module was linked to a home, the first time or again (new network, new key). */
    public static final String DEVICE_LINKED = "device.linked";

    /** A module was removed from its home. */
    public static final String DEVICE_UNLINKED = "device.unlinked";

    /** A device went offline: energy-monitor-backend raises a connectivity alert. */
    public static final String CONNECTIVITY_LOST = "device.connectivity-lost";

    /** An offline device reported again: the connectivity alert can be closed. */
    public static final String CONNECTIVITY_RESTORED = "device.connectivity-restored";

    private DeviceMessageTypes() {
    }
}
