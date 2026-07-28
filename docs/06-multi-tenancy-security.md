# 6. Multi-Tenant Security Design

Two competing microfinance institutions may have rows in the same table. Everything below
exists to make it structurally difficult for one to ever see the other's data.

## 6.1 The tenant is derived, never supplied

**The single most important rule in this codebase:** the tenant a request operates on comes
from a signature-verified JWT claim, and from nothing else.

```java
// TenantContextFilter - runs after the bearer token has been validated
UUID tenantId = parseUuid(jwt.getClaimAsString("tid"), "tid");
```

A `tenantId` in a request body, query parameter or unverified header is never read. There is no
code path that accepts one. If there were, the entire isolation model would rest on every
developer remembering to ignore it.

### The one exception, and how it is contained

Platform administrators legitimately operate across tenants. They select one with an explicit
`X-Tenant-Id` header — but:

1. The header is honoured **only** for callers holding `PLATFORM_ADMIN`, checked against the
   token's verified authorities.
2. The API gateway **strips the header entirely** for everyone else, so a forged value never
   reaches a business service at all.
3. Every write performed under an override is audited with the operator's identity.

```java
// CorrelationIdFilter (gateway) - removes what the caller is not entitled to send
if (!platformAdmin) {
    builder.headers(headers -> headers.remove("X-Tenant-Id"));
}
```

## 6.2 Defence in depth at the data layer

Four independent layers must all fail before cross-tenant data can leak.

### Layer 1 — Explicit predicates (the primary control)

Every repository method names the tenant:

```java
Optional<LoanProduct> findByIdAndTenantId(UUID id, UUID tenantId);
```

Note what is *absent*: plain `findById`. It is deliberately not used for tenant-owned entities,
because it bypasses every filter and would happily return another institution's loan.

### Layer 2 — Hibernate filter (belt and braces)

A `@FilterDef` on `TenantAwareEntity` is enabled for the duration of every transaction:

```java
session.enableFilter("tenantFilter").setParameter("tenantId", principal.tenantId());
```

This adds `tenant_id = ?` to entity queries even when a developer forgets. It is explicitly
*not* the primary control — it does not apply to native queries, `find()` by primary key, or
projections built outside the session — and the code says so where it is defined.

### Layer 3 — Immutable stamping on write

```java
@Column(name = "tenant_id", updatable = false)   // cannot be changed after insert
private UUID tenantId;

@PrePersist
void stampTenant() {
    if (tenantId == null) {
        tenantId = TenantContext.requireTenantId();   // from the token, not the payload
    }
}
```

A row cannot be moved between institutions — not by a malicious payload, not by a buggy mapper.

### Layer 4 — Database constraints

Uniqueness is scoped to the tenant, so one institution's data cannot collide with or overwrite
another's:

```sql
UNIQUE KEY ux_customer_tenant_national_id (tenant_id, national_id_index)
UNIQUE KEY ux_loan_product_tenant_code    (tenant_id, code)
UNIQUE KEY ux_idempotency_tenant_key      (tenant_id, idempotency_key)
```

### Why not schema- or database-per-tenant?

| Model | Isolation | Cost at 500 tenants | Verdict |
|---|---|---|---|
| Database per tenant | Strongest | 500 × 10 databases; migrations become an operations project | Rejected — unworkable at SaaS scale |
| Schema per tenant | Strong | Connection pools fragment; Flyway must run per schema | Rejected — same problem, delayed |
| **Shared schema + `tenant_id`** | **Good, with the layers above** | One migration, one pool | **Chosen** |

The chosen model trades a *structural* guarantee for a *defended* one. That trade is only
acceptable because of the four layers, the tests that verify them, and the fact that the tenant
is never client-supplied. A single-tenant regulator-mandated deployment would use the
database-per-tenant model instead — the code needs no change, only configuration.

## 6.3 Thread-local safety

`TenantContext` holds the principal in a `ThreadLocal`. Servlet containers pool threads, so a
leaked value would be *another user's identity on the next request*. It is therefore always
cleared in a `finally` block:

```java
try {
    TenantContext.set(principal);
    chain.doFilter(request, response);
} finally {
    TenantContext.clear();   // unconditional
    MDC.remove("tenantId");
}
```

Background work (event consumers, schedulers) has no inbound request, so it binds an explicit
system principal for the tenant it is processing:

```java
TenantContext.runAs(TenantPrincipal.system(event.tenantId()),
        () -> loanAccountService.openFromDisbursement(event));
```

## 6.4 Authentication

### Token design

RS256, not HS256. Resource servers must be able to **verify** tokens without holding a key that
would also let them **mint** them. Only the identity service holds the private key; everyone
else fetches the public key from `/oauth2/jwks`.

```json
{
  "sub": "9f1c...", "iss": "https://api.mfin.example", "aud": "mfin-api",
  "exp": 1767225600, "iat": 1767224700, "jti": "…",
  "tid": "4a2b...",              // tenant - the isolation anchor
  "tsl": "acme-microfinance",
  "bid": "7c3d...",              // branch
  "roles": ["LOAN_OFFICER"],
  "preferred_username": "jane.officer"
}
```

Access tokens live **15 minutes**. Short-lived by design: there is no revocation list, so the
window of a stolen token is bounded by its expiry, and revocation is enforced by refusing to
refresh.

### Refresh token rotation with reuse detection

Refresh tokens are stored only as SHA-256 digests, so a database compromise yields nothing
usable. Each is single-use; redeeming one issues a replacement in the same family.

**Presenting an already-used refresh token revokes the entire family.** Either the token was
stolen and replayed, or a client is racing itself; both are safest resolved by forcing a fresh
sign-in.

```java
if (stored.getUsedAt() != null) {
    refreshTokenRepository.revokeFamily(stored.getFamilyId(), Instant.now());
    throw new AccessDeniedException("This refresh token has already been used; please sign in again");
}
```

The mobile client cooperates: `AuthInterceptor` collapses concurrent refreshes onto a single
in-flight request, so parallel API calls cannot each spend the token and trip this detection.

### Sign-in hardening

| Control | Implementation |
|---|---|
| Password hashing | BCrypt cost 12 — deliberately slow against offline cracking |
| User enumeration | Wrong username, wrong password and unknown organisation all return the identical message |
| Timing attacks | BCrypt is executed against a dummy hash even when no user exists, so "no such user" is not measurably faster |
| Brute force | 5 failures → 15-minute lockout, time-boxed so an attacker cannot permanently deny service to a legitimate user |
| Rate limiting | Sign-in is limited **by IP** at 5 rps at the gateway (there is no tenant yet, and this is the endpoint credential stuffing targets) |
| Password policy | ≥12 chars, mixed case, digit, symbol, not a blocklisted value, not containing the username or email |
| Recovery | `/forgot-password` always returns 202, whether or not the address exists |

## 6.5 Authorisation

Seven roles, enforced with method security at each service — never only in the app:

| Role | May do |
|---|---|
| `PLATFORM_ADMIN` | Manage tenants and plans; the only role permitted to cross tenants |
| `TENANT_ADMIN` | Everything within one institution |
| `BRANCH_MANAGER` | Approve loans, reverse payments, view reports |
| `LOAN_OFFICER` | Register customers, create applications |
| `CASHIER` | Capture payments |
| `AUDITOR` | Read-only, including the audit trail |
| `CUSTOMER` | Self-service on their own loans |

```java
@PreAuthorize(Roles.Has.LOAN_APPROVE)   // hasAnyRole('TENANT_ADMIN','BRANCH_MANAGER')
public ApplicationResponse approve(...) { … }
```

The gateway verifies the token but deliberately makes **no authorisation decision** — each
service re-validates the same token and applies its own rules, so a request that reaches a
service by any other route is still refused.

### Separation of duties

Enforced in the domain, not by convention:

- One person cannot supply two approval levels on the same application
  (`LoanApplication.hasAlreadyDecided`).
- A payment may be captured by a cashier but reversed only by a manager.
- An administrator cannot remove their own admin role or deactivate their own account, so an
  institution cannot be locked out of its own console.

## 6.6 Encryption of personal data

National ids, phone numbers and identity-document numbers are encrypted at rest with
**AES-256-GCM** — authenticated, so a tampered ciphertext fails to decrypt rather than silently
yielding garbage.

```
stored form:  <keyVersion>:<base64(iv ‖ ciphertext ‖ tag)>
```

A fresh 96-bit IV per value, and a key version prefix so rotation can re-encrypt lazily.

### The searchability problem, and the blind index

Non-deterministic ciphertext cannot be searched. But "is this national id already registered?"
must remain a single indexed query, or duplicate borrowers slip past exposure limits.

Solution: a **keyed HMAC-SHA256 blind index** stored alongside the ciphertext.

```java
mac.init(indexKey);                                     // a different key from the data key
return hex(mac.doFinal(normalise(plaintext)));          // deterministic, but not reversible
```

```sql
national_id       VARCHAR(512)  -- AES-256-GCM ciphertext
national_id_index CHAR(64)      -- HMAC, indexed and unique per tenant
```

Equal inputs produce equal indexes, so uniqueness and exact-match lookup work — without the
plaintext ever being stored or scanned. The index key is separate from the data key: reusing
one key for both would defeat the purpose.

The converter **fails closed** — if the encryptor is not initialised it throws rather than
writing PII in the clear.

## 6.7 Not leaking data through the edges

| Channel | Control |
|---|---|
| API responses | Identity numbers returned masked (`****4821`); full values only on the separately authorised KYC screen |
| Error bodies | No stack traces, SQL or entity names. Rejected values are redacted for password/token/id fields |
| Logs | Tenant and user **ids** in MDC, never names or numbers; notification recipients logged masked |
| Domain events | Carry identifiers and display names only — never identity documents or contact details, because events replicate across topics and consumer databases |
| CSV export | Values beginning `= + - @` are prefixed, defeating spreadsheet formula injection |
| Correlation ids | Client-supplied ids are length-capped and stripped of control characters before reaching a log file |

## 6.8 Integrity of money and history

| Control | Where |
|---|---|
| `BigDecimal` for every amount | Engine, entities, `DECIMAL(19,4)` columns |
| Pessimistic lock on repayment | `findForUpdate` serialises concurrent repayments on one loan |
| Optimistic locking | `@Version` on every entity catches lost updates |
| Idempotency | Unique `(tenant_id, idempotency_key)`; the database, not the application, is the guarantee |
| Append-only ledger | No update or delete path; corrections are contra entries |
| Hash-chained audit | `entryHash = SHA-256(previousHash ‖ fields)`; altering history breaks every subsequent hash, and `/api/v1/audit/verify` walks the chain |
| Non-negative balances | `CHECK` constraints in MySQL |
| Single-sided ledger lines | `CHECK (debit_amount = 0 OR credit_amount = 0)` |

Application-level immutability is only a promise; the hash chain and the database constraints
are what make it verifiable. In production the audit service's database account is granted only
`INSERT` and `SELECT` on `audit_log`, so the database enforces what the code intends.

## 6.9 Transport and platform hardening

- TLS 1.2+ terminated at the ingress; HSTS with a one-year max-age and subdomains.
- Containers run as UID 1001, non-root, read-only root filesystem, all capabilities dropped,
  `seccompProfile: RuntimeDefault`, and the namespace enforces the `restricted` Pod Security
  Standard.
- Service accounts do not automount Kubernetes API tokens — the applications never call it.
- Default-deny NetworkPolicy; services may reach only each other, and only on the ports they
  need. This is what makes "each service owns its data" an enforced boundary rather than a
  convention.
- Secrets come from the cloud KMS or Vault via External Secrets / the CSI driver. Nothing
  sensitive is committed; `.gitignore` excludes `*.pem`, `*.p12` and `.env`.
- CI fails the build on a dependency with CVSS ≥ 7, and on a CRITICAL/HIGH image finding.

## 6.10 What is deliberately not claimed

Being explicit about the gaps is more useful than implying completeness:

- **No penetration test has been run.** The controls above are designed, implemented and unit
  tested; they have not been adversarially validated.
- **Key rotation is supported but not automated.** The `keyVersion` prefix makes lazy
  re-encryption possible; the rotation job itself is not written.
- **The notification service does not yet resolve borrower contact details.** It records the
  message against the customer id rather than inventing an address — deliberately inert until
  the customer-service contact lookup is wired.
- **Field-level encryption protects against database and backup compromise**, not against a
  compromised application process, which necessarily holds the keys.
