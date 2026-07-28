-- Identity and access schema.
-- Note that app_user.tenant_id is nullable: platform operators belong to no institution.
-- Uniqueness is therefore per (tenant_id, username) rather than global.

CREATE TABLE app_user (
    id                    CHAR(36)     NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    tenant_id             CHAR(36)     NULL,
    username              VARCHAR(128) NOT NULL,
    email                 VARCHAR(255) NOT NULL,
    full_name             VARCHAR(160) NOT NULL,
    phone_number          VARCHAR(32)  NULL,
    password_hash         VARCHAR(100) NOT NULL,
    password_updated_at   DATETIME(6)  NOT NULL,
    must_change_password  TINYINT(1)   NOT NULL DEFAULT 0,
    status                VARCHAR(24)  NOT NULL DEFAULT 'ACTIVE',
    branch_id             CHAR(36)     NULL,
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    locked_until          DATETIME(6)  NULL,
    last_login_at         DATETIME(6)  NULL,
    customer_id           CHAR(36)     NULL,
    created_at            DATETIME(6)  NOT NULL,
    created_by            VARCHAR(64)  NULL,
    updated_at            DATETIME(6)  NULL,
    updated_by            VARCHAR(64)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ux_user_tenant_username (tenant_id, username),
    KEY ix_user_email (email),
    KEY ix_user_tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE app_user_role (
    user_id CHAR(36)    NOT NULL,
    role    VARCHAR(32) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Refresh tokens are stored as SHA-256 digests, never in the clear.
CREATE TABLE refresh_token (
    id         CHAR(36)     NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0,
    user_id    CHAR(36)     NOT NULL,
    tenant_id  CHAR(36)     NULL,
    token_hash CHAR(64)     NOT NULL,
    family_id  CHAR(36)     NOT NULL,
    expires_at DATETIME(6)  NOT NULL,
    used_at    DATETIME(6)  NULL,
    revoked_at DATETIME(6)  NULL,
    user_agent VARCHAR(255) NULL,
    ip_address VARCHAR(45)  NULL,
    created_at DATETIME(6)  NOT NULL,
    created_by VARCHAR(64)  NULL,
    updated_at DATETIME(6)  NULL,
    updated_by VARCHAR(64)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ix_refresh_token_hash (token_hash),
    KEY ix_refresh_token_user (user_id),
    KEY ix_refresh_token_family (family_id),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE password_reset_token (
    id         CHAR(36)    NOT NULL,
    version    BIGINT      NOT NULL DEFAULT 0,
    user_id    CHAR(36)    NOT NULL,
    token_hash CHAR(64)    NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    created_by VARCHAR(64) NULL,
    updated_at DATETIME(6) NULL,
    updated_by VARCHAR(64) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ix_reset_token_hash (token_hash),
    KEY ix_reset_token_user (user_id),
    CONSTRAINT fk_reset_token_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Local read model of the tenant registry, kept current from mfin.tenant events.
-- Sign-in resolves a slug here rather than calling the tenant service synchronously.
CREATE TABLE tenant_directory (
    id            CHAR(36)     NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    tenant_id     CHAR(36)     NOT NULL,
    slug          VARCHAR(64)  NOT NULL,
    display_name  VARCHAR(160) NOT NULL,
    login_enabled TINYINT(1)   NOT NULL DEFAULT 1,
    created_at    DATETIME(6)  NOT NULL,
    created_by    VARCHAR(64)  NULL,
    updated_at    DATETIME(6)  NULL,
    updated_by    VARCHAR(64)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ux_tenant_directory_slug (slug),
    KEY ix_tenant_directory_tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
