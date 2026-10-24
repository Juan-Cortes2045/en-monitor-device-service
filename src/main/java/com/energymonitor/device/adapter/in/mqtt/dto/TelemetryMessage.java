package com.energymonitor.device.adapter.in.mqtt.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * What this service reads of a message on {@code energy-monitor/devices/+/telemetry}: only who
 * sent it and its signal. The readings are stored by energy-monitor-backend.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelemetryMessage(String deviceId, Integer rssi) {
}
