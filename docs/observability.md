# Observability

SafeTransfer exposes operational and business signals through Spring Boot Actuator and Micrometer.

## Endpoints

| Endpoint | Purpose |
| --- | --- |
| `/actuator/health` | Runtime health check for local demo, Docker health checks, and deployment readiness. |
| `/actuator/metrics` | List of available Micrometer meters. Useful for local inspection. |
| `/actuator/metrics/{metricName}` | JSON view of one metric, including available tags and measurements. |
| `/actuator/prometheus` | Prometheus-compatible scrape endpoint. |

## Custom Metrics

Transfer processing:

| Metric | Tags | Meaning |
| --- | --- | --- |
| `safetransfer.transfer.created` | `outcome` | Counter for transfer attempts grouped by business outcome. |
| `safetransfer.transfer.duration` | `outcome` | Timer for transfer processing duration grouped by business outcome. |

Transfer outcomes currently include:

- `success`
- `insufficient_funds`
- `wallet_not_found`
- `wallet_not_active`
- `currency_mismatch`
- `same_wallet`
- `transfer_limit_exceeded`
- `idempotency_conflict`
- `other_error`

Outbox publishing:

| Metric | Tags | Meaning |
| --- | --- | --- |
| `safetransfer.outbox.publish.success` | `event_type` | Counter for successfully published outbox events. |
| `safetransfer.outbox.publish.failure` | `event_type`, `status` | Counter for failed outbox publish attempts. |
| `safetransfer.outbox.fatal` | `event_type` | Counter for events that reached the retry limit. |
| `safetransfer.outbox.publish.duration` | `event_type`, optional `status` | Timer for outbox publish duration. |

## How To Demo In An Interview

Start the application, then run:

```powershell
.\scripts\demo-observability.ps1
```

The script:

1. Shows `/actuator/health`.
2. Shows the actuator metrics endpoint.
3. Creates wallets, deposits money, and runs one successful transfer.
4. Runs one expected failed transfer with insufficient funds.
5. Reuses an idempotency key with a different request body to produce an idempotency conflict.
6. Prints custom transfer metrics grouped by outcome.
7. Prints outbox publish metrics.
8. Shows where Prometheus can scrape metrics from.

Suggested explanation:

> I use Actuator for runtime health and Micrometer for technical and business metrics. The JVM and HTTP metrics tell me whether the process is healthy. The custom transfer metrics tell me what the business flow is doing: how many transfers succeed, why failures happen, and how long transfer processing takes. The outbox metrics tell me whether asynchronous event delivery is healthy. In production, Prometheus would scrape `/actuator/prometheus`, and Grafana or alerts would watch transfer failure rate, latency, and fatal outbox rows.

## Useful Prometheus Queries

Transfer success rate:

```promql
sum(rate(safetransfer_transfer_created_total{outcome="success"}[5m]))
```

Transfer failure rate by outcome:

```promql
sum by (outcome) (rate(safetransfer_transfer_created_total{outcome!="success"}[5m]))
```

95th percentile transfer duration:

```promql
histogram_quantile(0.95, sum by (le, outcome) (rate(safetransfer_transfer_duration_seconds_bucket[5m])))
```

Outbox publish failures:

```promql
sum by (event_type, status) (rate(safetransfer_outbox_publish_failure_total[5m]))
```

Fatal outbox rows:

```promql
sum by (event_type) (safetransfer_outbox_fatal_total)
```

## Alert Ideas

- Application health is not `UP`.
- Transfer failure rate is above an agreed threshold.
- `idempotency_conflict` spikes, which can indicate a client bug.
- `safetransfer.outbox.publish.failure` increases continuously.
- `safetransfer.outbox.fatal` is greater than zero.
- Transfer latency p95 exceeds the expected threshold.
