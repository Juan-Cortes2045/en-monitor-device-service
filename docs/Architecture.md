# Architecture

`device-service` follows **Hexagonal Architecture (Ports and Adapters)**, like the other services
of EnergyMonitor. The business rules of a device (linking, credentials, connectivity) stay
isolated from Spring, JPA, HTTP, MQTT and RabbitMQ, so they can be tested on their own.

The code is the `device` module of `energy-monitor-backend`, moved out with its structure, tests
and changesets. The package is `com.energymonitor.device`.

## Layers

```
com.energymonitor.device
│
├── api                     → Events other services hear: DeviceLinked, DeviceUnlinked,
│                             DeviceConnectivityLost, DeviceConnectivityRestored
├── domain/model            → Device, DeviceHome, DeviceStatusLog, ApplianceType, ApiKeyHash
├── application
│   ├── command, result     → Input and output of the use cases
│   ├── exception           → Application exceptions
│   ├── port/in             → Use case interfaces (LinkDevice, UnlinkHomeDevices, QueryDevices,
│   │                         ReceiveDeviceReport, AuthorizeBrokerAccess, ...)
│   ├── port/out            → Persistence, commands, events, identifiers, HomeLookupPort
│   └── usecase             → HomeDeviceService, DeviceConnectivityService, DeviceReportService,
│                             DeviceQueryService, BrokerAccessService (no Spring annotations)
├── adapter
│   ├── in/web              → Device and appliance endpoints, broker authentication
│   ├── in/internal         → Internal device API for energy-monitor-backend
│   ├── in/mqtt             → Telemetry and status subscriptions (connectivity)
│   ├── in/messaging        → home.deleted consumer
│   ├── in/event            → Sends the MQTT command after the transaction commits
│   ├── in/scheduler        → Inactivity check
│   └── out
│       ├── persistence     → JPA entities, repositories, mappers, adapters
│       ├── mqtt            → Commands to the modules (Paho)
│       ├── messaging       → Transactional outbox, relay and RabbitMQ publisher
│       ├── event           → Writes the domain events to the outbox
│       ├── home            → Asks energy-monitor-backend about home access (HTTP)
│       └── generator       → Identifiers and API keys
└── infrastructure          → Spring wiring: filter chains, JWT validation, internal API,
                              messaging, transactions, configuration properties
```

- **Domain**: no Spring, JPA or HTTP; an API key is kept only as its SHA-256 hash.
- **Application**: plain classes wired in `DeviceServiceConfiguration`. Transactions are applied
  around them there (`TransactionalDeviceConnectivity`), not inside the use cases.
- **Adapters**: thin controllers and consumers; everything external is behind a port.

## Who talks to whom

```
web / mobile ──► gateway :8080 ──► device :8084   /api/v1/homes/*/devices/**, /api/v1/appliance-types/**
mosquitto ───────────────────────► device :8084   /internal/mqtt/auth/user, /acl
backend :8083 ───────────────────► device :8084   /internal/devices/**, /internal/homes/**
device :8084 ────────────────────► backend :8083  /internal/homes/{homeId}/access
device ◄──── MQTT ────► mosquitto :1883           telemetry and status in, commands out
device ◄──── AMQP ────► rabbitmq :5672            device.* out, home.deleted in
```

## Tokens and callers

- **Users**: the security service signs the access tokens (RS256); this service validates them
  with its public key and issuer. Membership and ownership of a home are asked to
  energy-monitor-backend, which owns homes; no answer refuses the request with 503.
- **Services**: `/internal/devices/**` and `/internal/homes/**` require an allowed network **and**
  `X-Internal-Token`, compared in constant time; no token configured refuses everything.
- **Broker**: `/internal/mqtt/**` is open only to the allowed networks. It knows three clients:
  this service (reads telemetry and status, the only one writing commands), the backend (reads
  only) and each device (its own topics only).
- **Behind the gateway**: the caller address is taken from `X-Forwarded-For` only from loopback
  and the Docker network, so a relayed outside caller cannot reach the internal routes.

## Connectivity

Telemetry marks a registered device `ONLINE` and refreshes `last_seen`; the retained status
(including the last will) opens a row in `device_status_log`; the inactivity check marks it
`OFFLINE` after `DEVICE_OFFLINE_AFTER`. The backend subscribes to the same telemetry to store the
readings: one topic, two consumers, each with its own client id.

## Messaging

| Direction | Exchange | Routing key | Payload |
|---|---|---|---|
| out | `energy-monitor.device` | `device.linked` | `deviceId, homeId, deviceName, linkedBy, linkedAt` |
| out | `energy-monitor.device` | `device.unlinked` | `deviceId, homeId, unlinkedAt` |
| out | `energy-monitor.device` | `device.connectivity-lost` | `deviceId, homeId, occurredAt` |
| out | `energy-monitor.device` | `device.connectivity-restored` | `deviceId, homeId, occurredAt` |
| in | `energy-monitor.home` | `home.deleted` | `homeId, deletedBy, deletedAt` |

- **Outbox** (`device_outbox`, `device-009`): written in the transaction of the change; a relay
  publishes every second, oldest first, with correlated confirms and mandatory routing, and stops
  at the first failure so order holds. Delivery is at least once with a message id.
- **Consumer**: `device-service.device.home-deleted`, durable, with a dead-letter queue on
  `device-service.dlx` after three attempts; a second delivery finds nothing to unlink.
- energy-monitor-backend consumes the device events on `energy-monitor-backend.device.events`:
  connectivity feeds its alerts, links and unlinks drop its device cache.

## Database

- The same MySQL schema as the other services (ADR-003 of the platform); this service owns
  `appliance_type`, `device`, `device_home`, `device_status_log` and `device_outbox`.
- `device-001` to `device-008` keep the path they had in the monolith
  (`db/changelog/device/changes/...`), so Liquibase recognises them as applied. The shared
  `DATABASECHANGELOG` holds every service's changesets; each applies only its own.
- `home_id` references a home by identifier only: no foreign key across services.

## Migration status

The decision is in [ADR-001](adr/ADR-001-extract-the-device-service.md).

| Phase | Content | Status |
|---|---|---|
| 1 | Project structure; module moved here with its tests and changesets | Done |
| 2 | Connectivity from its own MQTT subscriptions | Done |
| 3 | RabbitMQ, transactional outbox and device events | Done |
| 4 | `home.deleted` from the backend's outbox; this service unlinks the home's modules | Done |
| 5 | Internal device API; the backend reads devices over HTTP with a cache and hears the events | Done |
| 6 | `device` module removed from energy-monitor-backend; it keeps a cached proxy | Done |
| 7 | Gateway routes, infra (container, Mosquitto pointing here), forwarded headers, image and CI/CD | Done |
| 8 | Documentation and end-to-end check of the whole stack | In progress |

## Working principles

1. Preserve the Hexagonal / Ports and Adapters structure.
2. Keep the domain free of Spring, JPA and messaging dependencies.
3. No business logic in controllers, consumers or JPA entities.
4. Every external dependency behind an output port.
5. Never import another service's entities or repositories; refer to them by identifier.
