# Backtesting — Operational Runbook

## Configuration knobs

All under `app.backtesting`:

- `engine-version` — stored on every run row. Bump when the engine's
  behaviour changes materially so historical runs remain identifiable.
- `max-candles` — hard cap per run (default 20,000).
- `max-concurrent-runs` — bounded thread pool (default 2).
- `max-range-days` — wall-clock window for start/end (default 365).
- `default-warmup-candles` — informational; strategies declare their
  own `warmup()`.

## Running a backtest

1. UI: **Backtesting** tab → fill the config form → **RUN BACKTEST**.
2. Or: `POST /api/v1/backtests` with the JSON body matching
   `BacktestConfigRequest`.
3. The response returns the run in `QUEUED`. Poll `GET /api/v1/backtests/{id}`
   (or watch the UI progress bar) until `status` is one of `COMPLETED /
   FAILED / CANCELLED`.
4. Fetch details: `/trades`, `/signals`, `/equity`.

## Reproducing a historical run

Every completed run stores:

- `configurationHash` — SHA-256 of the canonical config.
- `configurationJson` — verbatim request body.
- `strategyVersion` + `engineVersion` at run time.

To reproduce: POST the stored `configurationJson` again. If
`configurationHash` matches AND both versions match, the resulting
metrics will be identical.

If they don't match, one of the four axes changed (config / strategy /
engine / historical data). Investigate before comparing metrics.

## Common incidents

### "No historical candles for the requested range"
- `BinanceHistoricalDataProvider` returns only what Binance has in the
  requested window. Narrow the range or verify the symbol/timeframe.
- For tests: seed the `FixtureHistoricalDataProvider` explicitly.

### "Requested range exceeds N candles"
- `app.backtesting.max-candles` guardrail. Bump the property or split
  the range into multiple runs.

### Run stuck in `RUNNING`
- Check the backend logs for `[Backtest]` entries.
- Cancel via `POST /api/v1/backtests/{id}/cancel`. The engine checks
  the cancellation flag on every candle and exits with
  `CANCELLED` at the next iteration.

### Reproducing a "surprising" result
- Compare `configurationHash` between old and new runs.
- Compare `strategyVersion` — this bumps whenever the strategy logic
  changes.
- Compare `engineVersion` — this bumps when execution mechanics change
  (e.g. same-candle policy default, slippage model).

### "Result differs from live"
Expected sources of divergence:

- Backtest uses `NEXT_CANDLE_OPEN + slippage`; live fills happen at
  whatever price the exchange awards on a real market order.
- Backtest same-candle policy defaults to SL_FIRST; live is driven by
  actual intrabar execution and can beat that.
- Backtest funding fees are not applied (Futures only) — live is.
- Backtest doesn't model liquidity or partial fills — live does.

These are documented limitations. Backtests approximate; they do not
promise perfect live parity.

## What CI protects

- **No look-ahead** — `BacktestNoLookAheadTest`.
- **Same-candle policy** — `BacktestEngineSameCandlePolicyTest`.
- **Leverage doesn't inflate risk** — `BacktestExecutionSimulatorTest#leverage_doesNotChangeRiskAmount`.
- **Deterministic hash** — `BacktestDeterminismTest`.
- **Cross-module isolation** — `PaperLiveIsolationTest`.
- **No fake metrics** — `BacktestMetricsCalculatorTest` verifies
  undefined metrics come back as null, never sentinel values.

## When adding a new strategy

1. Implement `BacktestStrategy` — must have a distinct `id()` +
   `version()` and never access data past `history.get(currentIndex)`.
2. Annotate with `@Component` — the registry picks it up automatically.
3. Bump `version()` on every behaviour change so historical runs
   remain identifiable.
4. Add a strategy-specific test that pins expected trade outcomes on
   a fixture candle series.
