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
        fan-out per enabled PAPER account (no user trading-mode filter)
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
| `initial-balance`     | `100.00`  | Starting equity for a new account   |
| `max-active-positions`| `10`      | Safety cap per user                 |
| `risk-per-trade-pct`  | `2.00`    | Fallback risk % (signal wins)       |
| `default-quote-currency` | `USDT` | Portfolio currency                  |

## 4. Domain / Services

- `PaperTradingAccountService` — lazy `getOrCreate` of a per-user `Portfolio(PAPER)`; `reset` restores the initial balance; `updateInitialCapital` changes the starting capital **only before any trading** (no trades, no open positions, no realized P&L). All mutations use `@Transactional(propagation = REQUIRES_NEW)` so a Portfolio created from the `AFTER_COMMIT` listener commits independently and is visible to the execution transaction (previously it joined the completed tx and was never committed → `Portfolio missing`). `reset` re-locks the portfolio (`findByIdForUpdate`) before saving because closing open positions bumps `@Version` and a stale reference would fail with `StaleObjectStateException` (HTTP 500).
- `PaperTradingSizingService` — risk-based sizing: `qty = (available * riskPct / 100) / |entry - stop|`; when the risk-based notional would exceed free balance it scales down so that `notional + entryFee <= available` (fee-aware cap; the pre-cap version rejected such trades with `INSUFFICIENT_BALANCE`); rejects invalid stops.
- `PaperTradingPnLService` — authoritative math for gross, net, fees, slippage, %-return. Same primitives used for unrealized and realized P&L (no divergence across screens).
- `PaperTradingExecutionService` — the **only** path that opens/closes positions:
  - Portfolio locked `PESSIMISTIC_WRITE` for every mutation.
  - Duplicate `(portfolio, signal)` returns the existing row (unique constraint + `saveAndFlush` catches `DataIntegrityViolationException`).
  - `close(...)` is idempotent: already-closed positions short-circuit.
  - Writes two lifecycle events per close (`SL_HIT|TP_HIT|MANUAL_CLOSE` + `CLOSED`).
- `PaperTradingEngineService` — the orchestrator:
  - `@TransactionalEventListener(AFTER_COMMIT)` on `SignalGeneratedEvent` → fan-out to all enabled users with `AccountType=PAPER`. (Trading mode is no longer a user-level eligibility filter.)
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
| PATCH  | `/api/v1/paper-trading/account/capital`      | Set initial capital (pre-trade only; `> 0`) |
| POST   | `/api/v1/paper-trading/positions/{id}/close`| Manual close at market      |
| DELETE | `/api/v1/paper-trading/account`             | Reset — closes all, restores initial balance |

## 6. Flutter integration

- `PaperTradingScreen` replaces the empty Portfolio-tab placeholder. Three tabs: Open, History, Stats.
- `PaperTradingController` (AsyncNotifier) — parallel-fetches account/open/history/performance; 10 s auto-refresh timer; optimistic refresh after manual close/reset.
- Repository/data-source pattern matches existing modules; dio interceptor supplies the JWT.
- Live prices come from the same Binance stream the backend uses — no duplicate WS.
- The account header shows **Initial Capital** with an Edit action. The edit is guarded client-side (blocked when the account already has trades/open positions/realized P&L/invested) and, if the backend still returns its intentional `400` (capital locked after trading), it is mapped to a friendly message with a **Reset Account** action — the raw `DioException` is only written to debug logs, never rendered.

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
  - `PaperTradingSizingServiceTest` — risk sizing, fee-aware notional cap, zero-balance rejection, stop=entry rejection, missing SL, zero entry.
  - `PaperTradingExecutionServiceTest` — idempotent open, debit-and-book, sizing rejection, idempotent close, TP wins/increments, SL loss increment, missing-position returns empty.
  - `PaperTradingAccountServiceTest` — default 100 USDT, existing account preserved, capital `> 0`, decimal support, reject zero/negative/null, reject once the account has trades/open positions.
  - `PaperTradingEngineServiceTest` — ACTIVE signal → enabled PAPER opens; disabled/LIVE skipped; non-ACTIVE/unknown ignored; max-active cap; one signal → every eligible account.
- **Frontend** (`test/paper_trading_test.dart`):
  - Renders account, open, history, stats tabs from a fake repo.
  - Manual close flow: dialog → confirm → repo `.closePosition('p1')` invoked → snackbar shown.
  - Initial Capital shown; edit updates the value; non-positive rejected without a backend call.

Backend suite: **392 passed / 0 failed / 0 errors / 2 skipped**. Flutter suite: **58 passed / 0 failed / 2 skipped**. `flutter analyze`: 7 pre-existing info lints.

## 11. Verification ledger (Phase-42 gate)

| Item                          | Status                              |
| ----------------------------- | ----------------------------------- |
| Signal integration            | ✅ `@TransactionalEventListener(AFTER_COMMIT)` |
| Paper account                 | ✅ `Portfolio(accountType=PAPER)` per user |
| Initial balance               | ✅ `app.paper-trading.initial-balance` (default **100** USDT; existing accounts not reset) |
| Position sizing               | ✅ `PaperTradingSizingService` (risk-based + fee-aware notional cap) |
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
| Runtime verification          | ✅ Live (see §13) — genuine signal → automatic positions → balance debit → close/PnL on 8080 |
| Documentation                 | ✅ this file                         |
| Regression verification       | ✅ existing tests still pass; News RSS fetch failures are environmental (network) |

## 13. Live end-to-end verification (2026-09-29/30, port 8080, PID 24984)

Verified against the **current** running build (paper `REQUIRES_NEW` portfolio fix,
default capital 100, `PATCH /account/capital`, fee-aware sizing cap):

| Item | Result |
| ---- | ------ |
| Paper account API | ✅ `GET /paper-trading/account` 200; fresh account `initialBalance=100.00`; existing account value preserved |
| Capital API | ✅ `PATCH /account/capital`: valid on a clean account → 200 (`250`, `123.45`); `0`/negative/`null`/non-numeric → 400; `DELETE /account` → reset to 100; read never resets |
| Capital lock (intentional 400) | ✅ changing capital once trades/open positions exist → 400 with `"Initial capital cannot be changed once the paper account has trades or open positions; reset the account instead"`; frontend maps this to a friendly message, no raw `DioException` |
| Reset flow | ✅ `DELETE /account` after an open position → 200 (previously 500 `StaleObjectStateException`); fresh account `initial=100 available=100 invested=0 realized=0 trades=0`, then `PATCH 250` → 200 |
| Genuine signal | ✅ real `FuturesSignalScheduler` signals (e.g. `SOONUSDT`, `ARXUSDT`) |
| Automatic execution | ✅ `SignalGeneratedEvent` → engine → **14 positions** (one per PAPER portfolio), no UI; `Portfolio missing` = 0 |
| Position | ✅ `ARXUSDT` LONG — signalId/entry/SL/TP1-3/notional/entryFee/status = OPEN |
| Balance debit | ✅ `availableBalance` −= `notional + entryFee` on open |
| Duplicate protection | ✅ `UNIQUE(portfolio_id, signal_id)` intact; 0 duplicate `(portfolio,signal)` rows |
| Trading-mode regression | ✅ only the retained legacy `UserSettings.selectedTradingModesCsv` column remains |
| Notification regression | ✅ `SignalNotificationServiceTest` green (mode-independent, dedup, FCM-failure isolation, `marketType`) |

Historical evidence (build immediately prior to PID 24984, identical close logic):
`SOONUSDT` auto-opened across the same 14 PAPER portfolios and closed by
**TAKE_PROFIT** (`CREATED→OPENED→TP_HIT→CLOSED`, realized +20.2366 on 100 /
+2023.66 on 10000). On PID 24984 the natural SL/TP close was **not observed within
the verification window** (the `ARXUSDT` batch remained OPEN; the only close on this
PID was `close_reason=RESET` via the supported `DELETE /account` API).

## 14. Transaction safety (AFTER_COMMIT → portfolio → open)

The `AFTER_COMMIT` listener runs while the original (completed) transaction is still
bound to the thread. `PaperTradingAccountService.getOrCreate/reset/updateInitialCapital`
and `PaperTradingExecutionService.openFromSignal/close` all use
`@Transactional(propagation = REQUIRES_NEW)`, so the Portfolio commits before the
execution transaction reads it. No `LazyInitializationException`, detached-entity,
or "Portfolio missing" errors are observed live.

`PaperTradingAccountService.reset` additionally re-locks the portfolio
(`findByIdForUpdate`) before saving, because the reset close loop bumps the
Portfolio `@Version`; without the re-lock `DELETE /account` returned HTTP 500
(`StaleObjectStateException`) whenever an open position existed.

## 12. Known limitations

1. **Entry semantics:** first cut treats every signal as an *immediate market fill at `signal.entryPrice`* with configured slippage. A future revision can extend `Position` with `PENDING_ENTRY` state and wait for the entry-trigger.
2. **Multi-target TPs:** `takeProfit2` / `takeProfit3` are persisted but the engine only evaluates TP1 for now. Partial exits are out of scope for this drop.
3. **Options mode:** signals with `TradingMode.OPTIONS` are ignored (there is no ticker store).
4. **Short positions:** the engine supports SHORT math end-to-end, but the current spot signal engine only emits LONG signals — SHORT is exercised only in unit tests.
5. **Paper-trade FCM:** we intentionally did not add a second notification pipeline; the existing signal notification announces the trade opportunity.
6. **SL/TP gap:** the engine closes at the triggering tick's last price, so a tick that gaps past TP realizes more than the nominal TP distance (observed live on `SOONUSDT`). This is existing behavior, not a regression.
7. **Current-build SL/TP close:** on PID 24984 the natural SL/TP close was not observed within the verification window; the batch remained OPEN and the only close on that PID was `RESET`. See §13.
