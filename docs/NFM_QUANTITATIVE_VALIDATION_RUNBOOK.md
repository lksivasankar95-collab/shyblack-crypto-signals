# NFM Quantitative Validation Runbook

Deterministic orchestration for NFM_FUTURES historical validation. **The runner
existing does NOT mean real validation has been completed.**

## 1. Required datasets
- Market candles: BTCUSDT + ETHUSDT (1m/5m/15m/1h/4h), chronological, OHLC-valid.
- Events: exact-timestamp `NewsEvent`s (`event.time <= candle.closeTime`).
- Derivatives: OI (5m), funding (8h) — as-of only; liquidation UNKNOWN.

## 2. Dataset versions
`NFM_RESEARCH_2023_09_2026_09_V1` (market/OI/funding), `NFM_EVENTS_2023_09_2026_09_V1` (events), derivatives snapshot via `HistoricalDerivativesProvider`.

## 3. Configuration
Frozen `NFM_FUTURES_V1` (`NfmFuturesConfig.defaults()`): minScore 65, grades 85/75/65, reaction 0.35%, volume 1.2×, OI 1.0%, funding 0.0005/0.0010, SL 1.5×ATR, minRR 1.5, TP 1.5/2.5/4.0R, cooldown 60m. **Do not silently change.** Identity via `BacktestConfig.hash()`.

## 4–7. Execution modes
Single entry point: `NfmValidationRunner.run(runType, BacktestConfig, strategyFactory, candles, events, datasetVersions, windowDays, stepDays, variants)`.
- **BASELINE** → `PartialExitBacktestEngine`.
- **WALK_FORWARD** → `WalkForwardEngine` (chronological, fresh strategy per window).
- **OOS** → `OutOfSampleRunner` (single held-out window, frozen config).
- **SENSITIVITY** → `SensitivityRunner` (explicit variants, no winner selection).
Result (`NfmValidationResult`): runId (deterministic from config hash+runType), strategy id/version, runType, symbols, timeframe, dates, config hash, dataset versions, dataQuality, executionStatus, trades, netPnl, maxDrawdownPct, winRatePct, expectancy, profitFactor, notes, windows. Uncomputable metrics are **null**.

## 8. Data-quality gates
`ValidationDataQualityGate` runs first: candles (chronological/no-dup/valid OHLC/positive/volume), events (exact time/unique id/in-window), derivatives **missing ≠ 0** (value present iff availability flag). Hard failure ⇒ `DATA_QUALITY_BLOCKED`.

## 9. UNKNOWN handling
Missing OI/funding/liquidation remain UNKNOWN/null; empty event set ⇒ `DATA_COVERAGE_PARTIAL` (no fabricated events). Never zero.

## 10. No-lookahead
`event.time <= candle.closeTime` is the only availability rule; derivatives as-of; entries next-candle-open; SL/TP never use future candles; same-bar SL-first. Tests: `BacktestEngineEventNoLookAheadTest`, `NfmBacktestDerivativesNoLookAheadTest`, `RepositoryHistoricalDerivativesProviderTest`, `ResearchRunnerTest`.

## 11. Partial-exit accounting
`originalQty = exitedQty + remainingQty`; entry fee once (final slice), exit fee per exited qty; SL-first same-tick; each TP once. Tests: `PartialExitSimulatorTest`, `PartialExitBacktestEngineTest`, `PaperTradingPartialCloseTest`.

## 12. Result statuses
`COMPLETED · DATA_QUALITY_BLOCKED · DATA_COVERAGE_PARTIAL · DATA_COVERAGE_BLOCKED · RUNTIME_BLOCKED · NOT_EXECUTED`.

## 13. Runtime prerequisites
Externally-managed backend must be **restarted to the current jar** (Fed statements + BLS retry; schema columns). `RUNTIME_BLOCKED` otherwise. The runner does not start/stop the backend.

## 14. Current coverage limitations
113 EXACT events (BLS CPI/NFP/PPI). Fed implemented (runtime-pending). BEA/Census/DOL + central banks INELIGIBLE (date-only). Crypto/regulatory/security uncovered. Liquidation UNKNOWN.

**Current real validation remains BLOCKED by:** externally stale runtime, partial event coverage, unavailable historical liquidation data.

## 15. Result persistence lifecycle
RUN → VALIDATE → PERSIST IMMUTABLE RESULT → AUDITABLE REPORT → DESCRIPTIVE COMPARISON.
`NfmValidationRunner` stays persistence-free; `NfmValidationResultPersistenceService`
is the only writer. Entity `research_nfm_validation_run` (identity/config/provenance/
scope/status/metrics; NULL = UNKNOWN) plus `research_nfm_validation_detail`
(`WINDOW` | `VARIANT`).

## 16. Result immutability
`run_id` is UNIQUE; persistence refuses to overwrite an existing run (throws
"immutable") and never mutates `configurationHash`, dataset versions, strategy
version, execution assumptions, or run type. Same deterministic config → same
`runId`/`configurationHash` → second persist is rejected, original retained.

## 17. Run identity
`runId = UUID(baseConfig.hash() + "|" + runType)`; no randomness, no optimization.

## 18. Walk-forward result storage
Each window is persisted individually (label, test start/end, trades, netPnl,
drawdown, winRate, expectancy) and returned by `GET .../windows`; never collapsed.

## 19. Sensitivity result storage
Each explicit variant persisted independently (variant id, `paramsJson`, config
hash, metrics, status) and returned by `GET .../variants`. No `BEST_VARIANT`,
`TOP_VARIANT`, `WINNER`, or `OPTIMAL_CONFIGURATION` exists.

## 20. OOS result storage
OOS is stored with `runType = OOS`, frozen config hash and dataset versions, and
is distinguishable from BASELINE by run type and scope.

## 21. Report statuses
`COMPLETED · BLOCKED · DATA_COVERAGE_PARTIAL · DATA_QUALITY_BLOCKED ·
RUNTIME_BLOCKED · NOT_VALIDATED`.

## 22. Research comparison rules
Read-only: get run by id, list runs, list windows, list variants. **Research
comparison is descriptive only. No automatic winner selection or ranking is
performed.** A run existing does not mean validation has been completed.

## 23. Complete pipeline
DATA → EVENT → SIGNAL → TRADE → OUTCOME → ATTRIBUTION → PERSISTENCE → ANALYTICS →
BASELINE → WALK_FORWARD → OOS → SENSITIVITY. Run identity is deterministic
(`runId = UUID(configurationHash + "|" + runType)`); re-execution is idempotent.

## 24. Attribution persistence
`NfmValidationAttribution` (table `research_nfm_validation_attribution`) stores the
immutable EVENT→SIGNAL→TRADE→OUTCOME row (event ids/type/stage/tier/timestamp,
symbol, signal direction/score/grade/status, expected/actual/surprise,
priceReaction/volumeRatio/oiChange/funding/liquidation, regime, tradeability/reject
reason, entry, SL/TP1-3, TP/SL hits, gross/fees/slippage/net, outcome). Unique
`(run_id, signal_id)`; duplicates refused; NULL = UNKNOWN. Populated for BASELINE.

## 25. Analytics API (read-only, descriptive)
`GET /api/v1/admin/research/nmf-validation/{runId}/analytics` (+ `/events`,
`/symbols`, `/directions`, `/grades`, `/scores`, `/regimes`, `/no-trade`).
No write, ranking, best, top, optimal, or winner endpoint exists.

## 26. Funding / liquidation / surprise
Funding rate propagated from the as-of derivatives snapshot when available, else
NULL. Liquidation NULL unless an authoritative provider supplies it (historically
UNKNOWN). Surprise NULL unless expected+actual exist (`SURPRISE_DATA_UNAVAILABLE`
otherwise). No reconstruction, estimation, or synthetic values.

## 27. Non-actionable assessments
**NOT_AVAILABLE** in this revision: the engine returns `Optional.empty()` for
`WAIT_CONFIRMATION`/`NO_TRADE`/`RISK_BLOCKED`, so their reasons are not observable
without changing engine behavior. No reasons are inferred.
