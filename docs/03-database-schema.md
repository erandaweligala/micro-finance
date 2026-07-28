# 3. Database Schema

One database per service. Full DDL lives in each service's
`src/main/resources/db/migration/V*.sql` and is applied by Flyway at startup.

## Conventions applied everywhere

| Convention | Reason |
|---|---|
| `CHAR(36)` UUID primary keys, assigned in the application | A saga needs an id before the insert to correlate work across services |
| `DECIMAL(19,4)` for money — never `FLOAT`/`DOUBLE` | Binary floating point cannot represent 0.01; error compounds over a loan's life |
| `version BIGINT` on every table | Optimistic locking; catches lost updates |
| `created_at/by`, `updated_at/by` | Who changed what, when — populated from the verified principal |
| `tenant_id CHAR(36) NOT NULL` on tenant-owned tables, `updatable = false` | Rows cannot migrate between institutions |
| Uniqueness scoped by `tenant_id` | One institution's data cannot collide with another's |
| InnoDB, `utf8mb4_0900_ai_ci` | Transactions, foreign keys, full Unicode including emoji in names |
| UTC timestamps (`DATETIME(6)`) | A financial timestamp must not move with a server's local zone |

Every service database also carries `outbox_event` and `idempotency_record` (V1 migration).

## identity-service — `mfin_identity`

```
app_user               id, tenant_id (NULL for platform operators), username, email, full_name,
                       password_hash, password_updated_at, must_change_password, status,
                       branch_id, failed_login_attempts, locked_until, last_login_at, customer_id
                       UNIQUE (tenant_id, username)
app_user_role          user_id, role                             PK (user_id, role)
refresh_token          user_id, token_hash (SHA-256), family_id, expires_at, used_at, revoked_at,
                       user_agent, ip_address                    UNIQUE (token_hash)
password_reset_token   user_id, token_hash, expires_at, used_at  UNIQUE (token_hash)
tenant_directory       tenant_id, slug, display_name, login_enabled   UNIQUE (slug)
```

`tenant_id` is nullable because platform operators belong to no institution — which is why
uniqueness is per `(tenant_id, username)` rather than global.

## tenant-service — `mfin_tenant`

```
tenant                    slug UNIQUE, subdomain UNIQUE, name, legal_name, registration_number,
                          contact_email, country_code, default_currency, timezone,
                          logo_url, primary_color, secondary_color, status, onboarded_on
subscription_plan         code UNIQUE, name, monthly_price, currency,
                          max_users, max_customers, max_active_loans, max_branches (0 = unlimited)
subscription_plan_feature plan_id, feature                       PK (plan_id, feature)
tenant_subscription       tenant_id, plan_id, starts_on, ends_on, trial_ends_on, status
branch                    tenant_id, code, name, address_line, city, phone, active
                          UNIQUE (tenant_id, code)
tenant_setting            tenant_id, setting_key, setting_value  UNIQUE (tenant_id, setting_key)
```

Settings are key/value so a new setting needs neither a migration nor a redeploy of every
service.

## customer-service — `mfin_customer`

```
customer      tenant_id, customer_number, first/middle/last_name, date_of_birth, gender,
              marital_status, id_type,
              national_id       VARCHAR(512)  -- AES-256-GCM ciphertext
              national_id_index CHAR(64)      -- HMAC blind index
              phone_number      VARCHAR(512)  /  phone_index CHAR(64)
              email             VARCHAR(512)  /  email_index CHAR(64)
              address_*, occupation, employer, monthly_income,
              branch_id, loan_officer_id, kyc_status, kyc_verified_at/by, status
              UNIQUE (tenant_id, customer_number)
              UNIQUE (tenant_id, national_id_index)   -- one identity document, one customer
kyc_document  tenant_id, customer_id, document_type, document_number (encrypted),
              issuing_authority, issued_on, expires_on, storage_key, checksum,
              verification_status, verified_by/at, rejection_reason
```

Documents live in object storage; only the key is stored, so scans of identity documents never
enter this database or its backups.

## loan-product-service — `mfin_product`

```
loan_product  tenant_id, code, name, currency, currency_scale,
              min/max/default_principal, min/max/default_annual_rate, rate_quotation,
              interest_method, repayment_frequency, min/max/default_installments,
              grace_type, max_grace_periods, fee_type/value/collection, day_count,
              penalty_annual_rate, penalty_basis, penalty_grace_days, days_to_default,
              approval_levels, requires_collateral, requires_guarantor, status
              UNIQUE (tenant_id, code)
              CHECK min_principal <= max_principal, min_rate <= max_rate,
                    min_installments <= max_installments
```

## loan-origination-service — `mfin_origination`

```
loan_application  tenant_id, application_number, customer_id, product_id, currency,
                  requested_amount, requested_installments,
                  approved_amount, approved_installments,       -- kept separately, on purpose
                  annual_interest_rate, interest_method, repayment_frequency,
                  grace_type, grace_periods, purpose,
                  status, current_approval_level, required_approval_levels,
                  branch_id, loan_officer_id, submitted_at, decided_at, rejection_reason,
                  disbursement_date/method/reference, disbursed_amount, net_disbursed_amount
                  UNIQUE (tenant_id, application_number)
approval_record   tenant_id, application_id, approval_level, decision, decided_by,
                  comment, approved_amount, approved_installments      -- append-only
```

## loan-account-service — `mfin_loanaccount`

```
loan_account        tenant_id, account_number, application_id, customer_id, product_id,
                    currency, currency_scale, principal, total_interest, total_fees,
                    total_repayable, installment_amount, annual_interest_rate,
                    interest_method, repayment_frequency, number_of_installments,
                    outstanding_principal / _interest / _fees / _penalty,
                    principal_paid / interest_paid / fees_paid / penalty_paid,
                    advance_balance, disbursement_date, first_repayment_date, maturity_date,
                    last_payment_date, days_past_due, overdue_amount, status, closed_on
                    UNIQUE (tenant_id, account_number)
                    UNIQUE (tenant_id, application_id)   -- makes event consumption idempotent
                    CHECK all outstanding_* >= 0
schedule_installment tenant_id, loan_account_id, installment_number, due_date, opening_balance,
                    principal_due / interest_due / fee_due / penalty_due,
                    principal_paid / interest_paid / fee_paid / penalty_paid,
                    closing_balance, grace_installment, status, settled_on
                    UNIQUE (loan_account_id, installment_number)
applied_repayment   tenant_id, loan_account_id, payment_id, amount,
                    principal/interest/fee/penalty_allocated, excess_amount, value_date,
                    reversed_at/by, reversal_reason
                    UNIQUE (tenant_id, payment_id)   -- idempotency + exact reversal replay
```

Running balances are maintained on `loan_account` rather than summed from installments: a
portfolio query that aggregated millions of installment rows would not survive a real loan book.

## payment-service — `mfin_payment`

```
payment  tenant_id, receipt_number, loan_account_id, loan_account_number, customer_id,
         amount, currency, method, external_reference, value_date, narrative,
         principal/interest/fee/penalty_allocated, excess_amount,
         outstanding_principal_after, total_outstanding_after,
         status (POSTED | REVERSED), received_by, branch_id,
         reversed_at/by, reversal_reason
         UNIQUE (tenant_id, receipt_number)
         CHECK amount > 0
```

The allocation snapshot means a receipt can be reprinted years later without another service
call. There is no `DELETE` path: corrections are reversals.

## ledger-service — `mfin_ledger`

```
ledger_entry  tenant_id, loan_account_id, customer_id, transaction_date, transaction_reference,
              transaction_type, narrative, debit_amount, credit_amount,
              principal/interest/fee/penalty_allocation,
              outstanding_principal, total_outstanding, currency,
              source_event_id, source_event_type, reverses_entry_id, posted_at
              UNIQUE (tenant_id, source_event_id)   -- at-least-once delivery is safe
              CHECK (debit_amount = 0 OR credit_amount = 0)   -- a line is one-sided
```

## notification-service — `mfin_notification`

```
notification_template  tenant_id, code, channel, subject, body  UNIQUE (tenant_id, code, channel)
notification_log       tenant_id, channel, template_code, recipient_masked, subject, body,
                       status, attempts, last_error, sent_at, source_event_id
                       UNIQUE (tenant_id, source_event_id)   -- never message a borrower twice
```

## reporting-audit-service — `mfin_reporting`

```
audit_log      tenant_id, sequence_number, action, entity_type, entity_id, actor_id, actor_name,
               details JSON, source_event_id, occurred_at,
               previous_hash CHAR(64), entry_hash CHAR(64)
               UNIQUE (tenant_id, sequence_number)
loan_snapshot  tenant_id, loan_account_id, account_number, customer_id, branch_id,
               principal, outstanding_principal, total_outstanding, total_collected,
               days_past_due, status, disbursement_date, maturity_date, last_payment_date
               UNIQUE (tenant_id, loan_account_id)
```

`entry_hash = SHA-256(previous_hash ‖ canonical fields)`. Altering or deleting any historical
row breaks every hash after it, which `/api/v1/audit/verify` detects. In production the service
account holds only `INSERT` and `SELECT` on `audit_log`.

## Indexing strategy

Composite indexes always lead with `tenant_id`, because every query is tenant-scoped and a
leading-column match is what makes the index usable:

```sql
KEY ix_customer_tenant_status  (tenant_id, status)
KEY ix_loan_account_arrears    (tenant_id, days_past_due)     -- the daily arrears sweep
KEY ix_installment_due         (tenant_id, due_date, status)  -- overdue installments
KEY ix_ledger_loan_date        (tenant_id, loan_account_id, transaction_date)  -- statements
```
