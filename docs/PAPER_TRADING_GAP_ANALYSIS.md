# Paper Trading — Gap Analysis

**Date:** 2026-09-25  
**Scope:** Full paper trading module — backend + frontend — against the expected production-grade architecture.

---

## Executive Summary

The paper trading module is structurally sound: signal fan-out, SL/TP tick evaluation, PnL accounting, fee simulation, slippage, concurrency protection, and the REST API are all implemented and tested. However, four gaps were found that require fixes before the module can be called complete.

| # | Component | Classification | Severity |
|---|-----------|---------------|----------|
| 1 | `Position` entity missing `strategyId` / `strategyVersion` | MISSING | HIGH |
| 2 | `openPositionsForSymbol()` queries ALL open positions including LIVE | BUG | HIGH |
| 3 | `@Transactional` self-call on `openPositionsForSymbol()` is a no-op | BUG | MEDIUM |
| 4 | `PaperPositionResponse` missing `strategyId` / `strategyVersion` | MISSING | HIGH |
| 5 | Flutter `PaperPosition` entity / model missing `strategyId` / `strategyVersion` | MISSING | HIGH |
| 6 | No tests for SHORT side signal → position open flow | MISSING | MEDIUM |

Items deliberately deferred (already in project deferred list, not gaps):
- TP2/TP3 partial-close simulation — paper trading closes fully at TP1 by design.
- FUTURES leverage/margin simulation — `margin` and `liquidationPrice` fields exist on Position but are not populated; leverage simulation is a deferred enhancement.

---

## Component-by-Component Audit

### 1. `Position` Entity — PARTIAL

**Status:** Core fields complete. Strategy traceability fields missing.

**What exists:** symbol, side, size, notional, entryPrice, currentPrice, exitPrice, stopLoss, takeProfit1/2/3, entryFee, exitFee, realizedPnl, unrealizedPnl, status, closeReason, openedAt, closedAt, @Version, unique(portfolio_id, signal_id).

**Missing:** `strategyId` (UUID) and `strategyVersion` (Integer). These fields were added to the `Signal` entity in the Strategy Builder task and are stamped by both `SpotSignalScheduler` and `FuturesSignalScheduler`. However, `PaperTradingExecutionService.openFromSignal()` never copies them to the Position.

**Impact:** Position history has no strategy reference. After a strategy is updated or replaced, it is impossible to determine which version produced historical paper trades. This breaks auditability.

**Fix:** Add `@Column private UUID strategyId` and `@Column private Integer strategyVersion` to `Position`, then copy them from the signal in `openFromSignal()`.

---

### 2. `PaperTradingEngineService.openPositionsForSymbol()` — BUG

**Status:** BROKEN — queries ALL open positions regardless of account type.

**Code path:** `onTickBatch() → evaluateSymbol() → openPositionsForSymbol()`. The last method calls `positionRepository.findByStatus(PositionStatus.OPEN)` which returns open positions from ALL account types (PAPER, LIVE, FUTURES).

**Impact:** If a live trading position is open (AccountType.LIVE), the paper trading engine would evaluate its SL/TP on every tick and potentially call `executionService.close()` on it. While live positions have their own SL/TP enforcement, this cross-contamination violates the module isolation guarantee.

**Fix:** Replace with `positionRepository.findByStatusAndPortfolio_AccountType(PositionStatus.OPEN, AccountType.PAPER)` — this method already exists in `PositionRepository`.

---

### 3. `@Transactional` Self-Call on `openPositionsForSymbol()` — BUG

**Status:** BROKEN — annotation is a dead no-op.

**Code path:** `openPositionsForSymbol()` is a `protected` method on `PaperTradingEngineService` annotated with `@Transactional(propagation = REQUIRES_NEW, readOnly = true)`. It is called from `evaluateSymbol()`, a `private` method on the same bean.

Spring AOP proxies intercept calls from *outside* the bean. A self-call (within the same object) bypasses the proxy entirely, so the `@Transactional` annotation has no effect. The method runs in whatever transaction context the caller has — which in this case is no transaction at all (the `onTickBatch` call chain is non-transactional).

**Impact:** In practice the repository call works because JPA repositories open their own transaction per method call. The annotation is simply misleading and could cause future maintenance bugs.

**Fix:** Remove the `@Transactional` annotation from `openPositionsForSymbol()` and document the repository auto-transaction.

---

### 4. `PaperPositionResponse` DTO — MISSING FIELDS

**Status:** MISSING — does not include `strategyId` or `strategyVersion`.

Once GAP 1 is fixed, the REST response must expose these fields so the Flutter client can display them.

**Fix:** Add `UUID strategyId` and `Integer strategyVersion` to the record, and update `PaperTradingController.toPositionDto()`.

---

### 5. Flutter `PaperPosition` Entity + Model — MISSING FIELDS

**Status:** MISSING — no `strategyId` or `strategyVersion` fields in the domain entity or JSON model.

**Fix:** Add nullable `String? strategyId` and `int? strategyVersion` to `PaperPosition`, and update `PaperPositionModel.fromJson()` to parse them.

---

### 6. Tests — SHORT Side Coverage — MISSING

**Status:** MISSING — all existing tests use `PositionSide.LONG`. No test covers the SHORT path.

`PaperTradingPnLService` correctly handles SHORT via the `grossPnl(SHORT, ...)` branch (tested). But `PaperTradingExecutionService` tests only exercise LONG signals — no test verifies that a SHORT signal opens correctly or that `strategyId`/`strategyVersion` are copied.

**Fix:** Add tests for SHORT signal open, and a test that verifies strategy fields are copied to the opened position.

---

## What Is Complete and Correct

| Component | Status |
|-----------|--------|
| `PaperTradingAccountService` — lazy portfolio creation, reset | COMPLETE |
| `PaperTradingPnLService` — notional, fee, slippage, grossPnl (LONG+SHORT), netPnl, pctReturn | COMPLETE |
| `PaperTradingSizingService` — risk-based qty, scale-down when notional > balance | COMPLETE |
| `PaperTradingExecutionService` — open/close idempotency, PESSIMISTIC_WRITE, lifecycle events | COMPLETE |
| `PaperTradingQueryService` — live price from MarketBook, unrealized P&L | COMPLETE |
| `PaperTradingEngineService` — fan-out by user.tradingMode, tick listener wired at startup | COMPLETE |
| `PaperTradingController` — 7 endpoints, IDOR-safe, all auth from SecurityContext | COMPLETE |
| `Portfolio` accounting — available, invested, realizedPnl, fees, win/loss counts | COMPLETE |
| Concurrency — PESSIMISTIC_WRITE on portfolio, unique(portfolio_id, signal_id) | COMPLETE |
| SL/TP evaluation — LONG (SL ≤ price, TP ≥ price), SHORT (SL ≥ price, TP ≤ price) | COMPLETE |
| Fee simulation — 0.10% per side, configurable via `PaperTradingProperties` | COMPLETE |
| Slippage — adverse direction, 0.05%, configurable | COMPLETE |
| Flutter UI — 3-tab (Open/History/Stats), account header, position cards, manual close | COMPLETE |
| Flutter provider — 10s auto-refresh, optimistic close, reset | COMPLETE |
| REST DTOs — PaperAccountResponse, PaperPerformanceResponse, PaperPositionResponse | COMPLETE (after gap fix) |
| Tests — PnL (10), Sizing (5), Execution (6 — plus gaps above) | COMPLETE (after gap fix) |

---

## Fix Plan

1. `Position.java` — add `strategyId`, `strategyVersion` columns  
2. `PaperTradingExecutionService.java` — copy strategy fields from signal at open  
3. `PaperPositionResponse.java` — add `strategyId`, `strategyVersion` to record  
4. `PaperTradingController.java` — pass new fields in `toPositionDto()`  
5. `PaperTradingEngineService.java` — use PAPER account type filter, remove dead annotation  
6. `paper_position.dart` — add `strategyId`, `strategyVersion` fields  
7. `paper_position_model.dart` — parse new fields in `fromJson()`  
8. `PaperTradingExecutionServiceTest.java` — add SHORT open test + strategyId copy test  
