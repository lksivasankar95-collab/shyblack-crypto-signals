-- V4: Execution routing decision ledger and audit trail.
--
-- Phase 8. WRITTEN BUT NOT EXECUTED. Applied out of band, consistent with
-- V1-V3. There is no Flyway runner configured in this project.
--
-- Purpose
--   One authoritative record per (user, signal, account_mode, account_category)
--   routing decision. This is simultaneously the execution audit trail and the
--   idempotency ledger that prevents a duplicate signal event, an application
--   restart replay, or two concurrent consumers from placing two orders.
--
-- Why a table rather than an in-memory check
--   A heap-based "have I seen this?" set is erased by a restart, so a replayed
--   event after a restart would place a second order. A unique constraint makes
--   duplicate suppression restart-safe and race-safe: concurrent duplicates
--   collide in the database instead.
--
-- Security
--   No credential is stored. No API key, no API secret, no listen key, no signed
--   request string, no raw exchange response body, no authorization header. The
--   owning user_id is sufficient to resolve credentials when needed, and the row
--   is safe to read and log.
--
-- Note on ordering
--   V1-V3 were written before this file and are applied first. This migration
--   depends on no Portfolio table, so it is independent of them.

CREATE TABLE IF NOT EXISTS execution_decisions (
    id                  UUID        NOT NULL,
    account_mode        VARCHAR(16) NOT NULL,
    account_category    VARCHAR(16) NOT NULL,
    trading_mode        VARCHAR(16),
    user_id             UUID        NOT NULL,
    signal_id           UUID        NOT NULL,

    -- Mirrors the uniqueness constraint columns. Stored so the identity is
    -- readable and loggable without reconstructing it from four columns.
    identity_key        VARCHAR(200) NOT NULL,

    symbol              VARCHAR(32),
    side                VARCHAR(8),

    decision            VARCHAR(24) NOT NULL,
    -- Populated only when decision = 'REJECT'. A rejection always carries a
    -- reason; a non-rejection never carries a misleading one.
    rejection_reason    VARCHAR(48),
    detail              VARCHAR(500),
    target_engine       VARCHAR(32),

    -- Exchange-side result. NULL until a submission has actually been attempted.
    outcome             VARCHAR(32),
    exchange_order_id   VARCHAR(80),
    -- Stored verbatim rather than normalised, so an unexpected exchange status is
    -- visible instead of being mapped to a convenient default.
    exchange_status     VARCHAR(32),

    decided_at          TIMESTAMP   NOT NULL,
    dry_run             BOOLEAN     NOT NULL DEFAULT FALSE,

    created_at          TIMESTAMP   NOT NULL,
    updated_at          TIMESTAMP   NOT NULL,

    CONSTRAINT pk_execution_decisions PRIMARY KEY (id),

    -- The idempotency guarantee. Same user + signal + mode + category can be
    -- decided exactly once, forever.
    CONSTRAINT uk_execution_decisions_identity
        UNIQUE (user_id, signal_id, account_mode, account_category),

    CONSTRAINT ck_execution_decisions_mode
        CHECK (account_mode IN ('PAPER', 'LIVE')),

    -- MAIN is a read-model aggregate and is never an execution target. It is
    -- still permitted as a column value because a PAPER decision is recorded
    -- against MAIN, so this constraint deliberately allows all four.
    CONSTRAINT ck_execution_decisions_category
        CHECK (account_category IN ('MAIN', 'SPOT', 'FUTURES', 'OPTIONS')),

    CONSTRAINT ck_execution_decisions_decision
        CHECK (decision IN ('EXECUTE', 'DRY_RUN', 'REJECT', 'NOT_SUPPORTED', 'NOT_EXECUTABLE')),

    -- A REJECT must say why. This is what stops an unexplained refusal from
    -- becoming a silent no-op.
    CONSTRAINT ck_execution_decisions_reject_reason
        CHECK (decision <> 'REJECT' OR rejection_reason IS NOT NULL)
);

-- Lookup paths the router actually uses.
CREATE INDEX IF NOT EXISTS ix_execution_decisions_signal
    ON execution_decisions (signal_id);

CREATE INDEX IF NOT EXISTS ix_execution_decisions_decision
    ON execution_decisions (decision);

-- Reconciliation scan: finds every attempt whose exchange outcome was never
-- established. Those are the only orders safe to reconcile, and never safe to
-- blindly re-send.
CREATE INDEX IF NOT EXISTS ix_execution_decisions_outcome
    ON execution_decisions (outcome);

CREATE INDEX IF NOT EXISTS ix_execution_decisions_decided_at
    ON execution_decisions (decided_at);

-- Per-user execution history, newest first.
CREATE INDEX IF NOT EXISTS ix_execution_decisions_user_decided
    ON execution_decisions (user_id, decided_at DESC);