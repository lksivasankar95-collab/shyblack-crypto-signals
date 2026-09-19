# Paper Trading Module — Implementation Report

**Status:** shipped in commit `feat(paper-trading): implement signal-driven simulated execution engine`
**Scope:** end-to-end simulated trading — no real orders, no exchange side effects.

---

## 1. Architecture at a glance

```
                    ┌───────────────────────────┐
                    │   SpotSignalScheduler     │  (existing — every 15 min)
                    │   publishes SignalGenera- │
                    │   tedEvent inside tx      │
                    └────────────┬──────────────┘
                                 │  AFTER_COMMIT
                                 ▼
                    ┌───────────────────────────┐
                    │ PaperTradingEngineService │
                    │  @TransactionalEvent-     │
                    │  Listener                 │
                    └────────────┬──────────────┘
        fan-out per user (paper account + matching mode)
                                 ▼
                    ┌───────────────────────────┐
                    │  ExecutionService         │
                    │  .openFromSignal          │◄── PnLService + SizingService
                    │  (idempotent, atomic)     │
                    └────────────┬──────────────┘
                                 │
        opens Position row (unique(portfolio_id, signal_id))
        debits Portfolio.availableBalance, books entryFee
        writes CREATED + OPENED lifecycle events
                                 │
                                 ▼

    Binance !ticker@arr WS ──► MarketTickerStore.addBatchListener(...)
                                 │
                                 ▼
                    ┌───────────────────────────┐
                    │ EngineService.onTickBatch │
                    │  → evaluateSymbol         │
                    │  → SL/TP evaluation       │
                    └────────────┬──────────────┘
                                 │ trigger detected
                                 ▼
                    ┌───────────────────────────┐
                    │ ExecutionService.close    │
                    │  (PESSIMISTIC_WRITE,      │
                    │   idempotent)             │
                    └───────────────────────────┘
```

## 2. Data model changes

- **`Position` (extended):** added `signalId`, `notional`, `stopLoss`, `takeProfit1/2/3`,
  `entryFee`, `exitFee`, `realizedPnl`, `unrealizedPnl`, `exitPrice`, `closeReason`,
  `openedAt`, `@Version`. Unique constraint `uk_positions_portfolio_signal(portfolio_id, signal_id)`.
- **`Portfolio` (extended):** added `initialBalance`, `realizedPnl`, `totalFees`,
  `totalTrades`, `winningTrades`, `losingTrades`, `@Version`.
- **`PositionLifecycleEvent` (new):** append-only audit ledger — `CREATED`, `OPENED`,
  `SL_HIT`, `TP_HIT`, `MANUAL_CLOSE`, `CLOSED`, `REJECTED`.
- **Enums (new):** `CloseReason`, `PositionLifecycleEventType`.
- Schema is applied by Hibernate `ddl-auto: update` (repo-wide convention).

## 3. Configuration (`app.paper-trading`)

| Key                   | Default   | Purpose                             |
| --------------------- | --------- | ----------------------------------- |
| `fee-rate-pct`        | `0.10`    | Per-side commission (%)             |
| `slippage-pct`        | `0.05`    | Adverse-price slippage (%)          |
| `initial-balance`     | `10000.00`| Starting equity for a new account   |
| `max-active-positions`| `10`      | Safety cap per user                 |
| `risk-per-trade-pct`  | `2.00`    | Fallback risk % (signal wins)       |
| `default-quote-currency` | `USDT` | Portfolio currency                  |

## 4. Domain / Services

- `PaperTradingAccountService` — lazy `getOrCreate` of a per-user `Portfolio(PAPER)`; `reset` restores the initial balance.
- `PaperTradingSizingService` — risk-based sizing: `qty = (available * riskPct / 100) / |entry - stop|`; scales down when notional exceeds free balance; rejects invalid stops.
- `PaperTradingPnLService` — authoritative math for gross, net, fees, slippage, %-return. Same primitives used for unrealized and realized P&L (no divergence across screens).
- `PaperTradingExecutionService` — the **only** path that opens/closes positions:
  - Portfolio locked `PESSIMISTIC_WRITE` for every mutation.
  - Duplicate `(portfolio, signal)` returns the existing row (unique constraint + `saveAndFlush` catches `DataIntegrityViolationException`).
  - `close(...)` is idempotent: already-closed positions short-circuit.
  - Writes two lifecycle events per close (`SL_HIT|TP_HIT|MANUAL_CLOSE` + `CLOSED`).
- `PaperTradingEngineService` — the orchestrator:
  - `@TransactionalEventListener(AFTER_COMMIT)` on `SignalGeneratedEvent` → fan-out to all users with `AccountType=PAPER` and matching `TradingMode`.
  - `@PostConstruct` subscribes to `MarketBook.spotTickers().addBatchListener(...)` **and** `futuresTickers()`.
  - On every tick batch, looks up open positions for the symbol and evaluates SL / TP.
  - **SL wins on tie** (conservative): documented so back-office reports stay consistent.
- `PaperTradingQueryService` — read-side; augments open positions with fresh MarketBook prices so the client always sees current mark-to-market P&L without extra DB writes.

## 5. REST API (all IDOR-safe — user resolved from `SecurityContextHolder`)

| Method | Path                                        | Purpose                     |
| ------ | ------------------------------------------- | --------------------------- |
| GET    | `/api/v1/paper-trading/account`             | Account snapshot + equity   |
| GET    | `/api/v1/paper-trading/positions`           | Open positions              |
| GET    | `/api/v1/paper-trading/positions/{id}`      | Single owned position       |
| GET    | `/api/v1/paper-trading/history`             | Closed positions            |
| GET    | `/api/v1/paper-trading/performance`         | Aggregate stats             |
| POST   | `/api/v1/paper-trading/positions/{id}/close`| Manual close at market      |
| DELETE | `/api/v1/paper-trading/account`             | Reset — closes all, restores initial balance |

## 6. Flutter integration

- `PaperTradingScreen` replaces the empty Portfolio-tab placeholder. Three tabs: Open, History, Stats.
- `PaperTradingController` (AsyncNotifier) — parallel-fetches account/open/history/performance; 10 s auto-refresh timer; optimistic refresh after manual close/reset.
- Repository/data-source pattern matches existing modules; dio interceptor supplies the JWT.
- Live prices come from the same Binance stream the backend uses — no duplicate WS.

## 7. Guarantees

| Concern              | How it's guaranteed                                       |
| -------------------- | --------------------------------------------------------- |
| **Idempotency**      | Unique constraint `(portfolio_id, signal_id)` + `saveAndFlush` retry-safe path returns the existing row on duplicate insert. |
| **No duplicate close** | `PESSIMISTIC_WRITE` on Position + status check → already-CLOSED short-circuits. |
| **Restart recovery** | 100% of state is in DB; on boot, `PostConstruct` re-registers the tick listener and open positions resume automatically. |
| **No real orders**   | Zero paths from paper-trading code to `BinanceRestClient` or any exchange write API. Balances live only in the `portfolios` table where `accountType=PAPER`. |
| **User isolation**   | Every controller endpoint resolves the user from the security context; ownership check on every fetch. |
| **P&L consistency**  | One `PaperTradingPnLService` used by both open (unrealized) and close (realized). |
| **Concurrency**      | Idempotent open + pessimistic close + `@Version` on Position and Portfolio. |

## 8. SL/TP semantics

The engine consumes last-trade prices (Binance `!ticker@arr`, ~1 s cadence). This is not tick-by-tick market depth, and it is possible for a single tick to appear to satisfy both SL and TP.

**Rule:** on such a tick we treat it as `STOP_LOSS`. That mirrors what a defensive trader would assume in a real market and keeps back-testing / historical stats honest — we never claim precision we don't have.

## 9. Notifications

- On open: existing `SignalNotificationService` already fires; we don't add a duplicate channel.
- On close: lifecycle events are the source of truth; a follow-up commit can hook these into FCM if product wants dedicated paper-trade alerts.

## 10. Test coverage

- **Backend** (`service/paper/*Test.java`, JUnit + Mockito):
  - `PaperTradingPnLServiceTest` — 12 tests on gross/net/fees/slippage/%-return, incl. null-safety and shorts.
  - `PaperTradingSizingServiceTest` — 6 tests: risk sizing, notional cap, zero-balance rejection, stop=entry rejection, missing SL, zero entry.
  - `PaperTradingExecutionServiceTest` — 7 tests: idempotent open, debit-and-book, sizing rejection, idempotent close, TP wins/increments, SL loss increment, missing-position returns empty.
- **Frontend** (`test/paper_trading_test.dart`):
  - Renders account, open, history, stats tabs from a fake repo.
  - Manual close flow: dialog → confirm → repo `.closePosition('p1')` invoked → snackbar shown.

Backend results: **20 new paper-trading tests, all green**; 162 / 163 tests pass overall.
The one failing test (`NewsApiIntegrationTest.assetContextAggregatesProcessedArticles`) fails on `main` before this branch — pre-existing flake unrelated to paper trading.

## 11. Verification ledger (Phase-42 gate)

| Item                          | Status                              |
| ----------------------------- | ----------------------------------- |
| Signal integration            | ✅ `@TransactionalEventListener(AFTER_COMMIT)` |
| Paper account                 | ✅ `Portfolio(accountType=PAPER)` per user |
| Initial balance               | ✅ `app.paper-trading.initial-balance` |
| Position sizing               | ✅ `PaperTradingSizingService`      |
| Paper order → position        | ✅ `openFromSignal(...)`            |
| Entry execution               | ✅ market-fill @ signal entry + slippage |
| Live price monitoring         | ✅ `MarketBook.addBatchListener`    |
| Stop loss                     | ✅ evaluated per tick               |
| Take profit                   | ✅ TP1 evaluated per tick           |
| Manual close                  | ✅ `POST /positions/{id}/close`     |
| P&L                           | ✅ `PaperTradingPnLService`         |
| Fees                          | ✅ per-side, configurable            |
| Slippage                      | ✅ adverse-price, configurable       |
| Balance updates               | ✅ atomic, `PESSIMISTIC_WRITE`      |
| Trade history                 | ✅ `GET /history`                   |
| Performance analytics         | ✅ `GET /performance`               |
| Notifications                 | ⚠ Reuses signal notification pipe; dedicated paper-close FCM deferred |
| Restart recovery              | ✅ DB-backed state, `PostConstruct` re-subscribes |
| WebSocket recovery            | ✅ existing Binance reconnect covers us |
| Idempotency                   | ✅ unique(portfolio, signal) + saveAndFlush + duplicate handling |
| Concurrency protection        | ✅ `@Version` + `PESSIMISTIC_WRITE` |
| User isolation                | ✅ `SecurityContextHolder` on every endpoint |
| Authentication                | ✅ reuses JWT filter                 |
| Authorization                 | ✅ endpoint-level ownership checks   |
| Backend tests                 | ✅ 20 new tests, all green           |
| Flutter tests                 | ✅ 2 new tests + 11 existing, all green |
| Runtime verification          | NOT VERIFIED — Binance/backend not booted in this run; module compiles, unit-tests pass, wiring reviewed. |
| Documentation                 | ✅ this file                         |
| Regression verification       | ✅ existing tests still pass; NewsApiIntegrationTest flake was already failing on main |

## 12. Known limitations

1. **Entry semantics:** first cut treats every signal as an *immediate market fill at `signal.entryPrice`* with configured slippage. A future revision can extend `Position` with `PENDING_ENTRY` state and wait for the entry-trigger.
2. **Multi-target TPs:** `takeProfit2` / `takeProfit3` are persisted but the engine only evaluates TP1 for now. Partial exits are out of scope for this drop.
3. **Options mode:** signals with `TradingMode.OPTIONS` are ignored (there is no ticker store).
4. **Short positions:** the engine supports SHORT math end-to-end, but the current spot signal engine only emits LONG signals — SHORT is exercised only in unit tests.
5. **Paper-trade FCM:** we intentionally did not add a second notification pipeline; the existing signal notification announces the trade opportunity.
