package com.energymonitor.device.adapter.out.messaging;

import java.time.Instant;

/**
 * The JSON bodies of the messages in {@link DeviceMessageTypes}, one record per type.
 *
 * <p>They are the wire contract, kept apart from the {@code api} records on purpose: a domain
 * event can gain a field without every consumer having to understand it. The connectivity
 * messages carry the home the device was linked to when it happened, so a consumer does not have
 * to ask for it; it is null for a device that was linked to no home. Instants travel as ISO-8601
 * strings.
 */
public final class DeviceMessagePayloads {

    /** {@code device.linked} */
    public record DeviceLinkedPayload(String deviceId, String homeId, String deviceName,
                                      String linkedBy, Instant linkedAt) {
    }

    /** {@code device.unlinked} */
    public record DeviceUnlinkedPayload(String deviceId, String homeId, Instant unlinkedAt) {
    }

    /** {@code device.connectivity-lost} and {@code device.connectivity-restored} */
    public record ConnectivityPayload(String deviceId, String homeId, Instant occurredAt) {
    }

    private DeviceMessagePayloads() {
    }
}
