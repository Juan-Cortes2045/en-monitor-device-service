package com.energymonitor.device.application.result;

import java.time.Instant;

/**
 * A device as the other services read it: what it is, where it is and the home it is linked to.
 *
 * @param idDevice         the device
 * @param name             the display name
 * @param applianceTypeId  what it measures
 * @param location         the room, may be null
 * @param description      free text, may be null
 * @param installationDate when it was last linked
 * @param deviceCode       the code printed on the module
 * @param homeId           the home it is linked to, or null when it is linked to none
 */
public record DeviceSummary(String idDevice, String name, String applianceTypeId, String location,
                            String description, Instant installationDate, String deviceCode,
                            String homeId) {
}
