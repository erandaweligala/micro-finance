# 8. Deployment

## 8.1 Local development

```bash
docker compose -f deploy/docker/docker-compose.yml up -d --build
```

Brings up MySQL 8.4, Kafka (KRaft), Redis and all eleven services. The gateway is on
`localhost:8080`; each service also exposes its own port for direct debugging.

| Service | Port | Database |
|---|---|---|
| api-gateway | 8080 | — |
| identity-service | 8081 | `mfin_identity` |
| tenant-service | 8082 | `mfin_tenant` |
| customer-service | 8083 | `mfin_customer` |
| loan-product-service | 8084 | `mfin_product` |
| loan-origination-service | 8085 | `mfin_origination` |
| loan-account-service | 8086 | `mfin_loanaccount` |
| payment-service | 8087 | `mfin_payment` |
| ledger-service | 8088 | `mfin_ledger` |
| notification-service | 8089 | `mfin_notification` |
| reporting-audit-service | 8090 | `mfin_reporting` |

Each service owns a separate database. They share one MySQL container purely to keep a laptop
workable; in every deployed environment they are separate instances, because "shares a server"
degrades into "shares a schema" the first time someone is in a hurry.

The Compose file ships development encryption keys. Every deployed environment injects them
from a secret store, and the services refuse to start without them. `CRYPTO_DATA_KEY` must
decode to exactly 16, 24 or 32 bytes: AES rejects any other length on the first encrypted
write, not at startup, so the stack looks healthy until the first customer is created.

A fresh stack has no users. The identity schema seeds none, so before anything can be signed
in, insert a platform operator directly - the password is BCrypt, so hash it with the same
encoder the service uses:

```sql
-- mfin_identity. The hash below is BCrypt("ChangeMe123!") at strength 12,
-- which is what JwtKeyConfig configures the encoder with.
INSERT INTO app_user (id, tenant_id, username, email, full_name, password_hash,
                      password_updated_at, must_change_password, status, created_at)
VALUES (UUID(), NULL, 'platform.admin', 'admin@local', 'Local Platform Admin',
        '$2a$12$jTdxre6kR/ISSpdUhHCVp.XrN11FKoNm63zrmCL6ztSy6ag17lAOi',
        NOW(6), 0, 'ACTIVE', NOW(6));
INSERT INTO app_user_role (user_id, role)
SELECT id, 'PLATFORM_ADMIN' FROM app_user WHERE username = 'platform.admin';
```

Then `POST /api/v1/auth/platform-login` with those credentials returns the token every other
endpoint authorises against.

### Running one service from an IDE against the Compose stack

The usual debugging shape: infrastructure in containers, the service under test on the host.

```bash
# Infrastructure only - no application containers
docker compose -f deploy/docker/docker-compose.yml up -d mysql kafka redis

# Install the shared libraries into ~/.m2 once, from the aggregator
mvn -f backend/pom.xml install -DskipTests
```

Every service's `application.yml` already defaults `DB_URL`, `KAFKA_BROKERS` and `REDIS_HOST`
to `localhost`, and each defaults to its own database, so a service started on the host needs
only the encryption keys:

```bash
CRYPTO_DATA_KEY=ZGV2LWRhdGEta2V5LTMyLWJ5dGVzLWFlcy1sb2NhbCE= \
CRYPTO_INDEX_KEY=ZGV2LWluZGV4LWtleS0zMi1ieXRlcy1sb25nLWZvci1obWFj \
mvn -f backend/pom.xml -pl services/identity-service spring-boot:run
```

Two things differ from the container defaults. The gateway's downstream URIs default to
Docker service names, so running it on the host needs `IDENTITY_URI=http://localhost:8081`
and the equivalent for each service it routes to. And a service run on the host must not
also be running in Compose - the two would contend for the same port.

In IntelliJ, link `backend/pom.xml` as the Maven root project (not an individual module POM,
which cannot resolve its `com.mfin` siblings on its own) and set the environment variables
above on the Spring Boot run configuration.

### Running the mobile app against it

```bash
cd mobile
flutter pub get
# 10.0.2.2 is the host loopback as seen from the Android emulator
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080 --dart-define=ENVIRONMENT=local
```

### Building the backend

```bash
mvn -f backend/pom.xml verify          # compile + unit tests
mvn -f backend/pom.xml verify -Pintegration-test   # + Testcontainers integration tests
```

## 8.2 Container image

One parameterised `backend/Dockerfile` builds every service:

```bash
docker build --build-arg SERVICE=payment-service -f backend/Dockerfile -t payment-service:1.0.0 .
```

Points worth noting:

- **Layered caching** — POMs are copied and dependencies resolved before the source, so a code
  change rebuilds in seconds rather than re-downloading the dependency tree.
- **Multi-stage** — the Maven toolchain never reaches the runtime image; only a JRE and the jar.
- **Non-root** — UID 1001, read-only root filesystem, all capabilities dropped.
- **Container-aware heap** — `-XX:MaxRAMPercentage=75` rather than a fixed `-Xmx`, so the JVM
  respects the cgroup limit.
- **`-XX:+ExitOnOutOfMemoryError`** — a JVM that has exhausted its heap should die and be
  restarted, not linger unable to serve.

## 8.3 Kubernetes

Manifests in `deploy/k8s/base/`. `service-template.yaml` is the reference shape every business
service follows; only the name, port and database differ.

### The choices that matter

| Setting | Value | Why |
|---|---|---|
| `replicas` | ≥2 (gateway 3) | A rolling update or node drain must never take a service to zero |
| `maxUnavailable` | 0 | Never drop below the declared count during a deploy |
| `startupProbe` | 30 × 5s | Flyway migrations on a cold database must not be mistaken for a crash loop |
| `livenessProbe` | `/health/liveness` only | Checks the process, never its dependencies — restarting pods because the database blinked turns a brief outage into a restart storm |
| `readinessProbe` | `/health/readiness` | Includes dependencies; removes the pod from the load balancer without killing it |
| CPU limit | **none** | Throttling a JVM under load causes latency spikes; the request already guarantees a share |
| Memory limit | set | Memory is incompressible — a leak must be bounded |
| `terminationGracePeriodSeconds` | 45 | In-flight repayments finish before the pod goes |
| `preStop` sleep 5 | | Lets the load balancer stop sending traffic before shutdown begins |
| `PodDisruptionBudget` | `minAvailable: 1` | A node drain cannot take the last replica |
| `topologySpreadConstraints` | across zones | A zone failure does not take the whole service |
| HPA scale-down | 300s stabilisation | Collection peaks are spiky; shedding capacity right after one leaves nothing for the next |

### Network policy

Default deny for both ingress and egress, then explicit allows: the ingress controller may
reach only the gateway; services may reach each other and the data plane on 3306/9092/6379.
Without this, a compromised notification service could read the payment service's database
directly — network policy is what makes "services own their data" an enforced boundary.

### Secrets

Nothing sensitive is committed. `configmap.yaml` carries a placeholder `Secret` clearly marked
as such; real environments provision from the cloud KMS or Vault through External Secrets
Operator or the secrets-store CSI driver, which supports rotation without a redeploy.

### Data plane

MySQL, Kafka and Redis are **not** deployed as part of this chart. Use a managed service
(RDS/Cloud SQL, MSK/Confluent, ElastiCache) or a purpose-built operator. Running a stateful
financial datastore from a hand-written `StatefulSet` means owning backup, restore, failover and
version upgrades yourself, which is a false economy.

## 8.4 Environments

| Environment | Purpose | Data | Deploys |
|---|---|---|---|
| local | Development | Synthetic | Compose |
| staging | Pre-production verification | Anonymised copy | Automatic from `main` |
| production | Live | Real | Manual approval on a protected GitHub environment |

Staging mirrors production's topology, at smaller replica counts. It uses **anonymised** data —
a copy of production PII in a lower-tier environment is a breach waiting for an audit to find.

## 8.5 Database migrations

Flyway runs as a Job before the new pods roll out, not on application startup in production
(eleven services racing the same schema is not a plan).

**Migrations must be backwards compatible with the release still serving traffic.** During a
rolling update both versions run simultaneously. Expand-then-contract, over two releases:

1. Release N: add the nullable column, write to both, read from the old.
2. Release N+1: backfill, read from the new, then drop the old.

A migration that renames or drops a column in one release breaks the running version the moment
it applies.

Production deploys take a database snapshot immediately before the migration, so a bad deploy is
recoverable to a known-good point rather than reconstructed.
