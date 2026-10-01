# NFM Quantitative Validation Report

Persisted, auditable record of NFM_FUTURES historical validation runs. This
template is descriptive only. It never asserts profitability, a validated edge,
a superior strategy, or a best configuration — those require sufficient real
data and independent support that **does not currently exist**.

Allowed statuses: `COMPLETED · BLOCKED · DATA_COVERAGE_PARTIAL ·
DATA_QUALITY_BLOCKED · RUNTIME_BLOCKED · NOT_VALIDATED`.

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

## 15. Validation conclusion
Factual status only. **Real NFM validation has NOT been completed.** It remains
blocked by an externally stale runtime, partial event coverage, and unavailable
historical liquidation data. Persisting a run does not imply an edge.
