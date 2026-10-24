package com.energymonitor.device.adapter.in.internal;

import com.energymonitor.device.application.port.in.QueryDevices;
import com.energymonitor.device.application.result.DeviceSummary;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * What energy-monitor-backend asks about devices: the ingestion of readings, the alerts, the
 * notifications and the recommendations. Only the services reach it; see
 * {@code InternalApiConfiguration}.
 *
 * <ul>
 *   <li>{@code GET /internal/devices/{deviceId}}: the device with its {@code homeId} (null when it
 *       is linked to no home), or 404.</li>
 *   <li>{@code GET /internal/homes/{homeId}/devices}: the devices linked to a home, an empty list
 *       when it has none.</li>
 * </ul>
 */
@Hidden
@RestController
public class InternalDeviceController {

    private final QueryDevices queryDevices;

    public InternalDeviceController(QueryDevices queryDevices) {
        this.queryDevices = queryDevices;
    }

    @GetMapping("/internal/devices/{deviceId}")
    public ResponseEntity<DeviceSummary> device(@PathVariable String deviceId) {
        return ResponseEntity.of(queryDevices.find(deviceId));
    }

    @GetMapping("/internal/homes/{homeId}/devices")
    public List<DeviceSummary> devicesOfHome(@PathVariable String homeId) {
        return queryDevices.listByHome(homeId);
    }
}
