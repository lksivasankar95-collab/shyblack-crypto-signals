-- Phase 4: Binance user-data stream synchronisation support.
-- Same out-of-band precedent as the two earlier portfolio migrations. NOT executed by this change;
-- production runs ddl-auto=validate, so these must be applied before the app starts.
--
-- Two additive changes:
--
-- 1. portfolio_account_connections.last_event_at
--    Phase 3 stored only last_synced_at, which records the last REST snapshot. Phase 4 needs to
--    distinguish the last REST snapshot from the last applied WebSocket event, and needs a
--    per-scope high-water mark to reject out-of-order events and to detect gaps. One nullable
--    timestamp is the smallest additive change that expresses both without disturbing last_synced_at.
--
-- 2. portfolio_exchange_events
--    A restart-safe duplicate-suppression ledger keyed by a NATURAL event identity composed from
--    the exchange's own fields (event type, event time, transaction/update id, order id, status).
--    An in-memory set would not survive a restart and would grow without bound; a synthetic UUID
--    would not identify a duplicate at all, since a replayed event produces a *different* UUID.
--    Retention is operational: rows are append-only and prunable by applied_at.

ALTER TABLE portfolio_account_connections
    ADD COLUMN IF NOT EXISTS last_event_at timestamptz;

COMMENT ON COLUMN portfolio_account_connections.last_event_at IS
    'Event time of the last applied user-data WebSocket event for this scope, used as the ordering '
    'high-water mark. NULL when no event has been applied yet. Distinct from last_synced_at, which '
    'records the last REST snapshot.';

CREATE TABLE IF NOT EXISTS portfolio_exchange_events (
    id                uuid         NOT NULL,
    user_id           uuid         NOT NULL,
    account_category  varchar(20)  NOT NULL,
    exchange          varchar(20)  NOT NULL,
    event_type        varchar(40)  NOT NULL,
    event_time        timestamptz,
    event_identity    varchar(160) NOT NULL,
    applied_at        timestamptz  NOT NULL,
    CONSTRAINT pk_portfolio_exchange_events PRIMARY KEY (id),
    CONSTRAINT fk_portfolio_exchange_events_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_portfolio_exchange_events_identity
        UNIQUE (user_id, account_category, event_type, event_identity),
    -- Deliberately excludes exchange: the identity must be unique per scope, and a scope belongs to
    -- exactly one exchange for a given user in this product.
    CONSTRAINT ck_portfolio_exchange_events_category CHECK (account_category IN ('SPOT', 'FUTURES'))
);

COMMENT ON TABLE portfolio_exchange_events IS
    'Duplicate-suppression ledger for Binance user-data events. Keyed by a natural event identity, '
    'so a replayed event is recognised as a duplicate after a restart. Append-only.';

CREATE INDEX IF NOT EXISTS ix_portfolio_exchange_events_prune
    ON portfolio_exchange_events (applied_at);