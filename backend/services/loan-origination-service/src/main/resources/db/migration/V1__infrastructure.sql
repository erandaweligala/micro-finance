-- Infrastructure tables present in every service database.
-- The outbox makes domain events atomic with the state change that produced them;
-- idempotency records make money-moving requests safe to retry.

CREATE TABLE outbox_event (
    id             CHAR(36)     NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    tenant_id      CHAR(36)     NULL,
    aggregate_type VARCHAR(64)  NOT NULL,
    aggregate_id   CHAR(36)     NOT NULL,
    event_type     VARCHAR(96)  NOT NULL,
    topic          VARCHAR(128) NOT NULL,
    payload        JSON         NOT NULL,
    status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempts       INT          NOT NULL DEFAULT 0,
    published_at   DATETIME(6)  NULL,
    last_error     VARCHAR(512) NULL,
    created_at     DATETIME(6)  NOT NULL,
    created_by     VARCHAR(64)  NULL,
    updated_at     DATETIME(6)  NULL,
    updated_by     VARCHAR(64)  NULL,
    PRIMARY KEY (id),
    KEY ix_outbox_status_created (status, created_at),
    KEY ix_outbox_aggregate (aggregate_type, aggregate_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE idempotency_record (
    id               CHAR(36)     NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    tenant_id        CHAR(36)     NOT NULL,
    idempotency_key  VARCHAR(128) NOT NULL,
    operation        VARCHAR(96)  NOT NULL,
    request_hash     CHAR(64)     NOT NULL,
    response_payload JSON         NULL,
    response_status  INT          NOT NULL DEFAULT 0,
    expires_at       DATETIME(6)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    created_by       VARCHAR(64)  NULL,
    updated_at       DATETIME(6)  NULL,
    updated_by       VARCHAR(64)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ux_idempotency_tenant_key (tenant_id, idempotency_key),
    KEY ix_idempotency_expires (expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
