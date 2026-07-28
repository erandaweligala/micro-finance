# 4. API Specification

Every service publishes OpenAPI 3 at `/v3/api-docs` with Swagger UI at `/swagger-ui.html`.
This document is the summary; the generated spec is authoritative.

All traffic goes through the gateway at `https://api.mfin.example`. The app never addresses a
service directly.

## Conventions

**Authentication** — `Authorization: Bearer <access token>` on everything except sign-in, token
refresh, password recovery, tenant branding lookup and JWKS.

**Tenancy** — taken from the token's `tid` claim. Never send a tenant in a body or query
parameter; it will be ignored. Platform administrators select a tenant with `X-Tenant-Id`,
which the gateway strips for everyone else.

**Idempotency** — money-moving `POST`s accept `Idempotency-Key`. Generate it once per logical
operation and reuse it for every retry.

**Pagination** — `?page=0&size=20&sort=field,asc`, returning:

```json
{ "content": [...], "page": 0, "size": 20,
  "totalElements": 137, "totalPages": 7, "last": false }
```

**Errors** — one shape everywhere:

```json
{
  "timestamp": "2026-07-28T10:15:30Z",
  "status": 422,
  "code": "BUSINESS_RULE_VIOLATION",
  "message": "The requested terms breach product policy: amount must be between 5000 and 500000",
  "path": "/api/v1/loan-applications",
  "traceId": "3f2a9c...",
  "errors": [{ "field": "requestedAmount", "message": "must be greater than zero" }]
}
```

| Status | When |
|---|---|
| 400 `VALIDATION_FAILED` | Bean Validation rejected the request |
| 401 `UNAUTHENTICATED` | Missing, expired or invalid token |
| 403 `ACCESS_DENIED` / `TENANT_RESOLUTION_FAILED` | Authenticated but not entitled |
| 404 `RESOURCE_NOT_FOUND` | Absent, **or belongs to another tenant** — the two are indistinguishable by design |
| 409 `CONFLICT` / `IDEMPOTENCY_KEY_REUSED` | Duplicate key, stale version, or a key replayed with a different payload |
| 422 `BUSINESS_RULE_VIOLATION` / `LOAN_CALCULATION_INVALID` / `ILLEGAL_STATE_TRANSITION` | Valid syntax, disallowed by a domain rule |
| 429 `RATE_LIMITED` | Quota exceeded |
| 503 `UPSTREAM_UNAVAILABLE` | A downstream service is unavailable; safe to retry |

## Authentication — `/api/v1/auth`

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/login` | — | Sign in to an institution (`tenantSlug`, `username`, `password`) |
| POST | `/platform-login` | — | Platform operator sign-in |
| POST | `/refresh` | — | Rotate the refresh token |
| POST | `/logout` | Bearer | Revoke the presented refresh token |
| POST | `/change-password` | Bearer | Change own password; revokes all sessions |
| POST | `/forgot-password` | — | Request a reset link; **always 202** |
| POST | `/reset-password` | — | Complete a reset |
| GET | `/oauth2/jwks` | — | Public verification keys |

`POST /login` →

```json
{ "accessToken": "eyJ…", "refreshToken": "…", "tokenType": "Bearer",
  "expiresInSeconds": 900, "userId": "…", "tenantId": "…",
  "tenantSlug": "acme-microfinance", "fullName": "Jane Otieno",
  "roles": ["LOAN_OFFICER"], "mustChangePassword": false }
```

## Users — `/api/v1/users`

| Method | Path | Role |
|---|---|---|
| GET | `/me` | any |
| POST | `/` | `TENANT_ADMIN` |
| GET | `/` | management |
| GET/PUT | `/{userId}` | management / `TENANT_ADMIN` |
| PUT | `/{userId}/roles` | `TENANT_ADMIN` — revokes existing sessions |
| POST | `/{userId}/password-reset` | `TENANT_ADMIN` |
| POST | `/{userId}/activate`, DELETE `/{userId}` | `TENANT_ADMIN` |
| POST | `/provision-tenant-admin` | `PLATFORM_ADMIN` |

## Tenants — `/api/v1/tenants`

| Method | Path | Role |
|---|---|---|
| POST | `/` | `PLATFORM_ADMIN` — onboard an institution and its first administrator |
| GET | `/`, `/{tenantId}` | `PLATFORM_ADMIN` |
| POST | `/{tenantId}/suspend`, `/activate` | `PLATFORM_ADMIN` |
| GET/PUT | `/current` | read / `TENANT_ADMIN` |
| PUT | `/current/branding` | `TENANT_ADMIN` — requires the `CUSTOM_BRANDING` feature |
| GET | `/current/subscription` | `TENANT_ADMIN` |
| GET/POST/PUT/DELETE | `/current/branches` | read / `TENANT_ADMIN` |
| GET/PUT | `/current/settings` | `TENANT_ADMIN` |
| GET | `/branding/{slug}` | **public** — logo and colours for the sign-in screen |

## Customers — `/api/v1/customers`

| Method | Path | Role |
|---|---|---|
| POST | `/` | loan write |
| GET | `/` | read — `query`, `status`, `kycStatus`, `branchId`, `loanOfficerId` |
| GET | `/{id}` | read |
| GET | `/{id}/eligibility` | read — used by origination |
| PUT | `/{id}` | loan write |
| POST | `/{id}/deactivate`, `/reactivate` | management |
| POST/GET | `/{id}/kyc/documents` | loan write / read |
| POST | `/{id}/kyc/documents/{docId}/verify` | management |
| POST | `/{id}/kyc/decision` | management |

Search matches names and customer number by substring; a full phone or identification number
matches exactly, resolved through the blind index.

## Loan products and the calculator

| Method | Path | Role |
|---|---|---|
| POST/PUT | `/api/v1/loan-products`, `/{id}` | `TENANT_ADMIN` |
| GET | `/api/v1/loan-products`, `/{id}` | read |
| POST | `/api/v1/loan-products/{id}/activate` | `TENANT_ADMIN` |
| DELETE | `/api/v1/loan-products/{id}` | `TENANT_ADMIN` — withdraws from sale |
| POST | `/api/v1/loan-products/{id}/calculate` | read — quote against product policy |
| POST | `/api/v1/loan-calculations` | any — standalone calculator |

`POST /api/v1/loan-calculations`

```json
{ "principal": "100000.00", "annualInterestRate": "12", "numberOfInstallments": 12,
  "repaymentFrequency": "MONTHLY", "interestMethod": "REDUCING_BALANCE",
  "disbursementDate": "2026-01-15" }
```

→

```json
{ "installmentAmount": "8884.88", "totalInterest": "6618.53",
  "totalRepayable": "106618.53", "netDisbursedAmount": "100000.00",
  "periodicRate": "0.01", "firstDueDate": "2026-02-15", "maturityDate": "2027-01-15",
  "brokenPeriodInterest": "0.00",
  "schedule": [
    { "installmentNumber": 1, "dueDate": "2026-02-15", "openingBalance": "100000.00",
      "principal": "7884.88", "interest": "1000.00", "totalDue": "8884.88",
      "closingBalance": "92115.12" }
  ] }
```

## Loan applications — `/api/v1/loan-applications`

| Method | Path | Role |
|---|---|---|
| POST | `/` | loan write |
| GET | `/` | read — filter by `status` to build approval queues |
| GET | `/{id}`, `/{id}/schedule` | read |
| PUT | `/{id}` | loan write — drafts only |
| POST | `/{id}/submit` | loan write |
| POST | `/{id}/review` | approve |
| POST | `/{id}/approve` | approve — one level; a person may not approve twice |
| POST | `/{id}/reject` | approve |
| POST | `/{id}/cancel` | loan write |
| POST | `/{id}/disburse` | approve |

## Loan accounts — `/api/v1/loan-accounts`

| Method | Path | Role |
|---|---|---|
| GET | `/` | read |
| GET | `/{id}` | read |
| GET | `/{id}/schedule` | read — due and paid per bucket, per installment |
| GET | `/{id}/payoff-quote?asOf=` | read — early settlement cost |

Internal, not routed from the internet — `SYSTEM` or cashier role:

| POST | `/internal/v1/loan-accounts/{id}/repayments` | idempotent on `paymentId` |
| POST | `/internal/v1/loan-accounts/{id}/repayments/{paymentId}/reverse` | manager or system |

## Payments — `/api/v1/payments`

| Method | Path | Role |
|---|---|---|
| POST | `/` | payment write — **requires `Idempotency-Key`** |
| GET | `/` | read — `loanAccountId`, `customerId`, `status`, `from`, `to` |
| GET | `/{id}`, `/{id}/receipt` | read |
| POST | `/{id}/reverse` | `TENANT_ADMIN` or `BRANCH_MANAGER`, reason required |

```
POST /api/v1/payments
Idempotency-Key: 9f1c2b7a-3d4e-4f10-9a2b-6c8d0e1f2a3b

{ "loanAccountId": "…", "amount": "8884.88", "method": "MOBILE_MONEY",
  "externalReference": "MPESA-QGH7X2P1", "valueDate": "2026-02-15" }
```

→ 201 with the allocation:

```json
{ "receiptNumber": "RCP-00000042", "amount": "8884.88",
  "penaltyAllocated": "0.00", "feeAllocated": "0.00",
  "interestAllocated": "1000.00", "principalAllocated": "7884.88",
  "excessAmount": "0.00", "totalOutstandingAfter": "97733.65", "status": "POSTED" }
```

Replaying the same key with the same body returns the original receipt. Replaying it with a
*different* body returns 409 `IDEMPOTENCY_KEY_REUSED`.

## Ledger — `/api/v1/ledger`

| Method | Path | Role |
|---|---|---|
| GET | `/entries` | read — `loanAccountId`, `customerId`, `type`, `from`, `to`, paginated |
| GET | `/loans/{id}/statement` | read — opening/debits/credits/closing plus entries |
| GET | `/loans/{id}/statement.csv` | read — CSV export |

Each entry carries transaction date, reference, type, debit, credit, the principal/interest/
penalty allocation, and the outstanding principal and total outstanding **as at that
transaction**.

## Reporting and audit

| Method | Path | Role |
|---|---|---|
| GET | `/api/v1/dashboard` | read |
| GET | `/api/v1/reports/portfolio` | read |
| GET | `/api/v1/reports/arrears-aging` | read |
| GET | `/api/v1/reports/activity?from=&to=` | read |
| GET | `/api/v1/audit` | `PLATFORM_ADMIN`, `TENANT_ADMIN`, `AUDITOR` |
| GET | `/api/v1/audit/verify` | as above — walks the hash chain |

## Rate limits

| Scope | Limit |
|---|---|
| Sign-in (per IP) | 5 rps, burst 10 |
| Authenticated (per tenant) | 50 rps, burst 100 |
| Ingress (per IP) | 100 rps |

Exceeding a limit returns 429 with `Retry-After`.
