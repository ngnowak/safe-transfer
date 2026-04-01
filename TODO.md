


* Add logging
* Add result
* no illegal exception
* start testing by unit tests
* start testing by integration tests

Needed use cases:
- wallet creation OK
- get wallet OK
- deposit OK
- transfer between wallets
- balance from ledger
- idempotency
- multitenancy
- transactional outbox
- audit/notification async consumer
- global exception handling
- Swagger
- Docker + Postgres
- integration tests
- concurrency tests
- architecture diagrams

Do these in order:

transfer endpoint ok
idempotency ok
concurrency test

balance endpoint from ledger
outbox + async audit event
diagrams + README polish
metrics/logging polish

later:
* add security


What to build

Pick 4 core capabilities:

Tenant and customer management
tenant
customer
wallet
beneficiary
Money movement
top-up
internal transfer
withdrawal request
scheduled transfer
Ledger
immutable ledger entries
transfer lifecycle
idempotency key
balance snapshots
Async workflows
TransferCreated
TransferCompleted
TransferFailed
NotificationRequested
AuditEventRecorded

This matches your background well: domain modeling, event-driven flows, multitenancy, performance, resilience, and production-readiness.

The most important architectural decision

Build it first as a modular monolith, not as 6 microservices.

That means:

one deployable Spring Boot app
several internal modules
clear DDD boundaries
async events inside and outside the transaction boundary
Dockerized dependencies around it

Why this is the right move:

you still show DDD, hexagonal architecture, events, and concurrency
you avoid spending half the project on service-to-service plumbing
interviewers can still see that you know where the microservice boundaries would be

A good module split:

identity
tenant
wallet
ledger
transfer
fx
notification
shared-kernel
DDD shape

Use these bounded contexts:

Wallet / Ledger

Aggregate roots: Wallet, Transfer
Value objects: Money, Currency, WalletId, TenantId, TransferId
Domain rules:
no negative balance unless overdraft enabled
source and destination currencies must match unless FX is present
transfer must be idempotent
ledger must stay balanced

FX

ExchangeQuote
quote expiration
locked rate during transfer

Notification

consumes events only
email/webhook/in-app simulation
How to make it truly thread-safe

Do not rely on synchronized as your main safety mechanism. It only protects one JVM instance.

Use these instead:

1. Double-entry ledger

Never update balance as the source of truth.
Record debit and credit entries.
Balance is derived from ledger, optionally with snapshots.

2. Idempotency keys

Every money-moving command must carry an idempotency key.
Same request repeated = same result, no duplicate transfer.

3. Optimistic locking

Add @Version to aggregates like Wallet.
Great for concurrent updates with retry.

4. Pessimistic locking only where needed

For the hottest money movement path, SELECT ... FOR UPDATE can be justified.
Use it narrowly.

5. Ordered async processing

Partition events by walletId or accountId.
That preserves order for operations on the same wallet.

6. Transactional outbox

Write domain state and outbound event in the same DB transaction.
A separate publisher moves outbox rows to Kafka.
This is one of the best patterns to showcase in interviews.
Suggested technical stack
Java 25
Spring Boot 4
Spring Web
Spring Data JPA
Spring Validation
Spring Security
Spring Kafka
PostgreSQL
Liquibase
Docker Compose
Testcontainers
Micrometer + Prometheus
OpenTelemetry tracing
Local setup with Docker

Use docker-compose.yml for:

app
postgres
kafka
zookeeper only if you choose classic Kafka setup
prometheus
grafana

For a portfolio project, keep infra small. PostgreSQL + Kafka + Grafana is enough.

REST + async contract

REST for commands and queries:

POST /api/v1/tenants/{tenantId}/wallets
POST /api/v1/tenants/{tenantId}/transfers
GET /api/v1/tenants/{tenantId}/transfers/{transferId}
GET /api/v1/tenants/{tenantId}/wallets/{walletId}/balance
POST /api/v1/tenants/{tenantId}/fx/quotes

Kafka topics:

wallet-events
transfer-events
notification-commands
audit-events
Data model

Core tables:

tenant
customer
wallet
transfer
ledger_entry
fx_quote
outbox_event
idempotency_record

Important columns:

tenant_id on every business table
version for optimistic locking
request_id / idempotency_key
correlation_id and causation_id
audit timestamps everywhere
The feature order I would use

Phase 1

project skeleton
module boundaries
tenant + wallet creation
PostgreSQL + Liquibase
OpenAPI
Docker local run

Phase 2

deposit and internal transfer
double-entry ledger
optimistic locking
idempotency

Phase 3

transactional outbox
Kafka publisher/consumer
notification flow
retries + DLQ

Phase 4

FX quote + currency conversion
scheduled transfer
tracing, metrics, dashboards
performance and concurrency tests