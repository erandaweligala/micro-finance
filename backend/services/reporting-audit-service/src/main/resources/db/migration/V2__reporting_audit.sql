-- Immutable audit trail and the reporting read model.
--
-- audit_log is append-only and hash-chained: entry_hash = SHA-256(previous_hash || fields).
-- Editing or deleting any historical row breaks every hash after it, which the verification
-- endpoint detects. In production, grant only INSERT and SELECT on this table to the service
-- account so the database enforces what the application promises.

CREATE TABLE audit_log (
    id              CHAR(36)     NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    tenant_id       CHAR(36)     NULL,
    sequence_number BIGINT       NOT NULL,
    action          VARCHAR(96)  NOT NULL,
    entity_type     VARCHAR(64)  NOT NULL,
    entity_id       CHAR(36)     NULL,
    actor_id        CHAR(36)     NULL,
    actor_name      VARCHAR(128) NULL,
    details         JSON         NULL,
    source_event_id CHAR(36)     NULL,
    ip_address      VARCHAR(45)  NULL,
    occurred_at     DATETIME(6)  NOT NULL,
    previous_hash   CHAR(64)     NULL,
    entry_hash      CHAR(64)     NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    created_by      VARCHAR(64)  NULL,
    updated_at      DATETIME(6)  NULL,
    updated_by      VARCHAR(64)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ux_audit_tenant_sequence (tenant_id, sequence_number),
    KEY ix_audit_tenant_time (tenant_id, occurred_at),
    KEY ix_audit_actor (tenant_id, actor_id),
    KEY ix_audit_entity (tenant_id, entity_type, entity_id),
    KEY ix_audit_action (tenant_id, action)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE loan_snapshot (
    id                    CHAR(36)       NOT NULL,
    version               BIGINT         NOT NULL DEFAULT 0,
    tenant_id             CHAR(36)       NOT NULL,
    loan_account_id       CHAR(36)       NOT NULL,
    account_number        VARCHAR(32)    NULL,
    customer_id           CHAR(36)       NULL,
    branch_id             CHAR(36)       NULL,
    principal             DECIMAL(19, 4) NOT NULL DEFAULT 0,
    outstanding_principal DECIMAL(19, 4) NOT NULL DEFAULT 0,
    total_outstanding     DECIMAL(19, 4) NOT NULL DEFAULT 0,
    total_collected       DECIMAL(19, 4) NOT NULL DEFAULT 0,
    days_past_due         INT            NOT NULL DEFAULT 0,
    status                VARCHAR(24)    NOT NULL DEFAULT 'ACTIVE',
    disbursement_date     DATE           NULL,
    maturity_date         DATE           NULL,
    last_payment_date     DATE           NULL,
    created_at            DATETIME(6)    NOT NULL,
    created_by            VARCHAR(64)    NULL,
    updated_at            DATETIME(6)    NULL,
    updated_by            VARCHAR(64)    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ux_snapshot_loan (tenant_id, loan_account_id),
    KEY ix_snapshot_status (tenant_id, status),
    KEY ix_snapshot_arrears (tenant_id, days_past_due),
    KEY ix_snapshot_branch (tenant_id, branch_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
