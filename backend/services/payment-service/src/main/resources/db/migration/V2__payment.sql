-- Payments received from borrowers.
--
-- Append-only in spirit: a payment is never deleted and its amount is never edited. A mistake
-- is corrected by setting status = 'REVERSED' and recording who authorised it and why, so the
-- customer's ledger shows both the original entry and the correction.

CREATE TABLE payment (
    id                          CHAR(36)       NOT NULL,
    version                     BIGINT         NOT NULL DEFAULT 0,
    tenant_id                   CHAR(36)       NOT NULL,
    receipt_number              VARCHAR(32)    NOT NULL,
    loan_account_id             CHAR(36)       NOT NULL,
    loan_account_number         VARCHAR(32)    NULL,
    customer_id                 CHAR(36)       NOT NULL,
    amount                      DECIMAL(19, 4) NOT NULL,
    currency                    CHAR(3)        NOT NULL,
    method                      VARCHAR(32)    NOT NULL,
    external_reference          VARCHAR(64)    NULL,
    value_date                  DATE           NOT NULL,
    narrative                   VARCHAR(512)   NULL,
    principal_allocated         DECIMAL(19, 4) NOT NULL DEFAULT 0,
    interest_allocated          DECIMAL(19, 4) NOT NULL DEFAULT 0,
    fee_allocated               DECIMAL(19, 4) NOT NULL DEFAULT 0,
    penalty_allocated           DECIMAL(19, 4) NOT NULL DEFAULT 0,
    excess_amount               DECIMAL(19, 4) NOT NULL DEFAULT 0,
    outstanding_principal_after DECIMAL(19, 4) NULL,
    total_outstanding_after     DECIMAL(19, 4) NULL,
    status                      VARCHAR(24)    NOT NULL DEFAULT 'POSTED',
    received_by                 CHAR(36)       NULL,
    branch_id                   CHAR(36)       NULL,
    reversed_at                 DATETIME(6)    NULL,
    reversed_by                 CHAR(36)       NULL,
    reversal_reason             VARCHAR(512)   NULL,
    created_at                  DATETIME(6)    NOT NULL,
    created_by                  VARCHAR(64)    NULL,
    updated_at                  DATETIME(6)    NULL,
    updated_by                  VARCHAR(64)    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ux_payment_tenant_receipt (tenant_id, receipt_number),
    KEY ix_payment_loan (tenant_id, loan_account_id),
    KEY ix_payment_customer (tenant_id, customer_id),
    KEY ix_payment_value_date (tenant_id, value_date),
    KEY ix_payment_status (tenant_id, status),
    CONSTRAINT ck_payment_amount_positive CHECK (amount > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
