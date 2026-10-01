# Portfolio Architecture — Phase 0 Discovery

Discovery only. No code modified. Source of truth = current repository at HEAD.

## 1. Current architecture (as-is)

**Backend account model**
- `entity/Portfolio` is the account; discriminator `entity/enums/AccountType {LIVE, PAPER}` (only 2 values).
- `entity/Position` belongs to a `Portfolio`; carries SPOT/FUTURES-ish fields (leverage, margin, liquidation_price, TP1/2/3, partial-exit columns).
- LIVE already modeled separately: `LiveTradingAccount`, `LiveOrder`, `LiveOrderLifecycleEvent`, `FuturesTradingAccount`, `FuturesOrder`, `FuturesPosition`, `FuturesOrderLifecycleEvent`, `ExchangeCredential`.
- There is **no** MAIN/SPOT/FUTURES/OPTIONS account-type dimension. `TradingMode {SPOT, FUTURES, OPTIONS?}` exists on signals/backtests, not on the account.

**Binance integration (exists, reuse — do not duplicate)**
- `exchange/binance/BinanceLiveTradingAdapter` (Spot LIVE), `BinanceSignatureUtil`.
- `exchange/futures/binance/BinanceFuturesLiveAdapter` (Futures LIVE).
- Market streams: `market/BinanceSpotTickerStreamClient`, `market/BinanceFuturesTickerStreamClient`, `config/MarketWebSocketConfig`.
- Credentials: `entity/ExchangeCredential` + settings/exchange-credential APIs, `ExchangeName`, `ExchangeConnectionStatus`.
- Config: `LiveTradingConfig/Properties`, `FuturesTradingConfig/Properties`, `MarketProperties`.
- **Options:** no Binance Options adapter found → treat as UNSUPPORTED (architecture/UI boundary only; no fabrication).

**Execution today**
- Signals publish `service/SignalGeneratedEvent`; listeners execute per account domain:
  - `service/paper/PaperTradingEngineService.onSignalGenerated(...)` → PAPER (existing, working).
  - `service/live/LiveTradingEngineService.onSignalGenerated(...)` → LIVE spot.
  - `service/futures/FuturesEngineService` → FUTURES.
- This is effectively a router-by-listener, not a single execution router; LIVE is guarded by activate/kill-switch/risk services.

**APIs (existing)**
- `PortfolioController` `/api/v1/portfolios` (list/getById).
- `PaperTradingController` `/api/v1/paper-trading` (account/capital/positions/history/performance/close/reset).
- `LiveTradingController` `/api/v1/live-trading` (account/connection[/validate]/activate/deactivate/kill-switch/orders[/id/cancel]/positions/{id}/close/history/performance).
- `FuturesTradingController` `/api/v1/futures-trading` (account/connection/acknowledge/activate/deactivate/kill-switch/orders/positions/history/close).
- `ExchangeCredentialController` (settings), `SignalController`, `PositionController`.

**Database**
- PostgreSQL; **no Flyway/Liquibase** (only `resources/db/research/V1__research_market_data.sql`).
- dev `ddl-auto: update`, prod `validate`. New nullable columns must be boxed Java types (cf. prior `tp*_hit` defect).

**Flutter**
- `presentation/screens/portfolio/portfolio_screen.dart` currently returns `PaperTradingScreen` (Portfolio tab is paper-only).
- Separate `live_trading_screen.dart`, `futures_trading_screen.dart`, `paper_trading_screen.dart` with their own controllers/repositories/datasources.
- Generic `portfolio_remote_data_source.dart` + `portfolio_model.dart` + `domain/entities/portfolio.dart` exist.
- `api_constants.dart` has `portfolios`, `live-trading`, `futures-trading`, `paper-trading` paths. Riverpod + Dio patterns established.

## 2. Gap vs target
1. No unified Portfolio screen with `[PAPER][LIVE] × [MAIN][SPOT][FUTURES][OPTIONS]`.
2. No account-type (MAIN/SPOT/FUTURES/OPTIONS) dimension on the account model.
3. LIVE exists but is not surfaced through Portfolio; no LIVE reconciliation snapshot.
4. No single Signal→Execution router abstraction (per-listener execution exists).
5. Options: unsupported → boundary only.
6. No explicit migration mechanism (prod `validate`).

## 3. Minimum files to modify (Phase 1–9)
**Backend**
- `entity/enums/AccountType.java` → extend or add `AccountMode {PAPER,LIVE}` + `AccountType {MAIN,SPOT,FUTURES,OPTIONS}` (additive; avoid breaking existing `AccountType{PAPER,LIVE}` usage — likely introduce `AccountMode` and repurpose carefully with a compatibility mapping).
- `entity/Portfolio.java` → add `accountType` (MAIN/SPOT/FUTURES/OPTIONS) nullable + `exchange`.
- New: `entity/AccountConnection` (or reuse `ExchangeCredential`/`LiveTradingAccount`) for LIVE sync status/lastSyncedAt.
- `service/portfolio/PortfolioQueryService` (new) → unify PAPER (existing paper services) + LIVE (Binance adapters) into MAIN/SPOT/FUTURES/OPTIONS views.
- `controller/PortfolioController.java` → add mode/type query params (additive; keep existing list/getById).
- `service/execution/ExecutionRouter` (new) wrapping existing paper/live/futures engine services (no behavior change; LIVE still gated).
- Reconciliation service (new) using existing Binance adapters.

**Frontend**
- `presentation/screens/portfolio/portfolio_screen.dart` → host PAPER/LIVE + MAIN/SPOT/FUTURES/OPTIONS; reuse existing paper/live/futures providers.
- New `presentation/providers/portfolio_controller.dart`, models, datasource methods in `portfolio_remote_data_source.dart`.
- Reusable components: AccountModeSelector, AccountTypeTabs, PortfolioSummary, BalanceCard, ConnectionStatus/SyncStatus.

**Migrations**
- Introduce a production-safe migration path (Flyway recommended) OR additive nullable columns applied explicitly; do not rely on `ddl-auto=update` for prod.

## 4. Phased plan (per master prompt)
0 Discovery (this doc) → 1 domain model → 2 Paper into PAPER+type → 3 LIVE read-only sync → 4 LIVE WebSocket idempotency → 5 Portfolio API → 6 Flutter → 7 unified history → 8 Signal→router (paper only) → 9 LIVE execution behind flags → 10 reconciliation → 11 integration tests.

## 5. Defects discovered during discovery (NOT fixed — code frozen for Phase 0)
- **Backtesting 500 (confirmed):** `BacktestService.java:81` `run.setConfigurationJson(GSON.toJson(requestSnapshot))` throws
  `com.google.gson.JsonIOException` → `InaccessibleObjectException: Unable to make field private final long java.time.Instant.seconds accessible`.
  Gson cannot reflectively serialize the request record's `java.time.Instant` fields under the JPMS. Results in HTTP 500 on `POST /api/v1/backtests`. Minimal fix: serialize with Jackson (already a bean) or add a Gson `Instant` TypeAdapter / exclude the snapshot; add a regression test hitting `POST /api/v1/backtests`. This is out of Portfolio scope and waits for approval.

## 6. Runtime
External backend JVM not managed. `LIVE_RUNTIME_VALIDATION_BLOCKED = YES` for LIVE Binance verification (no credentials; must never auto-verify).
