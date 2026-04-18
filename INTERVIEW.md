# SafeTransfer Interview Notes

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
- Unit, integration, and e2e test strategy.
- OpenAPI documentation, Postman examples, and PlantUML architecture diagrams.

## Things I Am Proud Of

1. Balances are derived from immutable ledger entries.
2. Transfer creation is idempotent and detects same-key/different-body conflicts.
3. Transfer state, ledger entries, and outbox event are committed atomically.
4. Outbox publishing claims rows in a short DB transaction and dispatches outside that transaction.
5. Stale `PROCESSING` outbox rows can be reclaimed.
6. Audit consumption is idempotent, so duplicate message delivery is safe.
7. Transfer risk limits are externalized in `application.yaml`.
8. Tests are split into unit, integration, and e2e layers.

## Demo Flow

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

4. Show the happy path:

   - create source wallet
   - create destination wallet
   - deposit `1000.00 EUR`
   - transfer `100.00 EUR`
   - check balances
   - show outbox/audit behavior

5. Show interesting failures:

   - same idempotency key with a different request body returns `409 Conflict`
   - transfer above configured risk limit returns `409 Conflict`
   - insufficient funds returns `409 Conflict`

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

`TransferRiskPolicy` checks the configured maximum single transfer amount:

```yaml
safetransfer:
  transfer:
    risk:
      enabled: true
      max-single-transfer-amount: 10000.00
```

This shows that business behavior can be externalized in configuration instead of hardcoded.

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
.\gradlew.bat compileE2eJava
```

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
- The risk policy is intentionally simple, not a full fraud engine.
- Kafka schema registry, dead-letter topics, and advanced retry policies are future improvements.
- This is a focused money-transfer demo, not a complete banking product.

## What I Would Add Next

- OAuth2/JWT security with tenant-aware authorization.
- Daily transfer limits and velocity checks.
- Kafka dead-letter topic and schema versioning.
- Grafana dashboard for transfer and outbox metrics.
- OpenTelemetry tracing across REST, DB, outbox, Kafka, and audit.
- Admin view for outbox/audit rows and manual retry of fatal events.
- External payment provider or fraud-check integration.

## Short Pitch

SafeTransfer is a Spring Boot wallet transfer system focused on correctness. Balances are derived from immutable ledger entries. Transfer creation is idempotent using a request hash. Transfer state and outbox events are committed atomically. Outbox publishing is retried and happens outside the DB claim transaction. Kafka-backed audit processing is idempotent, so duplicate delivery is safe. The transfer risk limit is externalized in configuration and covered by tests.
