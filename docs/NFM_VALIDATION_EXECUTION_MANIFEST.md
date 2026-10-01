# NFM Validation Execution Manifest

Deterministic execution pack for NFM_FUTURES historical validation. Frozen
inputs only — no randomization, no optimization, no winner selection.

## STRATEGY
`NFM_FUTURES` / `NFM_FUTURES_V1` (frozen `NfmFuturesConfig.defaults()`; params JSON may be supplied, never mutated silently).

## SYMBOLS
`BTCUSDT`, `ETHUSDT` (executed per-symbol; one immutable run per symbol per run type).

## MARKET DATA RANGE
`2023-09-01T00:00:00Z` → `2026-09-30T23:59:59Z` UTC. Timeframes 1m/5m/15m/1h/4h.

## EVENT DATA
113 EXACT BLS events (CPI 34, NFP 46, PPI 33), 226 BTC/ETH mappings. Fed/FOMC adapter implemented; real collection RUNTIME_PENDING.

## DERIVATIVES
OI 5m (as-of), Funding 8h through 2026-08-31 (as-of), Liquidation **UNKNOWN** (stays null).

## RUN TYPES
`BASELINE` (PartialExitBacktestEngine) · `WALK_FORWARD` (per-window) · `OOS` (single held-out) · `SENSITIVITY` (explicit variants).

## FEE MODEL
Existing engine model. Default `feePct = 0.05` unless overridden in the request.

## SLIPPAGE MODEL
Existing engine model. Default `slippagePct = 0.03` unless overridden.

## EXECUTION MODEL
`NEXT_CANDLE_OPEN` (existing default).

## PARTIAL EXIT
Existing TP1/TP2/TP3 configuration (1.5R/2.5R/4.0R); quantities conserved.

## NO-LOOKAHEAD
`event.time <= candle.closeTime`; derivatives strictly as-of; entries next-candle open; SL-first same candle.

## SAME-CANDLE POLICY
`SL_FIRST` (existing default).

## DATASET VERSIONS
Market `NFM_RESEARCH_2023_09_2026_09_V1` · Event `NFM_EVENTS_2023_09_2026_09_V1` · Derivatives `NFM_DERIVATIVES_2023_09_2026_09_V1` (overridable per request; recorded verbatim).

## CONFIGURATION HASH
SHA-256 over the canonical `BacktestConfig` (`configurationHash`), deterministic and immutable. `runId = UUID(hash + "|" + runType)`.

## RUNTIME PREREQUISITES
Externally-managed backend on the current jar. The execution endpoint NEVER starts/stops/restarts the JVM. If the runtime is stale, runs report blocked statuses and are not retried.

## DATA-QUALITY GATES
`ValidationDataQualityGate` runs first. Hard failure ⇒ `DATA_QUALITY_BLOCKED` (no execution). Empty events ⇒ `DATA_COVERAGE_PARTIAL` (no fabricated events).

## BLOCKED STATUSES
`DATA_QUALITY_BLOCKED · DATA_COVERAGE_BLOCKED · DATA_COVERAGE_PARTIAL · RUNTIME_BLOCKED · NOT_EXECUTED`. Framework tests passing is NOT a completed validation.

## UNKNOWN HANDLING
Missing OI/funding/liquidation/events stay NULL/UNKNOWN, never 0.

## EXECUTION ENTRY
`POST /api/v1/admin/research/nfm-validation/execute` (ADMIN, read-only w.r.t. infra):

```json
{ "runType": "BASELINE", "symbols": ["BTCUSDT","ETHUSDT"],
  "start": "2023-09-01T00:00:00Z", "end": "2026-09-30T23:59:59Z" }
```
Returns one outcome per symbol: `runId`, `executionStatus`, `dataQuality`, `configurationHash`, `tradeCount`, `duplicate`. Identical requests collapse to the same run id (duplicate write refused).

## EXECUTION STATUS
`READY` (request valid, data/runtime available) is implied by a non-blocked result; `RUNNING` is not exposed because execution is synchronous; `COMPLETED` means the engine actually ran on real data; blocked states above otherwise.
