# NFM Final Validation Report

Status legend: **MEASURED · UNKNOWN · NOT_AVAILABLE · DATA_COVERAGE_PARTIAL ·
RUNTIME_BLOCKED · NOT_EXECUTED**. Code completion and validation completion are
different: this report never claims validated edge, profitability, or a best
configuration.

## 1. Executive status
NFM code pipeline is complete and fully tested (all gradle tasks green). **No
real historical validation has been executed** (runtime externally stale).
Therefore validation status = NOT_EXECUTED / RUNTIME_BLOCKED.

## 2. Strategy identity
`NFM_FUTURES` / `NFM_FUTURES_V1`.

## 3. Configuration
Frozen `NfmFuturesConfig.defaults()` (minScore 65, grades 85/75/65, reaction 0.35%,
volume 1.2×, OI 1.0%, funding 0.0005/0.0010, SL 1.5×ATR, minRR 1.5, TP 1.5/2.5/4.0R,
cooldown 60m). Deterministic `configurationHash`.

## 4. Dataset provenance
Market `NFM_RESEARCH_2023_09_2026_09_V1`; Event `NFM_EVENTS_2023_09_2026_09_V1`;
Derivatives `NFM_DERIVATIVES_2023_09_2026_09_V1`.

## 5. Event coverage
**DATA_COVERAGE_PARTIAL** — 113 EXACT BLS events (CPI 34, NFP 46, PPI 33); 226
BTC/ETH mappings. FOMC adapter implemented; collection RUNTIME_BLOCKED. Others
INELIGIBLE/UNAVAILABLE (see §19).

## 6. Data-quality status
`ValidationDataQualityGate` wired; hard failure ⇒ DATA_QUALITY_BLOCKED.

## 7. Baseline
Executable (PartialExitBacktestEngine). Real run: **NOT_EXECUTED**.

## 8. Walk-forward
Executable (per-window, causal, fresh strategy). Real run: **NOT_EXECUTED**.

## 9. OOS
Executable (single held-out window, frozen config). Real run: **NOT_EXECUTED**.

## 10. Sensitivity
Executable (explicit variants; no ranking). Real run: **NOT_EXECUTED**.

## 11. Event attribution
Persisted to `research_nfm_validation_attribution` (immutable, unique
`runId+signalId`). Causal event→signal, signal→trade, trade→outcome. No fabrication.

## 12. Symbol attribution
`bySymbol` per BTC/ETH; descriptive, no ranking.

## 13. Direction attribution
`byDirection` LONG/SHORT; no best direction.

## 14. Grade/score attribution
`byGrade` (A/B/C) + score buckets; score/grade from the real `NfmAssessment`.

## 15. Regime attribution
`byRegime` (event/signal/trade/LONG/SHORT/win/loss/net/winRate/expectancy/PF); no ranking.

## 16. No-trade attribution
**NOT_AVAILABLE** — non-actionable assessments return `Optional.empty()`; reasons
not observable without an engine change. No invented categories.

## 17. Partial-exit analysis
TP1/TP2/TP3/SL hit rates MEASURED from real lifecycle fills; timing NOT_AVAILABLE.

## 18. Risk metrics
netPnl/gross/fees/slippage/winRate/expectancy/PF/drawdown/returnPct — NULL when
unavailable, never zero.

## 19. Limitations
Fed/FOMC runtime-blocked; BEA/Census/DOL date-only INELIGIBLE; central banks
exact timestamp UNAVAILABLE; crypto/regulatory/security no authoritative exact-time
dataset; liquidation UNKNOWN; surprise NULL ⇒ SURPRISE_DATA_UNAVAILABLE; multi-window
reaction NOT_AVAILABLE (single configured window only).

## 20. Runtime status
**RUNTIME_BLOCKED** — PID 4988 stale, externally managed, untouched.

## 21. Reproducibility
Same dataset/version/config/symbols/range/strategy version ⇒ same `runId`,
`configurationHash`, and result. No randomization, no optimization, no winner.

## 22. Final validation status
**NOT_EXECUTED.** Code completion achieved; validation completion pending a
current externally-managed runtime and sufficient authoritative event data.
