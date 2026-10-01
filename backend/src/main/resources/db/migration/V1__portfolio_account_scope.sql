-- Portfolio account-scope schema (unified PAPER / LIVE read model).
-- Mirrors the out-of-band precedent of db/research/V1__research_market_data.sql: there is no
-- Flyway/Liquibase in this project, dev/test rely on Hibernate ddl-auto, and production runs
-- ddl-auto=validate, so these changes MUST be applied before the application validates.
--
-- Strictly additive. No column is dropped, renamed or retyped, no balance is backfilled and no
-- existing row is mutated: portfolios.account_category and portfolios.exchange stay NULL on legacy
-- rows and are interpreted by the read model (NULL category -> presented as MAIN).
--
-- Enum values are persisted as VARCHAR by @Enumerated(EnumType.STRING); the length is generous
-- enough that adding an enum constant later never truncates an existing value.

ALTER TABLE portfolios
    ADD COLUMN IF NOT EXISTS account_category varchar(20),
    ADD COLUMN IF NOT EXISTS exchange        varchar(20);

COMMENT ON COLUMN portfolios.account_category IS
    'Read-model category (MAIN|SPOT|FUTURES|OPTIONS). NULL on legacy rows = not categorised; presented as MAIN. Never backfilled.';
COMMENT ON COLUMN portfolios.exchange IS
    'Backing exchange for a real account. NULL for simulated accounts, which are reported as PAPER. Never defaulted.';

-- Synchronization / status record for one (user, account_mode, account_category) scope.
-- Holds NO credential reference: exchange_credentials remains the single Binance credential seam,
-- unique per (user, exchange) and resolved on demand. This table only persists status.
CREATE TABLE IF NOT EXISTS portfolio_account_connections (
    id                  uuid         NOT NULL,
    user_id             uuid         NOT NULL,
    account_mode        varchar(20)  NOT NULL,
    account_category    varchar(20)  NOT NULL,
    exchange            varchar(20),
    connection_status   varchar(20)  NOT NULL,
    availability        varchar(20)  NOT NULL,
    last_synced_at      timestamptz,
    last_sync_message   varchar(200),
    version             bigint       NOT NULL,
    created_at          timestamptz  NOT NULL,
    updated_at          timestamptz  NOT NULL,
    CONSTRAINT pk_portfolio_account_connections PRIMARY KEY (id),
    CONSTRAINT fk_portfolio_account_connections_user FOREIGN KEY (user_id) REFERENCES users (id),
    -- The key deliberately excludes `exchange`: it is nullable (a PAPER scope has no exchange) and
    -- PostgreSQL treats NULLs as distinct inside a unique constraint, which would allow duplicates.
    CONSTRAINT uk_portfolio_account_connections_scope UNIQUE (user_id, account_mode, account_category),
    CONSTRAINT ck_portfolio_account_connections_mode CHECK (account_mode IN ('PAPER', 'LIVE')),
    CONSTRAINT ck_portfolio_account_connections_category CHECK (account_category IN ('MAIN', 'SPOT', 'FUTURES', 'OPTIONS'))
);

CREATE INDEX IF NOT EXISTS ix_portfolio_account_connections_user_mode
    ON portfolio_account_connections (user_id, account_mode);