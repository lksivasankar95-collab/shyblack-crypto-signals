# NFM V1 Quantitative Validation Report

Strategy: **NFM_FUTURES** · Version: **NFM_FUTURES_V1** · Code commit: **85d1cc2**
Reaction-indexing correctness fix: **85d1cc2**. This is a reporting document only.

> **Reading rule — engine semantics:** the **official baseline** is measured with
> `PartialExitBacktestEngine` (TP1/TP2/TP3 scale-out + remainder SL). **Walk-Forward,
> OOS and Sensitivity** are measured with `WalkForwardEngine`/`OutOfSampleRunner`/
> `SensitivityRunner` → `BacktestEngine`, which is **single-TP** (uses `takeProfit`
> = TP1 only, ignores TP2/TP3). **Baseline metrics are therefore NOT directly
> comparable with Walk-Forward / OOS / Sensitivity metrics.** Results are never merged.

---

## 1. Implementation status
COMPLETE AND FROZEN. Reaction-indexing defect fixed at `85d1cc2`; regression tests PASS; no candle-0 fallback; no-lookahead PASS; event attribution PRESENT; partial-exit lifecycle PRESENT.

## 2. Data coverage
- Period: `2023-09-01 → 2026-09-30 UTC`; symbols BTCUSDT, ETHUSDT.
- Market: 1m/5m/15m/1h/4h candles; historical OI; historical funding.
- Events: **BLS CPI / NFP / PPI — 113 exact historical events, 226 event-symbol mappings (113 BTC, 113 ETH).**
- FOMC: adapter implemented; real runtime collection not completed.
- Other central-bank / crypto-structural / regulatory / security: no authoritative exact-timestamp history available.
- Liquidation: **UNKNOWN** (never fabricated).

**`DATA_COVERAGE_STATUS = DATA_COVERAGE_PARTIAL`.** The dataset is NOT complete historical news coverage.

## 3. Official corrected baseline — `PartialExitBacktestEngine`
| Metric | Value |
|---|---|
| Event-symbol pairs | 226 |
| Actionable / Signals / Trades | 17 / 17 / 17 |
| BTCUSDT / ETHUSDT | 8 / 9 |
| LONG / SHORT | 2 / 15 |
| Wins / Losses | 4 / 13 |
| Win rate | 23.53% |
| Gross profit / Gross loss | 1314.27 / -2421.34 |
| Net PnL | **-1107.07** |
| Expectancy | -65.1218 |
| Profit factor | 0.5428 |
| Average win / loss | 328.57 / 186.26 |
| Max drawdown / % | 1276.09 / 12.76% |
| Reaction min / max / median | -3.832947% / +1.697278% / -1.437488% |
| No-lookahead / Attribution | PASS / PRESENT |

`NFM_BASELINE_STATUS = COMPLETED`.

## 4. Walk-forward — `WalkForwardEngine → BacktestEngine` (single-TP)
90-day test windows, 30-day step; BTCUSDT + ETHUSDT.
| Metric | Value |
|---|---|
| Windows (total / BTC / ETH) | 70 / 35 / 35 |
| Trades | 47 |
| Wins / Losses | 16 / 31 |
| LONG / SHORT | 18 / 29 |
| Win rate | 34.04% |
| Gross profit / Gross loss | 4312.84 / -6880.41 |
| Fees | 753.91 |
| Net PnL | **-2567.58** |
| Expectancy | -54.63 |
| Profit factor | 0.6268 |
| Worst single-window DD% | 4.8726% |
| Average window return | -0.3668% |
| No-lookahead / Attribution | PASS / PRESENT |

Windows **overlap** (not independent). No window is ranked.

`NFM_WALK_FORWARD_STATUS = COMPLETED`.

## 5. Out-of-sample — `OutOfSampleRunner → BacktestEngine` (single-TP)
Held-out period: `2025-10-01T00:00:00Z → 2026-09-30T23:59:59Z`.
| Metric | Value |
|---|---|
| Event-symbol pairs (BTC/ETH) | 60 (30/30) |
| Actionable / Signals / Trades | 3 / 3 / 3 |
| BTC / ETH | 1 / 2 |
| LONG / SHORT | 3 / 0 |
| Wins / Losses | 1 / 2 |
| Win rate | 33.33% |
| Gross profit / Gross loss | 254.56 / -428.86 |
| Net PnL | **-174.30** |
| Expectancy | -58.10 |
| Profit factor | 0.5936 |
| Average win / loss | 254.56 / 214.43 |
| Max drawdown / % | 508.02 / 5.0802% |
| Average window return | -0.8715% |

OOS verification: strictly held out; frozen configuration; no parameter fitting; `event.time <= candle.closeTime`; derivatives as-of; corrected reaction indexing; no candle-0 fallback; attribution preserved; TP/SL data available.

**OOS sample is very small (3 trades) and `DATA_COVERAGE_PARTIAL`.** No profitability/edge claim.

`NFM_OOS_STATUS = COMPLETED`.

## 6. Sensitivity — `SensitivityRunner` (single-TP, determinism PASS)
Explicit predefined variants only; no search/optimization; no ranking; each recorded independently.

| Variant | Change | Trades | W | L | WR | GrossP | GrossL | Net | Exp | PF | WorstDD% |
|---|---|---|---|---|---|---|---|---|---|---|---|
| V0_BASELINE | defaults | 17 | 6 | 11 | 35.29% | 1605.18 | -2401.90 | -796.72 | -46.8657 | 0.6683 | 6.4261 |
| V1_MIN_SCORE_60 | minimumScore=60 | 25 | 10 | 15 | 40.00% | 2693.08 | -3274.07 | -580.99 | -23.2395 | 0.8225 | 8.3454 |
| V2_MIN_SCORE_70 | minimumScore=70 | 6 | 2 | 4 | 33.33% | 541.74 | -884.42 | -342.68 | -57.1140 | 0.6125 | 3.9967 |
| V3_MIN_RR_2_0 | minRR=2.0 | 0 | 0 | 0 | null | 0 | 0 | 0 | null | null | 0 |
| V4_SL_ATR_2_0 | slAtrMultiplier=2.0 | 18 | 7 | 11 | 38.89% | 1951.85 | -2339.59 | -387.75 | -21.5414 | 0.8343 | 10.9468 |
| V5_COOLDOWN_0 | cooldownMinutes=0 | 17 | 6 | 11 | 35.29% | 1605.18 | -2401.90 | -796.72 | -46.8657 | 0.6683 | 6.4261 |
| V6_REACTION_0_45 | priceReactionThresholdPct=0.45 | 14 | 4 | 10 | 28.57% | 1065.52 | -2141.39 | -1075.86 | -76.8475 | 0.4976 | 8.9303 |

Descriptive only. **Zero trades in V3 does not mean zero risk or profitability.** No variant is best/worst.

`NFM_SENSITIVITY_STATUS = COMPLETED`.

## 7. Baseline vs Sensitivity V0 reconciliation
| | Engine | Trades | Net |
|---|---|---|---|
| Official baseline | PartialExitBacktestEngine | 17 | -1107.07 |
| Sensitivity V0 | BacktestEngine (single-TP) | 17 | -796.72 |
| Difference | | 0 | **-310.35** |

Trade count MATCH; setup/entry set MATCH (identical entries). Root cause: **different exit semantics** — partial TP1/TP2/TP3 scale-out + remainder SL vs single TP at TP1. Per-trade reconciliation summed exactly to the -310.35 difference.

`RECONCILIATION_STATUS = EXPLAINED`; `CORRECTNESS_STATUS = NO_DEFECT`; `CODE_CHANGE_REQUIRED = NO`.

## 8. No-lookahead / causality (verified)
`event.time <= candle.closeTime`; derivatives `timestamp <= decision time`; no future OI/funding; no future event leakage; half-open window slicing `[start,end)`; corrected reaction indexing; no candle-0 fallback; next-candle-open entries where configured; same-bar `SL_FIRST`; historical data only; no fabricated liquidation; deterministic setup/event attribution.

`NO_LOOKAHEAD_STATUS = PASS`.

## 9. Event attribution
Captures: event ID, signal ID, event type, event stage, source tier, expected, actual, surprise, score, grade, price reaction, volume ratio, OI change, funding, liquidation, market regime, tradeability state (where available), event age, entry, SL, TP1, TP2, TP3, outcome, net PnL. UNKNOWN/null semantics preserved; missing liquidation is never replaced with zero.

## 10. Known limitations
1. Historical event coverage is partial.
2. BLS is the primary verified historical event source.
3. FOMC runtime collection was not completed.
4. Historical liquidation data is unavailable.
5. Other exact-timestamp authoritative event sources are incomplete/unavailable.
6. OOS contains only 3 trades.
7. Walk-forward windows overlap (not independent).
8. Baseline and WF/OOS/Sensitivity use different exit engines (not directly comparable).
9. Non-actionable decision-context observability remains unavailable because the existing engine returns `Optional.empty()` for those assessments.
10. Multi-window reaction analysis is unavailable (strategy uses its configured reaction window only).

## 11. Final status
```
NFM_IMPLEMENTATION_STATUS = COMPLETE_AND_FROZEN
NFM_BASELINE_STATUS       = COMPLETED
NFM_WALK_FORWARD_STATUS   = COMPLETED
NFM_OOS_STATUS            = COMPLETED
NFM_SENSITIVITY_STATUS    = COMPLETED
DATA_COVERAGE_STATUS      = DATA_COVERAGE_PARTIAL
NO_LOOKAHEAD_STATUS       = PASS
DETERMINISM_STATUS        = PASS
RECONCILIATION_STATUS     = EXPLAINED
CORRECTNESS_STATUS        = NO_DEFECT
PROFITABILITY_CLAIM       = NOT_MADE
PARAMETER_TUNING          = NOT_PERFORMED
SPOT_STRATEGIES_MODIFIED  = NO
CODE_CHANGE_REQUIRED      = NO
```

## 12. Final conclusion
No profitability verdict is offered. Factually:
- NFM V1 implementation is complete and frozen.
- Baseline, Walk-Forward, OOS and Sensitivity execution frameworks have been executed.
- No-lookahead and deterministic-execution checks passed; sensitivity determinism passed.
- The baseline vs sensitivity discrepancy was reconciled and explained by intentionally different exit-engine semantics.
- No correctness defect was found.
- Historical validation remains `DATA_COVERAGE_PARTIAL` because the event dataset is incomplete.
- OOS has only 3 trades and therefore limited statistical information.
- No parameter was selected or tuned based on validation results.
- No profitability or trading-edge claim is made.
