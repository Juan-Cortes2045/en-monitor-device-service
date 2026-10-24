package com.energymonitor.device.adapter.in.mqtt.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * What this service reads of a message on {@code energy-monitor/devices/+/status}. The firmware
 * sends more (name, IP, firmware version, uptime); none of it decides connectivity.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StatusMessage(String deviceId, String status, Integer rssi) {
}
