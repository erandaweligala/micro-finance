-- Tenant registry, subscription plans, branches and per-institution settings.
--
-- `tenant`, `subscription_plan` and `tenant_subscription` are intentionally NOT tenant-scoped:
-- they define the tenants themselves. Access is restricted to platform administrators, with a
-- tenant administrator able to read only their own institution.

CREATE TABLE tenant (
    id                  CHAR(36)     NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    slug                VARCHAR(64)  NOT NULL,
    name                VARCHAR(160) NOT NULL,
    legal_name          VARCHAR(200) NULL,
    registration_number VARCHAR(64)  NULL,
    subdomain           VARCHAR(96)  NULL,
    contact_email       VARCHAR(255) NOT NULL,
    contact_phone       VARCHAR(32)  NULL,
    country_code        CHAR(2)      NULL,
    default_currency    CHAR(3)      NOT NULL DEFAULT 'KES',
    timezone            VARCHAR(64)  NOT NULL DEFAULT 'UTC',
    logo_url            VARCHAR(512) NULL,
    primary_color       VARCHAR(9)   NULL,
    secondary_color     VARCHAR(9)   NULL,
    status              VARCHAR(24)  NOT NULL DEFAULT 'PENDING_ACTIVATION',
    onboarded_on        DATE         NULL,
    suspended_at        DATETIME(6)  NULL,
    suspension_reason   VARCHAR(512) NULL,
    created_at          DATETIME(6)  NOT NULL,
    created_by          VARCHAR(64)  NULL,
    updated_at          DATETIME(6)  NULL,
    updated_by          VARCHAR(64)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ux_tenant_slug (slug),
    UNIQUE KEY ux_tenant_subdomain (subdomain)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE subscription_plan (
    id               CHAR(36)       NOT NULL,
    version          BIGINT         NOT NULL DEFAULT 0,
    code             VARCHAR(32)    NOT NULL,
    name             VARCHAR(96)    NOT NULL,
    description      VARCHAR(512)   NULL,
    monthly_price    DECIMAL(19, 4) NOT NULL DEFAULT 0,
    currency         CHAR(3)        NOT NULL DEFAULT 'USD',
    -- 0 means unlimited
    max_users        INT            NOT NULL DEFAULT 0,
    max_customers    INT            NOT NULL DEFAULT 0,
    max_active_loans INT            NOT NULL DEFAULT 0,
    max_branches     INT            NOT NULL DEFAULT 0,
    active           TINYINT(1)     NOT NULL DEFAULT 1,
    created_at       DATETIME(6)    NOT NULL,
    created_by       VARCHAR(64)    NULL,
    updated_at       DATETIME(6)    NULL,
    updated_by       VARCHAR(64)    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ux_plan_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE subscription_plan_feature (
    plan_id CHAR(36)    NOT NULL,
    feature VARCHAR(48) NOT NULL,
    PRIMARY KEY (plan_id, feature),
    CONSTRAINT fk_plan_feature_plan FOREIGN KEY (plan_id) REFERENCES subscription_plan (id)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE tenant_subscription (
    id            CHAR(36)    NOT NULL,
    version       BIGINT      NOT NULL DEFAULT 0,
    tenant_id     CHAR(36)    NOT NULL,
    plan_id       CHAR(36)    NOT NULL,
    starts_on     DATE        NOT NULL,
    ends_on       DATE        NULL,
    trial_ends_on DATE        NULL,
    status        VARCHAR(24) NOT NULL DEFAULT 'TRIAL',
    created_at    DATETIME(6) NOT NULL,
    created_by    VARCHAR(64) NULL,
    updated_at    DATETIME(6) NULL,
    updated_by    VARCHAR(64) NULL,
    PRIMARY KEY (id),
    KEY ix_subscription_tenant (tenant_id),
    CONSTRAINT fk_subscription_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_subscription_plan FOREIGN KEY (plan_id) REFERENCES subscription_plan (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE branch (
    id           CHAR(36)     NOT NULL,
    version      BIGINT       NOT NULL DEFAULT 0,
    tenant_id    CHAR(36)     NOT NULL,
    code         VARCHAR(24)  NOT NULL,
    name         VARCHAR(128) NOT NULL,
    address_line VARCHAR(200) NULL,
    city         VARCHAR(96)  NULL,
    phone        VARCHAR(32)  NULL,
    active       TINYINT(1)   NOT NULL DEFAULT 1,
    created_at   DATETIME(6)  NOT NULL,
    created_by   VARCHAR(64)  NULL,
    updated_at   DATETIME(6)  NULL,
    updated_by   VARCHAR(64)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ux_branch_tenant_code (tenant_id, code),
    KEY ix_branch_tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE tenant_setting (
    id            CHAR(36)      NOT NULL,
    version       BIGINT        NOT NULL DEFAULT 0,
    tenant_id     CHAR(36)      NOT NULL,
    setting_key   VARCHAR(96)   NOT NULL,
    setting_value VARCHAR(1024) NOT NULL,
    description   VARCHAR(256)  NULL,
    created_at    DATETIME(6)   NOT NULL,
    created_by    VARCHAR(64)   NULL,
    updated_at    DATETIME(6)   NULL,
    updated_by    VARCHAR(64)   NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ux_setting_tenant_key (tenant_id, setting_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
