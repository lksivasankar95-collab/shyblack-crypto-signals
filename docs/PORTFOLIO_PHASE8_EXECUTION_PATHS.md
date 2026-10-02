# Phase 8 — Pre-Implementation Execution Path Audit

Written before any Phase 8 code. The purpose is to record what the execution
architecture actually is, so the router is built on facts rather than on the
assumption that the code is safe because it exists.

Paths are relative to `backend/src/main/java/com/shyblack/cryptosignals/`.

---

## 1. The event

`service/SignalGeneratedEvent.java` — a plain POJO, **not** an `ApplicationEvent`.

```java
public class SignalGeneratedEvent {
    private final UUID signalId;
    public SignalGeneratedEvent(UUID signalId) { this.signalId = signalId; }
    public UUID getSignalId() { return signalId; }
}
```

It carries one field. Every consumer re-reads the `Signal` from `SignalRepository`.

### Publishers (5)

| File | Line | Enclosing method | Guard |
|---|---|---|---|
| `service/SpotSignalScheduler` | 118 | `runSignalCycle()` | grade BUY / STRONG_BUY |
| `service/FuturesSignalScheduler` | 89 | `runSignalCycle()` | grade BUY / STRONG_BUY |
| `service/TrendPullbackSignalService` | 111 | `runCycle(...)` | none (builds actionable only) |
| `service/EmaTrendFollowingSignalService` | 102 | `runCycle(...)` | none (builds actionable only) |
| `service/nfm/NfmFuturesSignalService` | 168 | `processEventSymbol(...)` | grade BUY / STRONG_BUY |

All five publish inside an open `@Transactional` method, so `AFTER_COMMIT`
listeners run after the signal row is durable.

### Consumers (4, all `@TransactionalEventListener(AFTER_COMMIT)`)

| # | Class | Method | Mode gate | `autoExecute` gate |
|---|---|---|---|---|
| 1 | `service/paper/PaperTradingEngineService` | `onSignalGenerated` (:69) | **none** | **none** |
| 2 | `service/live/LiveTradingEngineService` | `onSignalGenerated` (:66) | `!= SPOT → return` (:91) | `!autoExecute → return` (:67) |
| 3 | `service/futures/FuturesEngineService` | `onSignalGenerated` (:79) | `!= FUTURES → return` (:85) | `!autoExecute → return` (:80) |
| 4 | `service/SignalNotificationService` | `onSignalGeneratedEvent` (:111) | BUY / STRONG_BUY only | n/a |

None uses `fallbackExecution = true`, so an event published outside a transaction
is silently dropped.

---

## 2. Existing execution paths

| Path | Entry point | Fan-out | Order submitter |
|---|---|---|---|
| Paper | `PaperTradingEngineService.onSignalGenerated` → `fanOutForSignal` → `openForUser` | every `User` with `accountType == PAPER` | none (local) |
| Live Spot | `LiveTradingEngineService.onSignalGenerated` → `processForAccount` | `findByEnabledTrueAndKillSwitchActiveFalse()` | `LiveTradingExecutionService.submit` → `ExchangeTradingAdapter.placeOrder` |
| Live Futures | `FuturesEngineService.onSignalGenerated` → `processForAccount` | `findByEnabledTrueAndKillSwitchActiveFalse()` | `FuturesExecutionService.submit` → `FuturesExchangeAdapter.placeOrder` |

### Structural facts that constrain the router

1. **Dispatch is a flat 4-way Spring multicaster fan-out.** There is no routing
   decision object anywhere; each listener independently re-reads the signal and
   re-applies its own gates.
2. **`Signal` has no user id and no target account.** Routing is inherently a
   fan-out over accounts, not a 1:1 dispatch. The router must own that fan-out.
3. **`PaperLiveIsolationTest` (`src/test/.../isolation/`) scans the raw text of
   `service/paper`, `service/live`, `service/futures`, `service/backtest` for
   forbidden package substrings — including inside comments and Javadoc.**
   A new package `service/execution/` is **not** scanned, so it is the only legal
   home for a component that references all three engines. Putting the router in
   `service/live` would fail the build immediately.
4. **Paper has no `autoExecute` gate and no kill switch.** Paper is expected to
   always execute. Adding a LIVE-style gate to paper would change paper
   semantics, which is out of scope for Phase 8.

---

## 3. Safety mechanisms that already exist

These are to be **reused, not reimplemented**.

### Kill switch — two independent per-account booleans

There is no global kill switch and no `KillSwitch` class.

- `LiveTradingAccount.killSwitchActive` (boolean, default `false`)
  set by `LiveTradingAccountService.triggerKillSwitch/releaseKillSwitch`
  exposed at `POST|DELETE /api/v1/live-trading/kill-switch`
  read by `LiveTradingRiskService:47`, `LiveTradingAccountRepository:21`,
  `LiveTradingEngineService:76`, `LiveTradingReconciliationService:66`
- `FuturesTradingAccount.killSwitchActive` — the futures equivalent, read by
  `FuturesRiskService:47`, `FuturesTradingAccountRepository:21`,
  `FuturesEngineService:87`, `FuturesReconciliationService:60`

### Account activation

Four independent gates, all of which must pass:

1. `account.enabled` (master gate, default `false`)
2. `account.connectionStatus == CONNECTED`
3. `account.acknowledged` (futures only, default `false`)
4. `UserSettings.liveTradingAllowed` (default `false`)

### Auto execution

| Property | Declared | Default |
|---|---|---|
| `app.live-trading.auto-execute` | `config/LiveTradingProperties:34` | `false` (`application.yml:142`) |
| `app.futures-trading.auto-execute` | `config/FuturesTradingProperties:29` | `false` (`application.yml:128`) |

Both properties lack a fallback default in their compact constructors, so the
effective default comes only from YAML. Neither has a constructor-level
default, which means a missing YAML key would bind to `false` — the safe
direction, but it is an implicit rather than an explicit guarantee.

`LiveTradingEngineService:67` and `FuturesEngineService:80` are the only readers.
**There is currently no test asserting `autoExecute == false` blocks execution.**

### Risk services

| Service | Method | Notes |
|---|---|---|
| `service/live/LiveTradingRiskService` | `check(user, account, signal)`, `hasOpenEntry(account, symbol)`, `dailyLossExceeded` | SPOT only, returns `LiveTradingRiskReason`, never throws |
| `service/futures/FuturesRiskService` | `check(user, account, signal)`, `checkLeverage(account, requested)` | returns `FuturesRiskReason` |

Both already check kill switch, `enabled`, `connectionStatus`,
`liveTradingAllowed`, duplicate entry, max positions and daily loss. The router
must call them, not reimplement their rules.

Dead reason constants exist in both enums (`CREDENTIALS_INVALID`,
`DUPLICATE_SIGNAL`, `SIGNAL_EXPIRED`, `MARGIN_INSUFFICIENT`,
`SYMBOL_NOT_SUPPORTED`, `LIQUIDATION_RISK`, and others). They are never returned
today.

---

## 4. Order lifecycle

`LiveOrderStatus` and `FuturesOrderStatus` are **identical constant-for-constant**:
`CREATED, SUBMITTING, SUBMITTED, ACKNOWLEDGED, PARTIALLY_FILLED, FILLED,
CANCEL_REQUESTED, CANCELLED, REJECTED, EXPIRED, FAILED, UNKNOWN, RECONCILING`.

This already models the required lifecycle; Phase 8 reuses it rather than
inventing a parallel enum.

**Is a submitted order ever treated as filled?**

- Real spot adapter: **no.** `mapStatus` maps `NEW → ACKNOWLEDGED`,
  `PARTIALLY_FILLED → PARTIALLY_FILLED`, `FILLED → FILLED`, unknown → `UNKNOWN`.
  `newOrderRespType=FULL` is requested, so real fills are returned.
- Real futures adapter: **no.** `NEW → ACKNOWLEDGED`. Note it requests
  `newOrderRespType=RESULT`, which returns no `fills[]`, so futures fees are
  always recorded as zero against real Binance.
- `MockExchangeTradingAdapter:143` / `MockFuturesExchangeAdapter:139`:
  `MARKET → FILLED` synchronously, using the caller-supplied reference price.
  This is a **simulator** behaviour, not an exchange claim, and MOCK is the
  default adapter mode.

`LiveTradingEngineService:128` acts on `FILLED || PARTIALLY_FILLED` only, which
is correct. The same holds for `FuturesEngineService:160`.

### Re-entrancy gap

`LiveTradingExecutionService.submit` and `FuturesExecutionService.submit`
**always** call `adapter.placeOrder`. `markSubmitting` advances
`CREATED → SUBMITTING` only, so a second `submit` on the same intent re-issues
the exchange call rather than short-circuiting. Duplicate protection currently
rests on the deterministic `clientOrderId` plus the exchange/mock rejecting or
caching a repeat — not on the service itself.

---

## 5. Idempotency inventory (all pre-existing, all to be reused)

| Mechanism | Location |
|---|---|
| Deterministic spot `clientOrderId` — SHA-256 of `userId\|signalId\|symbol\|side\|purpose`, prefixed `SB-` | `exchange/ClientOrderIdGenerator` |
| Deterministic futures `clientOrderId` — SHA-256 of `F\|userId\|signalId\|symbol\|positionSide\|purpose`, prefixed `SBF-` | `service/futures/FuturesClientOrderIdGenerator` |
| `UNIQUE (account_id, client_order_id)` | `LiveOrder`, `FuturesOrder` |
| `UNIQUE (portfolio_id, signal_id)` | `Position` (paper) |
| Pre-check + `catch (DataIntegrityViolationException)` | both execution services, and `PaperTradingExecutionService:108` |
| `PESSIMISTIC_WRITE` row locks | all order/position/account repositories expose `findByIdForUpdate` |
| Optimistic `@Version` | all execution entities extend `BaseEntity` |
| Open-entry dedup | `LiveTradingRiskService.hasOpenEntry`, `FuturesRiskService:72-83` |
| Stream idempotency | `LiveUserStreamManager.start` |

**Not found anywhere:** an `ExecutionAttempt` entity, an idempotency-key HTTP
header, a signal→execution ledger, or any dead-letter store. The router-level
"has this signal already been routed to this account?" question has **no**
existing answer above the order layer.

`AccountType` (`LIVE`/`PAPER`) and `AccountCategory`
(`MAIN`/`SPOT`/`FUTURES`/`OPTIONS`) are confusingly named relative to each
other; `docs/PORTFOLIO_PHASE0_DISCOVERY.md` D1 records this. The router must use
`AccountMode`/`AccountCategory` explicitly and never `AccountType`.

---

## 6. Schema and migrations

`backend/src/main/resources/db/migration/` contains V1–V3, all Portfolio
read-model only. **No migration exists for any execution table.** Those tables
exist solely as Hibernate DDL.

`spring.flyway` is not configured and there is no Flyway dependency; the V1–V3
files are applied out of band. `application-prod.yml` uses
`ddl-auto: validate`, so any new column needs a matching migration or production
will refuse to start.

---

## 7. Problems found in the existing design

Recorded for the report. **None of these are fixed in Phase 8** — Phase 8's
mandate is the routing boundary, not repairing the engines.

1. **Paper executes SPOT and FUTURES signals alike.** `fanOutForSignal` never
   inspects `signal.getTradingMode()`, so a single FUTURES signal opens a paper
   position *and* a live futures order. Pre-existing behaviour, preserved.
2. **Paper tick listener is double-registered** to both the spot and futures
   ticker streams (`wireMarketListeners`), so a symbol present in both is
   evaluated twice per cycle at two different prices.
3. **`submit()` is not re-entrancy guarded** (§4 above).
4. **No protection is placed against real Binance until reconciliation runs.**
   `placeProtectiveStop` only fires on `FILLED || PARTIALLY_FILLED`; a real
   `ACKNOWLEDGED` entry is unprotected for up to the reconciliation interval, and
   there is no order-trade-update listener.
5. **Futures real adapter requests `RESULT`, not `FULL`**, so fees are always
   recorded as zero against real Binance.
6. **The `uk_futures_positions_open` unique constraint is on
   `(account_id, symbol, position_side)` with no status predicate**, despite a
   comment claiming it applies only when `status = OPEN`. A closed row therefore
   permanently blocks a new position on the same account/symbol/side.
7. **Zero test coverage** for `LiveTradingEngineService`, `FuturesEngineService`,
   both execution services, both account services, and the adapters' `placeOrder`
   paths. `autoExecute` is untested. Kill switch has 2 tests, both at the risk
   service only.

---

## 8. Design constraints for Phase 8

1. The router belongs in a **new package `service/execution/`** — the only
   package not scanned by `PaperLiveIsolationTest`.
2. The router must become the **single** `SignalGeneratedEvent` execution
   listener. The three engines keep their logic but lose their own
   `@TransactionalEventListener` annotations, otherwise adding a router creates
   a second firing path.
3. Fan-out stays account-based; the router owns it.
4. Existing risk services are called, never reimplemented.
5. Existing `clientOrderId` generators and unique constraints are the
   idempotency mechanism at the order layer. The router adds the missing
   *router-level* ledger: one authoritative decision per
   (user, signal, mode, category).
6. `app.live-trading.auto-execute` and `app.futures-trading.auto-execute` stay
   `false` by default. No new property can enable execution.
7. Paper semantics — including its lack of a kill switch — are preserved exactly.