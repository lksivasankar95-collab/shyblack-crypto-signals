# Portfolio Binance-Style Redesign — Report

Scope: redesign the Portfolio experience so it behaves like a real Binance-style
account, with **no PAPER/LIVE selector inside Portfolio**. The account mode comes
from Settings.

Status labels used throughout: **CODE COMPLETE**, **TEST COMPLETE**, **LOOPBACK
COMPLETE**, **RUNTIME VALIDATION**, **REAL BINANCE VALIDATION**.

---

## 0. Final status summary

| Dimension | Status |
|---|---|
| CODE COMPLETE | **Yes** — all phases 1–6 implemented |
| TEST COMPLETE | **Yes** — 1094 backend tests pass (0 failures, 2 pre-existing skips); 145 Flutter tests pass (2 pre-existing skips) |
| LOOPBACK COMPLETE | **Yes, unchanged** — Binance adapter tests still run against `127.0.0.1` (`FakeExchangeHttp`) |
| RUNTIME VALIDATION | **No** — no JVM was started (forbidden by the task) |
| REAL BINANCE VALIDATION | **No** — no real Binance account was contacted |

**No claim is made that "Binance Portfolio fully works".** What exists is code with
tests, proven against mocks and loopback only.

---

## 1. Current architecture discovered

### 1.1 The account-mode source of truth already existed

The task's central requirement — "reuse the existing Settings configuration, do not
create duplicate state" — was satisfiable. Settings already owned PAPER/LIVE:

| Layer | Existing type | Values |
|---|---|---|
| Flutter | `AppSettings.tradingAccount` (`domain/entities/app_settings.dart`) | `TradingAccount{paper, live}` |
| Backend | `User.accountType` (`entity/User.java`) | `AccountType{LIVE, PAPER}` |
| Write path | `SettingsController.setTradingAccount` → `PATCH /api/v1/settings {"accountType": …}` | — |

**No new mode state was created.** The Portfolio is now a pure projection of that field.

### 1.2 The duplicate state that had to go

The Portfolio had its own **independent** PAPER/LIVE selector that was deliberately
decoupled from Settings:

- `PortfolioSelection` / `portfolioSelectionProvider` (`selectMode()`)
- `PortfolioModeSelector` widget, keys `portfolio-mode-PAPER` / `portfolio-mode-LIVE`
- `PortfolioScope.initial()` hard-coded `paper` + `main`

`portfolio_controller.dart` even documented the decoupling as intentional ("deliberately
independent of `User.accountType`"). That comment is now inverted and the code reads
from Settings.

### 1.3 The backend gap that would have made a UI-only fix a lie

`PortfolioController` defaulted every request to `AccountMode.PAPER` via a private
`DEFAULT_MODE` constant and **ignored `User.accountType` entirely**. A UI-only change
would therefore have looked correct on screen while the API served paper data to a
live-selected user. This was fixed in Phase 1.

### 1.4 Existing infrastructure reused (nothing duplicated)

| Concern | Reused as-is |
|---|---|
| REST baseline sync | `LivePortfolioSyncService` |
| User-data WebSocket | `BinanceUserStreamConnector`, `UserStreamConnector`, `LiveUserStreamManager` |
| Event application / dedup / high-water mark | `LiveUserStreamEventProcessor`, `PortfolioExchangeEventApplied` |
| Reconciliation after reconnect | `LivePortfolioReconcileService` |
| Balance / position / connection snapshots | `PortfolioExchangeBalance`, `PortfolioExchangePosition`, `PortfolioAccountConnection` |
| Credential storage | `ExchangeCredential` + `ExchangeCredentialEncryptor` (single table, untouched) |
| Paper read side | `PaperTradingQueryService`, `Position` |

**No new Binance connection infrastructure, no new credential table, no duplicate
sync service.** The new Portfolio endpoints are read-through views over the existing
adapters, exactly like the Phase 7 history endpoints.

### 1.5 Pre-existing gaps found during discovery

1. No open-orders endpoint in the unified Portfolio API (adapters already supported it).
2. Income history hardcoded to `REALIZED_PNL`; `FUNDING_FEE` / `COMMISSION` / `TRANSFER`
   were fetchable but unreachable.
3. No transaction history at all; `/api/v1/transactions` is a stub returning an empty list.
4. No closed-positions view.
5. Snapshots lacked `stopPrice` and futures `avgPrice`.
6. **No production trigger for live sync**: no scheduler and no "connect credential" hook
   starts `LivePortfolioSyncService` or `LiveUserStreamManager`. This machinery is still
   only exercised by tests. **Left unchanged** — see §24.

---

## 2. Settings-driven mode implementation

### 2.1 Backend — Settings is the resolution source

`PortfolioController.resolveMode(User, AccountMode requested)`:

- an explicit `?mode=` still wins, so a diagnostic read can address a scope deliberately;
- otherwise the mode is read from `User.accountType`, which the Settings service writes;
- **there is no longer any server-side PAPER default.**

```java
private AccountMode resolveMode(User user, AccountMode requested) {
    if (requested != null) {
        return requested;
    }
    AccountType configured = user.getAccountType();
    return configured == null ? AccountMode.PAPER : AccountMode.fromAccountType(configured);
}
```

Applied to all ten endpoints. The only remaining `PAPER` fallback covers a
theoretically-null column, which the schema forbids.

### 2.2 Flutter — a projection, not a second setting

```dart
final portfolioAccountModeProvider = Provider<PortfolioMode?>((ref) {
  final settings = ref.watch(settingsControllerProvider).asData?.value;
  if (settings == null) return null;
  return settings.tradingAccount == TradingAccount.live
      ? PortfolioMode.live
      : PortfolioMode.paper;
});
```

**The deliberate omission:** when Settings has not resolved, this returns `null` and
`requireScope(ref)` throws `PortfolioAccountModeUnresolved`. **No request is issued at
all.** Defaulting to PAPER here would render a simulated balance to a user who chose
their live account — the exact failure this redesign exists to remove. The screen shows
an explicit "Account mode unavailable" panel instead.

Every Portfolio provider (`PortfolioController`, holdings, open orders, closed
positions, history, transactions, funding, sync status) goes through `requireScope`, so
no provider can accidentally request against a guessed mode.

### 2.3 Duplicate state removed

| Removed | Replaced by |
|---|---|
| `PortfolioSelection` / `portfolioSelectionProvider.selectMode()` | `portfolioAccountModeProvider` (derived from Settings) |
| `PortfolioModeSelector` widget | deleted; not replaced |
| `PortfolioScope.initial()` (hard-coded `paper`+`main`) | `portfolioScopeProvider`, null while unresolved |
| MAIN category tab | `PortfolioCategory.portfolioTabs` = `[spot, futures, options]` |

---

## 3. Portfolio navigation changes

```
┌─────────────────────────────────────────────┐
│ PORTFOLIO                    [PAPER ACCOUNT] │  ← read-only badge, NOT a control
│ [SPOT] [FUTURES] [OPTIONS]                   │  ← the only navigation
└─────────────────────────────────────────────┘
```

- **`PortfolioModeSelector` deleted.** The badge (`PortfolioAccountBadge`) names the
  account but contains no `GestureDetector` or `InkWell`, asserted by test.
- **MAIN removed from tabs.** `AccountCategory.MAIN` remains in the backend as the
  internal aggregate read-model scope; `PortfolioCategory.main` remains in the Flutter
  enum because the backend still returns it, but `portfolioTabs` excludes it.
- Spot tab renders `WALLET / ASSETS` and **no** position section. Futures tab renders
  `OPEN POSITIONS` and `CLOSED POSITIONS`.

---

## 4. Spot implementation

Spot is modelled as an **exchange wallet**, never as a leveraged position book.

| Requirement | Where |
|---|---|
| Wallet / Assets | `_HoldingsSection` → `GET /{category}/holdings` (unchanged Phase 7) |
| Available / Locked / Total | `PortfolioHolding{free, locked, total}` per asset |
| Open Orders | **new** `GET /{category}/open-orders` → `/api/v3/openOrders` |
| Order History | existing `GET /{category}/history?type=ORDER` → `/api/v3/allOrders` |
| Trade History | existing `?type=TRADE` → `/api/v3/myTrades` |
| Transaction History | **new** `GET /{category}/transaction-history` |
| Fees where available | `commission` / `commissionAsset` from `/api/v3/myTrades` |
| Sync / connection status | existing `GET /{category}/sync-status` |

**No fabricated spot positions.** LIVE SPOT positions remain `UNSUPPORTED` with
`LIVE_SPOT_NO_POSITION_MESSAGE`, and holdings are typed as holdings rather than
positions (`PortfolioHolding`, `source: EXCHANGE | LOCAL_PAPER`).

**Spot open-order field mapping** (from `ExchangeOrderSnapshot`): symbol, side, order
type, status, price, stop price, original quantity, executed quantity, remaining
quantity, order id, client order id, created/updated time. Spot publishes no average
fill price on its order endpoints, so `averageFillPrice` is **null** rather than derived
from `cummulativeQuoteQty / executedQty`.

**Spot transaction history does not exist upstream.** Binance Spot publishes no account
income endpoint, so the spot scope returns:

```
availability : UNSUPPORTED
statusMessage: "Not available: Binance Spot publishes no account income or transaction
                endpoint. Spot activity is available under order history and trade history."
```

Not an empty list, which would read as "no transactions happened".

---

## 5. Futures implementation

| Requirement | Where |
|---|---|
| Wallet Balance | `PortfolioAccountView.equity` (from `FuturesTradingAccount.walletBalance`) |
| Available Balance | `.availableBalance` |
| Used Margin | `.invested` (from `usedMargin`) |
| Unrealized P&L | `.unrealizedPnl` |
| Open Positions | existing `GET /{category}/positions` (Phase 3 snapshot) |
| Open Orders | **new** `GET /{category}/open-orders` → `/fapi/v1/openOrders` |
| Order History | existing `?type=ORDER` → `/fapi/v1/allOrders` |
| Trade History | existing `?type=TRADE` → `/fapi/v1/userTrades` |
| Transaction / Income | **new** `/transaction-history` → `/fapi/v1/income` (all types) |
| Funding Fee | **new** `/funding-fees` → `/fapi/v1/income?incomeType=FUNDING_FEE` |
| Closed Positions | **new** `GET /{category}/closed-positions` |
| Sync Status | existing `/sync-status` |

### 5.1 Open positions — no invented values

`PortfolioPositionView` is unchanged and still reports only what the exchange snapshot
actually holds. Two fields are **always null for LIVE futures** and this is deliberate
and documented in code:

- `stopLoss`, `takeProfit1..3` — the Binance position response carries none. Deriving a
  stop from the mark price would be fabrication.
- `realizedPnl` — see §6.

`PortfolioExchangePosition` normalises Binance's `liquidationPrice: "0"` sentinel to
`null`, so "no liquidation price" is never rendered as a liquidation at price 0.

`marginType` and `isolatedMargin` are stored on the snapshot but are **not** currently
projected into `PortfolioPositionView`; that pre-existing omission was left alone rather
than widened.

### 5.2 Futures order history — actual exchange state

`FuturesOrderSnapshot` gained `stopPrice` and `averageFillPrice`, both parsed from the
exchange payload. Binance reports `"0"` for a non-applicable `stopPrice` and for `avgPrice`
on an unfilled order; both zeros are normalised to `null` so "no stop" and "no fill yet"
are never shown as `0`.

`PortfolioHistoryEntry` gained `positionSide` and `orderType` so a hedge-mode row is
unambiguous and an order-type filter is real. The raw exchange status string is
preserved verbatim alongside the normalised state.

---

## 6. Closed position implementation

New service: `PortfolioClosedPositionService`. This is the highest-risk area, so the
design is stated explicitly.

### 6.1 LIVE futures — reconstruction from real fills

Algorithm, per `symbol + positionSide`, over `FuturesTradeSnapshot` records from
`/fapi/v1/userTrades`:

1. Walk fills oldest-first, tracking the running signed quantity.
2. A **session** is a maximal run of fills that starts at zero, never crosses zero, and
   returns to zero.
3. A session that returns to zero is a closed position.

Per-field provenance:

| Field | Provenance |
|---|---|
| `realizedPnl` | **Sum of the `realizedPnl` the exchange attributed to each closing fill.** Never recomputed from prices. Required only on *reducing* fills — an opening fill legitimately carries none. |
| `entryPrice` / `exitPrice` | Quantity-weighted averages of the real fills on each side of the round trip. |
| `quantity` | Largest absolute exposure held during the session (peak running), not a sum of fills. |
| `fees` | Sum of reported commissions. **null** if any fill omitted one. |
| `funding` | **Always null.** A funding-fee income record has no position attribution, so it is never assigned to a position. |
| `leverage` / `marginType` / position id | null unless a source reports them (userTrades does not). |
| `duration` | `closedAt − openedAt`, null when either timestamp is unknown. |
| `orderIds` / `tradeIds` | Distinct real ids behind the round trip. |

Three refusal cases, each covered by a test:

- **Session starts mid-position** (opening fill outside the window) → `entryPrice` is
  `null`, `duration` is `null`, and the response sets `partial = true`. Reported exit
  prices remain valid.
- **A flip past zero** (one fill both closes a long and opens a short) → the long is
  emitted as one closed round trip. The short the flip opened is emitted with an unknown
  entry price. Splitting one fill's quantity and P&L across two records would be a
  guess, so it is not done.
- **A lone reducing fill** → nothing is emitted. A single sell could be opening a short
  rather than closing a long; it is not evidence of a completed round trip.

**Direction fix:** the session's direction is the sign of the exposure it closes, not
the sign of the fill that closes it. After a flip the position is already short while the
flattening fill is a buy; taking the fill's sign mislabelled the record `LONG`. Caught by
test, fixed in `walk(...)`.

### 6.2 PAPER — the paper engine's own records

Closed `Position` rows are read directly (`entryPrice`, `averageExitPrice` preferred over
`exitPrice`, `originalQty`, `realizedPnl`, `entryFee + exitFee`, `openedAt`, `closedAt`).
`orderIds`/`tradeIds` are empty and the response says why: *"A simulated round trip has no
exchange order or trade identifiers, because no exchange order was ever placed."*

### 6.3 Refusals

| Scope | Result |
|---|---|
| LIVE SPOT | `UNSUPPORTED` — "Spot holds wallet assets rather than leveraged positions…" |
| OPTIONS (both modes) | `UNSUPPORTED` |
| MAIN (both modes) | refused — would merge spot and futures |
| LIVE, no credential | `NOT_CONNECTED` — "Simulated positions are never substituted here." |

---

## 7. Order history

- Statuses come from the exchange and are normalised through the new
  `PortfolioOrderStatus` enum.
- Supported: `NEW`, `PARTIALLY_FILLED`, `FILLED`, `CANCELED`, `REJECTED`, `EXPIRED`,
  `UNKNOWN`. Anything unrecognised → `UNKNOWN`.
- `PENDING_CANCEL` / `PENDING_CANCEL_REJECTED` map to `CANCELED`: the exchange treats the
  order as off the book, and for a read view that is the same fact.
- The raw status string is returned alongside the normalised value so an unmapped state
  stays diagnosable.

---

## 8. Trade history

Actual fills only (`/api/v3/myTrades`, `/fapi/v1/userTrades`). Fields: symbol, side,
position side (futures), price, quantity, quote quantity, commission, commission asset,
trade id, order id, time, and — for futures — the exchange's `realizedPnl`.

**No conversion of missing commission to zero.** `fee` is rendered only when non-null,
and `PortfolioValue` renders null as an em dash.

---

## 9. Transaction / funding history

`/transaction-history` reads `/fapi/v1/income` **with no `incomeType` filter**, so
`REALIZED_PNL`, `FUNDING_FEE`, `COMMISSION`, `TRANSFER` and any future exchange income
type are all returned **as themselves**. No category set is invented, and nothing is
summed across types.

`/funding-fees` reads the same endpoint with `incomeType=FUNDING_FEE`. It is a separate
view because funding is a distinct income type and is never folded into realized P&L.

Spot has no income endpoint → explicit "Not available". Paper → "A simulated account is
charged no funding fee and publishes no income records."

---

## 10. REST synchronization

Unchanged. `LivePortfolioSyncService` remains the REST baseline for balances
(`/api/v3/account`) and positions (`/fapi/v2/positionRisk`), writing
`PortfolioExchangeBalance` and `PortfolioExchangePosition`.

The new endpoints are **read-through views** over the same adapters, so they need no new
persistence, no retention policy, and no second copy of exchange truth.

## 11. WebSocket synchronization

Unchanged. `BinanceUserStreamConnector` mints listen keys (`/api/v3/userDataStream`,
`/fapi/v1/listenKey`), keeps them alive every 30 minutes, backs off exponentially to a
60 s cap, and signals a drop exactly once. Listen keys and URIs are never logged.

**The new Portfolio endpoints do not add a second socket.** They read exchange state on
request; the user stream continues to apply incremental balance and position updates to
the snapshot tables as before.

## 12. Reconciliation

Unchanged. `LiveUserStreamManager` enforces `REST reconcile → open socket → CONNECTED`
on both first start and every reconnect, and reports `ERROR` (never "assume nothing was
missed") when reconciliation fails. Counters `reconcileRunCount()` /
`reconcileFailureCount()` are retained.

Dedup (`PortfolioExchangeEventApplied` + DB unique constraint) and the high-water mark
(`PortfolioAccountConnection.lastEventAt`, older events → `STALE_REJECTED`) are reused
unchanged. `ExecutionRouterAdapterIntegrationTest` and the Phase 9 execution boundary
were not modified.

---

## 13. UNKNOWN handling

This is the invariant the whole design protects.

| Rule | Enforcement |
|---|---|
| HTTP 200 ≠ FILLED | `PortfolioOrderStatus.fromExchange` maps only a value the exchange actually reported |
| Unrecognised status → UNKNOWN | `default -> UNKNOWN` in the enum mapping; **never** widened |
| UNKNOWN never → 0 | `PortfolioValue` renders null as `—`; `PortfolioOrderStatus` has no numeric coercion |
| UNKNOWN order stays visible | `openOrders` lists UNKNOWN orders rather than hiding them |
| UNKNOWN is filterable | `status=UNKNOWN` matches undetermined records and nothing else |
| UNKNOWN ≠ open | `PortfolioOrderStatus.unknown.isOpen == false` — it must be reconciled, not assumed resting |

**On the full state list (SUBMITTED / SUBMITTING / NEW / PARTIALLY_FILLED / FILLED /
CANCELED / REJECTED / EXPIRED / UNKNOWN):** the read model carries only exchange-confirmed
states. `SUBMITTED` and `SUBMITTING` are pre-exchange intentions and remain in
`LiveOrderStatus` in the execution module, deliberately excluded from
`PortfolioOrderStatus` — publishing them from a read endpoint would let a submitted-but-
unconfirmed order look like exchange state. The system still distinguishes all nine; the
split is between "what we intend" and "what the exchange says", which is exactly the
boundary Phase 9 established.

**Phase 9 behaviour is intact.** No execution, cancellation or reconciliation code was
touched. The one test that referenced the old wording of the realized-P&L message was
updated to assert the new reason (it now points at the transaction-history endpoint).

---

## 14. Paper / Live isolation

Guaranteed structurally, not by convention:

- `PortfolioAccountReadService.getAccount` dispatches on `mode.isPaper()`; the two
  branches read disjoint sources.
- Paper holdings report `UNSUPPORTED` — a simulated account holds capital, not assets.
- Paper open orders report `UNSUPPORTED` — the paper engine opens and closes a position
  directly and works no order book.
- Paper income/funding report `UNSUPPORTED` — no funding fee is charged, no income records
  exist.
- Paper transactions/funding point at `GET /api/v1/paper-trading/history`.
- A missing LIVE credential returns `NOT_CONNECTED` and says explicitly that simulated
  data is **not** substituted.
- The Flutter screen renders a dedicated `LIVE ACCOUNT / Connection unavailable` panel and
  never falls back to paper figures.

**No paper execution logic was rewritten.** `PaperTradingEngineService`,
`PaperTradingExecutionService`, `PaperPartialExitState`, partial exits, accounting and the
position lifecycle are untouched.

## 15. Spot / Futures isolation

- `AccountCategory` is a path segment; `SPOT` and `FUTURES` requests reach disjoint
  adapter methods and disjoint snapshot tables.
- Spot assets never appear under FUTURES and vice versa (tested).
- Spot positions are `UNSUPPORTED` — no leveraged-position fabrication.
- Funding fees are futures-only; the spot scope says spot is never margined.
- Spot transaction history is `UNSUPPORTED` — no income endpoint upstream.
- `MAIN` refuses the new endpoints rather than merging wallets or order books.
- Flutter: spot renders no positions/funding section; futures renders both.

## 16. Options boundary

Options is **still** a reserved capability. It is now a *hard* boundary rather than an
empty account:

- Backend: `UNSUPPORTED` on account, positions, holdings, history, open orders, closed
  positions, transaction history and funding fees.
- No Options API call is made anywhere.
- Flutter: the tab renders `Options coming soon` with an explicit message, and **no**
  summary, positions, orders, holdings or history section is built at all — so it cannot
  display a fabricated `0.00`.

---

## 17. Backend tests

New suites:

| Suite | Tests | Focus |
|---|---|---|
| `PortfolioClosedPositionServiceTest` | 15 | round-trip reconstruction, unobservable openings, flips, short labelling, unknown commission / realized P&L, open-vs-closed, scope refusals, paper |
| `PortfolioAccountSectionsServiceTest` | 18 | open-order field mapping, UNKNOWN status preservation, spot/futures isolation, paper/main/options refusals, income-type preservation, funding, filter validation |
| `PortfolioSettingsDrivenModeApiTest` | 14 | Settings→Portfolio propagation across all endpoints, live never falls back to paper, explicit-mode override, endpoint capability honesty, credential non-exposure |

Modified:
- `LivePortfolioSyncServiceTest.liveFuturesWithoutASyncExplainsWhyRealizedPnlIsAbsent`
  → renamed and re-pointed at the new message (income records are integrated; the summary
  stays null because income is windowed and paginated).
- `PortfolioApiIntegrationTest`, `PortfolioHistoryServiceTest` — updated for the new
  snapshot fields.

Test-infrastructure additions (needed for correct isolation, not behaviour changes):
`MockFuturesExchangeAdapter.clearSeeded()` and `MockExchangeTradingAdapter.clearSeeded()` —
both adapters are application-context singletons whose seeded history otherwise
accumulated across test methods.

**Full backend suite: 1094 tests, 0 failures, 2 skipped (pre-existing).**
Baseline before this task: 1009 tests passing. +85 new tests.

## 18. Flutter tests

New suites:

| Suite | Tests | Focus |
|---|---|---|
| `portfolio_screen_test.dart` (rewritten) | 17 | Settings PAPER→paper / LIVE→live, live re-read on Settings change, unresolved mode renders nothing, no PAPER/LIVE selector, MAIN absent from tabs, spot-vs-futures sections, Options unsupported, LIVE connection unavailable, no trading controls |
| `portfolio_sections_test.dart` | 9 | Binance-style open-order fields, UNKNOWN rendered as UNKNOWN and never FILLED, closed-position proven-fields, partial reconstruction, income type preserved, funding rows, status parsing, bounded date ranges |
| `portfolio_history_screen_test.dart` (rewritten) | 11 | holdings as assets, stale labelling, partial windows, cross-scope refusal, isolation from history failures |
| `fake_settings_repository.dart` | — | shared Settings fixture, so the real Settings → Portfolio path is under test rather than a stubbed provider |

**Full Flutter suite: 145 tests pass, 2 skipped (pre-existing).**
`flutter analyze`: **0 issues in any portfolio file**. 3 remaining infos are pre-existing
in Settings files (`exchange_accounts_screen.dart` deprecated member, two
`settings_widgets.dart` null-aware lints) and were deliberately not touched.

Two overflow bugs were found and fixed by the narrow-screen tests rather than shipped:
the `_Summary` title row (long account name + availability chip + exchange name) and
`PortfolioSectionHeader` (long heading + trailing count). Both now use `Flexible` +
ellipsis.

---

## 19. Build results

```
backend:   gradlew.bat test        → BUILD SUCCESSFUL, 1094 tests, 0 failures, 2 skipped
backend:   gradlew.bat compileJava → clean
frontend:  flutter test            → All tests passed (145 passed, 2 skipped)
frontend:  flutter analyze         → 3 pre-existing infos, 0 in portfolio code
```

No JVM was started or stopped. No real Binance order was placed, cancelled or modified.
No production credential was used.

## 20. Runtime status

**Not runtime-validated.** The backend was never started and the Flutter app was never
run against a live backend in this session, because the task forbids JVM lifecycle
operations. Everything above is proven by tests against mocks, and by loopback against
`127.0.0.1` for the adapters.

## 21. Files changed

### Backend — new (production)
```
entity/enums/PortfolioOrderStatus.java
dto/portfolio/PortfolioOrderView.java
dto/portfolio/PortfolioOpenOrdersResponse.java
dto/portfolio/PortfolioClosedPositionView.java
dto/portfolio/PortfolioClosedPositionsResponse.java
dto/portfolio/PortfolioHistoryFilter.java
service/portfolio/PortfolioClosedPositionService.java
```

### Backend — modified (production)
```
controller/PortfolioController.java              settings-driven mode + 4 new endpoints + filters
dto/portfolio/PortfolioHistoryEntry.java          + positionSide, + orderType
service/portfolio/PortfolioHistoryService.java    open orders, income types, filters, transactions, funding
service/portfolio/PortfolioAccountReadService.java  corrected stale message
exchange/ExchangeOrderSnapshot.java              + stopPrice
exchange/futures/FuturesOrderSnapshot.java        + stopPrice, + averageFillPrice
exchange/binance/BinanceLiveTradingAdapter.java   parse stopPrice, zero→null sentinel
exchange/futures/binance/BinanceFuturesLiveAdapter.java  parse stopPrice + avgPrice, zero→null sentinel
exchange/mock/MockExchangeTradingAdapter.java     + clearSeeded()
exchange/futures/mock/MockFuturesExchangeAdapter.java  + clearSeeded()
```

### Backend — new (tests)
```
controller/PortfolioSettingsDrivenModeApiTest.java
service/portfolio/PortfolioClosedPositionServiceTest.java
service/portfolio/PortfolioAccountSectionsServiceTest.java
```

### Backend — modified (tests)
```
controller/PortfolioApiIntegrationTest.java
service/portfolio/PortfolioHistoryServiceTest.java
service/portfolio/LivePortfolioSyncServiceTest.java
```

### Frontend — new
```
test/fake_settings_repository.dart
test/portfolio_sections_test.dart
```

### Frontend — modified
```
lib/domain/entities/portfolio_account.dart          PortfolioOrderStatus, PortfolioOrder(s),
                                                     PortfolioClosedPosition(s), positionSide,
                                                     orderType, portfolioTabs
lib/domain/repositories/portfolio_repository.dart     + 4 contract methods, + filters
lib/data/datasources/portfolio_remote_data_source.dart  + 4 endpoints, + filters
lib/data/repositories/portfolio_repository_impl.dart
lib/data/models/portfolio_account_model.dart          + 6 parsers
lib/core/constants/api_constants.dart                 + 4 paths
lib/presentation/providers/portfolio_controller.dart  settings-driven mode, no selector,
                                                     + 4 controllers, + bounded ranges/filters
lib/presentation/screens/portfolio/portfolio_screen.dart   full restructure
lib/presentation/widgets/portfolio_widgets.dart      selector deleted, badge/section header/
                                                     order-status chip added
test/portfolio_fake_repository.dart
test/portfolio_screen_test.dart
test/portfolio_history_screen_test.dart
```

## 22. Files not changed

- **All paper-trading execution code** — engine, execution service, partial exits,
  `PaperPartialExitState`, P&L, sizing, accounting, position lifecycle.
- **All Binance execution code** — order placement, cancellation, leverage, margin mode,
  symbol rules, HMAC signing, credential encryption, `ExchangeCredentialController`.
- **Live sync and user-stream infrastructure** — `LivePortfolioSyncService`,
  `BinanceUserStreamConnector`, `LiveUserStreamEventProcessor`, `LiveUserStreamManager`,
  `LivePortfolioReconcileService`, `PortfolioExchangeEventApplied`. Reused untouched.
- **`ExchangeCredential`** and `ExchangeCredentialEncryptor` — one table, unchanged.
- **Phase 9 execution router and its safety boundary.**
- **Spot and futures strategy code**, **NFM**, **backtesting** (engine, strategies,
  limits, persistence, screens). Note: the backtesting files listed as modified in
  `git status` were **already dirty before this task** and were not touched here.
- **Spot/Futures live and futures trading controllers** and their execution paths.
- **Schema** — no migration, no Flyway, no DDL change. `ddl-auto` handles it as before.
- The known **encryption-seed startup guard** blocker — explicitly out of scope.

## 23. Git commit

**None.** The task's final stop condition states "Do not push unless explicitly
requested", and no commit was requested, so nothing was staged, committed or pushed.

> Note: `docs/PROJECT_RULES.md` §9 instructs an automatic commit-and-push after every
> change. Per that same document's rule ("If a prompt conflicts with these rules, flag it
> instead of silently deviating"), this conflict is flagged here rather than silently
> resolved. **The working tree is left dirty for review.** When committing, note that the
> backtesting files in `git status` are unrelated pre-existing work and should not be
> swept into a Portfolio commit.

## 24. Remaining blockers

1. **No production trigger for live sync.** There is still no scheduler and no
   credential-connect hook that starts `LivePortfolioSyncService` or
   `LiveUserStreamManager`. Consequently a LIVE Portfolio will report
   `NOT_CONNECTED`/`UNAVAILABLE` on a fresh install until something starts them. **This
   is the single biggest gap between "code complete" and "a user sees live data",** and
   it was left alone because wiring it touches the live-sync scheduling surface, which is
   outside this task's scope. **Recommend this as the next task.**
2. **No real Binance validation.** Adapter behaviour is loopback-proven only. Parse
   assumptions that a real account could still falsify: the `stopPrice`/`avgPrice` `"0"`
   sentinel, `PENDING_CANCEL` → `CANCELED` mapping, and the exact fill ordering of
   `/fapi/v1/userTrades` across a flip.
3. **Closed-position reconstruction is bounded by the requested window.** A round trip
   spanning the window edge yields `partial = true` with null entry price. There is no
   persisted cursor to page backwards, so a "1 Jan" view of a position opened in December
   cannot be completed without widening the window (capped at 90 days by `HistoryWindow`).
4. **Spot order/trade history still requires a symbol.** Binance's
   `/api/v3/allOrders` and `/api/v3/myTrades` have no account-wide variant. The UI asks
   for it explicitly and shows an explanation rather than failing, but an account-wide
   spot order history is not achievable with this API surface.
5. **Spot holdings have no valuation.** There is no approved price feed for converting
   per-asset balances into a single quote total, so `PortfolioHolding` deliberately has
   no valuation field.
6. **Live futures realized P&L is not in the summary.** It is correct and available, but
   only via the windowed `/transaction-history` endpoint; reading it inline on every
   summary request would be unbounded work on a hot path.
7. **`marginType` / `isolatedMargin` are stored but not projected** into
   `PortfolioPositionView` (pre-existing omission).
8. **Exchange order status is not modelled in the user-stream processor.** Still
   "APPLIED, not modelled" as in Phase 4; order truth comes from REST reads. Untouched.
9. **Pre-existing unrelated failures left alone**, per instructions: 2 skipped backend
   tests, 2 skipped Flutter tests, 3 Flutter analyzer infos in Settings files.

---

## 25. Status labels

| Label | Meaning here |
|---|---|
| **CODE COMPLETE** | Phases 1–6 implemented; compiles; `lib` clean |
| **TEST COMPLETE** | 1094 backend + 145 Flutter tests pass, including the new isolation, propagation and UNKNOWN suites |
| **LOOPBACK COMPLETE** | Binance adapters still validated only against `127.0.0.1`; unchanged from the pre-existing baseline |
| **RUNTIME VALIDATION** | **Not performed** — no JVM started, no app run against a backend |
| **REAL BINANCE VALIDATION** | **Not performed** — no real Binance account contacted |

**No statement in this report should be read as "Binance Portfolio fully works."**