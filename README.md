# en-monitor-device-service

EnergyMonitor device microservice: links ESP32 + PZEM-004T meters to homes, issues and rotates their
MQTT credentials, authenticates the broker (Mosquitto go-auth), tracks device connectivity and
publishes its events over RabbitMQ. Spring Boot 4, Java 21, hexagonal architecture.

It is being extracted from the `device` module of `energy-monitor-backend`, following the same
steps as the security service.

## Requirements

- Java 21 (the repository brings `./mvnw`, no Maven install needed).
- MySQL from the `infra` repository: the schema is shared with `energy-monitor-backend` and the
  security service.

## Run locally

```bash
cp .env.example .env
cp src/main/resources/application-local.yaml.example src/main/resources/application-local.yaml
# set the MYSQL_PASSWORD of infra/.env in both files
./mvnw spring-boot:run          # http://localhost:8084/actuator/health
```

## Tests

```bash
SPRING_DATASOURCE_PASSWORD=... ./mvnw test
```

The tests run under the `test` profile against the MySQL of `infra`.
