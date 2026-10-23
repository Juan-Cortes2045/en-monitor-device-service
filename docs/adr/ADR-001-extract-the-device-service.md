# ADR-001: Extract the device module into its own service

- **Status:** Accepted
- **Date:** 2026-10
- **Context of:** EnergyMonitor, migration of energy-monitor-backend to services (after the
  security service)

## Context

The `device` module of energy-monitor-backend linked the ESP32 + PZEM-004T modules to homes,
issued their MQTT credentials, authenticated Mosquitto, sent the commands and tracked
connectivity. It changed for different reasons than readings, alerts or homes, and every broker
login went through the monolith. The security service had already been extracted with the same
tools (shared schema, internal REST with a token, RabbitMQ with an outbox, API gateway).

## Decision

Move the module, unchanged in structure, to `en-monitor-device-service` (:8084):

1. **Full migration**: endpoints, broker authentication, commands, connectivity and its tables.
2. **Connectivity from MQTT**: the service subscribes to telemetry and status with its own broker
   user; the backend keeps subscribing to telemetry only to store readings.
3. **Reads over internal HTTP with a cache**: the backend asks `/internal/devices/**` and
   `/internal/homes/**`, caches answers 30 s (unknown devices too) and falls back to a stale copy
   up to 1 h when the service does not answer.
4. **Home deletion as an event**: the backend writes `home.deleted` to its outbox; this service
   unlinks the modules. Device events travel the other way through this service's outbox.
5. **Same schema, same changeset paths**, so a migrated database needs no change besides
   `device_outbox`.

## Consequences

- Mosquitto depends on this service: while it is down, new MQTT logins are refused, and
  connected modules keep publishing only while the broker's cached ACL decisions last (5 min).
  The backend keeps storing the readings that arrive, with the devices it has cached (up to 1 h
  stale).
- Membership checks cost an internal call to the backend; without it, device endpoints answer
  503.
- Two clients read the same telemetry topic, each with its own client id.
- Delivery of events is at least once; consumers tolerate duplicates.
- Production must receive the backend without the module, the gateway routes, this service and
  the infra change together.

## Alternatives considered

- **Keep the module in the monolith**: simplest, but leaves broker authentication and commands
  coupled to every backend deploy.
- **Backend reads the device tables directly**: no network call, but shares tables across
  services and breaks ownership.
- **Connectivity reported by the backend**: one subscription only, but the backend would still
  own device state.
