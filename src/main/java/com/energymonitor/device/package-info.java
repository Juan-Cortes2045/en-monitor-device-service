/**
 * Device service: the Device bounded context of EnergyMonitor, deployed on its own.
 *
 * <p>Owns the measuring modules (ESP32 + PZEM-004T): their link to a home, the MQTT credentials
 * they are issued, the broker's authentication and authorisation of every MQTT client, the
 * commands sent to them and their connectivity. energy-monitor-backend reads devices through this
 * service's internal API and learns about connectivity changes from its events.
 *
 * <p>Internal layout: {@code domain}, {@code application}, {@code adapter},
 * {@code infrastructure}.
 */
package com.energymonitor.device;
