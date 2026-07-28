# 10. CI/CD, Production Deployment and Monitoring

## 10.1 CI pipeline

`.github/workflows/ci.yml`, on every push and pull request:

```
backend-test ──┬─▶ backend-integration-test ──┬─▶ build-images (main only)
               │                              │
security-scan ─┘──────────────────────────────┘
mobile-test  (parallel)
```

- Unit tests first, integration second — the cheapest signal arrives soonest.
- `dependency-check` fails the build at CVSS ≥ 7; CodeQL and gitleaks run on every change.
- Images build only from `main`, tagged with the **commit SHA** as well as `latest`. A
  deployment must be able to name exactly which build is running.
- Trivy fails the build on a CRITICAL or HIGH image finding. SBOM and provenance attestations
  are attached.

## 10.2 CD pipeline

`.github/workflows/cd.yml`:

**Staging** deploys automatically when CI passes on `main`: migration Job → rolling update →
smoke test → automatic rollback on failure.

**Production** requires a human approval on a protected GitHub environment. Nobody should be
able to change a live loan book by merging a pull request.

```
1. Pre-deploy database snapshot          ← a recoverable point, not a reconstruction
2. Flyway migration Job                  ← backwards compatible with the running release
3. Rolling update, one service at a time ← keeps the blast radius to that service
4. Health and JWKS verification
5. Automatic rollback on any failure
```

Cloud credentials come from OIDC federation, so no long-lived cluster keys sit in GitHub.

### Release strategy

Rolling updates with `maxUnavailable: 0`. Blue/green was rejected: it doubles the database
connection footprint and, with a shared database, offers no real isolation — the risk it
addresses is better handled by backwards-compatible migrations.

Feature flags gate risky behaviour changes (a new allocation order, a new penalty basis) so a
rollback is a configuration change rather than a redeploy.

## 10.3 Observability

### Structured logging

JSON to stdout, shipped by the platform's collector. Every line carries `traceId`, `requestId`,
`tenantId` and `userId` from MDC, so one support ticket resolves to one request across eleven
services.

**Never logged:** passwords, tokens, national ids, full phone numbers, card data. Rejected
values are redacted by field name in `GlobalExceptionHandler`; notification recipients are
masked before they reach a log line.

Expected business outcomes log at INFO without a stack trace; only genuine faults log at ERROR
with one — otherwise the noise floor rises and real errors stop being noticed.

### Metrics

Micrometer → Prometheus at `/actuator/prometheus`. Beyond the JVM and HTTP defaults:

| Metric | Why it matters |
|---|---|
| `mfin.outbox.published` / `.failed` | A failed outbox event means a business fact exists with no event — silent divergence |
| `mfin.payments.captured` (by method) | Business volume; a sudden drop is an outage signal before any error rate moves |
| `mfin.payments.reversed` | A spike suggests a process or training problem |
| `mfin.loans.disbursed` | Volume and value |
| `mfin.arrears.par30` | The portfolio health figure the business runs on |
| `resilience4j.circuitbreaker.state` | Which dependency is failing, and for how long |
| `hikaricp.connections.pending` | Pool exhaustion, usually the first sign of a slow query |

### Tracing

OpenTelemetry via Micrometer Tracing, OTLP to a collector. 10% sampling in production, 100% in
staging. Trace ids propagate through Kafka headers, so a disbursement can be followed from the
approving tap through to the ledger entry.

### Health

`/actuator/health/liveness` — the process only. `/actuator/health/readiness` — dependencies
included. Conflating them is a classic outage amplifier: a database blip restarts every pod,
and the restart storm outlasts the blip.

## 10.4 Alerting

Alert on **symptoms users feel**, not on causes. Every alert below is actionable; anything that
is not gets a dashboard instead.

| Alert | Condition | Severity |
|---|---|---|
| Payment capture failing | error rate > 1% over 5 min | **Page** |
| Payment latency | p99 > 2s over 10 min | Page |
| Outbox stalled | any event PENDING > 5 min | **Page** — events are being lost from the business's point of view |
| Outbox failed | any event in FAILED | Page |
| Audit chain broken | verification endpoint reports a break | **Page** — possible tampering |
| Service down | readiness failing on all replicas | Page |
| Circuit breaker open | open > 5 min | Ticket |
| Consumer lag | > 10,000 messages | Ticket |
| Connection pool | pending > 5 for 5 min | Ticket |
| Disk | > 80% | Ticket |
| Certificate expiry | < 14 days | Ticket |
| PAR30 rising | > 5% or +2pp week on week | Business notification |

## 10.5 Backup and recovery

| Aspect | Target |
|---|---|
| RPO | ≤5 minutes (binlog shipping) |
| RTO | ≤1 hour |
| Full backup | Nightly, retained 35 days |
| Point-in-time | Continuous binlog, 7 days |
| Long-term | Monthly, retained 7 years (statutory) |
| Restore drill | **Monthly, into a scratch environment** |

The restore drill is not optional. A backup that has never been restored is a hypothesis. The
drill reconciles the restored loan book against the ledger and confirms the audit chain still
verifies.

Kafka is retained for 7 days, which allows a consumer's read model to be rebuilt by replay
without touching the source services.

## 10.6 Scaling

| Component | Trigger | Approach |
|---|---|---|
| Stateless services | CPU > 70% | HPA, 2→10 replicas |
| Payment / loan account | Month-end | Raise `minReplicas` on schedule; these are the peaks that matter |
| MySQL reads | Read latency | Read replicas for reporting; **never for the balance path** |
| MySQL writes | Sustained saturation | Vertical first; then partition the largest tenants onto their own instance |
| Kafka | Consumer lag | More partitions and consumer instances |

Note the exception: reporting may read a replica, the balance path may not. A repayment
allocated against replica-lagged balances would be wrong in a way that is hard to detect and
expensive to unwind.

### Growth path

The tenant id is on every row and every event, so the natural scaling axis is **tenant
sharding**: move a large institution to its own database, then its own cluster, with no code
change — only configuration. That is the main reason `tenant_id` is present even on tables that
a shared-schema design does not strictly require it on.

## 10.7 Production readiness checklist

**Before the first live tenant**

- [ ] RSA signing key generated in the KMS; ephemeral-key warning absent from startup logs
- [ ] `CRYPTO_DATA_KEY` and `CRYPTO_INDEX_KEY` provisioned, distinct, from the secret store
- [ ] TLS certificates with auto-renewal; HSTS confirmed
- [ ] NetworkPolicies applied and verified with a deliberate denied connection
- [ ] Database accounts least-privileged; audit service holds only INSERT/SELECT on `audit_log`
- [ ] Backups running **and a restore verified**
- [ ] Alert routing tested end to end with a deliberate failure
- [ ] Load test at 3× projected peak
- [ ] Penetration test completed and findings closed
- [ ] Runbooks written for: outbox stalled, payment reversal dispute, tenant suspension,
      audit chain break, database failover
- [ ] Data retention and erasure procedure agreed with counsel (GDPR/local equivalent vs
      statutory retention of financial records — these conflict and the resolution must be
      written down before the first customer, not after the first request)

**Ongoing**

- [ ] Monthly restore drill · quarterly load test · quarterly key rotation
- [ ] Dependency updates within 30 days of a high-severity advisory
- [ ] Audit chain verification scheduled and alerted on
