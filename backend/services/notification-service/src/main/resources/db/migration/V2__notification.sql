-- Outbound message history and per-institution templates.
-- Recipient addresses are stored masked: a notification history is a rich PII target.

CREATE TABLE notification_template (
    id          CHAR(36)     NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    tenant_id   CHAR(36)     NOT NULL,
    code        VARCHAR(64)  NOT NULL,
    channel     VARCHAR(16)  NOT NULL,
    subject     VARCHAR(255) NULL,
    body        TEXT         NOT NULL,
    active      TINYINT(1)   NOT NULL DEFAULT 1,
    created_at  DATETIME(6)  NOT NULL,
    created_by  VARCHAR(64)  NULL,
    updated_at  DATETIME(6)  NULL,
    updated_by  VARCHAR(64)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ux_template_tenant_code_channel (tenant_id, code, channel)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE notification_log (
    id                CHAR(36)     NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0,
    tenant_id         CHAR(36)     NOT NULL,
    channel           VARCHAR(16)  NOT NULL,
    template_code     VARCHAR(64)  NOT NULL,
    recipient_masked  VARCHAR(128) NOT NULL,
    subject           VARCHAR(255) NULL,
    body              TEXT         NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempts          INT          NOT NULL DEFAULT 0,
    last_error        VARCHAR(512) NULL,
    sent_at           DATETIME(6)  NULL,
    source_event_id   CHAR(36)     NULL,
    related_entity_id CHAR(36)     NULL,
    created_at        DATETIME(6)  NOT NULL,
    created_by        VARCHAR(64)  NULL,
    updated_at        DATETIME(6)  NULL,
    updated_by        VARCHAR(64)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ux_notification_source_event (tenant_id, source_event_id),
    KEY ix_notification_status (tenant_id, status),
    KEY ix_notification_recipient (tenant_id, related_entity_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
