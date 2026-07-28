# 2. Microservice Responsibilities

Each service owns its database outright. No service reads another's tables; the only paths
between them are the REST contracts and domain events listed here.

## API Gateway — port 8080

The single ingress. Reactive (Netty) rather than servlet-based, because a gateway spends nearly
all its time waiting on downstream I/O.

**Does:** TLS termination, JWT signature/expiry verification, routing, per-tenant rate limiting
(Redis-backed so the quota is shared across replicas), correlation ids, circuit breakers with
fallbacks, CORS.

**Deliberately does not:** make authorisation decisions. Each service re-validates the same
token, so a request reaching a service by any other route is still refused.

**Notable rules:** sign-in is rate limited *by IP* at 5 rps (no tenant exists yet, and it is the
credential-stuffing target); `/internal/**` is denied outright and never routed; `X-Tenant-Id`
is stripped for anyone who is not a platform administrator.

## Identity and Access Service — port 8081

**Owns:** `app_user`, `refresh_token`, `password_reset_token`, `tenant_directory`.

**Does:** issues RS256 access and refresh tokens, publishes JWKS, rotates refresh tokens with
reuse detection, enforces the password policy and lockout, manages users and roles within an
institution, provisions the first administrator during tenant onboarding.

**Design note:** it keeps a local `tenant_directory` projection fed by `tenant.onboarded.v1`
and `tenant.status-changed.v1`. Sign-in is the most availability-sensitive path in the platform
and must not depend on a synchronous call to the tenant service.

## Tenant and Subscription Service — port 8082

**Owns:** `tenant`, `subscription_plan`, `tenant_subscription`, `branch`, `tenant_setting`.

**Does:** onboards institutions, manages plans/limits/features, branches, per-tenant settings,
branding, suspension and reactivation.

**Design note:** `tenant` is the one aggregate that is *not* tenant-scoped — it defines the
tenants. Plan limits are enforced at the point of use (creating a branch, a user, a customer),
not by a nightly sweep, so an institution cannot quietly exceed what it pays for.

**Publishes:** `tenant.onboarded.v1`, `tenant.status-changed.v1`.

## Customer and KYC Service — port 8083

**Owns:** `customer`, `kyc_document`.

**Does:** registration, search, update, deactivation, KYC document capture and verification,
and the eligibility check origination depends on.

**Design note:** identity numbers, phone numbers and email are AES-256-GCM encrypted with an
HMAC blind index for exact-match search. KYC approval requires every mandatory document to be
verified and unexpired — enforced in the service, and shown as a disabled button in the app.

**Publishes:** `customer.registered.v1`, `customer.updated.v1`, `customer.kyc-status-changed.v1`,
`customer.deactivated.v1`.

## Loan Product Service — port 8084

**Owns:** `loan_product`.

**Does:** product configuration (amount/rate/term bounds, interest method, frequency, grace,
fees, penalties, approval levels) and **the loan calculator API**.

**Design note:** the calculator lives here because this service owns the terms a loan may be
written on. `POST /api/v1/loan-calculations` prices a hypothetical loan; `POST
/api/v1/loan-products/{id}/calculate` prices against a product and enforces its policy.

## Loan Origination Service — port 8085

**Owns:** `loan_application`, `approval_record`.

**Does:** the application lifecycle (DRAFT → SUBMITTED → UNDER_REVIEW → APPROVED → DISBURSED,
with REJECTED and CANCELLED terminals), configurable multi-level approval, and recording
disbursement.

**Design note:** permitted state transitions are declared in one place
(`LoanApplicationStatus`), not scattered through service methods. Requested and approved terms
are stored separately, because an approver may sanction less than was asked for and a committee
needs to see the difference. Disbursement is where this service hands the loan over.

**Calls:** customer service (eligibility), product service (policy bounds).
**Publishes:** `loan.approved.v1`, `loan.disbursed.v1`.

## Loan Account and Schedule Service — port 8086

**Owns:** `loan_account`, `schedule_installment`, `applied_repayment`.

The most important service in the platform: **it is the only writer of a loan's balance.**

**Does:** opens accounts from `loan.disbursed.v1` (idempotent on application id), materialises
the repayment schedule, applies and reverses repayments, runs the daily arrears and penalty
job, produces payoff quotes.

**Design notes:**
- Repayment posting takes a pessimistic lock on the loan row, so concurrent repayments
  serialise instead of racing on the same balance.
- Posting is idempotent on `payment_id`, so the payment service's retry is safe.
- Reversal *replays the stored allocation backwards* rather than recomputing it — recomputing
  would produce a different split once penalties have accrued, and the loan would not return to
  its pre-payment state.

**Publishes:** `loan.account-opened.v1`, `loan.account-opening-failed.v1`, `loan.overdue.v1`,
`loan.closed.v1`.

## Payment Service — port 8087

**Owns:** `payment`, `idempotency_record`.

**Does:** captures repayments, issues receipts, reverses payments under authorisation, and
guarantees idempotency.

**Design note:** owns the *receipt* — channel, reference, cashier, value date — while the loan
account service owns the *balances*. The loan is updated first and the receipt written
afterwards: the loan call is idempotent and safely repeatable, whereas a receipt written before
a failed posting would promise the customer something that never reached their loan.

**Calls:** loan account service (apply/reverse).
**Publishes:** `payment.posted.v1`, `payment.reversed.v1`.

## Ledger Service — port 8088

**Owns:** `ledger_entry`.

**Does:** maintains the append-only customer loan ledger from domain events; serves filtered,
paginated queries; produces statements and CSV export.

**Design note:** a projection — it never originates a transaction. Every write is guarded by
the source event id (unique per tenant), because at-least-once delivery would otherwise
duplicate a disbursement line and misstate a customer's debt. Reversals are contra entries; the
original line is never removed.

## Notification Service — port 8089

**Owns:** `notification_template`, `notification_log`.

**Does:** renders per-tenant templates and delivers email/SMS/push; records every attempt.

**Design note:** delivery failures never propagate to the caller — a repayment must not roll
back because an SMS gateway is down. Recipients are stored and logged masked. The transport is
an interface, so a tenant can be moved between providers without touching business logic.

## Reporting and Audit Service — port 8090

**Owns:** `audit_log`, `loan_snapshot`.

**Does:** writes the immutable hash-chained audit trail from every business topic; maintains a
denormalised loan read model; serves portfolio, arrears-ageing and activity reports and the
dashboard.

**Design notes:**
- Auditing is derived from **events**, not HTTP interception, so an action is recorded because
  it happened — not because someone remembered to annotate a controller.
- It keeps its own read model because portfolio reports scan the whole book, and running those
  scans against the transactional database would put analytics load on the system that has to
  accept repayments.
- Portfolio at risk is measured on outstanding principal, the definition regulators and funders
  use.
