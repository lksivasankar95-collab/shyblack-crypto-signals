# NFM Quantitative Validation Report

Persisted, auditable record of NFM_FUTURES historical validation runs. This
template is descriptive only. It never asserts profitability, a validated edge,
a superior strategy, or a best configuration — those require sufficient real
data and independent support that **does not currently exist**.

Allowed statuses: `COMPLETED · BLOCKED · DATA_COVERAGE_PARTIAL ·
DATA_QUALITY_BLOCKED · RUNTIME_BLOCKED · NOT_VALIDATED`.

> Framework status and real-market results are kept strictly separate below.
> Passing framework tests is NOT a real validation.

## 0. Status split
- **FRAMEWORK STATUS:** implemented + tested (runner, engines, persistence, read APIs, execution endpoint, manifest).
- **REAL EXECUTION STATUS:** NOT EXECUTED — no real historical run has been performed.
- **DATA COVERAGE:** EVENT_COVERAGE_PARTIAL (113 BLS events); derivatives OI/funding present, liquidation UNKNOWN.
- **RUNTIME STATUS:** RUNTIME_BLOCKED (externally stale).
- **BASELINE / WALK_FORWARD / OOS / SENSITIVITY:** prepared and executable; each is `RUNTIME_BLOCKED` until the runtime/data allow a real run.
- **LIMITATIONS:** see §14.
- **MEASURED STATISTICS:** none yet (no run executed). No profitability/edge claim is made.

## 1. Run identity
`runId` (deterministic `UUID(baseConfig.hash() + "|" + runType)`). Immutable.

## 2. Strategy / version
`strategyId` = `NFM_FUTURES`, `strategyVersion` = `NFM_FUTURES_V1`.

## 3. Configuration hash
`configurationHash` (SHA-256 of the frozen `BacktestConfig`). `configurationJson`
stored verbatim. Same config → same identity.

## 4. Dataset provenance
`marketDatasetVersion`, `eventDatasetVersion`, `derivativesDatasetVersion`
(e.g. `NFM_RESEARCH_V1`, `NFM_EVENTS_V1`, `NFM_DERIV_V1`).

## 5. Historical range
`symbols` (BTCUSDT, ETHUSDT), `timeframe`, `startTime`, `endTime`.

## 6. Data quality
`dataQualityStatus` = PASS | BLOCKED. Hard gate failure ⇒ `DATA_QUALITY_BLOCKED`.

## 7. Execution status
`validationStatus` (COMPLETED | BLOCKED | NOT_VALIDATED) and `executionStatus`
(the precise `NfmValidationStatus`).

## 8. Trade statistics
`tradeCount`, `wins`, `losses`, `winRate` — NULL when not computable.

## 9. PnL statistics
`netPnl`, `grossProfit`, `grossLoss`, `fees`, `slippage`, `expectancy`,
`profitFactor` — NULL when not computable (never 0 as a substitute).

## 10. Drawdown
`maxDrawdown`, `maxDrawdownPct` — NULL when unavailable.

## 11. Risk metrics
`returnPct` and (for walk-forward) `worstDrawdownPct`. Descriptive only.

## 12. Event coverage
`eventCoverageStatus` = `EVENT_COVERAGE_PARTIAL` (113 exact BLS events:
CPI 34 / NFP 46 / PPI 33) | `EVENT_COVERAGE_UNKNOWN`.

## 13. Derivatives coverage
OI 5m (99.96%), funding 8h (to 2026-08-31), liquidation **UNKNOWN**. Missing
⇒ NULL.

## 14. Limitations
Fed/FOMC collection runtime-pending. BEA/Census/DOL date-only → INELIGIBLE.
ECB/BoE/BoJ/BoC/RBA/SNB exact timestamps unavailable → INELIGIBLE.
Crypto/regulatory/security no exact-time dataset. Liquidation UNKNOWN.

## 15. Event attribution & validation analytics
Status legend: **MEASURED** (real data), **UNKNOWN** (null, not zero), **NOT_AVAILABLE**, **DATA_COVERAGE_PARTIAL**.

1. **Event coverage** — DATA_COVERAGE_PARTIAL: 113 EXACT BLS events (CPI 34 / NFP 46 / PPI 33).
2. **Event → signal attribution** — deterministic causal rule `event.time <= candle.closeTime` (`NfmEventAttributionBuilder.causallyEligible`); multiple eligible events preserved; if none, `EVENT_ATTRIBUTION_UNKNOWN` (never guessed).
3. **Event → trade attribution** — engine captures `PartialExitBacktestEngine.Entry` (signal + entry + lifecycle); signal grade/score not exposed by the engine → UNKNOWN.
4. **Event type stats** — `NfmValidationAnalytics.byEventType` (event/signal/trade/long/short/win/loss/net/PF); score/reaction/drawdown UNKNOWN.
5. **Symbol stats** — per BTCUSDT/ETHUSDT; descriptive only, no ranking.
6. **Direction stats** — LONG/SHORT separately; no BEST_DIRECTION field.
7. **Score/grade stats** — NOT_AVAILABLE (grade/score not surfaced by the engine); no threshold recommendation.
8. **Reaction stats** — NOT_AVAILABLE (windows/series not captured); priceReaction/volumeRatio/oiChange/funding/liquidation UNKNOWN.
9. **Regime stats** — NOT_AVAILABLE (regime not surfaced per trade); no new regime algorithm.
10. **No-trade reasons** — NOT_AVAILABLE (reject/block reasons not captured per signal); no invented categories.
11. **Trade lifecycle** — MEASURED from lifecycle fills: TP1/TP2/TP3/SL hit rates; timing UNKNOWN (bar timestamps not captured).
12. **OOS attribution** — separated via `runType=OOS` and `NfmValidationDetail` (test window); wins/organised separately from in-sample.
13. **Walk-forward attribution** — per-window rows retained in `research_nfm_validation_detail` (kind WINDOW); never collapsed, no best window.
14. **Sensitivity attribution** — per-variant rows (kind VARIANT) with `paramsJson` + `configurationHash`; never ranked.
15. **Data limitations** — Fed runtime-pending; BEA/Census/DOL + central banks INELIGIBLE; crypto/regulatory/security uncovered; liquidation UNKNOWN; surprise data absent → SURPRISE_DATA_UNAVAILABLE.

All analytics are descriptive; no profitability/edge/best claim. Run-scoped analytics endpoints and attribution persistence are **NOT_AVAILABLE** in this revision (analytics are produced from engine-captured entries in-process).

## 16. Validation conclusion
Factual status only. **Real NFM validation has NOT been completed.** It remains
blocked by an externally stale runtime, partial event coverage, and unavailable
historical liquidation data. Persisting a run does not imply an edge.
