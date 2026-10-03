-- V5: Market identity on positions.
--
-- Phase: SPOT/FUTURES market identity.
--
-- Purpose
--   "positions.symbol" identifies an instrument only within one market. BTCUSDT is a
--   valid spot pair AND a valid USDT-M futures perpetual, and the two have different
--   prices. A position that records only the symbol cannot be matched to the feed it
--   is supposed to follow, which allows a futures tick to evaluate a spot position
--   against the futures price and close it on the wrong trigger.
--
--   This column records which market the position was opened on, so price lookup,
--   trigger evaluation, and display all resolve against one market by construction.
--
-- Schema
--   trading_mode VARCHAR NOT NULL DEFAULT 'SPOT' on the positions table.
--
--   Nullable in the JPA mapping (boxed enum) rather than NOT NULL so that Hibernate can
--   hydrate rows whose value is unknown, but defaulted in the database so no new row is
--   ever ambiguous. See Position.effectiveTradingMode() for the legacy-safe accessor.
--
-- Backfill
--   Existing rows inherit the market of the signal that opened them. That is the
--   authoritative answer, not a guess: positions.signal_id is a 1:1 link to the Signal
--   that produced them, and Signal.trading_mode is already populated.
--
--   Rows with no linked signal (manually opened positions) cannot have their market
--   recovered from the database and keep the 'SPOT' default. This is recorded here
--   rather than silently assumed: it is the one case where the stored market is a
--   default rather than a fact.
--
-- Index
--   ix_positions_trading_mode_status supports the market-scoped open-position lookup
--   that replaced the previous "load every open position and filter in Java" scan.
--
-- Safety
--   Additive only. No column is dropped, renamed or retyped, and no existing value is
--   modified except the backfill above. The unique constraint
--   uk_positions_portfolio_signal is untouched.

ALTER TABLE positions
	ADD COLUMN IF NOT EXISTS trading_mode VARCHAR(32);

UPDATE positions p
SET trading_mode = s.trading_mode
FROM signals s
WHERE p.signal_id = s.id
	AND p.trading_mode IS NULL
	AND s.trading_mode IS NOT NULL;

UPDATE positions
SET trading_mode = 'SPOT'
WHERE trading_mode IS NULL;

ALTER TABLE positions
	ALTER COLUMN trading_mode SET DEFAULT 'SPOT';

CREATE INDEX IF NOT EXISTS ix_positions_trading_mode_status
	ON positions (trading_mode, status);