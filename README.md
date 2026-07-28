# Multi-Tenant SaaS Microfinance Platform

A production-oriented lending platform on which many microfinance institutions each run their
own operation — customers, KYC, loan products, applications, disbursements, repayments,
ledgers and reporting — from one deployment, with strict data isolation between them.

**Backend:** Java 21 · Spring Boot 3.4 · eleven microservices · MySQL 8.4 · Kafka · OAuth 2.0/JWT
**Mobile:** Flutter · Riverpod · Dio · Android and iOS
**Deployment:** Docker · Kubernetes · GitHub Actions

---

## Quick start

```bash
# Backend: build and test
mvn -f backend/pom.xml verify

# Full local stack (MySQL, Kafka, Redis, all eleven services)
docker compose -f deploy/docker/docker-compose.yml up -d --build

# Mobile app against the local gateway
cd mobile && flutter pub get
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080
```

The gateway is on `localhost:8080`; API documentation is at
`http://localhost:8084/swagger-ui.html` (and the equivalent path on each service).

---

## Repository layout

```
backend/            Maven reactor: 2 shared libraries + 11 services
  common/
    loan-engine/    dependency-free amortisation engine (46 tests)
    platform-common/tenancy, security, errors, auditing, outbox, field encryption
  services/         api-gateway, identity, tenant, customer, loan-product,
                    loan-origination, loan-account, payment, ledger,
                    notification, reporting-audit
mobile/             Flutter app, clean architecture per feature
deploy/docker/      local Compose stack
deploy/k8s/         Kubernetes manifests
docs/               design documentation
.github/workflows/  CI and CD
```

---

## Documentation

| Document | Covers |
|---|---|
| [1. Architecture](docs/01-architecture.md) | High-level architecture, domain model, aggregates, saga design, the outbox, technology trade-offs |
| [2. Services](docs/02-services.md) | What each of the eleven services owns, publishes and calls |
| [3. Database schema](docs/03-database-schema.md) | Per-service schema, conventions, indexing strategy |
| [4. API specification](docs/04-api-specification.md) | Endpoints, auth, pagination, the error contract, rate limits |
| [5. Loan calculations](docs/05-loan-calculations.md) | Formulas and **worked examples generated from the shipped engine** |
| [6. Multi-tenant security](docs/06-multi-tenancy-security.md) | Tenant isolation, authentication, authorisation, encryption, what is *not* claimed |
| [7. Project structure](docs/07-project-structure.md) | Spring Boot and Flutter layering, conventions |
| [8. Deployment](docs/08-deployment.md) | Compose, images, Kubernetes, environments, migrations |
| [9. Testing strategy](docs/09-testing-strategy.md) | The test pyramid, what ships, what CI runs, coverage targets |
| [10. Operations](docs/10-operations.md) | CI/CD, observability, alerting, backup, scaling, readiness checklist |

---

## The decisions that shape this system

Everything else follows from a handful of choices. Each is argued in full in the documents
above; the summary is here so a reviewer can disagree quickly.

**One service owns a loan's balance.** The loan account service is the only writer. The payment
service owns the receipt — channel, reference, cashier, idempotency — and calls the loan account
service synchronously to apply the money. Splitting the balance invariant across two databases
would break on the first concurrent repayment.

**The tenant comes from a verified JWT claim, never from the request.** There is no code path
that reads a tenant id from a body or an unverified header. Four independent layers then defend
the boundary: explicit `tenant_id` predicates on every query, a Hibernate filter as belt and
braces, immutable stamping on insert, and tenant-scoped unique constraints.

**`BigDecimal` everywhere, `double` nowhere.** Binary floating point cannot represent `0.01`.
Over a 240-month loan that error compounds into a real discrepancy on a real customer's balance.
Amounts stay as strings all the way into the Flutter app and are formatted only for display.

**Events are written in the same transaction as the state change.** The transactional outbox
makes "the loan was disbursed" and "a `LoanDisbursed` event exists" atomic. Delivery is
at-least-once and every consumer is idempotent — losing a disbursement event is unacceptable, a
duplicate is merely something to handle.

**Money-moving requests are idempotent by database constraint, not by convention.** A unique
`(tenant_id, idempotency_key)` index is the guarantee; the application logic is only the
ergonomics around it. The mobile client generates the key once per payment and reuses it across
retries, so a double tap on a slow connection cannot take the money twice.

**Financial history is append-only.** The ledger has no update or delete path; a correction is a
contra entry. Audit records are hash-chained, so altering history breaks every subsequent hash
and `/api/v1/audit/verify` detects it.

**Reversals replay the original allocation backwards.** Recomputing would produce a different
split once penalties have accrued, and the loan would not return to its pre-payment state.

**One calculation engine.** The quotation a customer is shown, the preview on an application,
and the schedule generated at disbursement all come from the same code. Two implementations
would drift on rounding alone.

---

## Verified behaviour

The loan engine's documented examples are generated by running the shipped code, not written by
hand. For 100,000 at 12% p.a. over 12 monthly installments:

| | Reducing balance | Flat rate |
|---|--:|--:|
| Monthly installment | 8,884.88 | 9,333.33 |
| Total interest | 6,618.53 | 12,000.00 |
| Total repayable | 106,618.53 | 112,000.00 |

The final installment is 8,884.85 — three cents below the level payment — because the rounding
residue is deliberately concentrated there so the schedule closes at exactly zero. See
[docs/05](docs/05-loan-calculations.md) for grace periods, broken periods, fees, non-monthly
frequencies and allocation.

```bash
mvn -f backend/pom.xml test    # 62 unit tests across the engine and the allocator
```

---

## Status and known gaps

Stated plainly, because a list of what is missing is more useful than an implied claim of
completeness.

**Built and tested:** the amortisation engine and repayment allocator (62 unit tests, all
passing); all eleven services compile and the full reactor builds; tenancy, security, error
handling, auditing, outbox and field encryption; the Flutter app with all fifteen required
screens; Compose, Kubernetes manifests and CI/CD pipelines.

**Not yet done:**

- **Integration tests are specified but not written.** The Testcontainers scaffolding and the
  list of what they must prove are in [docs/09](docs/09-testing-strategy.md); the tests
  themselves are outstanding. This is the most significant gap.
- **The Flutter app has not been compiled** — no Flutter SDK was available in the build
  environment. The Dart is written to the analyzer's strict settings and the import graph and
  structure are verified, but `flutter analyze` has not been run against it.
- **The notification service does not resolve borrower contact details.** It records the message
  against the customer id rather than inventing an address — deliberately inert until the
  customer-service contact lookup is wired.
- **No penetration test.** The security controls are designed, implemented and unit tested; they
  have not been adversarially validated.
- **Key rotation is supported but not automated.** The `keyVersion` prefix makes lazy
  re-encryption possible; the rotation job is not written.
- **Reporting reads its own event-sourced projection**, which is eventually consistent. A report
  run seconds after a payment may not include it.
