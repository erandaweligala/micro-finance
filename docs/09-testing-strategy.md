# 9. Testing Strategy

The guiding principle: **test where the risk is.** A CRUD controller failing is an
inconvenience; an amortisation schedule that rounds wrongly is a customer dispute, and a
repayment applied twice is a loss. Effort is allocated accordingly.

## 9.1 The shape of the suite

```
        ▲  Manual exploratory — new workflows, before a major release
       ╱ ╲
      ╱   ╲   Contract tests — event schemas and REST contracts between services
     ╱─────╲
    ╱       ╲  Integration tests — Testcontainers MySQL: migrations, real SQL,
   ╱         ╲                     transactions, locking, tenant isolation
  ╱───────────╲
 ╱             ╲ Unit tests — the financial core, exhaustively
╱───────────────╲
```

Deliberately not a broad end-to-end layer. E2E tests across eleven services are slow, flaky and
tend to fail for reasons unrelated to the change. Contract tests at the boundaries plus thorough
integration tests inside each service catch the same defects, sooner and more legibly.

## 9.2 Unit tests — what ships today

### `LoanCalculatorTest` — 46 tests

| Group | Covers |
|---|---|
| Reducing balance | Annuity formula, first-installment split, monotonic interest/principal curves, single installment, 240-month tenor |
| Flat rate | Total interest scaling with tenor in years, flat-vs-reducing comparison, level installments with the residue on the last row |
| Zero interest | Both methods, and indivisible principals |
| Grace periods | Interest-only and full moratorium with capitalisation |
| Broken periods | Stub interest, all three day-count conventions, no negative amortisation |
| Fees | All three collection modes |
| Frequencies | All six, month-end clamping, zero-decimal currencies |
| Validation | Every rule, and that the exception names the offending field |
| Precision | Recurring-decimal rates, tiny principals, agreement with a double-precision reference |

Every schedule-producing test also asserts universal invariants via
`assertScheduleIsCoherent`: the balance walk is continuous, components sum to the headline
totals, and the loan closes at **exactly** zero. That single helper is what catches a rounding
regression anywhere in the engine, including in a case nobody thought to write a test for.

> This suite earned its keep during development: it caught a real due-date bug where a loan
> disbursed on 31 January drifted permanently to the 28th of every later month.

### `RepaymentAllocatorTest` — 16 tests

All three allocation orders; oldest-debt-first settlement; partial, advance and excess payments;
settled-installment skipping; due-today boundary handling; and the invariant that **every
payment is accounted for exactly once** — asserted across a range of amounts, which is the test
that would catch double-counting.

### Flutter — 3 suites

`validators_test.dart` pins client validation to the backend's rules (drift means users are
told a value is fine and then rejected). `formatters_test.dart` covers money formatting,
including null and unparseable input. `session_test.dart` covers role parsing — notably that an
unknown role grants nothing — and every capability getter, because those decide what the UI
offers.

## 9.3 Integration tests — Testcontainers

Run against a **real MySQL**, never H2. An in-memory database accepts DDL that MySQL rejects and
has different locking semantics — passing on H2 would be actively misleading for exactly the
behaviour these tests exist to prove.

```java
@SpringBootTest
@Testcontainers
@Tag("integration")
class PaymentIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }
}
```

What they must prove:

| Area | Assertion |
|---|---|
| Migrations | Flyway applies cleanly from empty, and Hibernate's `ddl-auto: validate` agrees with the result |
| **Tenant isolation** | A request bearing tenant A's token cannot read, update or delete tenant B's row — by id, by search, or by any endpoint |
| Idempotency | The same key twice yields one payment and two identical responses; a different payload yields 409 |
| Concurrency | Two simultaneous repayments on one loan produce correct balances, exercising the pessimistic lock |
| Outbox | A business change and its event commit together, and roll back together |
| Constraints | Negative balances and double-sided ledger lines are rejected by the database |
| Optimistic locking | A stale version surfaces as 409, not a silent overwrite |

Tagged `@Tag("integration")` and bound to a Maven profile, so `mvn verify` stays fast for the
inner loop while CI runs the full set.

## 9.4 Security tests

Authorisation is verified as behaviour, not by reading annotations:

```java
@Test
@WithMockUser(roles = "CASHIER")
void aCashierCannotApproveALoan() throws Exception {
    mockMvc.perform(post("/api/v1/loan-applications/{id}/approve", id))
           .andExpect(status().isForbidden());
}
```

The set that must exist for every service:

- Every endpoint rejects an absent, expired or wrongly-signed token.
- Every role is tested against every endpoint it must *not* reach — the negative cases are the
  ones that matter.
- A tenant id in a request body is ignored; the token's claim wins.
- `X-Tenant-Id` is honoured only for `PLATFORM_ADMIN`.
- Error responses never contain stack traces, SQL or entity names.
- Sign-in returns an identical message and comparable timing for unknown user, wrong password
  and unknown organisation.
- Lockout engages after the configured number of failures.
- A replayed refresh token revokes the family.

## 9.5 Contract tests

Services are coupled through event schemas and REST contracts, and those break silently. Two
guards:

**Events** — each consumer keeps a fixture of the producer's serialised event and asserts it
still deserialises. Adding a field must not break a consumer; removing or renaming one must fail
the producer's build.

**REST** — Spring Cloud Contract, or a checked-in OpenAPI snapshot diffed in CI. A removed field
or a changed status code fails the build rather than staging.

## 9.6 What CI runs

```
backend-test              unit tests, all modules                    ~2 min
backend-integration-test  Testcontainers MySQL                       ~8 min
security-scan             OWASP dependency check (fails CVSS ≥ 7),
                          CodeQL, gitleaks                           ~6 min
mobile-test               dart format, flutter analyze --fatal-infos,
                          flutter test                               ~4 min
build-images              11 images, Trivy scan (fails CRITICAL/HIGH)
```

Ordered cheapest-and-most-informative first: unit tests fail before the integration suite has
started a container.

## 9.7 Coverage targets

Coverage is a diagnostic, not a goal — but the distribution matters:

| Area | Target | Why |
|---|---|---|
| `loan-engine` | ≥95% branch | The financial core; every branch is a money path |
| `RepaymentAllocator` | ≥95% branch | Decides where a customer's money goes |
| Domain entities | ≥90% | State transitions and invariants live here |
| Application services | ≥80% | Orchestration; integration-tested as well |
| Controllers | ≥70% | Thin; contract tests carry more weight |
| DTOs, config | untracked | Testing a record's accessor proves nothing |

## 9.8 Load and resilience testing

Before a production launch, and quarterly after:

- **Load** — sustained payment capture at 3× the observed month-end peak; p99 < 500 ms.
- **Soak** — 24 hours at expected load, watching for heap growth and connection-pool leaks.
- **Chaos** — kill the loan account service mid-repayment and assert no partial application;
  partition Kafka and assert the outbox drains on recovery; fail over MySQL and assert
  reconnection.
- **Recovery** — restore from backup into a scratch environment and reconcile the loan book
  against the ledger. A backup that has never been restored is a hypothesis, not a backup.
