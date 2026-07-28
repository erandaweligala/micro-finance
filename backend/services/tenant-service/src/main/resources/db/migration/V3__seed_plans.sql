-- Baseline subscription plans. Priced in USD; a limit of 0 means unlimited.
INSERT INTO subscription_plan
    (id, version, code, name, description, monthly_price, currency,
     max_users, max_customers, max_active_loans, max_branches, active, created_at, created_by)
VALUES
    ('11111111-1111-4111-8111-111111111111', 0, 'STARTER', 'Starter',
     'For a single branch getting started', 49.0000, 'USD', 5, 500, 250, 1, 1, NOW(6), 'system'),
    ('22222222-2222-4222-8222-222222222222', 0, 'GROWTH', 'Growth',
     'For a growing institution with several branches', 199.0000, 'USD', 25, 5000, 2500, 5, 1,
     NOW(6), 'system'),
    ('33333333-3333-4333-8333-333333333333', 0, 'ENTERPRISE', 'Enterprise',
     'Unlimited scale with full branding and API access', 749.0000, 'USD', 0, 0, 0, 0, 1,
     NOW(6), 'system');

INSERT INTO subscription_plan_feature (plan_id, feature) VALUES
    ('11111111-1111-4111-8111-111111111111', 'EMAIL_NOTIFICATIONS'),
    ('22222222-2222-4222-8222-222222222222', 'EMAIL_NOTIFICATIONS'),
    ('22222222-2222-4222-8222-222222222222', 'SMS_NOTIFICATIONS'),
    ('22222222-2222-4222-8222-222222222222', 'CUSTOM_BRANDING'),
    ('33333333-3333-4333-8333-333333333333', 'EMAIL_NOTIFICATIONS'),
    ('33333333-3333-4333-8333-333333333333', 'SMS_NOTIFICATIONS'),
    ('33333333-3333-4333-8333-333333333333', 'CUSTOM_BRANDING'),
    ('33333333-3333-4333-8333-333333333333', 'API_ACCESS'),
    ('33333333-3333-4333-8333-333333333333', 'ADVANCED_REPORTING');
