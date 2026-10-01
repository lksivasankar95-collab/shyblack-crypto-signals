-- Portfolio authoritative exchange read snapshots (Phase 3, READ-ONLY integration).
-- Same out-of-band precedent as db/research/V1__research_market_data.sql and
-- db/migration/V1__portfolio_account_scope.sql: there is no Flyway/Liquibase in this project,
-- dev/test rely on Hibernate ddl-auto, and production runs ddl-auto=validate, so these tables
-- MUST exist before the application validates.
--
-- These tables are owned by the Portfolio READ MODEL (service/portfolio). They are deliberately
-- separate from live_trading_accounts and futures_positions, which are owned by the trading engine:
--   - live_trading_accounts.cached_* is narrowed to one quote asset by the 60s reconciler;
--   - futures_positions is the LOCAL shadow created from our own orders by FuturesEngineService.
-- Keeping the exchange's own view apart means a read can never overwrite execution state and two
-- writers never race on the same row.
--
-- Strictly additive. No existing table is altered, no row is backfilled, no column is dropped.
-- These tables only ever receive data written by a successful authenticated exchange read.

CREATE TABLE IF NOT EXISTS portfolio_exchange_balances (
    id              uuid            NOT NULL,
    user_id         uuid            NOT NULL,
    exchange        varchar(20)     NOT NULL,
    asset           varchar(20)     NOT NULL,
    free_balance    numeric(30,12),
    locked_balance  numeric(30,12),
    fetched_at      timestamptz     NOT NULL,
    version         bigint          NOT NULL,
    created_at      timestamptz     NOT NULL,
    updated_at      timestamptz     NOT NULL,
    CONSTRAINT pk_portfolio_exchange_balances PRIMARY KEY (id),
    CONSTRAINT fk_portfolio_exchange_balances_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_portfolio_exchange_balances_scope UNIQUE (user_id, exchange, asset)
);

COMMENT ON TABLE portfolio_exchange_balances IS
    'Per-asset exchange balances as last reported. An asset row is deleted when the exchange stops reporting it.';
COMMENT ON COLUMN portfolio_exchange_balances.free_balance IS
    'Free balance exactly as reported. NULL means the exchange sent an unusable value; it is never defaulted to 0.';

CREATE INDEX IF NOT EXISTS ix_portfolio_exchange_balances_user_exchange
    ON portfolio_exchange_balances (user_id, exchange);

CREATE TABLE IF NOT EXISTS portfolio_exchange_positions (
    id                 uuid           NOT NULL,
    user_id            uuid           NOT NULL,
    exchange           varchar(20)    NOT NULL,
    symbol             varchar(20)    NOT NULL,
    position_side      varchar(10)    NOT NULL,
    position_amount    numeric(30,12),
    entry_price        numeric(30,12),
    mark_price         numeric(30,12),
    liquidation_price  numeric(30,12),
    leverage           integer,
    margin_mode        varchar(20),
    isolated_margin    numeric(30,12),
    notional           numeric(30,12),
    unrealized_profit  numeric(30,12),
    fetched_at         timestamptz    NOT NULL,
    version            bigint         NOT NULL,
    created_at         timestamptz    NOT NULL,
    updated_at         timestamptz    NOT NULL,
    CONSTRAINT pk_portfolio_exchange_positions PRIMARY KEY (id),
    CONSTRAINT fk_portfolio_exchange_positions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_portfolio_exchange_positions_scope UNIQUE (user_id, exchange, symbol, position_side),
    CONSTRAINT ck_portfolio_exchange_positions_side CHECK (position_side IN ('LONG', 'SHORT'))
);

COMMENT ON TABLE portfolio_exchange_positions IS
    'Open futures positions as last reported by the exchange position-risk endpoint. A position the exchange reports as flat is deleted, because flat is a real closed state, not a zero-amount open position.';
COMMENT ON COLUMN portfolio_exchange_positions.liquidation_price IS
    'NULL when the exchange reports 0, which it uses to mean "not applicable", never to mean liquidated at zero.';
COMMENT ON COLUMN portfolio_exchange_positions.position_amount IS
    'Signed amount: positive is LONG, negative is SHORT. The sign carries the direction.';

CREATE INDEX IF NOT EXISTS ix_portfolio_exchange_positions_user_exchange
    ON portfolio_exchange_positions (user_id, exchange);