# 7. Project Structure

```
micro-finance/
├── backend/                    Maven multi-module reactor
│   ├── pom.xml                 parent: Java 21, Spring Boot 3.4, dependency management
│   ├── Dockerfile              one parameterised build for all eleven services
│   ├── common/
│   │   ├── loan-engine/        pure-domain amortisation engine (no Spring)
│   │   └── platform-common/    tenancy, security, errors, auditing, outbox, crypto
│   └── services/               api-gateway + ten business services
├── mobile/                     Flutter application
├── deploy/
│   ├── docker/                 local Compose stack
│   └── k8s/                    Kubernetes manifests
├── docs/                       this documentation
└── .github/workflows/          CI and CD pipelines
```

## Spring Boot service structure

Domain-driven layering, applied identically in every service. Using `payment-service` as the
example:

```
payment-service/
├── pom.xml
└── src/main/
    ├── java/com/mfin/payment/
    │   ├── PaymentServiceApplication.java   scans com.mfin.payment + com.mfin.common
    │   ├── domain/                          ── the model, with the rules in it
    │   │   ├── Payment.java                 entity: reverse(), isReversed(), invariants
    │   │   ├── PaymentMethod.java
    │   │   └── PaymentStatus.java
    │   ├── repository/                      ── persistence contracts
    │   │   └── PaymentRepository.java       every finder names the tenant explicitly
    │   ├── application/                     ── use cases, transaction boundaries
    │   │   └── PaymentService.java          @Transactional; orchestrates domain + clients
    │   ├── client/                          ── outbound calls to other services
    │   │   └── LoanAccountClient.java       @Retry, @CircuitBreaker, explicit fallback
    │   ├── messaging/                       ── Kafka consumers
    │   ├── config/                          ── service-specific configuration
    │   └── web/                             ── HTTP: thin, no business logic
    │       ├── PaymentController.java       @PreAuthorize, @Valid, OpenAPI annotations
    │       └── dto/PaymentDtos.java         request/response records
    └── resources/
        ├── application.yml
        └── db/migration/                    V1__infrastructure.sql, V2__payment.sql
```

**Dependency direction:** `web → application → domain`, and `application → repository/client`.
The domain layer depends on nothing outward — it holds no Spring annotations beyond JPA
mapping, which is why the amortisation engine can be tested in milliseconds without a context.

**Why entities are not anaemic:** `Payment.reverse()` refuses to reverse twice;
`LoanApplication.approve()` refuses a second approval from the same person;
`LoanAccount.applyAllocation()` is the only way balances move. Putting these in the entity means
there is no path around them — a service method that forgot the check cannot exist.

### The two shared libraries

`loan-engine` is deliberately dependency-free (bar `jakarta.validation-api`). It has no Spring,
no JPA, no database. That is what lets 46 tests run in under a second and makes the calculation
logic reusable and independently reviewable — which matters, because it is the part regulators
would ask about.

`platform-common` carries what would otherwise be copy-pasted eleven times:

```
com/mfin/common/
├── tenant/       TenantContext, TenantContextFilter, TenantPrincipal, Roles, TenantClaims
├── persistence/  BaseEntity, TenantAwareEntity, TenantFilterAspect, JpaAuditingConfig
├── security/     ResourceServerSecurityConfig
├── error/        ApiError, ApiExceptions, ErrorCodes, GlobalExceptionHandler
├── crypto/       FieldEncryptor, EncryptedStringConverter, CryptoProperties
├── outbox/       OutboxEvent, OutboxRelay, DomainEventPublisher
├── idempotency/  IdempotencyRecord, IdempotencyService
├── events/       DomainEvent + the published event contracts
└── web/          PageResponse, RequestIdFilter, OpenApiConfig, ApiHeaders
```

Shared *infrastructure*, never shared *domain*. Services do not share entities; the only things
crossing the boundary are event contracts, which are versioned by name (`loan.disbursed.v1`).

## Flutter structure

Clean architecture per feature, so a feature can be read, tested or removed on its own:

```
mobile/lib/
├── main.dart
├── app.dart                     MaterialApp.router; theme from tenant branding
├── core/
│   ├── config/                  AppConfig (--dart-define), ApiEndpoints
│   ├── network/                 ApiClient, AuthInterceptor
│   ├── storage/                 SecureStorage (Keychain / EncryptedSharedPreferences)
│   ├── error/                   ApiException — the one failure type screens handle
│   ├── theme/                   AppTheme, light and dark from a seed colour
│   ├── router/                  AppRouter — auth guards live here, not in screens
│   ├── utils/                   Formatters, Validators
│   ├── widgets/                 AsyncView, EmptyState, ErrorState, StatTile, StatusChip…
│   └── providers.dart           dependency wiring + SessionController
└── features/
    ├── auth/
    │   ├── domain/session.dart          Session, UserRole, capability getters
    │   ├── data/auth_repository.dart    the only place tokens are written
    │   └── presentation/                tenant selection, login, forgot/change password
    ├── dashboard/ customers/ loan_calculator/ loan_applications/
    ├── loan_accounts/ payments/ ledger/ reports/ settings/
    │       each: domain/ (entities) · data/ (repository + providers) · presentation/ (screens)
    └── shared/domain/paged.dart         the pagination envelope
```

**Layer rules**

- `domain/` — plain Dart. Entities and their derived logic (`Session.canApproveLoans`,
  `LoanAccount.repaymentProgress`). No Flutter imports.
- `data/` — repositories and Riverpod providers. The only layer that knows about HTTP.
- `presentation/` — widgets and screens. Never construct an `ApiClient`; always read a provider.

**Why Riverpod over Bloc.** Most of this app is read-heavy async data, and `AsyncValue` models
loading/data/error as a single type — which is exactly the four-state contract `AsyncView`
enforces on every screen. Bloc would need an event, a state class and a mapper per screen to
express the same thing.

**Why no code generation.** No `freezed`, no `json_serializable`. Models are written by hand
with explicit `fromJson`. It is more typing, but the parsing is visible and reviewable — and in
a financial client, being able to read exactly how an amount is converted is worth the verbosity.

**Money handling.** Amounts stay as `String` end to end, exactly as the backend sent them, and
are formatted only for display. The app never does floating-point arithmetic on money; the two
places it adds numbers (a display-only fee+penalty column, a progress percentage) are commented
as display-only and derive no balance.

## Naming conventions

| Thing | Convention | Example |
|---|---|---|
| Java package | `com.mfin.<service>` | `com.mfin.loanaccount` |
| Table | snake_case singular | `loan_account`, `schedule_installment` |
| Index / constraint | `ix_` / `ux_` / `fk_` / `ck_` prefix | `ux_payment_tenant_receipt` |
| Flyway migration | `V<n>__snake_case.sql` | `V2__loan_account.sql` |
| Event type | `<aggregate>.<past-tense>.v<n>` | `loan.disbursed.v1` |
| Kafka topic | `mfin.<aggregate>` | `mfin.payment` |
| REST path | `/api/v1/<plural-resource>` | `/api/v1/loan-accounts` |
| Dart file | snake_case | `loan_calculator_screen.dart` |
