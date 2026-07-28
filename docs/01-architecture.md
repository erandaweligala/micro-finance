# 1. Architecture and Domain Model

## 1.1 What this system is

A multi-tenant SaaS platform on which many microfinance institutions each run their own
lending operation — customers, loan products, applications, disbursements, repayments and
ledgers — from one deployment, with strict data isolation between them.

Two constraints shape every decision below:

1. **It handles real money.** A lost repayment, a double-charged customer or a balance that
   two screens disagree about is not a bug to fix next sprint; it is a loss, a dispute, or a
   regulatory finding.
2. **Tenants must never see each other.** Two competing institutions may sit in the same
   database. A single missing `WHERE tenant_id = ?` is a data breach.

## 1.2 High-level architecture

```
                    ┌──────────────────────────────┐
   Flutter app ────▶│         API Gateway          │  TLS, JWT verification,
   (Android/iOS)    │  (Spring Cloud Gateway)      │  per-tenant rate limits,
                    └──────────────┬───────────────┘  correlation ids
                                   │
        ┌──────────────┬───────────┼───────────┬──────────────┬─────────────┐
        ▼              ▼           ▼           ▼              ▼             ▼
  ┌──────────┐  ┌───────────┐ ┌─────────┐ ┌─────────┐  ┌───────────┐ ┌──────────┐
  │ Identity │  │  Tenant   │ │Customer │ │  Loan   │  │   Loan    │ │   Loan   │
  │ & Access │  │    &      │ │  & KYC  │ │ Product │  │Origination│ │ Account  │
  │          │  │Subscription│ │        │ │         │  │           │ │& Schedule│
  └────┬─────┘  └─────┬─────┘ └────┬────┘ └────┬────┘  └─────┬─────┘ └────┬─────┘
       │              │            │           │             │            │
       │              │            │           │             │            │
  ┌────▼─────┐  ┌─────▼─────┐ ┌────▼────┐ ┌────▼─────────────▼────────────▼─────┐
  │ identity │  │  tenant   │ │customer │ │  Payment ──▶ Ledger ──▶ Reporting   │
  │    DB    │  │    DB     │ │   DB    │ │     & Audit    &  Notification      │
  └──────────┘  └───────────┘ └─────────┘ └─────────────────────────────────────┘
                                   │
                    ┌──────────────▼───────────────┐
                    │  Kafka  (mfin.* topics)      │  domain events, published
                    │  via transactional outbox    │  atomically with state
                    └──────────────────────────────┘
```

Eleven services, each owning its own MySQL database. No service reads another's tables — the
only paths between them are REST calls through a published contract, and domain events on
Kafka.

### Why microservices here

The honest answer is that a single well-structured modular monolith would serve a small
deployment perfectly well, and would be simpler to operate. Services are justified here by
three specific pressures:

- **Different scaling profiles.** Payment capture spikes hard at month end; reporting runs
  heavy scans; the calculator is pure CPU. Scaling these independently is worth real money.
- **Different blast radii.** A reporting query that goes wrong must not be able to slow down
  the endpoint that takes a cashier's repayment.
- **Different change rates and compliance surfaces.** The ledger and audit services change
  rarely and are audited closely; the customer-facing services change constantly.

Where those pressures do not apply, services were *not* split. KYC lives inside the customer
service rather than in its own, because a customer and their KYC state are one aggregate with
one consistency requirement.

## 1.3 The domain model

### Aggregates and their boundaries

An aggregate is a consistency boundary: everything inside it commits in one transaction,
everything across them is eventually consistent.

| Aggregate | Owned by | Invariant it protects |
|---|---|---|
| **Tenant** | Tenant service | An institution's identity, plan limits and settings |
| **AppUser** | Identity service | Credentials, roles, lockout state |
| **Customer** (+ KycDocument) | Customer service | A borrower's identity and their KYC state |
| **LoanProduct** | Loan product service | The policy envelope loans must fit inside |
| **LoanApplication** (+ ApprovalRecord) | Origination service | The approval workflow and its history |
| **LoanAccount** (+ ScheduleInstallment, AppliedRepayment) | Loan account service | **The loan's balances** — the single most important invariant in the system |
| **Payment** | Payment service | A receipt and its idempotency |
| **LedgerEntry** | Ledger service | Append-only financial history |
| **AuditLog** | Reporting & audit service | Tamper-evident record of who did what |

### The critical boundary: who owns a balance

Exactly one service — the loan account service — may change a loan's outstanding balance. The
payment service owns the *receipt* (channel, reference, cashier, idempotency); the loan account
service owns the *balances* and performs the allocation.

This is deliberate. The obvious alternative — letting the payment service compute the
allocation and tell the loan account what to store — spreads the balance invariant across two
services and two databases, and the first concurrent repayment breaks it. Instead:

```
Cashier ──▶ Payment service ──(synchronous REST, idempotent on paymentId)──▶ Loan account service
                 │                                                              │
                 │                                              locks the loan row,
                 │                                              allocates, moves balances,
                 │◀────────── authoritative allocation ──────────  commits
                 │
                 └──▶ outbox ──▶ Kafka ──▶ Ledger, Notification, Reporting (eventually consistent)
```

The money path is synchronous and strongly consistent. Everything derived from it — the ledger
projection, the SMS to the customer, the portfolio report — is asynchronous, because none of
those need to be correct *at the instant* the cashier hands back a receipt, and making them
synchronous would mean an SMS gateway outage could block a repayment.

### Domain model (core entities)

```
Tenant 1──* Branch
Tenant 1──* AppUser ──* Role
Tenant 1──* Customer 1──* KycDocument
Tenant 1──* LoanProduct

Customer 1──* LoanApplication *──1 LoanProduct
LoanApplication 1──* ApprovalRecord
LoanApplication 1──1 LoanAccount          (created on disbursement)

LoanAccount 1──* ScheduleInstallment
LoanAccount 1──* AppliedRepayment  *──1 Payment
LoanAccount 1──* LedgerEntry
```

## 1.4 Communication patterns

### Synchronous REST — used when the caller cannot proceed without the answer

- Origination → Customer service: *is this borrower KYC-verified?* An application must not be
  accepted otherwise.
- Origination → Product service: *what are this product's bounds?* Needed to validate terms.
- Payment → Loan account: *apply this repayment.* The cashier cannot be given a receipt until
  the money has actually been applied.

Every one of these is wrapped in Resilience4j retry + circuit breaker, and every one has an
explicit, honest failure mode. When the loan account service is unreachable, payment capture
**fails** rather than issuing a receipt for money that never reached a loan.

### Asynchronous events — used when the consumer can lag

Published through a **transactional outbox** (see §1.5) onto Kafka topics keyed by aggregate id,
so all events for one loan land on one partition and are consumed in order.

| Event | Producer | Consumers |
|---|---|---|
| `customer.kyc-status-changed.v1` | Customer | Reporting/audit |
| `loan.approved.v1` | Origination | Notification, reporting/audit |
| `loan.disbursed.v1` | Origination | **Loan account** (opens the account), reporting/audit |
| `loan.account-opened.v1` | Loan account | Ledger, notification, reporting |
| `payment.posted.v1` | Payment | Ledger, notification, reporting |
| `payment.reversed.v1` | Payment | Ledger (contra entry), reporting |
| `loan.overdue.v1` | Loan account | Notification (dunning), reporting |
| `tenant.onboarded.v1` | Tenant | Identity (sign-in directory) |

Every consumer is **idempotent**, keyed on the source event id, because Kafka delivery is
at-least-once and a duplicated `loan.disbursed` would otherwise open two loan accounts.

## 1.5 The transactional outbox

The problem: a service must both change its state and publish an event, and those are two
different systems. Publishing inside the transaction risks a *phantom* event (published, then
the transaction rolls back). Publishing after commit risks a *lost* event (committed, then the
process dies).

The solution used throughout: write the event to an `outbox_event` row **in the same
transaction** as the business change. A relay polls that table and publishes to Kafka, marking
rows delivered only after the broker acknowledges.

```java
@Transactional
public void disburse(...) {
    application.markDisbursed(...);      // business state
    eventPublisher.publish(new LoanDisbursed(...));  // outbox row - same transaction
}                                         // both commit, or neither does
```

`DomainEventPublisher.publish` is annotated `@Transactional(propagation = MANDATORY)`, so
calling it outside a transaction fails immediately rather than silently losing the guarantee.

Delivery is **at least once**, never at most once. Losing a disbursement event is unacceptable;
a duplicate is merely something consumers must handle, and they do.

## 1.6 Sagas

Two workflows span services and therefore cannot be one transaction.

### Disbursement saga

```
1. Origination:   records disbursement + LoanDisbursed event      [committed atomically]
2. Loan account:  consumes event, opens account, builds schedule  [idempotent on applicationId]
3. Loan account:  emits LoanAccountOpened
4. Ledger:        posts the opening debit
5. Notification:  tells the customer

Compensation: if step 2 fails permanently, the event dead-letters and
LoanAccountOpeningFailed is published, which raises the disbursed-but-unopened loan
to an operator. It is never silently dropped.
```

### Repayment flow

Strongly consistent for the money (steps 1–2), eventually consistent for the rest:

```
1. Payment:      claims the idempotency key
2. Loan account: locks the loan, allocates, moves balances     [synchronous, must succeed]
3. Payment:      writes the receipt + PaymentPosted event      [same transaction]
4. Ledger / Notification / Reporting consume the event
```

If step 2 fails, no receipt exists and the cashier retries — the safe direction to fail.

## 1.7 Technology choices and their trade-offs

| Choice | Why | What it costs |
|---|---|---|
| Java 21 + Spring Boot 3.4 | Virtual threads, records, mature financial ecosystem, long support | Heavier memory footprint per service than Go |
| MySQL 8.4, one DB per service | Strong ACID for money; `SELECT ... FOR UPDATE` for balance serialisation | No cross-service joins; reporting needs its own read model |
| `BigDecimal` everywhere | `double` cannot represent 0.01; error compounds over a loan's life | More verbose arithmetic |
| Kafka | Ordered per-partition delivery, replayable, consumers can lag | Operational weight; needs KRaft or ZooKeeper |
| RS256 JWT | Resource servers verify without holding a minting key | Key rotation must be managed |
| Flyway | Schema is versioned, reviewable, and identical in every environment | Migrations must be backwards compatible during rollout |
| Riverpod + Dio (Flutter) | `AsyncValue` models loading/data/error as one type | Riverpod's learning curve over `setState` |

## 1.8 Non-functional targets

| Concern | Target | How it is met |
|---|---|---|
| Payment capture latency | p99 < 500 ms | Synchronous path is two hops; ledger work is async |
| Availability | 99.9% | ≥2 replicas, PDBs, zone spread, circuit breakers |
| Money correctness | Exact to the cent | `BigDecimal`, DB constraints, optimistic + pessimistic locking |
| Tenant isolation | Absolute | Token-derived tenant + explicit predicates + Hibernate filter |
| Recovery point | ≤5 minutes | Binlog shipping + pre-deploy snapshot |
| Auditability | Every financial action | Event-derived, hash-chained audit log |
