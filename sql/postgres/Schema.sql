
-- PostgreSQL schema
-- Creation of tables, constraints, and relationships


-- CUSTOMER TABLE
CREATE TABLE IF NOT EXISTS customers (
    customer_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    email VARCHAR(254) NOT NULL UNIQUE,
    phone VARCHAR(30),
    username VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    failed_login_count INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT check_customers_status
        CHECK (status IN ('ACTIVE', 'LOCKED', 'CLOSED')),

    CONSTRAINT check_customers_failed_login_count
        CHECK (failed_login_count >= 0)
);


-- ACCOUNTS TABLE
CREATE TABLE IF NOT EXISTS accounts (
    account_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    customer_id BIGINT NOT NULL,
    account_number VARCHAR(34) NOT NULL UNIQUE,
    account_type VARCHAR(20) NOT NULL,

    balance DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT foreign_key_accounts_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id),

    CONSTRAINT check_accounts_account_type
        CHECK (account_type IN (
            'SAVINGS',
            'CURRENT',
            'DEPOSIT',
            'LOAN'
        )),

    CONSTRAINT check_accounts_status
        CHECK (status IN (
            'ACTIVE',
            'FROZEN',
            'MATURED',
            'CLOSED'
        )),

    CONSTRAINT check_accounts_balance
        CHECK (balance >= 0)
);

-- Helps search for a customer's accounts faster
CREATE INDEX IF NOT EXISTS index_accounts_customer
    ON accounts(customer_id);


-- DEPOSIT DETAILS TABLE
-- Additional information for fixed-term deposit accounts
-- Child table of accounts
CREATE TABLE IF NOT EXISTS deposit_details (
    account_id BIGINT PRIMARY KEY,

    principal DECIMAL(19,2) NOT NULL,
    term_months INT NOT NULL,
    interest_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    maturity_date DATE NOT NULL,

    CONSTRAINT foreign_key_deposit_details_account
        FOREIGN KEY (account_id)
        REFERENCES accounts(account_id),

    -- Prevents nonpositive deposit terms and principal amounts
    CONSTRAINT check_deposit_details_term_and_principal
        CHECK (term_months > 0 AND principal > 0)
);


-- LOAN DETAILS TABLE
-- Additional information for loan accounts
-- Child table of accounts
CREATE TABLE IF NOT EXISTS loan_details (
    account_id BIGINT PRIMARY KEY,

    principal_amount DECIMAL(19,2) NOT NULL,
    annual_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    tenure_months INT NOT NULL,
    start_date DATE NOT NULL,

    CONSTRAINT foreign_key_loan_details_account
        FOREIGN KEY (account_id)
        REFERENCES accounts(account_id),

    CONSTRAINT check_loan_details_values
        CHECK (principal_amount > 0 AND tenure_months > 0)
);


-- TRANSACTIONS TABLE
-- Financial transaction ledger
-- Successful financial operations are recorded here
CREATE TABLE IF NOT EXISTS transactions (
    transaction_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    reference_number VARCHAR(40) NOT NULL UNIQUE,

    source_account_id BIGINT,
    destination_account_id BIGINT,

    transaction_type VARCHAR(20) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    status VARCHAR(20) NOT NULL,

    description VARCHAR(255),

    source_balance_after DECIMAL(19,2),
    destination_balance_after DECIMAL(19,2),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT foreign_key_transactions_source_account
        FOREIGN KEY (source_account_id)
        REFERENCES accounts(account_id),

    CONSTRAINT foreign_key_transactions_destination_account
        FOREIGN KEY (destination_account_id)
        REFERENCES accounts(account_id),

    CONSTRAINT check_transactions_amount
        CHECK (amount > 0),

    CONSTRAINT check_transactions_type
        CHECK (transaction_type IN (
            'DEPOSIT',
            'WITHDRAWAL',
            'TRANSFER',
            'LOAN_REPAYMENT'
        )),

    CONSTRAINT check_transactions_status
        CHECK (status IN (
            'PENDING',
            'SUCCESS',
            'FAILED',
            'REVERSED'
        )),

    CONSTRAINT check_transactions_account_relationships
        CHECK (
            (
                transaction_type = 'DEPOSIT'
                AND source_account_id IS NULL
                AND destination_account_id IS NOT NULL
            )
            OR
            (
                transaction_type = 'WITHDRAWAL'
                AND source_account_id IS NOT NULL
                AND destination_account_id IS NULL
            )
            OR
            (
                transaction_type IN (
                    'TRANSFER',
                    'LOAN_REPAYMENT'
                )
                AND source_account_id IS NOT NULL
                AND destination_account_id IS NOT NULL
                AND source_account_id <> destination_account_id
            )
        )
);

-- Indexes for account transaction history
CREATE INDEX IF NOT EXISTS index_transactions_source_account_created_at
    ON transactions(source_account_id, created_at, transaction_id);

CREATE INDEX IF NOT EXISTS index_transactions_destination_account_created_at
    ON transactions(destination_account_id, created_at, transaction_id);


-- TRANSACTION AUDIT TABLE
-- Records successful, rejected, failed, and rolled-back financial attempts
CREATE TABLE IF NOT EXISTS transaction_audit (
    audit_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    customer_id BIGINT NOT NULL,
    reference_number VARCHAR(40),

    event_type VARCHAR(30) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    failure_code VARCHAR(40),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT foreign_key_transaction_audit_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id),

    CONSTRAINT check_transaction_audit_outcome
        CHECK (outcome IN (
            'SUCCESS',
            'REJECTED',
            'FAILED',
            'ROLLED_BACK'
        ))
);

CREATE INDEX IF NOT EXISTS index_transaction_audit_customer_created_at
    ON transaction_audit(customer_id, created_at);


-- LOGIN AUDIT TABLE
-- Records login attempts and session events
CREATE TABLE IF NOT EXISTS login_audit (
    audit_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    customer_id BIGINT,
    username_attempted VARCHAR(80) NOT NULL,
    outcome VARCHAR(20) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT foreign_key_login_audit_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id),

    CONSTRAINT check_login_audit_outcome
        CHECK (outcome IN (
            'SUCCESS',
            'FAILURE',
            'LOCKED',
            'TIMEOUT',
            'LOGOUT'
        ))
);



-- ACCOUNT DAILY BALANCE TABLE
-- Stores one end-of-day balance snapshot per account per date
-- Used for optional monthly average balance and interest calculations

CREATE TABLE IF NOT EXISTS account_daily_balance (
    daily_balance_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    account_id BIGINT NOT NULL,
    balance_date DATE NOT NULL,
    closing_balance DECIMAL(19,2) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT foreign_key_account_daily_balance_account
        FOREIGN KEY (account_id)
        REFERENCES accounts(account_id),

    --an account cannot have two daily balance snapshots for the same date.
    CONSTRAINT unique_account_daily_balance_account_date
        UNIQUE (account_id, balance_date),

    CONSTRAINT check_account_daily_balance_closing_balance
        CHECK (closing_balance >= 0)
);

CREATE INDEX IF NOT EXISTS index_account_daily_balance_date
    ON account_daily_balance(balance_date);


ALTER TABLE accounts DROP CONSTRAINT check_accounts_account_type;

ALTER TABLE accounts ADD CONSTRAINT accounts_account_type_check
CHECK (account_type IN ('SAVINGS', 'CHECKING', 'DEPOSIT', 'LOAN'));

CREATE SEQUENCE account_number_seq
START WITH 100000
INCREMENT BY 1
MINVALUE 100000
MAXVALUE 999999
NO CYCLE;

ALTER TABLE accounts
ALTER COLUMN account_number
SET DEFAULT nextval('account_number_seq')::text;

ALTER TABLE accounts
DROP CONSTRAINT accounts_account_type_check;

ALTER TABLE accounts
ADD CONSTRAINT accounts_account_type_check
CHECK (account_type IN ('SAVINGS', 'CHECKING', 'INVESTMENT', 'LOAN'));

ALTER TABLE deposit_details RENAME TO investment_details;

ALTER TABLE investment_details
RENAME COLUMN maturity_date TO withdrawal_unlocked_date;

ALTER TABLE loan_details
RENAME COLUMN annual_rate TO annual_interest_rate;

ALTER TABLE loan_details
RENAME COLUMN tenure_months TO repayment_period_in_months;

ALTER TABLE loan_details
RENAME COLUMN start_date TO loan_start_date;

ALTER TABLE transactions
DROP COLUMN reference_number;

ALTER TABLE transaction_audit
DROP COLUMN reference_number;

ALTER TABLE transaction_audit
ADD COLUMN transaction_id BIGINT;

ALTER TABLE transaction_audit
ADD CONSTRAINT foreign_key_transaction_audit_transaction
FOREIGN KEY (transaction_id)
REFERENCES transactions(transaction_id);


CREATE TABLE investment_contributions (
    contribution_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    account_id BIGINT NOT NULL
        REFERENCES accounts(account_id),

    amount NUMERIC(19,2) NOT NULL
        CHECK (amount > 0),

    contributed_at TIMESTAMPTZ NOT NULL
        DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_investment_contributions_account_date
    ON investment_contributions(account_id, contributed_at);

ALTER TABLE transactions
DROP CONSTRAINT check_transactions_type;

ALTER TABLE transactions
ADD CONSTRAINT check_transactions_type
CHECK (
    transaction_type IN (
        'DEPOSIT',
        'WITHDRAWAL',
        'TRANSFER',
        'LOAN_REPAYMENT',
        'INVESTMENT_CONTRIBUTION'
    )
);

ALTER TABLE transactions
DROP CONSTRAINT check_transactions_account_relationships;

ALTER TABLE transactions
ADD CONSTRAINT check_transactions_account_relationships
CHECK (
    (
        transaction_type = 'DEPOSIT'
        AND source_account_id IS NULL
        AND destination_account_id IS NOT NULL
    )
    OR
    (
        transaction_type = 'WITHDRAWAL'
        AND source_account_id IS NOT NULL
        AND destination_account_id IS NULL
    )
    OR
    (
        transaction_type IN (
            'TRANSFER',
            'LOAN_REPAYMENT',
            'INVESTMENT_CONTRIBUTION'
        )
        AND source_account_id IS NOT NULL
        AND destination_account_id IS NOT NULL
        AND source_account_id <> destination_account_id
    )
);

ALTER TABLE transactions
ALTER COLUMN transaction_type TYPE VARCHAR(30);

ALTER TABLE transactions
DROP CONSTRAINT check_transactions_type;

ALTER TABLE transactions
ADD CONSTRAINT check_transactions_type
CHECK (
    transaction_type IN (
        'DEPOSIT',
        'WITHDRAWAL',
        'TRANSFER',
        'LOAN_REPAYMENT',
        'INVESTMENT_CONTRIBUTION',
        'INVESTMENT_MATURITY_PAYOUT'
    )
);

ALTER TABLE transactions
DROP CONSTRAINT check_transactions_account_relationships;

ALTER TABLE transactions
ADD CONSTRAINT check_transactions_account_relationships
CHECK (
    (
        transaction_type = 'DEPOSIT'
        AND source_account_id IS NULL
        AND destination_account_id IS NOT NULL
    )
    OR
    (
        transaction_type = 'WITHDRAWAL'
        AND source_account_id IS NOT NULL
        AND destination_account_id IS NULL
    )
    OR
    (
        transaction_type IN (
            'TRANSFER',
            'LOAN_REPAYMENT',
            'INVESTMENT_CONTRIBUTION',
            'INVESTMENT_MATURITY_PAYOUT'
        )
        AND source_account_id IS NOT NULL
        AND destination_account_id IS NOT NULL
        AND source_account_id <> destination_account_id
    )
);

CREATE TABLE IF NOT EXISTS zelle_payment_requests (
    request_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    requester_customer_id BIGINT NOT NULL
        REFERENCES customers(customer_id),
    payer_customer_id BIGINT NOT NULL
        REFERENCES customers(customer_id),
    amount NUMERIC(19,2) NOT NULL,
    description VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    funding_account_id BIGINT
        REFERENCES accounts(account_id),
    payment_transaction_id BIGINT UNIQUE
        REFERENCES transactions(transaction_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMPTZ,
    CONSTRAINT zelle_request_distinct_customers
        CHECK (requester_customer_id <> payer_customer_id),
    CONSTRAINT zelle_request_positive_amount
        CHECK (amount > 0),
    CONSTRAINT zelle_request_valid_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT zelle_request_resolution_consistency
        CHECK (
            (status = 'PENDING' AND resolved_at IS NULL
                AND funding_account_id IS NULL AND payment_transaction_id IS NULL)
            OR (status = 'APPROVED' AND resolved_at IS NOT NULL
                AND funding_account_id IS NOT NULL AND payment_transaction_id IS NOT NULL)
            OR (status IN ('REJECTED', 'CANCELLED') AND resolved_at IS NOT NULL
                AND funding_account_id IS NULL AND payment_transaction_id IS NULL)
        )
);

CREATE INDEX IF NOT EXISTS idx_zelle_requests_payer_status
    ON zelle_payment_requests (payer_customer_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_zelle_requests_requester_status
    ON zelle_payment_requests (requester_customer_id, status, created_at DESC);

ALTER TABLE transactions
DROP CONSTRAINT check_transactions_type;

ALTER TABLE transactions
ADD CONSTRAINT check_transactions_type
CHECK (
    transaction_type IN (
        'DEPOSIT',
        'WITHDRAWAL',
        'TRANSFER',
        'LOAN_REPAYMENT',
        'INVESTMENT_CONTRIBUTION',
        'INVESTMENT_MATURITY_PAYOUT',
        'ZELLE_TRANSFER'
    )
);

ALTER TABLE transactions
DROP CONSTRAINT check_transactions_account_relationships;

ALTER TABLE transactions
ADD CONSTRAINT check_transactions_account_relationships
CHECK (
    (
        transaction_type = 'DEPOSIT'
        AND source_account_id IS NULL
        AND destination_account_id IS NOT NULL
    )
    OR
    (
        transaction_type = 'WITHDRAWAL'
        AND source_account_id IS NOT NULL
        AND destination_account_id IS NULL
    )
    OR
    (
        transaction_type IN (
            'TRANSFER',
            'LOAN_REPAYMENT',
            'INVESTMENT_CONTRIBUTION',
            'INVESTMENT_MATURITY_PAYOUT',
            'ZELLE_TRANSFER'
        )
        AND source_account_id IS NOT NULL
        AND destination_account_id IS NOT NULL
        AND source_account_id <> destination_account_id
    )
);