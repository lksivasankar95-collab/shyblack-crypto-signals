# Market Identity & Price Source Report

Scope: make Binance market handling market-aware end to end so that SPOT and FUTURES
instruments, prices, candles, WebSocket events, signals, paper positions and UI can no
longer collide when both markets use the same symbol (`BTCUSDT`).

Status: implemented, tested, verified against the live backend on `8080`.

---

## 1. Root cause

`BTCUSDT` names two different instruments at the same moment — a spot pair and a USDT-M
perpetual — at different prices. The codebase stored only the symbol. Every failure found
had the same shape: a layer took the symbol, dropped the market, and resolved against
whichever market it happened to check first.

The decisive case, confirmed against live data:

| | Spot | Futures |
|---|---|---|
| exchangeSymbol | BTCUSDT | BTCUSDT |
| price | 84608.19 | 84573.80 |

A 0.04% basis. Small enough to look like noise and large enough to silently mis-price a
position, which is exactly why it survived until now.

## 2. Defects found and fixed

### 2.1 Paper trading priced positions from the wrong market

`PaperTradingQueryService.currentPrice(Position)` probed spot first and fell back to
futures. A spot-first fallback is not a neutral default — it is a rule that *every futures
position gets marked to spot*.

Compounding it, `PaperTradingEngineService` registered one tick handler against both
markets' stores and the handler discarded which store produced the tick. Position lookup
then matched on symbol alone. Net effect: **a futures tick could close a spot position** on
a stop-loss the spot market never reached. `Position` had no market column, so there was
nothing to disambiguate with.

Fixed: `Position.trading_mode`, stamped from the signal at open; tick batches now carry
their market; position selection filters on it; price resolution is a strict per-market
lookup with no cross-market fallback.

### 2.2 Live order sizing read the spot price for futures signals

`LiveTradingEngineService.liveReferencePrice` used `spotTickers()` regardless of the
signal's market, so a futures order was sized against the spot price — a quantity error,
not just a display issue. Fixed to resolve on the signal's own market.

### 2.3 Spot candles drove futures signal generation

`FuturesSignalEngine.detectMarketRegime()` read BTC 4h **spot** klines to decide the
regime that governed futures LONG/SHORT generation. Spot and futures BTC trend
differently and perpetuals carry basis and funding that spot does not.

Fixed to read futures klines. **Only the data source changed** — the classification rules
and every strategy parameter are untouched. This does change generated signals relative to
before, because it removes a defect, not because logic was retuned.

### 2.4 Markets browser could only ever show spot

`markets_controller.dart` used `const mode = kAppMarketMode`, pinned to spot. The domain
and repository layers were already mode-parameterised; the UI discarded it, so the futures
universe was unreachable. The domain/repository implementation was correct and simply not
used.

### 2.5 Paper UI priced positions off whichever market was on screen

The position card resolved its live price from the Markets view's ticker map. That map
holds one market at a time, so a futures position was priced from the spot stream — or from
whatever market the user happened to be browsing. A mixed portfolio could not be displayed
correctly at all.

### 2.6 Identity metadata was discarded at the exchange boundary

`exchangeInfo` returns `baseAsset`, `quoteAsset`, `contractType` and settlement date. The
futures client read `contractType` only to *filter* on it, then threw it away. There was
no `marketType`, no `displaySymbol`, and no contract identity anywhere in the payload, so
the client had to guess — which it did, by slicing `USDT` off the symbol.

### 2.7 Defect introduced and caught during this work

The first implementation rendered perpetuals as `BTCUSDT Perpetual 2100-12-25`. Binance
does not omit a perpetual's settlement date; it sends a sentinel (`0` for USDⓈ-M, a
year-2100 value elsewhere). Taken literally that fabricates an expiry on a contract that
has none. Caught by live verification, fixed at the source *and* normalised in
`MarketInstrument` so no caller can reintroduce it, with a regression test.

## 3. Identity model

`MarketInstrument` (backend) is the unit of identity: `marketType`, `exchangeSymbol`,
`baseAsset`, `quoteAsset`, `contractType`, `contractExpiry`. `marketType` is non-null and
rejects construction without it — a symbol alone is not an instrument.

Composite key: `marketType` + `exchangeSymbol` + contract identity.

Display labels are derived, never hand-written:
- Spot → `BTC/USDT`
- Perpetual → `BTCUSDT Perpetual`
- Dated → `BTCUSDT Quarterly 2026-12-25`

A perpetual is deliberately *not* collapsed to `BTC/USDT`; doing so would make it
indistinguishable from the spot pair sharing its symbol.

## 4. Where the market is now bound

| Layer | Change |
|---|---|
| `MarketTickerStore` | Bound to one market at construction; rejects null/OPTIONS. Listeners receive the market as a parameter. |
| `UsdtSymbolDirectory` | Bound to one market; refuses a foreign-market instrument. Stores full identity. |
| `MarketBook` | Single place mapping market → store/directory. Added `ticker(mode, symbol)` and `instrument(mode, symbol)`. |
| `Position` | New `trading_mode` column + `effectiveTradingMode()`. |
| `MarketTicker` (Flutter) | Carries `marketType`/`displaySymbol`/`contractType`/assets; equality includes `marketType`. |
| Provider families | Keyed by `MarketRef(symbol, market)` / `KlineQuery.market`, not bare symbol. |

## 5. Markets UI

`SPOT` / `FUTURES` selector added above the existing tabs. "All Markets" renamed to
"Markets".

There is deliberately **no** merged view. A combined list would place the spot pair and
the perpetual side by side as if they were one instrument, and tapping one would silently
choose which market is meant — the decision the selector exists to make explicit.

`Watchlist` and `New Listings` were left in place (both were pre-existing placeholders,
not in scope).

## 6. Price integrity rules applied

- Display price, signal price and paper price resolve on the **same** market identity.
- No symbol-only probe anywhere in a price path.
- No cross-market fallback in a price lookup — missing returns null/recorded value, never
  another market's number.
- A ticker whose `marketType` contradicts the stream it arrived on is dropped.
- WebSocket payloads declaring a different market than their socket are not trusted.

## 7. Schema change

`V5__position_market_identity.sql` — additive only:
- `positions.trading_mode`, backfilled from the linked signal's `trading_mode` (the
  authoritative answer, not a guess), defaulting to `SPOT` for rows with no signal.
- Index on `(trading_mode, status)`.

Legacy rows with no recoverable market are recorded as defaulted rather than silently
assumed. There is no Flyway runner in this project; migrations are applied out of band,
consistent with V1–V4. The running instance reported `trading_mode` in its positions
schema at startup.

## 8. Test results

| Suite | Before | After | Result |
|---|---|---|---|
| Backend | 1138 | **1156** | 0 failures, 2 skipped |
| Flutter | 145 (+2 skipped) | **163** (+2 skipped) | all passed |
| `flutter analyze` | 3 infos | 3 infos | 0 errors, 0 warnings (infos pre-existing) |

New tests: `MarketIdentityCollisionTest` (8), `PaperTradingMarketIsolationTest` (7),
`MarketTickerStoreTest` (+3), `market_identity_test.dart` (15),
`markets_mode_selector_test.dart` (1), `paper_trading_test.dart` (+2).

The required synthetic collision case is covered on both sides using distinct prices
(spot `100000`, futures `100500`) so a cross-market read is detectable rather than
coincidentally equal. Includes: cache isolation, listener market scoping, directory
rejection of foreign instruments, paper price resolution per market, per-position tick
isolation, display-label distinctness, and perpetual-expiry normalisation.

## 9. Live verification (backend PID 19668, port 8080)

Nothing was listening on `8080` beforehand, so the instance was **started, not restarted**.
`8081` was not used. No credentials, orders, or account-scoped endpoints were touched —
public market data only.

REST `GET /api/markets?mode=SPOT` and `?mode=FUTURES`:

```
SYMBOL          : BTCUSDT  /  BTCUSDT      identical
MARKET TYPE     : SPOT     /  FUTURES      distinct
DISPLAY SYMBOL  : BTC/USDT /  BTCUSDT Perpetual   distinct
CONTRACT TYPE   : <null>   /  PERPETUAL
UNDERLYING/QUOTE: BTC/USDT  /  BTC/USDT
PRICE           : 84608.19 /  84573.80     distinct
UNIVERSE        : SPOT=503  FUTURES=528
```

WebSocket `ws://localhost:8080/ws/markets?mode=…`:

```
SPOT    socket: 503 tickers, every ticker marketType=SPOT,     no contractType leaked
FUTURES socket: 528 tickers, every ticker marketType=FUTURES, PERPETUAL
same exchange symbol across sockets: true
different prices across sockets    : true
```

Catalog: `Loaded 503 Spot USDT instruments` and `Loaded 528 USDT-M Futures USDT
instruments from exchangeInfo`. Log confirms `[Paper] engine wired to spot + futures
ticker streams (market-scoped)`.

## 10. UI verification

Flutter web server on `5555` (PID 8736), serving `index.html` HTTP 200.

The Markets selector is covered by `markets_mode_selector_test.dart`, which renders the
real `MarketsScreen` and asserts the `SPOT`/`FUTURES` selector is present, "All Markets"
is absent, spot is the default, and selecting `FUTURES` requests the futures universe.

**Not performed:** visual inspection in a real browser. No browser automation is available
in this environment, so the selector was verified through the widget-test harness and a
served HTTP 200 rather than by eye. A human should eyeball
`http://localhost:5555` before sign-off.

## 11. Not changed, deliberately

- No strategy parameters, thresholds, or entry/exit logic.
- No paper-trading semantics (SL-first, partial exits, sizing, fees) beyond market scoping.
- No notification format changes.
- No unrelated module refactors.
- No real orders; `[LIVE]` and `[FUTURES]` remain in MOCK mode per startup logs.

## 12. Honest caveats

1. **Signal output will change.** Removing spot candles from futures regime detection is a
   correctness fix, but it means futures signals are no longer bit-identical to before.
   This is the intended consequence, not a regression to be reverted.
2. **Coin detail lost a subtitle fallback.** `baseSymbol` previously stripped `USDT` off
   the symbol. Slicing a quote asset out of a symbol is a guess that breaks on any
   non-USDT market, so it now reads `baseAsset` from Binance metadata and falls back to
   the exchange symbol. Test fixtures were updated to carry realistic metadata.
3. **Live visual UI check outstanding** (see §10).
4. **Paper positions opened before this change** carry a defaulted market. The migration
   backfills from the signal where one exists; manually-opened positions cannot be
   recovered from the database and remain `SPOT`. This is stated in the migration rather
   than hidden.
5. **Concurrency in the working tree.** A separate, unrelated workstream is editing
   `PaperTradingAccountService` plus new `PaperCapital*` files in the same tree (files
   changed at 06:48–06:50, mid-test-run). Nothing in this task touched them. The full
   suite passes with them present, but the working tree is **not** attributable solely to
   this task.

## 13. Commit status

**Nothing has been committed.** The task instruction was explicit: do not commit unless
requested. This also conflicts with `.cursor/rules/automation.mdc` and
`docs/PROJECT_RULES.md`, which auto-commit and auto-push after each task; the explicit
instruction wins, and the conflict is flagged rather than silently resolved. Because of
the concurrent workstream in §12.4, committing selectively would risk capturing or
stranding someone else's in-progress changes.