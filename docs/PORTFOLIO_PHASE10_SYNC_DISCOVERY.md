# Portfolio Phase 10 — LIVE Sync Lifecycle Discovery

Scope: find why a LIVE Portfolio stays `NOT_CONNECTED` despite valid Binance
credentials, and identify the smallest correct fix.

This document is **discovery only**. No production change is described as
implemented here.

---

## 1. Git safety baseline (recorded before any edit)

`git stash list` → empty. `HEAD` = `b8c5994 fix(binance): scope-aware connection
validation with deterministic error classification`.

The working tree was **already dirty** in two independent sets before this task.

### Set A — Backtesting work, UNRELATED to Phase 10 (must not be touched or committed)

```
 M backend/src/main/java/com/shyblack/cryptosignals/config/BacktestingProperties.java
 M backend/src/main/java/com/shyblack/cryptosignals/config/ResearchBacktestProperties.java
 M backend/src/main/java/com/shyblack/cryptosignals/entity/BacktestSignal.java
 M backend/src/main/java/com/shyblack/cryptosignals/service/backtest/BacktestJobRunner.java
 M backend/src/main/java/com/shyblack/cryptosignals/service/backtest/BacktestLimits.java
 M backend/src/main/java/com/shyblack/cryptosignals/service/backtest/engine/BacktestEngine.java
 M backend/src/main/java/com/shyblack/cryptosignals/service/backtest/engine/BacktestExecutionSimulator.java
 M backend/src/main/java/com/shyblack/cryptosignals/service/backtest/engine/BacktestMetricsCalculator.java
 M backend/src/main/java/com/shyblack/cryptosignals/service/backtest/strategy/EmaRsiBacktestStrategy.java
 M backend/src/main/java/com/shyblack/cryptosignals/service/backtest/strategy/TrendPullbackBacktestStrategy.java
 M backend/src/main/resources/application.yml
 M backend/src/test/java/com/shyblack/cryptosignals/service/backtest/engine/BacktestMetricsCalculatorTest.java
 M frontend/lib/presentation/screens/backtesting/backtesting_screen.dart
 M frontend/test/backtesting_test.dart
?? backend/src/main/java/com/shyblack/cryptosignals/service/backtest/BacktestRunPersistence.java
?? backend/src/test/java/com/shyblack/cryptosignals/service/backtest/BacktestRunPersistenceTest.java
?? backend/src/test/java/com/shyblack/cryptosignals/service/backtest/BacktestRunPersistenceTransactionTest.java
?? backend/src/test/java/com/shyblack/cryptosignals/service/backtest/engine/BacktestEngineCorrectnessTest.java
```

**`application.yml` is in Set A.** Any Phase 10 property must therefore be added
without rewriting the file's existing content, and staging must be by explicit
path, never `git add -A`.

### Set B — Portfolio Phase 1–9 work (this feature's lineage, uncommitted)

All `controller/PortfolioController.java`, `dto/portfolio/*`, `service/portfolio/*`,
`exchange/*OrderSnapshot*`, `exchange/binance/Binance*Adapter`,
`exchange/*mock/Mock*`, the frontend portfolio files and the Phase 1–9 tests and
report.

### Phase 10 additions will be Set C

New and modified files created by this task only. **Staging must list Set C paths
explicitly.**

---

## 2. Root cause

**`LiveUserStreamManager`, `LivePortfolioSyncService` and `LivePortfolioReconcileService`
have zero production callers.**

An exhaustive search for those three types across `backend/src` returns matches only
inside the portfolio package itself, `PortfolioUserStreamConfig`,
`BinanceUserStreamConnector`, and test sources. There is no `@Scheduled`, no
`@EventListener`, no `ApplicationRunner`, no `@PostConstruct` and no controller that
reaches any of them.

The existing 8 `@Scheduled` methods in the backend are:

| Class | Annotation | Target |
|---|---|---|
| `SpotSignalScheduler` | `cron = SignalConstants.SIGNAL_CRON` | signals |
| `FuturesSignalScheduler` | `cron = SignalConstants.SIGNAL_CRON` | signals |
| `MarketCatalogService` | `initialDelay/fixedDelay = ${app.markets.exchange-info-refresh-ms:3600000}` | market catalog |
| `NewsIngestionScheduler` | `cron = ${app.news.sync-cron:0 */15 * * * *}` | news |
| `LiveTradingReconciliationService#reconcileOpenOrders` | `fixedDelay = ${app.live-trading.reconcile-interval-ms:30000}` | `live_orders` |
| `LiveTradingReconciliationService#refreshBalances` | `fixedDelay = ${app.live-trading.balance-refresh-ms:60000}` | `live_trading_accounts` |
| `FuturesReconciliationService#reconcileOpenOrders` | `fixedDelay = ${app.futures-trading.reconcile-interval-ms:30000}` | `futures_orders` |
| `FuturesReconciliationService#refreshBalances` | `fixedDelay = ${app.futures-trading.balance-refresh-ms:60000}` | `futures_trading_accounts` |

**None touches the portfolio read model.**

### The consequential consequence

`portfolio_exchange_balances` and `portfolio_exchange_positions` have **no
periodic writer whatsoever**. They are populated only by a direct call to
`LivePortfolioSyncService`. Consequently `PortfolioAccountReadService.liveSpotAvailability`
sees `connection == null` and returns `UNAVAILABLE`, and the Flutter screen shows a
paper-ish "Summary unavailable". A LIVE user with valid credentials sees an account
that never synchronises.

---

## 3. What already works and must be reused unchanged

| Concern | Component | Notes |
|---|---|---|
| REST baseline read | `LivePortfolioSyncService.syncSpot/syncFutures` | writes `portfolio_exchange_balances` / `portfolio_exchange_positions`; records status on the connection row |
| REST-before-stream ordering | `LiveUserStreamManager.start` | `reconcile(...)` then `connector.open(...)`; ERROR if reconcile fails |
| Reconnect ordering | `LiveUserStreamManager.handleDrop` | `RECONNECTING` → reconcile → `CONNECTED`/`ERROR` |
| Duplicate-drop suppression | `LiveUserStreamManager.handleDrop` | early return when already `RECONNECTING`/`ERROR` |
| Per-scope idempotency | `LiveUserStreamManager.sessions` + `BinanceUserStreamConnector.sessions` | keyed `userId:CATEGORY` |
| PAPER / MAIN / OPTIONS refusal | `LiveUserStreamManager.start` + `LiveUserStreamEventProcessor.process` | before any credential lookup or write |
| Event dedup | `PortfolioExchangeEventApplied` + DB unique constraint | restart-safe |
| High-water mark | `PortfolioAccountConnection.lastEventAt` | older events → `STALE_REJECTED` |
| Listen key lifecycle | `BinanceUserStreamConnector` | mint per (re)connect, 30 min keep-alive, backoff capped 60 s, drop signalled exactly once |
| Secret hygiene | `BinanceUserStreamConnector`, `LivePortfolioSyncService.describe` | listen key, URI, key and secret never logged |
| Failure classification | `LivePortfolioSyncService.describe`, `ExchangeConnectionClassifier` | auth codes `-2014/-2015/-1022`, `-1003`, HTTP 429/418, 5xx |

### Writer ownership that must not be duplicated

| Table | Sole writer(s) |
|---|---|
| `live_trading_accounts` | `LiveTradingReconciliationService.refreshBalances` (scheduled) + `LiveTradingAccountService` |
| `futures_trading_accounts` | `FuturesReconciliationService.refreshBalances` (scheduled) + `FuturesTradingAccountService` |
| `portfolio_exchange_balances` | `LivePortfolioSyncService.syncSpot` + `LiveUserStreamEventProcessor` |
| `portfolio_exchange_positions` | `LivePortfolioSyncService.syncFutures` + `LiveUserStreamEventProcessor` |
| `portfolio_account_connections` | `LivePortfolioSyncService.record`, `LiveUserStreamManager.updateConnection`, `LiveUserStreamEventProcessor` |

`LivePortfolioSyncService` already documents that it deliberately does not write
`LiveTradingAccount` / `FuturesTradingAccount` because those fields have another
owner. **The coordinator must go through `LivePortfolioSyncService` /
`LivePortfolioReconcileService` and never call an adapter to persist balances.**

---

## 4. Candidate trigger points

### 4.1 Primary — `ExchangeCredentialService.testConnection`

`POST /api/v1/settings/exchanges/{id}/test-connection` is the **only** production
path that reaches `credential.setStatus(CONNECTED)` after a real authenticated,
signed, read-only exchange read:

```java
boolean canTrade = scope == ValidationScope.FUTURES
        ? futuresAdapter.validateCredentials(credential).canTrade()
        : spotAdapter.validateCredentials(credential).canTrade();
credential.setStatus(ExchangeConnectionStatus.CONNECTED);
credentialRepository.save(credential);
```

Why this is the correct hook:

- it proves the exchange actually accepted the credential, which is exactly the
  precondition for synchronising;
- it already distinguishes credential-attributable failure from transport failure,
  so a laptop losing Wi-Fi never marks a good key broken;
- it does not place, cancel or modify an order — only an account read;
- it creates no new state: `ExchangeCredential`, `PortfolioAccountConnection`,
  `AccountMode` and `AccountCategory` all already exist.

### 4.2 Secondary — `SettingsService.update`

```java
user.setAccountType(request.accountType());   // SettingsService.java:101
```

This is the **only** PAPER↔LIVE switch in the system, and today it has **no
lifecycle side effect at all**. Switching to LIVE starts nothing; switching to PAPER
stops nothing.

### 4.3 Teardown — `ExchangeCredentialService`

`delete`, `disconnect` and `revoke` all exist on the service.
**`disconnect` and `revoke` have no controller endpoint today** — only `delete`
does (`DELETE /api/v1/settings/exchanges/{id}`).

---

## 5. Adjacent gap discovered (recorded, not necessarily in scope)

`LiveUserStreamEventProcessor` returns `Outcome.RECONCILE_REQUIRED` for spot
`balanceUpdate` (deposit/withdrawal) and when a payload omits its balance array. It
then sets the connection row to `availability = STALE`. **Nothing anywhere reads
`STALE` and reacts by reconciling.** `LiveUserStreamManager` reconciles on *drop*,
not on *staleness*.

So even with a start trigger, a deposit would leave the portfolio permanently stale
until the next disconnect. This is a real defect in the same lifecycle and is
reported here so it is a conscious decision, not an oversight.

---

## 6. Truthfulness constraint discovered

`BinanceUserStreamConnector.open()` is **non-blocking**: it registers a session and
schedules `connect()` at delay 0, so the returned `Handle.isOpen()` is `false` until
the socket is actually up. It also never throws for a socket problem — it logs and
schedules a reconnect.

Therefore `LiveUserStreamManager.start()` returning `CONNECTED` means *"a stream was
registered"*, not *"the socket is live"*.

The task requirement "never show LIVE CONNECTED before the initial REST baseline
succeeds" **is** satisfied by the existing code, because `reconcile()` must succeed
before `connector.open()` is reached. But the stronger statement "stream readiness"
cannot be truthfully asserted at `start()` return time. This must not be overstated
in the UI or the report.

---

## 7. Existing test infrastructure to reuse

| Asset | Path | Reuse |
|---|---|---|
| `FakeConnector implements UserStreamConnector` | `LiveUserStreamManagerTest` (private inner, line 491) | records requests per scope, counts `open()`, can inject payloads and disconnects. Must be extracted to a shared test double so a `@SpringBootTest` can override the bean. |
| `MockExchangeTradingAdapter` / `MockFuturesExchangeAdapter` | `exchange/*/mock/` | `reset()`, `putBalance`, `putPosition`, `putTrade`, `putIncome`, `clearSeeded()` |
| `FakeExchangeHttp` | `exchange/binance/` (test) | loopback `127.0.0.1` HTTP server for adapter tests |
| `ExchangeCredentialServiceConnectionTest` | `service/` | credential validation with mock adapters |
| `LivePortfolioSyncServiceTest` | `service/portfolio/` | REST baseline outcomes |
| `LiveUserStreamManagerTest` | `service/portfolio/` | start/stop/reconnect/idempotency |

---

## 8. Available kill switches and gaps

Existing flags in `application.yml`:

| Property | Default |
|---|---|
| `app.live-trading.mode` | `MOCK` |
| `app.live-trading.auto-execute` | `false` |
| `app.futures-trading.mode` | `MOCK` |
| `app.futures-trading.auto-execute` | (present, default false) |
| `app.markets.websocket-enabled` | `true` (matchIfMissing) |
| `app.news.enabled` | `true` |

**There is no flag for portfolio synchronization.** One is required, and it must
default to **off** so that merely deploying this code cannot open exchange streams,
and so the test profile cannot open sockets.

Precedent for self-startup exists and should be reused rather than invented:
`SystemStrategySeeder` and `ResearchImportRunner` implement `ApplicationRunner`;
`BinanceSpotTickerStreamClient` / `BinanceFuturesTickerStreamClient` use
`@EventListener(ApplicationReadyEvent.class)` guarded by `@ConditionalOnProperty`.

---

## 9. Discovery conclusion

The gap is exactly one missing edge: **nothing calls `LiveUserStreamManager.start`.**

Every mechanism downstream of it — REST-before-stream ordering, reconnect with
reconciliation, dedup, high-water mark, per-scope idempotency, PAPER/OPTIONS/MAIN
refusal, secret hygiene, failure classification — already exists and is already
tested. The fix must be a thin coordinator that:

1. triggers `start` when a LIVE credential is actually validated, and when Settings
   switches an account to LIVE;
2. triggers `stop` when the credential is deleted, revoked or disconnected, and when
   Settings switches an account back to PAPER;
3. never starts for PAPER, OPTIONS or MAIN;
4. is idempotent, and delegates all real work to the existing components.

Nothing in the existing lifecycle needs redesigning.