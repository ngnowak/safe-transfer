# SafeTransfer Notes

## What This Is

SafeTransfer is a small Spring Boot wallet transfer system focused on correctness under retries, concurrency, and asynchronous side effects.

It is intentionally scoped as a modular monolith. The main goal is not CRUD, but safe money movement:

- wallets are tenant-scoped
- deposits create ledger credits
- transfers create debit and credit ledger entries
- balances are derived from immutable ledger entries
- transfer creation is idempotent
- transfer completion creates an outbox event
- audit processing is asynchronous and idempotent

## What I Wanted To Demonstrate

- Java and Spring Boot application design.
- Clear module boundaries inside a modular monolith.
- Ledger-based balance calculation instead of mutable balance fields.
- Idempotent transfer creation using an idempotency key and request hash.
- Transactional outbox for reliable side effects.
- Kafka-backed audit flow when Kafka publishing is enabled.
- Concurrency-aware transfer processing.
- Configurable transfer risk policy.
- Actuator health checks, Prometheus endpoint, and custom business metrics.
- Unit, integration, and e2e test strategy.
- OpenAPI documentation, Postman examples, and PlantUML architecture diagrams.

## Things I Am Proud Of

1. Balances are derived from immutable ledger entries.
2. Transfer creation is idempotent and detects same-key/different-body conflicts.
3. Transfer state, ledger entries, and outbox event are committed atomically.
4. Outbox publishing claims rows in a short DB transaction and dispatches outside that transaction.
5. Stale `PROCESSING` outbox rows can be reclaimed.
6. Audit consumption is idempotent, so duplicate message delivery is safe.
7. Transfer policies are extensible, and the current risk limit is externalized in `application.yaml`.
8. Transfer and outbox behavior is observable through custom Micrometer metrics.
9. Tests are split into unit, integration, and e2e layers.

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

1. Start local infrastructure:

   ```powershell
   docker compose -f docker/docker-compose.yml up -d
   ```

2. Start the app:

   ```powershell
   .\gradlew.bat bootRun
   ```

3. Open Swagger UI:

   ```text
   http://localhost:8080/swagger-ui.html
   ```

4. Run the main demo script:

   ```powershell
   .\scripts\demo-safe-transfer.ps1
   ```

   This shows:

   - health check
   - source and destination wallet creation
   - deposit
   - ledger-derived balances
   - idempotent transfer creation
   - idempotent replay returning `200 OK`
   - same idempotency key with a different body returning `409 Conflict`
   - final balances
   - transfer lookup

5. Run the observability demo script:

   ```powershell
   .\scripts\demo-observability.ps1
   ```

   This shows:

   - `/actuator/health`
   - `/actuator/metrics`
   - `/actuator/prometheus`
   - transfer success metrics
   - insufficient funds metrics
   - idempotency conflict metrics
   - outbox publish metrics

6. Optional Kafka proof:

   Start the app with Kafka publishing enabled:

   ```powershell
   $env:APPLICATION_KAFKA_PUBLISHING="true"
   $env:SPRING_KAFKA_BOOTSTRAP_SERVERS="localhost:9092"
   .\gradlew.bat bootRun
   ```

   Then run:

   ```powershell
   .\scripts\demo-kafka-local.ps1
   ```

7. Mention additional failures covered by tests:

   - transfer above configured risk limit returns `409 Conflict`
   - insufficient funds returns `409 Conflict`

## Transfer Flow

1. Client sends a transfer request with an `Idempotency-Key`.
2. `TransferService` validates wallets, currency, balance, and idempotency.
3. `TransferPolicyEvaluator` runs configured transfer policies, including the single-transfer risk limit from `application.yaml`.
4. Transfer row and ledger rows are written in one transaction.
5. A `transfer.completed` outbox row is written in the same transaction.
6. A newly created transfer returns `201 Created`; an idempotent replay of the same request returns the existing transfer with `200 OK`.
7. `OutboxPublisher` atomically claims retryable outbox rows, marks them `PROCESSING`, and commits the claim transaction.
8. The claimed event is dispatched outside the claim transaction.
9. With Kafka publishing enabled, the event is sent to Kafka and consumed by `AuditKafkaListener`; otherwise the local in-process dispatcher calls `AuditConsumer` directly.
10. `AuditConsumer` records an audit row.
11. Outbox row becomes `PUBLISHED`, `FAILED`, or `FATAL`.

## Reliability Rules

- Transfer state and outbox event are committed atomically.
- Audit writing is asynchronous.
- Duplicate audit inserts are tolerated via unique `source_event_id`.
- Outbox publishing retries failed rows.
- Rows that exceed retry limit become `FATAL`.
- Concurrent publishers do not double-claim rows because claim uses `FOR UPDATE SKIP LOCKED` and immediately marks rows as `PROCESSING`.
- Stale `PROCESSING` rows can be reclaimed after the claim lease expires.

## Outbox States

- `NEW`: freshly written business event.
- `PROCESSING`: claimed by a publisher; stale claims can be retried.
- `PUBLISHED`: successfully processed by async consumer.
- `FAILED`: processing failed, will be retried.
- `FATAL`: retry limit reached, no more attempts.

## Async Scope

Implemented async flow:

- Kafka disabled: `transfer.completed` -> `outbox_event` -> `OutboxPublisher` -> `AuditConsumer` -> `audit_event`
- Kafka enabled: `transfer.completed` -> `outbox_event` -> `OutboxPublisher` -> Kafka -> `AuditKafkaListener` -> `AuditConsumer` -> `audit_event`

## Main Technical Decisions

### Ledger As Source Of Truth

The wallet does not store a mutable balance. Balance is calculated from ledger entries. This keeps money movement auditable and avoids hidden state mutations.

### Idempotency

Transfers use an `Idempotency-Key` header. The service stores a request hash:

- same key and same request returns the existing transfer
- same key and different request returns `409 Conflict`

This is important for payment APIs because clients retry requests after timeouts or network failures.

### Transactional Outbox

The transfer transaction writes:

- transfer row
- debit ledger entry
- credit ledger entry
- outbox event

The outbox publisher processes the event separately. This avoids losing side effects when the database commit succeeds but the async publish fails.

### Kafka And Audit

With Kafka publishing enabled, the outbox publisher sends transfer events to Kafka. `AuditKafkaListener` consumes them and delegates to `AuditConsumerImpl`.

Audit inserts are idempotent via unique `source_event_id`, so duplicate Kafka delivery does not create duplicate audit rows.

### Risk Policy

`TransferPolicyEvaluator` runs all `TransferPolicy` implementations. The current `SingleTransferLimitPolicy` checks the configured maximum single transfer amount:

```yaml
safetransfer:
  transfer:
    risk:
      enabled: true
      max-single-transfer-amount: 10000.00
```

This shows that business behavior can be externalized in configuration instead of hardcoded, and that new policies can be added by implementing one interface.

## Tests To Mention

- Unit tests for mappers, policies, services, metrics, and error mapping.
- Integration tests for controllers, persistence, idempotency, outbox failure/retry, Kafka publishing failure, and concurrency.
- E2e tests for wallet and transfer flows against a running app.
- CI runs unit, integration, and e2e tests as separate jobs and uploads test results.

Useful commands:

```powershell
.\gradlew.bat test
.\gradlew.bat integrationTest
.\gradlew.bat testAll
.\gradlew.bat e2eTest
```

## Running Full Docker Stack

The normal local setup starts PostgreSQL and Kafka:

```powershell
docker compose -f docker/docker-compose.yml up -d
```

The full Docker stack starts PostgreSQL, Kafka, and the application container:

```powershell
docker compose -f docker/docker-compose-with-app.yml up --build
```

Swagger UI is available at:

```text
http://localhost:8080/swagger-ui.html
```

## Observability

SafeTransfer exposes health checks, Actuator metrics, Prometheus-formatted metrics, custom transfer metrics, outbox publishing metrics, and Logbook request/response logs.

Details are in `docs/observability.md`.

## Architecture Assets

- `architecture/README.md`
- `architecture/system-context.puml`
- `architecture/component-view.puml`
- `architecture/domain-model.puml`
- `architecture/transfer-sequence.puml`
- `architecture/outbox-kafka-audit-sequence.puml`
- `architecture/outbox-state.puml`
- `architecture/use-cases.puml`
- `architecture/deployment-local.puml`

## Tradeoffs

- Authentication and authorization are intentionally out of scope.
- Tenant ID is passed in the path; in production it should be checked against authenticated user claims.
- The current risk policy is intentionally simple, not a full fraud engine.
- Kafka schema registry, dead-letter topics, and advanced retry policies are future improvements.
- This is a focused money-transfer demo, not a complete banking product.

## What I Would Add Next

- OAuth2/JWT security with tenant-aware authorization.
- Daily transfer limits and velocity checks.
- Kafka dead-letter topic and schema versioning.
- Grafana dashboard for transfer and outbox metrics.
- OpenTelemetry tracing across REST, DB, outbox, Kafka, and audit.
- Admin view for outbox/audit rows and manual retry of fatal events.
- Async fraud-check integration:
  - create high-risk transfers as `PENDING_VERIFICATION`
  - publish `fraud-check-requested` through the existing outbox
  - call or simulate an external fraud provider outside the transfer transaction
  - consume the decision asynchronously
  - complete or reject the transfer idempotently
- External payment provider integration for top-ups or withdrawals, using provider idempotency keys, timeouts, retries, and contract tests.

## Summary

SafeTransfer is a Spring Boot wallet transfer system focused on correctness. Balances are derived from immutable ledger entries. Transfer creation is idempotent using a request hash. Transfer state and outbox events are committed atomically. Outbox publishing is retried and happens outside the DB claim transaction. Kafka-backed audit processing is idempotent, so duplicate delivery is safe. Transfer policies are extensible, and the current risk limit is externalized in configuration and covered by tests.
