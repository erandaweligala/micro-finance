-- The customer loan ledger.
--
-- Append-only by design: there is no UPDATE or DELETE path in the application, and a
-- correction is posted as a new contra entry pointing at the line it reverses. The unique
-- index on (tenant_id, source_event_id) is what makes at-least-once event delivery safe -
-- a redelivered event cannot post a second line.

CREATE TABLE ledger_entry (
    id                    CHAR(36)       NOT NULL,
    version               BIGINT         NOT NULL DEFAULT 0,
    tenant_id             CHAR(36)       NOT NULL,
    loan_account_id       CHAR(36)       NOT NULL,
    loan_account_number   VARCHAR(32)    NULL,
    customer_id           CHAR(36)       NULL,
    transaction_date      DATE           NOT NULL,
    transaction_reference VARCHAR(64)    NOT NULL,
    transaction_type      VARCHAR(32)    NOT NULL,
    narrative             VARCHAR(512)   NULL,
    debit_amount          DECIMAL(19, 4) NOT NULL DEFAULT 0,
    credit_amount         DECIMAL(19, 4) NOT NULL DEFAULT 0,
    principal_allocation  DECIMAL(19, 4) NOT NULL DEFAULT 0,
    interest_allocation   DECIMAL(19, 4) NOT NULL DEFAULT 0,
    fee_allocation        DECIMAL(19, 4) NOT NULL DEFAULT 0,
    penalty_allocation    DECIMAL(19, 4) NOT NULL DEFAULT 0,
    outstanding_principal DECIMAL(19, 4) NOT NULL DEFAULT 0,
    total_outstanding     DECIMAL(19, 4) NOT NULL DEFAULT 0,
    currency              CHAR(3)        NOT NULL,
    source_event_id       CHAR(36)       NOT NULL,
    source_event_type     VARCHAR(96)    NOT NULL,
    reverses_entry_id     CHAR(36)       NULL,
    posted_at             DATETIME(6)    NOT NULL,
    created_at            DATETIME(6)    NOT NULL,
    created_by            VARCHAR(64)    NULL,
    updated_at            DATETIME(6)    NULL,
    updated_by            VARCHAR(64)    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ux_ledger_entry_source_event (tenant_id, source_event_id),
    KEY ix_ledger_loan_date (tenant_id, loan_account_id, transaction_date),
    KEY ix_ledger_customer (tenant_id, customer_id),
    KEY ix_ledger_reference (tenant_id, transaction_reference),
    -- A line is either a debit or a credit, never both.
    CONSTRAINT ck_ledger_single_sided CHECK (debit_amount = 0 OR credit_amount = 0),
    CONSTRAINT ck_ledger_non_negative CHECK (debit_amount >= 0 AND credit_amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
