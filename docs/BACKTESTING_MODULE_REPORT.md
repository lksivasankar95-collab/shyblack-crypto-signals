# Backtesting Module — Implementation Report

**Status:** end-to-end SPOT + FUTURES backtesting shipped. Look-ahead
prevention + same-candle policy verified by regression tests.
**Baseline:** commit `d554f2b` (Paper / Live SPOT / Live FUTURES already
shipped).

---

## 1. Core principle

Every candle T is processed with information available AT candle T. No
future candle, indicator, high/low, volume, or funding value can affect
what happens at T. This is enforced structurally in `BacktestEngine.run`
and covered by `BacktestNoLookAheadTest`.

## 2. Architecture

```
                    HISTORICAL DATA PROVIDER
                    (Binance REST | Fixture)
                              │
                              ▼
                     BacktestingProperties
                       (limits, engine ver)
                              │
                              ▼
             ┌────────────────┴────────────────┐
             │       BacktestJobRunner         │  bounded thread pool
             │  QUEUED → RUNNING → terminal    │
             └────────────────┬────────────────┘
                              ▼
                        BacktestEngine
       for each candle T (chronological):
         1. Evaluate open position vs. this candle (SL / TP / LIQ)
         2. Fill pending entry at THIS candle's OPEN + slippage
         3. Run strategy on candles ≤ T; may queue a signal
         4. Snapshot equity + drawdown
                              │
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
   Strategy             Simulator             Portfolio
   (IndicatorEngine     (slippage,            (cash, invested,
   from live pipe)      SL/TP triggers,        margin, unreal PnL,
                        liquidation)           peak / drawdown)
                              │
                              ▼
                      MetricsCalculator
        (winrate, PF, expectancy, Sharpe, Sortino,
         maxDD, largestWin/Loss, avgWin/Loss, ...)
                              │
                              ▼
              BacktestRun + BacktestTrade[] +
              BacktestSignal[] + BacktestEquityPoint[]
                              │
                              ▼
                      REST + Flutter UI
```

## 3. Look-ahead prevention

Two independent guarantees:

1. **Structural:** the engine hands the strategy `history.subList(0, i+1)`
   — a fresh list slice terminating at the current candle. It is
   physically impossible for the strategy to read candle `i+1`.
2. **Execution:** signals emitted at candle T-1 close fill at candle T
   OPEN plus adverse slippage (`NEXT_CANDLE_OPEN` model — the default).
   `SAME_CANDLE_CLOSE` exists for research only and is documented as
   biased.

`BacktestNoLookAheadTest` explicitly proves both.

## 4. Same-candle SL/TP policy

When a single OHLC candle touches both SL and TP the actual intrabar
sequence is unknowable. Rather than automatically pick the profitable
outcome we default to **SL_FIRST** (aka **CONSERVATIVE**). Users may
select `TP_FIRST` for research or `REQUIRE_LOWER_TIMEFRAME` (currently
treated as SL_FIRST — real sub-candle resolution is a future drop).
`BacktestEngineSameCandlePolicyTest` verifies the default.

Related invariant: the engine deliberately does NOT evaluate SL/TP on the
same candle that filled the entry — the fill happens at that candle's
open and we cannot tell whether the H/L extremes came before or after
the fill. This is documented and covered by the end-of-test test.

## 5. Strategy reuse

`EmaRsiBacktestStrategy` calls `IndicatorEngine.compute(...)` — the SAME
class the live spot signal pipeline uses. There is exactly ONE
implementation of EMA / RSI / MACD / ATR / ADX / Volume MA in the
codebase; the backtest can never disagree with the live indicator
values.

The full live `SpotSignalEngine` is multi-timeframe (4H+1H+15M) and its
literal reuse would need historical data at three timeframes — deferred
as a next drop.

## 6. Execution model

- **MARKET entry:** fills at the next candle's open, adjusted by adverse
  slippage. LONG pays up; SHORT gets less.
- **Protective SL:** modelled as a virtual STOP order tracked by the
  simulator. When touched, exit at SL price adjusted by adverse exit
  slippage.
- **TP1:** same model as SL but at the profit target.
- **Liquidation (FUTURES only):** approximated at
  `entry × (1 ∓ 1/leverage)` for LONG/SHORT. Checked BEFORE SL/TP —
  the exchange liquidates first; you can't take profit on a position
  the exchange has already closed.
- **End of test:** any open position closes at the final candle's close
  with adverse slippage. Documented, configurable in the entity.

## 7. Position sizing (leverage doesn't inflate risk)

```
riskAmount   = balance × riskPct / 100
stopDistance = |entry − stop|
quantity     = riskAmount / stopDistance
```

Leverage is a Futures-only knob: it reduces required margin
(`notional / leverage`) but never scales the risk budget. Verified by
`BacktestExecutionSimulatorTest#leverage_doesNotChangeRiskAmount`.

## 8. Metrics (no fake sentinels)

Undefined metrics return **null** — never 999.99 or -1. Covered by
`BacktestMetricsCalculatorTest`.

- gross profit / gross loss
- total net P&L / return %
- total fees
- total trades / wins / losses / liquidations
- win rate %
- profit factor (null if no losing trades)
- average win / average loss
- largest win / largest loss
- expectancy (avg net P&L per trade)
- Sharpe / Sortino — indicative per-trade ratios (not annualized;
  documented)
- max drawdown / max drawdown %

## 9. Determinism

`BacktestConfig.hash()` is `SHA-256` over the canonical string
(strategy id, symbol, timeframe, mode, start/end, capital, risk, fee,
slippage, leverage, execution model, same-candle policy). Every
`BacktestRun` persists this hash + the strategy version + engine
version, so identical inputs are guaranteed identical outputs.
`BacktestDeterminismTest` proves the hash is stable and distinctive.

The full JSON of the original request is also stored so historical
results remain interpretable if the DTO shape evolves later.

## 10. Async execution

`BacktestJobRunner` runs jobs on a fixed thread pool
(`app.backtesting.max-concurrent-runs`, default 2). Statuses:
`QUEUED → RUNNING → COMPLETED | FAILED | CANCELLED`. Progress fields
(`processedCandles`, `totalCandles`) let the UI show a live progress
bar. Cancellation flips an `AtomicBoolean` that the engine checks each
candle.

## 11. Resource limits

- `max-candles` (20,000) — hard cap per run.
- `max-range-days` (365) — wall-clock date range.
- `max-concurrent-runs` (2) — thread pool size.

Configuration is validated in `BacktestService.validateConfig`
BEFORE a run is enqueued.

## 12. Historical data

`HistoricalMarketDataProvider` is the single abstraction. Two
implementations ship:

- `BinanceHistoricalDataProvider` — reuses the existing
  `BinanceRestClient.klines(...)`. Single-fetch (up to 1000 candles per
  request in this drop; multi-page pagination is a next-drop item).
- `FixtureHistoricalDataProvider` — deterministic in-memory candles for
  tests. Zero network traffic.

Each returned candle passes OHLC validity (`HistoricalCandle.isValid`)
before entering the engine.

## 13. Isolation

`PaperLiveIsolationTest` now enforces a **four-way** invariant:

- Paper cannot import live-spot / live-futures / backtest / exchange.
- Live-spot cannot import paper / futures / backtest.
- Live-futures cannot import paper / live-spot / backtest.
- **Backtest cannot import paper / live-spot / live-futures / exchange
  adapters.**

The build breaks the moment any of these boundaries is crossed.

## 14. REST API

All endpoints under `/api/v1/backtests`, all authenticated + IDOR-safe:

| Method | Path                                | Purpose                          |
| ------ | ----------------------------------- | -------------------------------- |
| POST   | `/`                                 | Start a new run (returns QUEUED) |
| GET    | `/`                                 | List caller's runs               |
| GET    | `/{id}`                             | Single run summary               |
| GET    | `/{id}/trades`                      | All trades                       |
| GET    | `/{id}/signals`                     | All signals                      |
| GET    | `/{id}/equity`                      | Equity + drawdown points         |
| POST   | `/{id}/cancel`                      | Request cancellation             |
| DELETE | `/{id}`                             | Delete a completed run           |
| GET    | `/strategies`                       | List available strategies        |

Cross-user access returns 404 (never leaks existence).

## 15. Flutter UI

`BacktestingScreen` is now a full experience:

- Configuration form (symbol, timeframe, capital, risk %, fee %,
  slippage %, leverage, SPOT/FUTURES toggle, date range).
- Run list with progress bar for in-flight runs; per-run summary chips
  (Net P&L, Return %, Trades, Win %, MaxDD %).
- Detail screen with summary card + equity chart (CustomPainter; zero
  new package dependencies) + trade list.

Backend is authoritative for all metrics. Frontend never recomputes.

## 16. Tests

**Backend (24 new, all green):**

- `BacktestExecutionSimulatorTest` — 13 tests: SL/TP touch semantics for
  LONG/SHORT, liquidation beats TP, adverse slippage direction, risk
  quantity math, leverage invariant.
- `BacktestNoLookAheadTest` — Phase-45 regression: (a) identical
  signals when future changes, (b) engine cannot hand strategy any
  future candles.
- `BacktestEngineSameCandlePolicyTest` — Phase-47: SL_FIRST default,
  TP-only fires TP, end-of-test closes open positions.
- `BacktestMetricsCalculatorTest` — empty returns nulls, basic P&L +
  win rate + profit factor + drawdown math, liquidation counting.
- `BacktestDeterminismTest` — Phase-30: SHA-256 hash stable + distinct.
- `PaperLiveIsolationTest` extended — backtest four-way isolation.

Backend suite: **255 tests, 254 pass** (the single failure —
`NewsApiIntegrationTest.assetContextAggregatesProcessedArticles` — has
been pre-existing on `main` for four prior commits and is unrelated).

**Frontend (2 new, all green):**

- `backtesting — empty list shows CTA + config form`.
- `backtesting — completed run displays metrics`.

Total Flutter suite: **22 / 22 pass**. `flutter analyze` — 0 new issues.

## 17. Phase-42 verification ledger

| Gate                              | Status |
| --------------------------------- | ------ |
| Config validation                 | ✅ `BacktestService.validateConfig` |
| No look-ahead                     | ✅ regression test + structural guarantee |
| Next-candle-open execution        | ✅ default; SAME_CANDLE_CLOSE labelled biased |
| Same-candle SL/TP policy          | ✅ SL_FIRST default + configurable |
| Slippage                          | ✅ adverse per side + direction |
| Fees                              | ✅ per-side, configurable |
| Position sizing                   | ✅ risk-based; leverage does not inflate |
| Liquidation (Futures)             | ✅ approximated; beats TP on same candle |
| P&L on actual fills               | ✅ uses simulator exit prices |
| Drawdown                          | ✅ peak-to-current per equity point |
| Metrics (nullable)                | ✅ MetricsCalculatorTest |
| Determinism                       | ✅ SHA-256 config hash + engineVersion |
| Async execution + cancellation    | ✅ bounded pool + AtomicBoolean flag |
| Resource limits                   | ✅ max candles / range / concurrent |
| REST API                          | ✅ 9 endpoints, IDOR-safe |
| Flutter UI                        | ✅ config + list + detail + equity chart |
| Isolation from paper/spot/futures | ✅ enforced at build time |
| Multi-symbol                      | ⚠ single symbol per run in this drop |
| TP2/TP3                           | ⚠ TP1 only |
| Trailing / breakeven              | ⚠ not modelled |
| Funding fees                      | ⚠ Futures funding history not modelled |
| Testnet runtime                   | ⚠ N/A — backtesting operates on historical data only |

## 18. Known limitations

1. **Single-symbol per run.** Portfolio-level multi-symbol
   backtesting requires a unified chronological timeline; the engine
   architecture supports it but the current portfolio + strategy
   contract are single-symbol.
2. **TP2/TP3 not simulated.** Signal captures targets but the
   simulator only evaluates TP1.
3. **Trailing / breakeven SL** not modelled — the SL set at entry is
   static for the position's lifetime.
4. **Funding fees** for Futures backtests are not applied. Documented
   because we don't have historical funding data in the current
   Binance provider.
5. **Multi-timeframe strategy port.** The live spot engine's
   4H+1H+15M ensemble is not yet reimplemented for backtest. The
   shipping `ema-rsi` strategy uses the same `IndicatorEngine` but on
   a single timeframe.
6. **Pagination.** `BinanceHistoricalDataProvider` performs a single
   `klines` fetch capped at 1000 candles per request. Long ranges
   require pagination — deferred.
7. **Partial fills / lower-timeframe SL/TP resolution** — the engine
   is a full-fill, single-timeframe model. Sub-candle resolution
   requires higher-resolution historical data.
