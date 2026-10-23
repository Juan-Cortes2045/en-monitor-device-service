# en-monitor-device-service

EnergyMonitor device microservice: links ESP32 + PZEM-004T meters to homes, issues and rotates their
MQTT credentials, authenticates the broker (Mosquitto go-auth), tracks device connectivity and
publishes its events over RabbitMQ. Spring Boot 4, Java 21, hexagonal architecture.

It is being extracted from the `device` module of `energy-monitor-backend`, following the same
steps as the security service.

## What it serves

| Path | Who calls it | What it does |
|---|---|---|
| `GET /api/v1/homes/{homeId}/devices` | web and mobile, through the gateway | Devices of a home (any member) |
| `POST /api/v1/homes/{homeId}/devices` | idem | Links a module and returns its API key and the broker address (owner) |
| `PUT /api/v1/homes/{homeId}/devices/{deviceId}` | idem | Edits name, appliance and room (owner) |
| `POST /api/v1/homes/{homeId}/devices/{deviceId}/credentials` | idem | New API key, for a new Wi-Fi network (owner) |
| `DELETE /api/v1/homes/{homeId}/devices/{deviceId}` | idem | Unlinks the module and tells it over MQTT (owner) |
| `GET /api/v1/appliance-types` | idem | Appliance catalogue |
| `POST /internal/mqtt/auth/user`, `/acl` | Mosquitto (go-auth) | Who may connect and what each client may read or write |

Whether the caller is a member or the owner of the home is asked to energy-monitor-backend
(`GET /internal/homes/{homeId}/access`, with `X-Internal-Token`). Without an answer the request
gets 503.

The broker knows three kinds of client: this service (`MQTT_DEVICE_SERVICE_USER`, the only one
that writes commands), energy-monitor-backend (`MQTT_BACKEND_USER`, reads telemetry and status)
and the devices (device code and API key, only their own topics).

## Connectivity

This service subscribes to `energy-monitor/devices/+/telemetry` and `energy-monitor/devices/+/status`
with its own MQTT user and client ids (`${MQTT_CLIENT_ID}-telemetry` and `-status`):

- every telemetry message marks the device `ONLINE` and refreshes `last_seen` with the server
  clock (the reading itself is stored by energy-monitor-backend, which subscribes to the same
  topic);
- the retained `online` / `offline` status, including the last will the broker publishes when a
  module drops, opens a new row in `device_status_log`;
- a device not heard from for `DEVICE_OFFLINE_AFTER` (150 s) is marked `OFFLINE` by the
  inactivity check every 30 s.

The topic, not the payload, identifies the device; a message from an unregistered device, on an
unexpected topic or that does not parse is logged and dropped. `mqtt.inbound.enabled=false` turns
the subscriptions off (the test profile does).

## Requirements

- Java 21 (the repository brings `./mvnw`, no Maven install needed).
- MySQL and Mosquitto from the `infra` repository: the schema is shared with
  `energy-monitor-backend` and the security service.
- The public key of the security service (`SECURITY_JWT_PUBLIC_KEY_PATH`).

## Run locally

```bash
cp .env.example .env
cp src/main/resources/application-local.yaml.example src/main/resources/application-local.yaml
# set the MYSQL_PASSWORD of infra/.env, the public key path, INTERNAL_API_TOKEN and the MQTT users
./mvnw spring-boot:run          # http://localhost:8084/actuator/health
```

## Tests

```bash
SPRING_DATASOURCE_PASSWORD=... ./mvnw test
```

The tests run under the `test` profile against the MySQL of `infra`, with a key pair generated
for the run. They never reach a running backend or broker.

## Database

The changelogs `device-001` to `device-008` keep the path they had in energy-monitor-backend, so
on a database the monolith already migrated Liquibase recognises them as applied.
