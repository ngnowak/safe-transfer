# SafeTransfer Architecture Diagrams

PlantUML diagrams for interview walkthroughs.

Recommended reading order:

1. `system-context.puml`
2. `component-view.puml`
3. `domain-model.puml`
4. `transfer-sequence.puml`
5. `outbox-kafka-audit-sequence.puml`
6. `outbox-state.puml`
7. `use-cases.puml`
8. `deployment-local.puml`

- Controllers implement OpenAPI contract interfaces, keeping endpoint documentation separate from controller logic.
- Wallet balances are derived from immutable ledger entries.
- Transfers create debit and credit ledger entries in one transaction.
- Transfer idempotency uses a request hash to detect same-key/different-body conflicts.
- Transfer risk limits are externalized in `application.yaml`.
- Transfer completion creates an outbox event in the same transaction.
- The outbox publisher claims rows in a short transaction and dispatches outside the claim transaction.
- Kafka publishing can be enabled for external-style event delivery.
- Stale `PROCESSING` outbox rows can be reclaimed.
- Audit consumption is idempotent, so duplicate event delivery does not create duplicate audit rows.
- Local deployment can run either as a host JVM plus Docker infrastructure or as a full Docker stack.
