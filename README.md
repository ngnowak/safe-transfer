# SafeTransfer

`SafeTransfer` is a modular Spring Boot application for wallet management, deposits, internal transfers, and immutable ledger-based balance calculation.

The project is intentionally built as a modular monolith. It uses PostgreSQL for persistence, Liquibase for schema management, and a transactional outbox with asynchronous audit processing for reliable side effects.

## Features

- Multi-tenant wallet model
- Wallet creation and wallet lookup
- Deposits
- Internal wallet-to-wallet transfers
- Configurable transfer risk limit
- Immutable ledger entries and balance derived from ledger
- Idempotent transfer handling
- Concurrency-safe transfer processing
- Transactional outbox
- Asynchronous audit consumer
- Global exception handling
- Swagger / OpenAPI
- Dockerized local PostgreSQL and Kafka
- Unit and integration tests

## Stack

- Java 25
- Spring Boot 4
- Spring Web MVC
- Spring Data JPA
- Spring Validation
- Spring Kafka
- Spring Actuator
- PostgreSQL
- Liquibase
- Micrometer / Prometheus registry
- Lombok
- Spring Scheduling
- JUnit 5 / Mockito

## Modules

- `wallet`
  - wallet lifecycle and queries
- `ledger`
  - immutable debit/credit entries
- `transfer`
  - transfer orchestration, idempotency, concurrency control
- `outbox`
  - transactional outbox persistence and publisher
- `audit`
  - asynchronous audit persistence
- `common`
  - shared API and configuration

## How It Works

### Transfer Flow

1. Client sends a transfer request.
2. `TransferService` validates wallets, currency, balance, and idempotency.
3. `TransferRiskPolicy` checks configured risk limits from `application.yaml`.
4. Transfer row and ledger rows are written in one transaction.
5. A `transfer.completed` outbox row is written in the same transaction.
6. A newly created transfer returns `201 Created`; an idempotent replay of the same request returns the existing transfer with `200 OK`.
7. `OutboxPublisher` atomically claims retryable outbox rows, marks them `PROCESSING`, and commits the claim transaction.
8. The claimed event is dispatched outside the claim transaction.
9. With Kafka publishing enabled, the event is sent to Kafka and consumed by `AuditKafkaListener`; otherwise the local in-process dispatcher calls `AuditConsumer` directly.
10. `AuditConsumer` records an audit row.
11. Outbox row becomes `PUBLISHED`, `FAILED`, or `FATAL`.

### Reliability Rules

- Transfer state and outbox event are committed atomically.
- Audit writing is asynchronous.
- Duplicate audit inserts are tolerated via unique `source_event_id`.
- Outbox publishing retries failed rows.
- Rows that exceed retry limit become `FATAL`.
- Concurrent publishers do not double-claim rows because claim uses `FOR UPDATE SKIP LOCKED` and immediately marks rows as `PROCESSING`.
- Stale `PROCESSING` rows can be reclaimed after the claim lease expires.

## Architecture

PlantUML diagrams are stored in [`architecture/README.md`](/C:/Users/Kamil/IdeaProjects/safetransfer/architecture/README.md).

Useful interview walkthrough diagrams:

- [System context](/C:/Users/Kamil/IdeaProjects/safetransfer/architecture/system-context.puml)
- [Component view](/C:/Users/Kamil/IdeaProjects/safetransfer/architecture/component-view.puml)
- [Domain model](/C:/Users/Kamil/IdeaProjects/safetransfer/architecture/domain-model.puml)
- [Transfer sequence](/C:/Users/Kamil/IdeaProjects/safetransfer/architecture/transfer-sequence.puml)
- [Outbox, Kafka, and audit sequence](/C:/Users/Kamil/IdeaProjects/safetransfer/architecture/outbox-kafka-audit-sequence.puml)
- [Outbox state lifecycle](/C:/Users/Kamil/IdeaProjects/safetransfer/architecture/outbox-state.puml)
- [Use cases](/C:/Users/Kamil/IdeaProjects/safetransfer/architecture/use-cases.puml)
- [Local deployment](/C:/Users/Kamil/IdeaProjects/safetransfer/architecture/deployment-local.puml)

## Observability

SafeTransfer exposes health checks, Actuator metrics, Prometheus-formatted metrics, and custom business metrics for transfer outcomes and outbox publishing reliability.

See [Observability](/C:/Users/Kamil/IdeaProjects/safetransfer/docs/observability.md).

Demo script:

```powershell
.\scripts\demo-observability.ps1
```

## Outbox States

- `NEW`
  - freshly written business event
- `PROCESSING`
  - claimed by a publisher; stale claims can be retried
- `PUBLISHED`
  - successfully processed by async consumer
- `FAILED`
  - processing failed, will be retried
- `FATAL`
  - retry limit reached, no more attempts

## Running Locally

### Prerequisites

- JDK 25
- Docker

### Start local infrastructure

The application points to [`docker/docker-compose.yml`](/C:/Users/Kamil/IdeaProjects/safetransfer/docker/docker-compose.yml), which starts PostgreSQL and Kafka.

```bash
docker compose -f docker/docker-compose.yml up -d
```

### Run the application

```bash
./gradlew bootRun
```

On Windows:

```powershell
.\gradlew.bat bootRun
```

By default, local `bootRun` uses the in-process outbox dispatcher. To test Kafka publishing locally, enable Kafka publishing explicitly:

```powershell
$env:APPLICATION_KAFKA_PUBLISHING="true"
$env:SPRING_KAFKA_BOOTSTRAP_SERVERS="localhost:9092"
.\gradlew.bat bootRun
```

Swagger UI:

- `http://localhost:8080/swagger-ui.html`

### Run the full Docker stack

This starts PostgreSQL, Kafka, and the application container from [`docker/docker-compose-with-app.yml`](/C:/Users/Kamil/IdeaProjects/safetransfer/docker/docker-compose-with-app.yml).

```bash
docker compose -f docker/docker-compose-with-app.yml up --build
```

## Tests

Unit tests:

```bash
./gradlew test
```

Integration tests:

```bash
./gradlew integrationTest
```

All tests:

```bash
./gradlew testAll
```

## Important Technical Decisions

### Ledger as source of truth

Balances are derived from ledger entries instead of being stored as a mutable balance field.

### Idempotency

Transfers use `idempotency_key` to make repeated requests safe.

### Transfer concurrency

Wallets are loaded in deterministic order to reduce deadlock risk during transfer processing.

### Configurable risk policy

The maximum single transfer amount is externalized in `application.yaml` under `safetransfer.transfer.risk`.

### Transactional outbox

Business state and async side effects are separated correctly:

- transfer transaction writes `outbox_event`
- publisher claims outbox rows in a short transaction
- Kafka dispatch happens outside the claim transaction
- publisher updates final outbox status in a separate transaction
- audit persistence happens asynchronously after event delivery

## Current Scope

Implemented async flow:

- Kafka disabled: `transfer.completed` -> `outbox_event` -> `OutboxPublisher` -> `AuditConsumer` -> `audit_event`
- Kafka enabled: `transfer.completed` -> `outbox_event` -> `OutboxPublisher` -> Kafka -> `AuditKafkaListener` -> `AuditConsumer` -> `audit_event`

## Expansion Ideas

- Security: enable Spring Security with OAuth2/JWT, tenant-aware authorization, service-to-service authentication, and audit-friendly principal propagation.
- Observability: Prometheus metrics are already exposed through Actuator; the next step is Grafana dashboards, alert rules for failed/fatal outbox rows, transfer latency, Kafka lag, and database pool pressure.
- Distributed tracing: add OpenTelemetry traces across REST requests, transfer processing, outbox publishing, Kafka delivery, and audit persistence.
- Kafka hardening: add schema versioning, a dead-letter topic, consumer retry/backoff policy, and explicit monitoring for consumer lag.
- Operations: add an admin endpoint or internal UI for inspecting outbox rows, retrying `FATAL` rows, and viewing audit history.
- Product features: wallet status transitions, daily cumulative limits, holds/reservations, refunds/reversals, and external payment provider integration.
- API maturity: add versioned OpenAPI examples, pagination/filtering for wallet and transfer history, and consistent correlation IDs in responses.
- Deployment readiness: add container image publishing, environment-specific configuration, Kubernetes manifests or Helm charts, and secret management.
- Quality gates: add mutation testing, dependency/security scanning, contract tests, and performance tests for concurrent transfers.
