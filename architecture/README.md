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

Render with PlantUML from the `safetransfer` project directory, for example:

```powershell
plantuml .\architecture\*.puml
```

Main points to explain:

- Wallet balances are derived from immutable ledger entries.
- Transfers create debit and credit ledger entries in one transaction.
- Transfer completion creates an outbox event in the same transaction.
- The outbox publisher sends events asynchronously.
- Kafka publishing can be enabled for external-style event delivery.
- Audit consumption is idempotent, so duplicate event delivery does not create duplicate audit rows.
