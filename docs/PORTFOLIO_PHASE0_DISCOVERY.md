# Portfolio Architecture — Phase 0 Discovery

**Discovery only. No production code modified.** Source of truth = repository at HEAD.

| Field | Value |
|---|---|
| Repository root | `E:\Shy Black\ShyBalck Crypto Signels` |
| Branch | `main` |
| HEAD | `293b5f7 docs(portfolio): phase 0 architecture discovery` |
| Working tree | clean |
| Backend | Spring Boot **4.1.1**, Java **17**, Gradle 9.7.1, PostgreSQL |
| Frontend | Flutter / Dart 3.12.2, **Riverpod 3.4.2**, Dio 5.11.0, `web_socket_channel` 3.0.3 |
| Base package | `com.shyblack.cryptosignals` |

This document supersedes the previous Phase 0 note. It is verified against the **actual code**,
not only against the module reports in `docs/`.

---

## 1. Executive summary

The repository already contains **four of the eight execution/account verticals** the target
architecture requires, but they are **four disconnected silos** with **no shared account
abstraction**:

| Vertical | Backend account root | Positions | Orders | Status |
|---|---|---|---|---|
| Paper SPOT+FUTURES | `Portfolio` (`AccountType.PAPER`) | `Position` | — (position rows are the trade record) | **Working, runtime-verified** |
| Live SPOT | `LiveTradingAccount` | — (none) | `LiveOrder` | Shipped, runtime **NOT** verified |
| Live FUTURES | `FuturesTradingAccount` | `FuturesPosition` | `FuturesOrder` | Shipped, runtime **NOT** verified |
| Options | — | — | — | **Does not exist** |

Three findings materially change the plan that was sketched in the earlier Phase 0 note:

1. **`/api/v1/portfolios` and `/api/v1/positions` are dead stubs.** `PortfolioService.findAll()`
   returns `List.of()`; `findById` throws `UnsupportedOperationException` → HTTP 500.
   `PositionService` is identical. These are **not** a foundation to build on — they are unimplemented
   scaffolding. Likewise the Flutter `Portfolio`/`Position`/`Transaction` entity→repo→datasource→usecase
   stacks are wired into DI but **no screen ever watches them**.
2. **There is no Binance user-data stream (listenKey) — zero implementation.** Every "real-time"
   LIVE behaviour today is 30 s / 60 s REST polling. Phases 4 and 10 are therefore **net-new
   engineering**, not "wire up existing infrastructure".
3. **Paper SPOT and Paper FUTURES already share one `Portfolio` row.** Splitting them into separate
   accounts would mutate existing balances and break backward compatibility, which the master
   prompt forbids. The `MAIN / SPOT / FUTURES / OPTIONS` dimension must therefore be introduced
   as a **read-model / view partition** over existing rows, not as a physical account split.

---

## 2. Backend domain model as-is

### 2.1 `entity/Portfolio` — table `portfolios` — the PAPER account

Base: `entity/BaseEntity.java` (`UUID id`, `Instant createdAt/updatedAt`, auditing).
No unique constraints, no indexes.

| Field | Java type | Null | Notes |
|---|---|---|---|
| `user` | `User` `@ManyToOne` LAZY | no | |
| `name` | `String` | no | paper accounts created as `"Paper Trading"` |
| `accountType` | `AccountType` (STRING) | no | **this is the existing PAPER/LIVE discriminator** |
| `quoteCurrency` | `String` | no | `"USDT"` — raw String despite an existing `QuoteCurrency` enum |
| `initialBalance` | `BigDecimal(19,8)` | no | never mutated except by reset |
| `totalBalance` | `BigDecimal(19,8)` | no | `available + invested` |
| `availableBalance` | `BigDecimal(19,8)` | no | |
| `invested` | `BigDecimal(19,8)` | no | spot notion of deployed notional |
| `realizedPnl`, `totalFees` | `BigDecimal(19,8)` | no | |
| `totalTrades`, `winningTrades`, `losingTrades` | **primitive `int`** | no | columns are NOT NULL → primitives are safe |
| `version` | `Long` `@Version` | no | |

**There is no link from `Portfolio` to `ExchangeCredential`, `LiveTradingAccount` or
`FuturesTradingAccount`.**

### 2.2 `entity/Position` — table `positions` — the PAPER trade record

Unique: `uk_positions_portfolio_signal (portfolio_id, signal_id)` — the idempotency key.
Indexes: `(status)`, `(symbol,status)`, `(portfolio_id,status)`.

Nullability audit — **all nullable columns are correctly boxed; there are no primitives on
nullable columns**. This directly addresses master-prompt rule 17/18 and the historical `tp*_hit`
defect (fixed in `d395ca0`, guarded by
`src/test/java/com/shyblack/cryptosignals/entity/PositionPartialExitNullabilityTest.java`):

| Field | Type | Null |
|---|---|---|
| `signalId` | `UUID` | **yes** (plain column, not FK) |
| `symbol`, `side` (`PositionSide`), `size`, `entryPrice`, `status` | non-null | no |
| `notional`, `currentPrice`, `exitPrice`, `stopLoss`, `takeProfit1/2/3`, `margin`, `liquidationPrice`, `entryFee`, `exitFee`, `realizedPnl`, `unrealizedPnl`, `closeReason`, `openedAt`, `closedAt`, `strategyId` | `BigDecimal` / `Instant` / enums | **yes** |
| `strategyVersion` | **`Integer` (boxed)** | **yes** |
| `originalSize`, `remainingSize`, `averageExitPrice` | `BigDecimal(19,8)` | yes (NFM partial-exit state) |
| `tp1Hit`, `tp2Hit`, `tp3Hit` | **`Boolean` (boxed)** | **yes** — legacy SQL NULL preserved; `isTp1Hit()` etc. treat NULL as "not hit" |
| `version` | `Long` `@Version` | no |

Helpers: `isTp1Hit()/isTp2Hit()/isTp3Hit()`, `originalQty()`, `remainingQty()` (falls back to `size`).

### 2.3 `entity/PositionLifecycleEvent` — table `position_lifecycle_events`

Append-only audit ledger. Fields: `position` (`@ManyToOne`, not null), `eventType`
(`PositionLifecycleEventType`), `price`, `pnl`, `message(500)`. No `@Version`.
Event types: `CREATED, OPENED, TP_HIT, SL_HIT, MANUAL_CLOSE, CLOSED, REJECTED`.

### 2.4 Live SPOT entities

- **`LiveTradingAccount`** — `live_trading_accounts`. Unique `(user_id, exchange)`.
  `user` `@OneToOne`, `credential` `@OneToOne ExchangeCredential`, `exchange`,
  `connectionStatus` (default `NOT_CONNECTED`), **`enabled` primitive `boolean` default `false`**,
  **`killSwitchActive` primitive `boolean` default `false`**, `quoteCurrency`,
  `maxNotionalPerTrade` (nullable), **`maxActivePositions` primitive `int` default 3**,
  `dailyLossLimitPct` (nullable), `sessionStartEquity`, `sessionDate`,
  `cachedAvailableBalance`, `cachedTotalBalance`, `lastValidatedAt`, `lastValidationMessage(200)`,
  `@Version`. All primitives sit on NOT NULL columns → safe.
- **`LiveOrder`** — `live_orders`. Unique `(account_id, client_order_id)`.
  `clientOrderId(40)` deterministic `SB-…`; `exchangeOrderId(80)` nullable; `signalId`, `parentOrderId`
  nullable; `symbol`, `side`, `type`, `purpose`, `status` (default `CREATED`), `protectionStatus`,
  `requestedQuantity`, `price`/`stopPrice` nullable, `executedQuantity`/`cumulativeQuoteQty`/`fees`
  NOT NULL default 0, `avgFillPrice`, `feeAsset(20)`, `rejectReason(500)`, timestamps, `@Version`.
  **There is no `LivePosition` entity** — spot position state is derived from `LiveOrder` +
  `ProtectionStatus`.
- **`LiveOrderLifecycleEvent`** — append-only, no `@Version`.

### 2.5 Live FUTURES entities

- **`FuturesTradingAccount`** — `futures_trading_accounts`. Unique `(user_id, exchange)`.
  Mirrors the SPOT account plus: **`acknowledged` primitive `boolean`**, `marginAsset`,
  `marginMode` (default `ISOLATED`), `positionMode` (default `ONE_WAY`), **`maxLeverage` primitive
  `int` default 3**, cached wallet metrics (`walletBalance`, `availableBalance`, `marginBalance`,
  `unrealizedPnl`, `usedMargin`, `maintenanceMargin`, `totalFundingPaid`), `realizedPnlToday`.
- **`FuturesOrder`** — `futures_orders`. Unique `(account_id, client_order_id)`. Deterministic
  `SBF-…`. Adds `positionSide`, **`reduceOnly` primitive `boolean`**, **`leverage` primitive `int`**.
- **`FuturesPosition`** — `futures_positions`. Unique `(account_id, symbol, position_side)`
  (app-guarded for `status=OPEN`). Fields: `entryOrderId`, `stopOrderId`, `marginMode`,
  `leverage`, `quantity`, `entryPrice`, `exitPrice`, `stopLoss`, `takeProfit` (**single TP only**),
  `initialMargin`, `liquidationPrice`, `realizedPnl`, `unrealizedPnl`, `tradingFees`, `fundingFees`,
  `status`, `protectionStatus`, `@Version`.
- **`FuturesOrderLifecycleEvent`** — append-only; `order` nullable, `positionId` nullable.

### 2.6 `entity/ExchangeCredential` — table `exchange_credentials`

Unique `(user_id, exchange)`. `apiKey`/`apiSecret` are `String(2000)` NOT NULL holding
**AES-GCM ciphertext (Base64)** — encrypted in the service layer
(`service/ExchangeCredentialService.create`), not via a JPA converter. `@Transient maskedApiKey()` /
`maskedSecret()`. Unique-per-(user, exchange) means **one credential row serves both the SPOT and
FUTURES adapters for the same exchange** — this is the correct seam for a unified LIVE connection.

`security/AesGcmEncryptor.java`: `AES/GCM/NoPadding`, 256-bit key = `SHA-256(seed)`, 12-byte random
IV per encryption, 128-bit tag, `Base64(IV ‖ ciphertext+tag)`.
Key seed = `app.settings.encryption-secret-key`, default
`dev-only-settings-encryption-key-seed-change-me` (**hardcoded dev default, env
`SETTINGS_ENCRYPTION_SECRET_KEY`**).

### 2.7 Account abstraction — **DOES NOT EXIST**

- Zero matches for `AccountConnection`.
- No `TradingAccount` interface or superclass. `LiveTradingAccount` and `FuturesTradingAccount` are
  deliberately duplicated parallel hierarchies; the only shared base is `BaseEntity`.
- Paper has **no account entity abstraction at all** — it is `Portfolio` + `AccountType.PAPER`,
  managed by `service/paper/PaperTradingAccountService`.

### 2.8 Existing discriminators

| Enum | File | Values |
|---|---|---|
| `AccountType` | `entity/enums/AccountType.java` | **`LIVE, PAPER`** |
| `TradingMode` | `entity/enums/TradingMode.java` | `SPOT, FUTURES, OPTIONS` |
| `PositionSide` | `entity/enums/PositionSide.java` | `LONG, SHORT` (overloaded: exchange side **and** futures `positionSide`) |
| `PositionStatus` | | `OPEN, CLOSED` |
| `CloseReason` | | `STOP_LOSS, TAKE_PROFIT, MANUAL, SIGNAL_EXPIRED, SYSTEM, RESET` |
| `ExchangeName` | | `BINANCE, BYBIT, OKX, COINBASE` — **only BINANCE is implemented** |
| `ExchangeConnectionStatus` | | `NOT_CONNECTED, CONNECTING, CONNECTED, FAILED, REVOKED` |
| `QuoteCurrency` | | `USDT, USDC, BTC, ETH` — exists but `Portfolio.quoteCurrency` is a raw `String` |

**Naming collision to resolve in Phase 1:** the target architecture wants
`accountMode {PAPER, LIVE}` and `accountType {MAIN, SPOT, FUTURES, OPTIONS}`, but
`AccountType` **already means `{PAPER, LIVE}`** and is read/written by `User.accountType`,
`Portfolio.accountType`, `UserSettings`, `PaperTradingQueryService` (3 methods),
`PaperTradingEngineService` (2 methods), `PaperTradingAccountService` (2 methods), and 4 repository
derived queries. **Renaming it is not backward compatible.** Phase 1 must add a *new* enum for
`MAIN/SPOT/FUTURES/OPTIONS` and leave `AccountType` intact — see §9 Decision D1.

### 2.9 `entity/User` — portfolio-relevant fields

`accountType` (`AccountType`, default `PAPER`, set to `PAPER` on register in `AuthService:54,103`),
`tradingMode` (`TradingMode`, default `SPOT` — **no longer read or written** per
`docs/TRADING_MODES_AND_SIGNAL_NOTIFICATIONS.md` §1; the legacy column is retained, inert),
`riskProfile`, `enabled` (primitive `boolean`, default true).

### 2.10 `entity/Signal` — execution-routing fields

`symbol`, `status` (`SignalStatus`: `ACTIVE/PENDING/CLOSED/DRAFT`), `side` (`PositionSide`),
`confidence`, `entryPrice`, `targetPrice` (TP1), `targetPrice2`, `targetPrice3` (both nullable),
`stopLoss`, `suggestedRiskPercent`, **`tradingMode`** (`TradingMode`, default `SPOT`) — the mode
router used by the live and futures engines, `strategyId`, `strategyVersion`, `setupId`,
`entryType`, `signalGrade`, `marketRegime`, `score`, `riskReward`, `createdBy`.

**Critical for Phase 2:** a `Position` does **not** carry `tradingMode`. Paper mode is currently
recovered at read time by loading `Signal.tradingMode` (see
`PaperTradingController.mapPosition` → `marketType`). Any `SPOT`/`FUTURES` partition of paper data
must therefore join through `signalId`, and `signalId` is **nullable** (manual positions).

---

## 3. Backend services, controllers and endpoints

### 3.1 Complete endpoint index (trading/portfolio relevant)

**`/api/v1/portfolios` — `controller/PortfolioController.java` — STUB**
| Method | Path | Service | Note |
|---|---|---|---|
| GET | `/api/v1/portfolios` | `PortfolioService.findAll()` | returns `List.of()` |
| GET | `/api/v1/portfolios/{id}` | `findById` | throws → **HTTP 500** |

No `@SecurityRequirement`, **no principal resolution** (authenticated by route rule only).

**`/api/v1/positions` — `controller/PositionController.java` — STUB** — identical shape, `PositionService` stub.

**`/api/v1/signals` — `controller/SignalController.java`**
| Method | Path | Note |
|---|---|---|
| GET | `/api/v1/signals?mode=SPOT&status=` | `mode` defaults to `SPOT`, so the `findAll()` branch is dead code |
| GET | `/api/v1/signals/{id}` | |

No principal resolution.

**`/api/v1/paper-trading` — `controller/PaperTradingController.java`** (`@SecurityRequirement("bearer-jwt")`, Pattern A principal)
| Method | Path |
|---|---|
| GET | `/api/v1/paper-trading/account` |
| PATCH | `/api/v1/paper-trading/account/capital` |
| GET | `/api/v1/paper-trading/positions` |
| GET | `/api/v1/paper-trading/positions/{id}` |
| POST | `/api/v1/paper-trading/positions/{id}/close` |
| GET | `/api/v1/paper-trading/history` |
| GET | `/api/v1/paper-trading/performance` |
| DELETE | `/api/v1/paper-trading/account` (reset) |

**`/api/v1/live-trading` — `controller/LiveTradingController.java`** (`bearer-jwt`, Pattern A)
`GET /account` · `POST /connection` · `POST /connection/validate` · `DELETE /connection` ·
`POST /activate` (requires `acknowledged`) · `POST /deactivate` · `POST /kill-switch` ·
`DELETE /kill-switch` · `GET /orders` · `GET /orders/{id}` · `POST /orders/{id}/cancel` ·
`POST /positions/{entryOrderId}/close` · `GET /history` · `GET /performance`

**`/api/v1/futures-trading` — `controller/FuturesTradingController.java`** (`bearer-jwt`, Pattern A)
`GET /account` · `POST /connection` · `POST /connection/validate` · `DELETE /connection` ·
`POST /acknowledge` · `POST /activate` · `POST /deactivate` · `POST /kill-switch` ·
`DELETE /kill-switch` · `GET /orders` · `GET /orders/{id}` · `POST /orders/{id}/cancel` ·
`GET /positions` · `GET /positions/history` · `POST /positions/{id}/close`

**`/api/v1/settings/exchanges` — `controller/ExchangeCredentialController.java`** (`bearer-jwt`, Pattern B)
`GET /` · `POST /` · `POST /{id}/test-connection` (**simulated** — hardcoded 42 ms, never calls
`adapter.validateCredentials()`) · `DELETE /{id}`

**`/api/v1/backtests`** — `POST /` · `GET /` · `GET /{id}` · `GET /{id}/trades` · `GET /{id}/signals`
· `GET /{id}/equity` · `POST /{id}/cancel` · `DELETE /{id}` · `GET /strategies`

**No aggregated/unified portfolio endpoint exists.** A unified Portfolio screen must either compose
these client-side or a new backend read-model endpoint must be added.

### 3.2 Paper Trading — must not be rewritten

`service/paper/`: `PaperTradingEngineService` (205), `PaperTradingAccountService` (109),
`PaperTradingExecutionService` (294), `PaperTradingSizingService` (78), `PaperTradingPnLService` (76),
`PaperTradingQueryService` (98), `PaperPartialExitState` (172, pure, not a bean).

- Signal hook: `@TransactionalEventListener(AFTER_COMMIT) public void onSignalGenerated(SignalGeneratedEvent)`.
  Only `SignalStatus.ACTIVE`. Fans out to `userRepository.findAll()` skipping
  `!user.isEnabled()` and `user.getAccountType() != AccountType.PAPER`. Per-user try/catch.
- Market hook: `@PostConstruct` subscribes to `marketBook.spotTickers()` **and**
  `marketBook.futuresTickers()` batch listeners → `onTickBatch` → `evaluateSymbol`.
  SL wins ties (conservative). Legacy path when TP2 and TP3 are both null, otherwise
  `evaluatePartial` (NFM 3-way TP1/TP2/TP3 scale-out).
- `openPositionsForSymbol` filters `findByStatusAndPortfolio_AccountType(OPEN, AccountType.PAPER)` —
  the isolation fix from the gap analysis.
- `PaperTradingExecutionService` is the only open/close path. `openFromSignal` / `close` /
  `partialClose`, all `@Transactional(REQUIRES_NEW)`, all idempotent
  (`findByPortfolioAndSignalId`, plus DB unique constraint + `DataIntegrityViolationException`
  recovery). Pessimistic lock via `findByIdForUpdate`.
- `AccountType.PAPER` is enforced in exactly **4 classes / 8 call sites** (2 in engine, 3 in query,
  2 in account service) plus 4 repository derived queries. **These are the isolation invariants the
  new architecture must not break.**
- `PaperTradingQueryService.AccountView(portfolio, equity, unrealizedPnl, winRate)`.

### 3.3 Live SPOT

`service/live/`: `LiveTradingEngineService` (204), `LiveTradingRiskService` (105),
`LiveTradingSizingService` (72), `LiveTradingExecutionService` (232),
`LiveTradingReconciliationService` (152), `LiveTradingAccountService` (196),
`LiveTradingQueryService` (84), `LiveTradingCloseService` (160).

- Signal hook: AFTER_COMMIT listener; gate order `!autoExecute` → signal missing → not ACTIVE →
  `findByEnabledTrueAndKillSwitchActiveFalse()`; then per account
  `tradingMode != SPOT` → return; risk → symbol rules → ref price → sizing → `createIntent` →
  `submit` → if filled, `placeProtectiveStop` (`STOP_LOSS_LIMIT`).
- `LiveTradingRiskService.check` ordered gate (12 steps): account null / `!enabled` /
  `killSwitch` / not `CONNECTED` / signal null / mode≠SPOT / side≠LONG (no naked short) /
  SL invalid / `!settings.isLiveTradingAllowed()` / existing position / max active / daily loss.
- Transaction contract: intent committed **before** the exchange HTTP call; HTTP never inside a DB
  transaction. Deterministic `clientOrderId` = `SB-` + 24 chars of base64url(SHA-256 of
  `userId|signalId|symbol|side|purpose`).
- Reconciliation: `@Scheduled(fixedDelay 30 s)` order poll + `@Scheduled(fixedDelay 60 s)` balance
  refresh. **Observe only** — never creates or cancels orders.

### 3.4 Live FUTURES

`service/futures/`: `FuturesEngineService` (277), `FuturesRiskService` (121),
`FuturesTradingSizingService` (79), `FuturesExecutionService` (209),
`FuturesLiquidationService` (58), `FuturesReconciliationService` (143),
`FuturesAccountService` (199), `FuturesQueryService` (72), `FuturesCloseService` (121),
`FuturesClientOrderIdGenerator` (40).

- Signal hook: AFTER_COMMIT; gate order `!autoExecute` → signal missing → not ACTIVE →
  **`tradingMode != FUTURES`** → enabled && !killSwitch. Then 11 ordered steps including
  `checkLeverage`, liquidation safety, `setLeverage`, `setMarginMode`, entry, `openPositionRow`,
  `placeProtectiveStop` (`STOP_MARKET`, `reduceOnly=true`).
- `FuturesRiskService.check` is a **16-step ordered gate** (adds acknowledged, margin mode,
  position mode, SL required).
- clientOrderId = `SBF-` + 23 chars, canonical string prefixed `"F|"` so a SPOT and a FUTURES order
  on the same signal can never collide.
- `FuturesLiquidationService` is a conservative approximation of liq price, **not** Binance's
  maintenance-margin formula. Exchange remains authoritative at submission.

### 3.5 Signal → execution flow as-is

`SignalGeneratedEvent` (`service/SignalGeneratedEvent.java`) is a plain POJO carrying **only
`UUID signalId`**. Every listener re-reads the `Signal` from `SignalRepository` inside the callback.

Published from **5** sites, all inside a transaction, all wrapped in try/catch:
`SpotSignalScheduler:118`, `FuturesSignalScheduler:89`, `TrendPullbackSignalService:111`,
`EmaTrendFollowingSignalService:102`, `NfmFuturesSignalService:168`.

Consumed by **4** AFTER_COMMIT listeners:

| # | Listener | Behaviour |
|---|---|---|
| 1 | `PaperTradingEngineService.onSignalGenerated` | all modes, all PAPER users |
| 2 | `LiveTradingEngineService.onSignalGenerated` | SPOT only |
| 3 | `FuturesEngineService.onSignalGenerated` | FUTURES only |
| 4 | `SignalNotificationService.onSignalGeneratedEvent` | BUY/STRONG_BUY only, per-user idempotency, WS alert |

**None uses `fallbackExecution = true`** — an event published outside a transaction is silently
dropped.

**ExecutionRouter: DOES NOT EXIST.** The string appears only as a *proposal* in the previous version
of this document. Dispatch today is a 4-way fan-out over Spring's event multicaster — there is no
router object, no execution strategy interface, no dispatch table. Adapter selection
(`LiveTradingConfig` / `FuturesTradingConfig`, both default **`MOCK`**) is bean-level and
config-driven, not per-signal. Engine selection is by `TradingStrategy.engineKey`
(`SpotSignalSchedulerRoutingTest`, `FuturesSignalSchedulerRoutingTest`).

### 3.6 Structural isolation test — **must keep passing**

`src/test/java/com/shyblack/cryptosignals/isolation/PaperLiveIsolationTest.java` (98 lines) is a
**textual** guard: it walks `service/paper`, `service/live`, `service/futures`, `service/backtest`
and fails if any `.java` file's full text *contains* a forbidden substring.

| Test | Scanned | Forbidden substrings |
|---|---|---|
| `paperPackageMustNotImportLiveOrFuturesOrBacktestOrExchange` | `service/paper` | `...service.live`, `...service.futures`, `...service.backtest`, `...exchange.`, `ExchangeTradingAdapter`, `FuturesExchangeAdapter` |
| `livePackageMustNotImportPaperOrFuturesOrBacktest` | `service/live` | `...service.paper`, `...service.futures`, `...service.backtest` |
| `futuresPackageMustNotImportPaperOrLiveOrBacktest` | `service/futures` | `...service.paper`, `...service.live`, `...service.backtest` |
| `backtestPackageMustNotImportPaperOrLiveOrFutures` | `service/backtest` | `...service.paper`, `...service.live`, `...service.futures`, `ExchangeTradingAdapter`, `FuturesExchangeAdapter` |

**Consequences for the new architecture:**
- A new shared `service/portfolio/` package may reference paper **and** live **and** futures —
  it is **not** scanned, so it is the legal home for an aggregating read-model and an
  `ExecutionRouter`.
- But it must **not** live inside any of the four scanned packages.
- Because matching is substring-based, a forbidden identifier appearing in a **comment** or
  **javadoc** fails the test. New shared code must not be added to the scanned packages at all.
- `controller/`, `config/`, `exchange/`, `market/`, `security/`, `repository/` are **not** scanned.

### 3.7 Security posture as-is

`config/SecurityConfig.java` (77): stateless, JWT filter, BCrypt, `@EnableMethodSecurity`.
Route rules (complete): `OPTIONS /**` permitAll → `/api/auth/**`, `/api/markets/**`, `/ws/**`,
`/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/actuator/health` permitAll →
`anyRequest().authenticated()`.

- No in-scope controller appears in the permit list → all require a valid Bearer JWT.
- **No `@PreAuthorize`/`@Secured` anywhere in these controllers**; all authority comes from handler
  code resolving the principal.
- Three principal-resolution patterns: **A** `UserPrincipal → userRepository.findById` (paper, live,
  futures, backtest — IDOR-safe), **B** `UserPrincipal` returned directly
  (`ExchangeCredentialController` — service does the lookup), **C** **none**
  (`PortfolioController`, `PositionController`, `SignalController`, `TransactionController`,
  `WatchlistController`).
- Ownership enforcement in force: paper → filter `listAll(user)`; live/futures → filter
  `order.getAccount().getUser().getId().equals(user.getId())` → **404** on mismatch;
  backtest → `findByIdAndUser`; credentials → `findByIdAndUser_Id`.
- `@SecurityRequirement("bearer-jwt")` present on paper/live/futures/backtest/settings/
  strategies/users/admin; **absent** on portfolio/position/signal/news/transaction/watchlist/
  notification/device-token. It is springdoc documentation only — zero runtime effect.

---

## 4. Binance integration as-is

### 4.1 What EXISTS (reuse — do not duplicate)

| Capability | File | Endpoint |
|---|---|---|
| Spot public data | `market/BinanceRestClient.java` (166, `@Component`) | `/api/v3/exchangeInfo`, `/api/v3/ticker/24hr`, `/api/v3/klines` (5-arg, `MAX_KLINES_PER_REQUEST=1000`) |
| Futures public data | `market/BinanceFuturesRestClient.java` (103) | `/fapi/v1/exchangeInfo`, `/fapi/v1/ticker/24hr`, `/fapi/v1/klines` (**no** time-window args, **no** 1000 clamp) |
| Derivatives / NFM | `market/BinanceFuturesDerivativesClient.java` (171) | `/fapi/v1/premiumIndex`, `/fapi/v1/openInterest`, `/futures/data/openInterestHist`, `/fapi/v1/allForceOrders` |
| Historical paginator | `service/backtest/historical/BinanceHistoricalDataProvider.java` (141) | forward-paging loop, `MAX_PAGES=100_000`, `TreeMap` dedupe, no look-ahead |
| Spot ticker WS | `market/BinanceSpotTickerStreamClient.java` (37) → `market/BinanceArrayTickerStreamClient.java` (126) | `wss://stream.binance.com:9443/ws/!miniTicker@arr` |
| Futures ticker WS | `market/BinanceFuturesTickerStreamClient.java` (37) | `wss://fstream.binance.com/ws/!ticker@arr` |
| Spot signed adapter | `exchange/binance/BinanceLiveTradingAdapter.java` (324) | `/api/v3/account` (**USDT row only**), `/api/v3/exchangeInfo?symbol=`, `/api/v3/order` POST/GET/DELETE by `origClientOrderId` |
| Futures signed adapter | `exchange/futures/binance/BinanceFuturesLiveAdapter.java` (312) | `/fapi/v2/account`, `/fapi/v1/positionSide/dual`, `/fapi/v1/leverage`, `/fapi/v1/marginType`, `/fapi/v1/order` POST/GET/DELETE, `/fapi/v1/exchangeInfo` |
| HMAC signing | `exchange/binance/BinanceSignatureUtil.java`, `exchange/futures/binance/BinanceFuturesSignatureUtil.java` (identical duplicates, package-private) | `HMAC-SHA256` hex over insertion-ordered urlencoded params + `X-MBX-APIKEY` header |
| Mocks for tests | `exchange/mock/MockExchangeTradingAdapter.java` (174), `exchange/futures/mock/MockFuturesExchangeAdapter.java` (146) | `putRules`, `setBalances`, `forceFill`, `allOrders`, `setPositionMode`, `effectiveLeverage`, `effectiveMarginMode` |
| Adapter interfaces | `exchange/ExchangeTradingAdapter.java` (7 methods), `exchange/futures/FuturesExchangeAdapter.java` (9 methods) | |
| Shared value objects | `exchange/SymbolRules.java` (48), `ExchangeOrderResult` (25), `ExchangeAccountSnapshot` (12), `PlaceOrderRequest` (15), `ExchangeAdapterException` (32, `retryable`/`httpStatus`/`exchangeCode`), `FuturesAccountSnapshot` (25), `FuturesOrderResult` (24), `PlaceFuturesOrderRequest` (17) | |
| Deterministic ids | `exchange/ClientOrderIdGenerator.java` (44, `SB-`), `service/futures/FuturesClientOrderIdGenerator.java` (40, `SBF-`) | |
| Inbound sockets | `config/MarketWebSocketConfig.java` (26) → `/ws/markets` (`market/MarketsWebSocketHandler.java` 161), `/ws/private` (`market/AlertsWebSocketHandler.java` 63, `broadcastAlert`) | |

### 4.2 What is MISSING (net-new work)

| Gap | Evidence |
|---|---|
| **Binance user-data stream (listenKey) — ZERO implementation** | No `POST /api/v3/userDataStream`, no `POST /fapi/v1/listenKey`, no keepalive. Exhaustive grep for `listenKey\|userData\|OutboundAccountPosition\|ExecutionReport\|AccountUpdate\|ORDER_TRADE_UPDATE` returns **3 hits, all comments/javadoc**: `config/LiveTradingProperties.java:13` (javadoc "spotStreamBaseUrl — Binance spot user-data stream base"), `BinanceFuturesLiveAdapter.java:291-292` (comment "commission comes via user-data stream (**not implemented**)"), `MockExchangeTradingAdapter.java:152` (javadoc on `forceFill`). Only **one** class imports `WebSocketClient` (`BinanceArrayTickerStreamClient`) and it constructs it with **no HTTP headers** — it cannot carry an API key, let alone a listenKey. |
| **Dead-but-reserved stream config** | `LiveTradingProperties.spotStreamBaseUrl` = `wss://testnet.binance.vision/ws` (env `LIVE_BINANCE_SPOT_STREAM`) and `FuturesTradingProperties.streamBaseUrl` = `wss://stream.binancefuture.com/ws` (env `FUTURES_BINANCE_STREAM`) — **bound, defaulted, referenced nowhere**. |
| **No Binance list/read endpoints at all** | Absent: `/api/v3/openOrders`, `/api/v3/allOrders`, `/api/v3/myTrades`, `/fapi/v1/openOrders`, `/fapi/v1/allOrders`, `/fapi/v1/userTrades`, `/fapi/v1/income`, `/fapi/v1/leverageBracket`, `/fapi/v2/positionRisk`, `/fapi/v1/marginType`. Every order read today is a **single-order lookup by `origClientOrderId`**. |
| **No `getPositions()` anywhere** | Verified: `FuturesQueryService.openPositions()` reads the **local** `FuturesPositionRepository`, not Binance. `FuturesPosition` is a local shadow, not a synced snapshot. |
| **Per-asset spot balances discarded** | `BinanceLiveTradingAdapter.getAccountBalance` calls `/api/v3/account` but extracts **only the `USDT` row**. No holdings endpoint exists. |
| **Binance Options — ZERO support** | `service/BinanceOptionsMarketService.java` (26 lines) is a documented placeholder: *"Placeholder until Binance Options … is integrated. Do not invent Options prices here."* It has no `RestClient`, no base URL, **no HTTP call at all**, returns `List.of()`. Zero `/eapi/` occurrences. `MarketBook.tickers(OPTIONS)` / `symbols(OPTIONS)` **throw** `IllegalArgumentException`; `MarketService` returns `MarketListResponse.optionsUnavailable()`; `MarketsWebSocketHandler` sends `{"message":"Options data not yet available","tickers":[]}`; test-enforced by `MarketApiIntegrationTest.optionsModeReturnsEmptyPlaceholder`. Trading is hard-blocked (`LiveTradingRiskServiceTest.optionsSignal_isRejectedWith_UNSUPPORTED_TRADING_MODE`). |
| **No COIN-M Futures** | No `/dapi/`. `ExchangeName.BYBIT/OKX/COINBASE` are unreachable enum values. |
| **No rate limiting / retry / circuit breaker** | Resilience4j, Spring Retry, Bucket4j: **all absent**. `HttpClientFactory` uses JDK `SimpleClientHttpRequestFactory` (no connection pooling), 5 s connect / 15 s read. `retryable` is only a status label, not a retry loop — the next 30 s poll *is* the retry. HTTP 429/418 unhandled. |
| **No adapter unit tests** | Zero tests for `BinanceLiveTradingAdapter`, `BinanceFuturesLiveAdapter`, `BinanceRestClient`, `BinanceFuturesRestClient`, `BinanceArrayTickerStreamClient`, `LiveTradingConfig`, `FuturesTradingConfig`. |
| **Futures commissions always 0** | `newOrderRespType=RESULT` returns no `fills[]`; `BinanceFuturesLiveAdapter.parse` hardcodes `fees=ZERO`, `feeAsset=null`. |
| **Per-symbol `marginMode` never read back** | `FuturesAccountSnapshot.marginMode` hardcoded `ISOLATED`. |

### 4.3 Configuration

There is **no `BinanceProperties`** — URLs are split across three records:

| Record | Prefix | Notable |
|---|---|---|
| `config/MarketProperties.java` (19) | `app.markets` | `restBaseUrl=https://api.binance.com` (**production**), `streamUrl=…/!miniTicker@arr`, `futuresRestBaseUrl=https://fapi.binance.com`, `futuresStreamUrl=…/!ticker@arr`, `quoteAsset=USDT`, `exchangeInfoRefreshMs=3600000` |
| `config/LiveTradingProperties.java` (48) | `app.live-trading` | `mode=MOCK`, `spotRestBaseUrl=https://testnet.binance.vision` (**testnet**), `spotStreamBaseUrl` **unused**, `futuresRestBaseUrl` **unused**, `recvWindowMs=5000`, `defaultMaxNotional=200.00`, `defaultMaxActive=3`, `defaultDailyLossPct=5.00`, `autoExecute=false` |
| `config/FuturesTradingProperties.java` (47) | `app.futures-trading` | `mode=MOCK`, `restBaseUrl=https://testnet.binancefuture.com`, `streamBaseUrl` **unused**, `maxLeverage=3`, `defaultMarginMode=ISOLATED`, `requiredPositionMode=ONE_WAY`, `minStopDistancePct=0.30`, `liquidationBufferPct=15.00`, `autoExecute=false` |

**Market data points at PRODUCTION; signed trading points at TESTNET.** There is no unified toggle —
switching to production requires editing two base URLs in two different property records.

Both `LiveTradingConfig` and `FuturesTradingConfig` log `WARN "[LIVE]/[FUTURES] EXCHANGE mode active"`
on boot when armed. **Default is `MOCK` for both.**

### 4.4 Symbol normalization

There is **no `SymbolNormalizer` class**. `MarketTickerStore.normalize(String)` (`trim().toUpperCase()`)
is the only shared helper. Adapters use bare `symbol.toUpperCase()` — **no trim**, inconsistent.
`BTC/USDT → BTCUSDT` slash removal is **not implemented anywhere**. Interval lower-casing exists
**only on the spot path**.

---

## 5. Flutter as-is

### 5.1 The seam

`presentation/screens/portfolio/portfolio_screen.dart` — **14 lines total**:

```dart
class PortfolioScreen extends StatelessWidget {
  const PortfolioScreen({super.key});
  @override
  Widget build(BuildContext context) => const PaperTradingScreen();
}
```

This is `IndexedStack` index **3** of `presentation/shell/main_shell.dart` (tab titles:
`Signals | Markets | News | Portfolio | Backtesting`; AppBar reads `"ShyBlack · Portfolio"`).
Because it is an `IndexedStack`, the Portfolio screen builds as soon as `MainShell` builds —
its providers and 10 s timer start immediately even when another tab is visible.

### 5.2 Three separate trading stacks

| | Paper | Live SPOT | Futures |
|---|---|---|---|
| Screen | `screens/paper_trading/paper_trading_screen.dart` (810) | `screens/live_trading/live_trading_screen.dart` (699) | `screens/futures_trading/futures_trading_screen.dart` (600) |
| Reachable from | Portfolio tab | **Settings only** (`settings_screen.dart:124`) | **Settings only** (`settings_screen.dart:131`) |
| Has own `AppBar` | no | yes | yes |
| Tabs | `DefaultTabController(3)`: Open / History / Stats | none (sections) | none (sections) |
| Controller | `providers/paper_trading_controller.dart` (93) `AsyncNotifier<PaperTradingViewData>` | `providers/live_trading_controller.dart` (131) | `providers/futures_trading_controller.dart` (120) |
| Poll | `Timer.periodic(10 s)` | `Timer.periodic(10 s)` | `Timer.periodic(10 s)` |
| Repository / datasource | `paper_trading_repository_impl.dart` / `paper_trading_remote_data_source.dart` | ditto `live_…` | ditto `futures_…` |
| Entities | `PaperAccount`, `PaperPosition`, `PaperPerformance` | `LiveAccount`, `LiveOrder`, `LivePerformance` | `FuturesAccount`, `FuturesOrder`, `FuturesPosition` |

**Watching all three controllers from a unified screen would start three independent 10 s pollers.**
A single aggregate notifier watching only the selected mode is required.

### 5.3 Legacy portfolio stack — dead

`domain/entities/portfolio.dart` (`Portfolio{id,name,accountType}` — 10 lines),
`domain/entities/position.dart`, `domain/entities/transaction.dart`, with parallel
`data/datasources/portfolio_remote_data_source.dart`, `data/models/portfolio_model.dart`,
`data/repositories/portfolio_repository_impl.dart`, `domain/usecases/get_portfolios.dart`, and DI
providers (`portfolioRemoteDataSourceProvider:149`, `portfolioRepositoryProvider:185`,
`getPortfoliosProvider:217`). **No screen watches any of them.** They mirror the dead backend stubs.

### 5.4 Existing mode selectors — none reusable

- `TradingAccount { paper, live }` in `domain/entities/app_settings.dart:11` + `TradingAccountLabel`
  extension. Rendered by the **private** `_AccountTile` in `settings_screen.dart:444-499`
  (`Icons.science_outlined` vs `Icons.account_balance`). Persisted via `SharedPreferences`
  (`app_settings`). **Settings-only, 2 modes, no futures, no test keys.**
- `TradingMode { spot, futures, options }` + `const kAppMarketMode = TradingMode.spot` —
  application-controlled market-browser mode, deliberately not user-selectable.
- `StrategyTradingMode { spot, futures }` → `StrategyTabController` subclasses →
  `strategy_build_screen.dart` `TabBar` (the closest existing precedent for a two-tab header).
- **No `SegmentedButton` / `ToggleButtons` anywhere in `lib/`.** `ChoiceChip` appears only in
  `news_screen.dart`.

### 5.5 Widgets duplicated across the three trading screens

`_Badge`, `_Meta`, `_Kpi`/`_StatCard`/`_StatRow`, `_SectionHeader`, `_EmptyState`/`_Empty`/`_EmptyRow`,
`_ErrorPanel` are **private and triplicated**. Reusable design-system widgets that do exist:
`presentation/widgets/settings_widgets.dart` (`PremiumBadge`, `SettingsSectionTitle`, `SettingsCard`,
`SettingsNavTile`).

### 5.6 Theme

`core/theme/app_colors.dart` (8 tokens, exactly the project design system):
`background 0xFF0D0D0D`, `card 0xFF1A1A1A`, `accent 0xFF00E676`, `loss 0xFFFF3B30`,
`onBackground`, `onCard`, `muted`, `profit`.

`core/theme/app_theme.dart` (106) — Material 3 dark. Trading screens do **not** use `Card`; they
build `Container` + `BoxDecoration(color: AppColors.card, borderRadius: 12|14)`. Badges are
`Container` + `color.withValues(alpha: 0.12–0.16)` + `BorderRadius.circular(6)` + `w800` text.
Hardcoded `Divider(color: Color(0xFF2A2A2A))` at `paper_trading_screen.dart:159` matches
`scheme.outline`.

### 5.7 WebSocket (2 sockets, both already open — do not add a third)

- **`/ws/markets?mode=SPOT`** — `providers/markets_controller.dart:211`, reconnect backoff
  `min(10 s, 500·2^n)`. Provides `bySymbol: Map<String,MarketTicker>` + `connected`. **The paper
  position card already consumes it** for live prices.
- **`/ws/private`** — `providers/notifications_controller.dart:37`. Handles `type=='alert'` and
  `type=='news'`.
- **No auth token is appended to either WS URI.**
- `market/AlertsWebSocketHandler` + `config/MarketWebSocketConfig` use
  `setAllowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*", "*")` — **note the `"*"`
  wildcard** — and `/ws/private` has **no authentication interceptor** (a documented pre-existing
  follow-up in `docs/NEWS_INTELLIGENCE_MODULE.md` §8/§13).

### 5.8 Navigation

**No GoRouter.** Imperative `Navigator.push(MaterialPageRoute)`. `main.dart` →
`ProviderScope` → `MaterialApp(theme: AppTheme.dark())` → `MainShell`.
`pubspec.yaml`: `flutter_riverpod ^3.4.2`, `dio ^5.11.0`, `web_socket_channel ^3.0.3`,
`fl_chart ^1.2.0`, `flutter_secure_storage ^11.0.0`, `shared_preferences ^2.5.5`,
`google_sign_in ^7.2.0`, `firebase_core`/`firebase_messaging`, `flutter_local_notifications ^17.2.4`.
**Absent:** `go_router`, `intl`, `provider`, `get_it`, `build_runner`, `freezed`,
`json_serializable`. All providers and models are hand-written; DI is manual in
`core/di/providers.dart` (349 lines).

### 5.9 Frontend tests

15 test files. Portfolio-relevant: `test/paper_trading_test.dart` (asserts
**`Key('paper_capital_edit')`** — must be preserved or the test updated),
`test/live_trading_test.dart`, `test/futures_trading_test.dart`, `test/settings_navigation_test.dart`.

---

## 6. Database, build and test as-is

### 6.1 Schema management — no migration tool

| Profile | `ddl-auto` | Datasource |
|---|---|---|
| dev (`application-dev.yml`) | **`update`** (additive only; never drops) | `jdbc:postgresql://localhost:5432/shyblack`, `shyblack/shyblack/shyblack`, `show-sql: true` |
| prod (`application-prod.yml`) | **`validate`** | env-only `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` |
| test (`application-test.yml`) | `create-drop` | H2 in-memory `MODE=PostgreSQL` |

**Flyway: absent. Liquibase: absent.** Grep for `flyway|liquibase` across `backend/` returns **zero
matches** — no build dependency, no properties, no code.

The only SQL file in the repository is
`src/main/resources/db/research/V1__research_market_data.sql` (132 lines). Despite the Flyway-style
`V1__` name it is applied **out-of-band**; its own header states: *"Production applies this file
out-of-band … production uses ddl-auto=validate, so these tables MUST exist before the app
validates."* It creates only research/NFM tables (`market_candle` range-partitioned monthly,
`market_open_interest`, `market_funding_rate`, `market_liquidation`,
`research_dataset_version`, `research_import_reject`, `research_import_checkpoint`).

**There is no SQL defining `portfolios`, `positions`, `live_orders`, `futures_orders` or any other
trading table.** All of them are created and maintained **purely by Hibernate `ddl-auto`.**

**Consequence for master-prompt rule 27:** production-safe explicit migrations do not exist today.
New schema changes need a new mechanism (see §9 Decision D7).

### 6.2 Build

`build.gradle.kts` (62): Spring Boot **4.1.1**, dependency-management 1.1.7, Java toolchain **17**,
Gradle wrapper **9.7.1** (`settings.gradle.kts` applies `foojay-resolver-convention` 1.0.0,
`rootProject.name = "cryptosignals"`).

Deps: `starter-actuator`, `starter-data-jpa`, `starter-security-oauth2-client`, `starter-security`,
`starter-validation`, **`starter-webmvc`** (Boot 4 artifact, *not* `-web`),
`starter-websocket` + `org.java-websocket:Java-WebSocket:1.6.0`, `gson`,
`springdoc-openapi-starter-webmvc-ui:3.0.1`, `jjwt-api:0.12.6` (+ `jjwt-impl`, `jjwt-jackson`),
`google-api-client:2.8.1`, `google-http-client-gson:1.47.1`, `runtimeOnly postgresql`,
`developmentOnly spring-boot-devtools` (excluded from `bootJar`), Lombok
`compileOnly` + `annotationProcessor`.
**Not present:** Resilience4j, WebFlux, explicit Jackson, Firebase Admin SDK, Testcontainers,
Flyway, Liquibase.

Note: Gson is a first-class dependency while Jackson comes transitively — this is the root of the
out-of-scope Backtesting defect in §10.

### 6.3 Tests

Single source set `src/test/java`, **95 files** (92 `*Test`, 2 `*IT`, 1 `*Tests`).
`NewsRuntimeSyncIT` and `TrendPullbackBaselineBacktestIT` are env-gated/skipped.
**No Testcontainers** — H2 in-memory with `ddl-auto: create-drop`, and
`app.news.enabled=false`, `app.nfm.enabled=false`, `app.markets.websocket-enabled=false`,
`catalog-enabled=false` for an offline suite.

Portfolio/trading-relevant tests that must keep passing:
`isolation/PaperLiveIsolationTest`, `entity/PositionPartialExitNullabilityTest`,
`service/paper/PaperPartialExitStateTest`, `service/paper/PaperTradingEngineServiceTest`,
`exchange/ClientOrderIdGeneratorTest`, `service/futures/FuturesClientOrderIdGeneratorTest`,
`service/futures/FuturesRiskServiceTest`, `service/futures/FuturesLiquidationServiceTest`,
`service/futures/FuturesTradingSizingServiceTest`, `service/live/LiveTradingRiskServiceTest`,
`service/live/LiveTradingSizingServiceTest`, `service/live/LiveTradingCloseServiceTest`,
`exchange/SymbolRulesTest`, `exchange/mock/MockExchangeTradingAdapterTest`,
`exchange/futures/mock/MockFuturesExchangeAdapterTest`, `market/MarketTickerStoreTest`,
`market/MarketApiIntegrationTest` (asserts the Options placeholder contract).

---

## 7. Gap analysis — as-is vs. target

| # | Target requirement | Status | Blocking fact |
|---|---|---|---|
| 1 | Unified Portfolio screen `[PAPER][LIVE]` | ❌ | `portfolio_screen.dart` returns `PaperTradingScreen`; no mode selector exists |
| 2 | `[MAIN][SPOT][FUTURES][OPTIONS]` tabs | ❌ | no such dimension in the domain at all |
| 3 | `accountMode {PAPER, LIVE}` | ⚠ | exists **as** `AccountType`, but on `User`/`Portfolio`, and its name collides with the target's `accountType` |
| 4 | `accountType {MAIN, SPOT, FUTURES, OPTIONS}` | ❌ | **does not exist**; `TradingMode` is close but means market mode on signals, is application-controlled, and is not on accounts |
| 5 | PAPER existing behaviour preserved | ✅ | working, runtime-verified, 8 `AccountType.PAPER` call sites to protect |
| 6 | PAPER ⊂ MAIN/SPOT/FUTURES partitioning | ⚠ | SPOT+FUTURES already **share one Portfolio row**; `Position` has no `tradingMode` — partition must be a read-model join through nullable `signalId` |
| 7 | LIVE BINANCE balances synced | ⚠ | only the **USDT** row; no per-asset holdings read |
| 8 | LIVE BINANCE positions synced | ❌ | **no `getPositions()`**; `/fapi/v2/positionRisk` unused; `FuturesPosition` is a local shadow only |
| 9 | LIVE BINANCE orders synced | ⚠ | **single-order lookup only**; no `openOrders` / `allOrders` |
| 10 | LIVE BINANCE trade/fill history | ❌ | no `/api/v3/myTrades`, no `/fapi/v1/userTrades` |
| 11 | WebSocket idempotent updates | ❌ | **no user-data stream exists**; 30 s/60 s REST polling is the only mechanism |
| 12 | Reconciliation | ⚠ | exists for orders + balances only; **no position reconciliation**, no mismatch status record |
| 13 | Signal → Execution Router | ❌ | **does not exist**; 4-way event fan-out, no abstraction |
| 14 | Paper execution unchanged | ✅ | must stay byte-compatible |
| 15 | LIVE execution disabled by default | ✅ | `app.live-trading.mode=MOCK`, `auto-execute=false`, `enabled=false` default, kill switch, 12-step risk gate |
| 16 | No secret reaches Flutter | ✅ | AES-GCM at rest, masked views, `requireOwned` IDOR-safe; **but** see §10 note on the hardcoded dev seed |
| 17 | No Paper/LIVE accounting mixing | ⚠ | currently guaranteed by 8 call sites + 4 derived queries; must be re-guaranteed at the new read-model layer |
| 18 | No SPOT/FUTURES/OPTIONS mixing | ⚠ | not currently a guard — futures lives in a wholly separate account hierarchy, paper has none |
| 19 | Legacy rows readable | ✅ | `Position` nullability already audited + test-guarded |
| 20 | Production-safe migrations | ❌ | **no migration tool at all**; prod is `validate` only |
| 21 | OPTIONS boundary (no fabrication) | ✅ | already consistently unavailable — must be *preserved*, not "fixed" |
| 22 | Backend + Flutter tests pass | ✅ | baseline is green (one known unrelated flake) |

---

## 8. Minimum files to modify — Phases 1 → 11

### Phase 1 — domain boundaries (backend only)

| Action | File | Why |
|---|---|---|
| **new** | `entity/enums/AccountMode.java` *(or reuse `AccountType`)* | See §9 **D1** |
| **new** | `entity/enums/AccountCategory.java` *(or `AccountType`/`PortfolioScope`)* | `MAIN, SPOT, FUTURES, OPTIONS` |
| **modify** | `entity/Portfolio.java` | add nullable `accountCategory` + `exchange` — see §9 **D2** |
| **new** | `entity/PortfolioAccountConnection.java` | sync status + `lastSyncedAt` + `connectionState` per `(user, accountMode, accountCategory, exchange)` |
| **modify** | `repository/PortfolioRepository.java` | derived queries for the new columns |
| **modify** | `entity/enums/AccountType.java` | **only if D1 = rename**; otherwise untouched |
| **new** | `src/main/resources/db/migration/V1__portfolio_account_scope.sql` | production-safe additive DDL — see §9 **D7** |

### Phase 2 — PAPER into MAIN/SPOT/FUTURES/OPTIONS

**Intended: zero modification to `service/paper/*`.** The partition is a read-model concern.

| Action | File | Why |
|---|---|---|
| **new** | `service/portfolio/PortfolioPaperQueryService.java` | partitions `Portfolio`/`Position` by mode via the `Signal.tradingMode` join |
| **modify** | `controller/PaperTradingController.java` | **additive** optional `category` query params; existing 8 endpoints untouched |
| **new** | `dto/portfolio/*` | response records |
| — | `service/paper/**`, `entity/Position.java`, `repository/PositionRepository.java` | **must not change** |

### Phase 3 — LIVE read-only sync

| Action | File | Why |
|---|---|---|
| **modify** | `exchange/ExchangeTradingAdapter.java` | add `getBalances`, `getOpenOrders`, `getAllOrders`, `getMyTrades` |
| **modify** | `exchange/binance/BinanceLiveTradingAdapter.java` | implement the above; **keep `getAccountBalance` for back-compat** |
| **modify** | `exchange/mock/MockExchangeTradingAdapter.java` | test fixtures for the new methods |
| **modify** | `exchange/futures/FuturesExchangeAdapter.java` | add `getPositions` (`/fapi/v2/positionRisk`), `getOpenOrders`, `getAllOrders`, `getUserTrades` |
| **modify** | `exchange/futures/binance/BinanceFuturesLiveAdapter.java` | implement; fix the hardcoded `fees=ZERO`/`feeAsset=null` |
| **modify** | `exchange/futures/mock/MockFuturesExchangeAdapter.java` | fixtures |
| **new** | `service/portfolio/LiveAccountSyncService.java` | REST snapshot → normalized rows |
| **modify** | `service/live/LiveTradingReconciliationService.java`, `service/futures/FuturesReconciliationService.java` | **additive** position/order/trade reconciliation; must keep observe-only semantics |

### Phase 4 — LIVE WebSocket sync

| Action | File | Why |
|---|---|---|
| **modify** | `market/BinanceArrayTickerStreamClient.java` | add a header-carrying variant for the user-data stream |
| **new** | `market/BinanceUserDataStreamClient.java` | listenKey obtain/keepalive/close; reconnect backoff |
| **new** | `market/BinanceUserDataEvent.java` + parser | `executionReport`, `outboundAccountPosition`, `balanceUpdate`, `ORDER_TRADE_UPDATE`, `ACCOUNT_UPDATE` |
| **new** | `service/portfolio/LiveEventIngestService.java` | idempotent upsert keyed on stable Binance ids |
| **new** | `entity/ExchangeEventDedup.java` + repository | idempotency ledger (`(exchange, eventType, eventId)`) |
| **modify** | `config/LiveTradingProperties.java`, `config/FuturesTradingProperties.java` | **activate the already-reserved dead `*StreamBaseUrl` fields**; add listenKey endpoints |
| **modify** | `market/AlertsWebSocketHandler.java` | publish normalized `portfolio` event types (reusing the existing socket) |

### Phase 5 — Portfolio API

| Action | File | Why |
|---|---|---|
| **modify** | `controller/PortfolioController.java` | **replace the stub**; add additive `mode`/`category` endpoints; **must** resolve the principal (currently Pattern C) |
| **new** | `service/portfolio/PortfolioQueryService.java` | unified read-model; aggregates paper + live + futures per `(mode, category)` |
| **new** | `dto/portfolio/PortfolioOverviewResponse.java`, `PortfolioBalanceResponse.java`, `PortfolioPositionResponse.java`, `PortfolioOrderResponse.java`, `PortfolioTradeResponse.java` | |
| — | `/api/v1/paper-trading/**`, `/api/v1/live-trading/**`, `/api/v1/futures-trading/**` | **must keep working unchanged** |

### Phase 6 — Flutter

| Action | File | Why |
|---|---|---|
| **modify** | `presentation/screens/portfolio/portfolio_screen.dart` | becomes the unified host |
| **new** | `presentation/screens/portfolio/portfolio_main_screen.dart` *(or inline)* | PAPER/LIVE selector + MAIN/SPOT/FUTURES/OPTIONS tabs |
| **new** | `presentation/providers/portfolio_controller.dart` | **single** aggregate notifier watching only the selected mode (avoids 3 × 10 s pollers) |
| **new** | `presentation/widgets/account_mode_selector.dart`, `account_category_tabs.dart`, `portfolio_summary.dart`, `balance_card.dart`, `connection_status_card.dart` | extract the triplicated private widgets |
| **modify** | `core/constants/api_constants.dart` | add unified portfolio paths |
| **new** | `data/datasources/portfolio_read_model_remote_data_source.dart` + repository | keep the **dead legacy** `portfolio_*` stack untouched or delete it deliberately — see §9 **D6** |
| **modify** | `presentation/screens/settings/settings_screen.dart` | the Live/Futures routes become reachable from Portfolio instead |
| — | `test/paper_trading_test.dart` | must preserve `Key('paper_capital_edit')` |

### Phase 7 — unified history

| Action | File | Why |
|---|---|---|
| **new** | `service/portfolio/PortfolioHistoryService.java` | strict `(mode, category)` separation, **never merged** |
| **new** | `entity/ExchangeTrade.java` + repository | persisted Binance fills (idempotent on `(exchange, tradeId)`) |
| **modify** | `controller/PortfolioController.java` | additive history endpoints |

### Phase 8 — Signal → Execution Router (PAPER only, LIVE disabled)

| Action | File | Why |
|---|---|---|
| **new** | `service/execution/ExecutionRouter.java` + `ExecutionTarget` | **lives in `service/execution/`, which `PaperLiveIsolationTest` does NOT scan** |
| **new** | `service/execution/PaperExecutionTarget.java` | delegates to `PaperTradingExecutionService` — **zero behaviour change** |
| **new** | `service/execution/LiveExecutionTarget.java` | **throws / returns a disabled result in Phase 8** |
| **new** | `service/execution/LiveTradingDisabledException.java` | explicit rejection reason, **never a silent fallback to Paper** |

**Deliberately NOT modified in Phase 8:** the 4 existing AFTER_COMMIT listeners. They keep working
unchanged; the router is introduced alongside and switched over only in Phase 9 behind a flag.

### Phase 9 — LIVE execution behind flags

| Action | File | Why |
|---|---|---|
| **new** | `service/execution/LiveExecutionTarget.java` (complete) | delegates to `LiveTradingExecutionService` / `FuturesExecutionService` |
| **modify** | `config/LiveTradingProperties.java`, `config/FuturesTradingProperties.java` | add `executionEnabled` (default **false**) |
| — | `LiveTradingRiskService`, `FuturesRiskService`, `LiveTradingExecutionService`, `FuturesExecutionService` | **unchanged** — the guards already exist and are proven |

### Phase 10 — reconciliation

| Action | File | Why |
|---|---|---|
| **new** | `service/portfolio/PortfolioReconciliationService.java` | REST snapshot vs local LIVE state; records a **status**, never silently overwrites |
| **new** | `entity/PortfolioReconciliationRecord.java` + repository | mismatch evidence |
| **modify** | `service/live/LiveTradingReconciliationService.java`, `service/futures/FuturesReconciliationService.java` | hand off to the shared service |

### Phase 11 — integration tests

| Action | File | Why |
|---|---|---|
| **new** | `src/test/.../portfolio/PortfolioAccountIsolationTest.java` | the 8 combinations + cross-contamination assertions |
| **new** | `src/test/.../portfolio/PortfolioApiIntegrationTest.java` | per-`(mode, category)` contracts |
| **new** | `src/test/.../portfolio/LiveSyncIdempotencyTest.java` | duplicate + out-of-order events |
| **new** | `src/test/.../portfolio/ExecutionRouterTest.java` | PAPER routes; LIVE blocked unless enabled |
| **modify** | `isolation/PaperLiveIsolationTest.java` | add a rule covering the new shared packages |
| **new** | `frontend/test/portfolio_screen_test.dart` | tab switching, loading/empty/disconnected/error/unsupported/live-status states |

### Explicitly NOT to be touched

`entity/Position.java`, `service/paper/**`, `service/live/**`, `service/futures/**` (Phase 8),
`signal/**`, `engine/**` (Spot + NFM), `service/SignalGeneratedEvent.java`, all notification code,
`service/BinanceOptionsMarketService.java`, `market/MarketBook.java` Options throws,
`config/SecurityConfig.java` route list, `app.news*`, `app.nfm*`, `service/research/**`.

---

## 9. Decisions required before Phase 1

These are genuine architectural forks that change the shape of every later phase. Discovery cannot
resolve them; they need an explicit decision.

**D1 — Naming collision on `AccountType`.** The target wants `accountMode{PAPER,LIVE}` +
`accountType{MAIN,SPOT,FUTURES,OPTIONS}`. `AccountType` already means `{PAPER,LIVE}` and is read by
`User`, `Portfolio`, `UserSettings`, 8 paper-service call sites and 4 derived queries.
→ Option A: add `AccountMode{PAPER,LIVE}` (duplicate values, migrate readers later) +
`AccountCategory{MAIN,SPOT,FUTURES,OPTIONS}`, leaving `AccountType` untouched (safest, some
duplication). Option B: rename `AccountType` → `AccountMode` and add a new `AccountType` (cleanest
end-state, breaks 8 call sites + 4 derived queries + `User` + `Portfolio` + Flutter
`PaperPosition.accountType` parsing). **Recommend A for backward compatibility.**

**D2 — How do PAPER MAIN/SPOT/FUTURES/OPTIONS partition the existing single `Portfolio` row?**
All paper SPOT and FUTURES positions share one balance today. Physical splitting would change
balances and violate master-prompt rules 9/20/26.
→ Option A (recommend): **read-model partition only** — one `Portfolio`, positions filtered by
`Signal.tradingMode`; `MAIN` = whole account; `OPTIONS` = empty + `UNAVAILABLE`. Zero balance impact.
Option B: physically split into per-category `Portfolio` rows — accurate allocation but a
behaviour change.

**D3 — LIVE `MAIN`.** Binance exposes no single "main wallet" balance.
→ Recommend `MAIN` = a **derived summary** (spot balances + futures wallet + `NOT_CONNECTED` /
`UNAVAILABLE` per unavailable leg), with every component labelled and no invented totals.

**D4 — Binance user-data stream: build it in Phase 4, or gate the whole plan on it?**
It is ~600+ lines of net-new code (listenKey lifecycle, header-carrying WS client, event parsing,
idempotency ledger) with **zero existing tests and zero existing adapter unit tests** to model.
→ Option A (recommend): Phases 1–3, 5–8 proceed independently; Phase 4 built after the read-model
is proven. Option B: build it first. **Recommend A** — Phases 1–3 already deliver most of the value
and are far lower risk.

**D5 — Production-safe migrations without Flyway.** Prod is `ddl-auto=validate` and no migration
tool exists. The research precedent is an out-of-band SQL file.
→ Option A (recommend): follow the existing precedent — a versioned, idempotent
`db/migration/V*.sql` applied out-of-band, documented in `docs/`, plus a JPA test asserting
nullable-column nullability. Option B: introduce Flyway (new runtime dependency, new baseline
story for an existing database).

**D6 — The dead legacy portfolio stack.** `/api/v1/portfolios`, `/api/v1/positions` and the Flutter
`Portfolio`/`Position`/`Transaction` stacks are stubs throwing 500, wired but unwatched.
→ Option A (recommend): **replace** the two backend stubs with the real unified implementation, and
**delete** the dead Flutter stacks + their DI/usecase files. Option B: keep them untouched and add a
parallel `/api/v1/portfolio/...` namespace.

**D7 — Does `ExchangeCredential` remain the single LIVE connection seam?** It is unique per
`(user, exchange)` and shared by the SPOT and FUTURES adapters, so one credential can drive both.
→ Option A (recommend): **reuse as-is**; add only a separate sync-status/last-synced record rather
than a new `AccountConnection` entity. Option B: introduce `PortfolioAccountConnection` anyway
(cleaner isolation, more duplication).

---

## 10. Defects and risks found during discovery — NOT fixed

**D-1. Backtesting 500 — confirmed regression at HEAD (out of Portfolio scope).**
`service/backtest/BacktestService.java:81` calls `run.setConfigurationJson(GSON.toJson(requestSnapshot))`.
Gson reflectively serializes `java.time.Instant`, which under JPMS throws
`JsonIOException → InaccessibleObjectException: Unable to make field private final long java.time.Instant.seconds accessible`.
Result: **HTTP 500 on `POST /api/v1/backtests`.** Minimal fix: serialize with Jackson (already a
transitive bean) or register a Gson `Instant` TypeAdapter; add a regression test.
*This contradicts `docs/BACKTESTING_MODULE_REPORT.md`, which reports the API working end-to-end.*
**Awaiting approval — outside Portfolio scope.**

**D-2. `ExchangeCredentialService.testConnection` is cosmetic.** It sets `CONNECTED` with a
hardcoded `SIMULATED_LATENCY_MS = 42` and **never calls `adapter.validateCredentials()`**. The UI
therefore shows "Connection verified" without any network call. Should the unified LIVE UI rely on
this endpoint? → **Recommend re-pointing it at `adapter.validateCredentials()` in Phase 3**, since
master-prompt rule 13 forbids faking LIVE data.

**D-3. Hardcoded default AES seed.** `SettingsProperties.DEFAULT_ENCRYPTION_SECRET_KEY` =
`dev-only-settings-encryption-key-seed-change-me`, and the same literal appears in
`application.yml:102`. Any deployment that omits `SETTINGS_ENCRYPTION_SECRET_KEY` encrypts every
stored exchange credential under a **publicly known key**, with no startup guard. Out of scope, but
material to requirement 29/30.

**D-4. `apiKey = null; secret = null` in a `finally` block is ineffective.** Java `String` is
immutable; the decrypted secret remains in the heap until GC. Real hardening needs `char[]` +
`Arrays.fill`. The code comment claiming otherwise is incorrect.

**D-5. `/ws/private` is unauthenticated** with `allowedOriginPatterns(..., "*")`. Pre-existing,
documented in `docs/NEWS_INTELLIGENCE_MODULE.md` §8/§13. The new portfolio event fan-out (Phase 4)
would inherit this — **do not widen it**; if account-scoped portfolio events are ever broadcast
here, auth must be added first.

**D-6. Zero unit tests for either live Binance adapter** (signing, param building, status mapping,
filter parsing, `-2013`/`-4046` handling, error classification). Phase 3 adds account read methods
to exactly these two untested classes. **Recommend writing adapter tests in Phase 3 before/with
them.**

**D-7. `PaperLiveIsolationTest` is substring-based.** A forbidden identifier inside a **comment**
fails the build. Any new shared code must live outside the four scanned packages.

**D-8. Per-asset spot balances are parsed then discarded.** `/api/v3/account` is already being
called; extracting all assets instead of only `USDT` is a small, high-value Phase 3 change.

---

## 11. Runtime status

| Check | Status |
|---|---|
| External backend JVM on `:8080` | **externally managed — not started, stopped, restarted or killed by this work** |
| `LIVE_RUNTIME_VALIDATION_BLOCKED` | **YES** |
| Code-level (compile + unit) verification | available |
| `gradlew test` / `gradlew build` | available |
| `flutter analyze` / `flutter test` | available |
| Real Binance credential verification | **BLOCKED** — no credentials, and must never be auto-executed |
| Paper runtime verification | possible **only** if the developer restarts the JVM (not performed here) |

Per master-prompt rule 24/25 and §26, any phase whose validation is blocked by the stale/down JVM
is **stopped and reported**, not worked around.

---

## 12. Phase 0 exit checklist

| Deliverable | State |
|---|---|
| Current architecture | ✅ §2–§5 |
| Current Portfolio flow | ✅ §2.1, §3.1, §5.1, §5.3 |
| Current Paper Trading flow | ✅ §3.2 |
| Current Binance integration | ✅ §4 |
| Current Spot/Futures/Options support | ✅ §4.2 (Spot partial, Futures partial, **Options none**) |
| Current WebSocket infrastructure | ✅ §4.1, §5.7 |
| Current database model | ✅ §2, §6.1 |
| Existing APIs | ✅ §3.1 (full endpoint index) |
| Exact files to modify | ✅ §8 |
| No code modified during discovery | ✅ |

**Phase 0 is complete. Phases 1–11 have not been started. Section 9 decisions are required before
Phase 1.**