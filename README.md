# en-monitor-device-service

EnergyMonitor device microservice: links ESP32 + PZEM-004T meters to homes, issues and rotates their
MQTT credentials, authenticates the broker (Mosquitto go-auth), tracks device connectivity and
publishes its events over RabbitMQ. Spring Boot 4, Java 21, hexagonal architecture.

It was extracted from the `device` module of `energy-monitor-backend`, following the same steps as
the security service. The design and the decisions are in
[`docs/Architecture.md`](docs/Architecture.md).

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

### Internal API (for energy-monitor-backend and the measurement service)

| Path | Answer |
|---|---|
| `GET /internal/devices/{deviceId}` | `200` with `{idDevice, name, applianceTypeId, location, description, installationDate, deviceCode, homeId}` (`homeId` null when it is linked to no home), or `404` |
| `GET /internal/homes/{homeId}/devices` | `200` with the devices linked to the home, an empty list when it has none |

energy-monitor-backend reads them for the alerts, the notifications and the recommendations, and
en-monitor-measurement-service to know whether a reading's device exists and which devices a home
has. These paths have their own filter chain: the caller must come from
`INTERNAL_API_ALLOWED_NETWORKS` **and** send `INTERNAL_API_TOKEN` in `X-Internal-Token`; without a
token configured every call is refused. A user's access token does not open them. The reverse proxy
and the gateway never forward `/internal/**`.

Whether the caller is a member or the owner of the home is asked to the home service
(`GET /internal/homes/{homeId}/access`, with `X-Internal-Token`, at `HOME_SERVICE_URL`). Without
an answer the request gets 503.

The broker knows three kinds of client: this service (`MQTT_DEVICE_SERVICE_USER`, reads telemetry
and status and is the only one that writes commands), the measurement service
(`MQTT_MEASUREMENT_SERVICE_USER`, reads only telemetry, to store the readings) and the devices
(device code and API key, only their own topics). energy-monitor-backend is no longer a broker
client: it hears each reading from the measurement service over RabbitMQ.

## Behind the gateway

Web and mobile requests arrive through the API gateway (`/api/v1/homes/*/devices/**`,
`/api/v1/appliance-types/**`), which validates the token first; this service validates it again.
From loopback or a Docker network (`172.16.0.0/12`) the container takes the caller's address and
scheme from `X-Forwarded-For` / `X-Forwarded-Proto` (`server.forward-headers-strategy=native`,
`server.tomcat.remoteip.internal-proxies`); any other private network is not trusted, so a machine
of the LAN cannot pass as `127.0.0.1`.

The internal API and the broker authentication check that address: a request relayed from outside
counts as the outside caller and is refused, even if it forwards `127.0.0.1`. The gateway never
forwards `/internal/**`; the backend and Mosquitto call this service directly over the Docker
network. `BehindGatewayTest` checks it on a running server.

## Connectivity

This service subscribes to `energy-monitor/devices/+/telemetry` and `energy-monitor/devices/+/status`
with its own MQTT user and client ids (`${MQTT_CLIENT_ID}-telemetry` and `-status`):

- every telemetry message marks the device `ONLINE` and refreshes `last_seen` with the server
  clock (the reading itself is stored by en-monitor-measurement-service, which subscribes to the
  same topic with its own user);
- the retained `online` / `offline` status, including the last will the broker publishes when a
  module drops, opens a new row in `device_status_log`;
- a device not heard from for `DEVICE_OFFLINE_AFTER` (150 s) is marked `OFFLINE` by the
  inactivity check every 30 s.

The topic, not the payload, identifies the device; a message from an unregistered device, on an
unexpected topic or that does not parse is logged and dropped. `mqtt.inbound.enabled=false` turns
the subscriptions off (the test profile does).

## Events (RabbitMQ)

What other services need to know about a device is published on the topic exchange
`energy-monitor.device`, through a transactional outbox (`device_outbox`, changelog `device-009`):

| Routing key | Payload | Raised when |
|---|---|---|
| `device.linked` | `{deviceId, homeId, deviceName, linkedBy, linkedAt}` | A module is linked, the first time or again |
| `device.unlinked` | `{deviceId, homeId, unlinkedAt}` | A module is unlinked |
| `device.connectivity-lost` | `{deviceId, homeId, occurredAt}` | An online device goes offline (status, last will or inactivity) |
| `device.connectivity-restored` | `{deviceId, homeId, occurredAt}` | An offline device reports again |

- The message is written in the transaction of the change; the connectivity use case runs one
  transaction per call (`TransactionalDeviceConnectivity`), so the status row and its message
  commit together.
- A relay publishes the pending messages every second, oldest first. A message counts as published
  only when the broker confirms it (correlated confirms) and a queue took it; until the consumer
  declares its queue it stays pending. The first failure stops the run, so the order holds.
- `homeId` is the home the device was linked to when the connectivity changed, or null.
- Settings: `RABBITMQ_*` and `messaging.*`. The test profile turns the relay off and stores the rows
  already discarded. `RabbitDeviceOutboxPublisherBrokerTest` runs against the RabbitMQ of infra
  when it is reachable (export `RABBITMQ_USERNAME` and `RABBITMQ_PASSWORD`) and is skipped
  otherwise.

### Events it consumes

| Queue | Exchange and routing key | What it does |
|---|---|---|
| `device-service.device.home-deleted` | `energy-monitor.home`, `home.deleted` | Unlinks every module of the deleted home, revokes their API keys, sends each the MQTT unlink command after commit and announces `device.unlinked` |

- The queue is declared here at startup, durable, with a `.dlq` dead-letter queue on
  `device-service.dlx`: a message that fails three attempts (1, 2 and 4 s apart) is moved there
  instead of blocking the queue. A message without a `homeId` is dropped.
- A message may arrive twice: the second delivery finds no module linked to the home and does
  nothing.
- Declaring the queue is what lets the home service publish `home.deleted`: until it exists,
  the message waits in its `home_outbox`.
- The test profile does not start the consumers.

## Requirements

- Java 21 (the repository brings `./mvnw`, no Maven install needed).
- MySQL, RabbitMQ and Mosquitto from the `infra` repository: the schema is shared with
  `energy-monitor-backend`, the security service and the measurement service.
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

## Docker and CI/CD

```bash
docker build -t energy-monitor-device .
```

Multi-stage build (Maven on JDK 21, then JRE 21), running as uid 10001. The infra repository
builds it as `device` (`infra/docker-compose.yml`, `infra/docker-compose.prod.yml`): no published
port, `public.pem` only, health on `/actuator/health`. Mosquitto, the gateway, the backend and the
measurement service reach it as `device:8084`.

`.github/workflows/ci-cd.yml`:

- on every pull request and push to `dev` and `main`: `./mvnw test` against a MySQL 8.4 service of
  the runner (no RabbitMQ or broker needed), and `docker build`;
- after a push to `main`, and the approval of the `production` environment: `infra/deploy/deploy.sh
  device` over SSH, which rebuilds only this container and waits for every service to be healthy,
  then a smoke test through the gateway. The environment needs the same secrets as the other
  repositories: `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY` and `DEPLOY_KNOWN_HOSTS`.

## Database

The changelogs `device-001` to `device-008` keep the path they had in energy-monitor-backend, so
on a database the monolith already migrated Liquibase recognises them as applied.
