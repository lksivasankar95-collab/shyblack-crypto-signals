# Futures Trading Module — Implementation Report

**Status:** vertical slice shipped. Testnet runtime verification NOT PERFORMED.
**Baseline:** live-spot commit `2fdcb98`.
**Supported:** Binance USDT-M **FUTURES**, MARKET orders (entry + reduce-only
close + STOP_MARKET protective), **ISOLATED margin**, **ONE_WAY position mode**,
LONG and SHORT.
**Not supported (rejected at risk gate):** SPOT signals, CROSS margin, HEDGE
mode, unsupported order types, TP2/TP3 auto-placement.

---

## 1. Architecture

```
                       FUTURES SIGNAL (TradingMode.FUTURES)
                                    │
                     @TransactionalEventListener(AFTER_COMMIT)
                                    ▼
                           FuturesEngineService
                                    │
                                    ▼
                            FuturesRiskService
                    (SPOT/CROSS/HEDGE all refused here)
                                    │
                                    ▼
                        FuturesTradingSizingService
                     (risk-based; leverage → margin only)
                                    │
                                    ▼
                        FuturesLiquidationService
                       (SL vs. estimated liq. price)
                                    │
                                    ▼
                     setLeverage + setMarginMode on adapter
                                    │
                                    ▼
                        FuturesExecutionService
                     (createIntent → commit → submit)
                                    │
                                    ▼
                       FuturesExchangeAdapter
             MOCK (default, dev + CI) or Binance USDT-M signed
                                    │
                                    ▼
                            Entry ACK / FILL
                                    │
             ┌──────────────────────┴──────────────────────┐
             │                                             │
             ▼                                             ▼
      Persist FuturesPosition                Submit protective STOP_MARKET
      (leverage, margin, liq)                (REDUCE_ONLY; SELL for LONG,
             │                               BUY for SHORT)
             └──────────────────┬──────────────────────────┘
                                ▼
                    ProtectionStatus updated on position
                    (PENDING → PROTECTED or PROTECTION_FAILED)

    ┌──────────────────────────────────────────────┐
    │  FuturesReconciliationService (@Scheduled)   │
    │  REST poll of order + balance every 30s      │
    └──────────────────────────────────────────────┘

    Manual close: POST /positions/{id}/close
                  → cancel sibling SL
                  → REDUCE_ONLY market
                     (LONG → SELL, SHORT → BUY)
                  → mark position CLOSED once filled
```

## 2. Domain model

| Entity                        | Role                                                                            |
| ----------------------------- | ------------------------------------------------------------------------------- |
| `FuturesTradingAccount`       | Per user/exchange. Holds credential ref + safety limits + acknowledgement flag. |
| `FuturesOrder`                | Local shadow of a Binance Futures order. Tracks `side`, `positionSide`, `reduceOnly`, `leverage`. Unique (account, clientOrderId). |
| `FuturesPosition`             | LONG or SHORT position with quantity, entry, SL, TP, liq price, margin, protection status. |
| `FuturesOrderLifecycleEvent`  | Append-only audit ledger.                                                       |

Enums added: `FuturesOrderStatus`, `FuturesOrderType`, `FuturesOrderPurpose`,
`FuturesMarginMode`, `FuturesPositionMode`, `FuturesRiskReason`,
`FuturesProtectionStatus`, `FuturesPositionStatus`,
`FuturesLifecycleEventType`.

## 3. Configuration (`app.futures-trading`)

| Key                        | Default                              | Purpose                                    |
| -------------------------- | ------------------------------------ | ------------------------------------------ |
| `mode`                     | `MOCK`                               | Only `EXCHANGE` produces real orders.      |
| `rest-base-url`            | `https://testnet.binancefuture.com`  | USDT-M REST base — testnet by default.     |
| `stream-base-url`          | `wss://stream.binancefuture.com/ws`  | Reserved for user-data stream (next drop). |
| `recv-window-ms`           | `5000`                               | Signed-request recvWindow.                 |
| `max-leverage`             | `3`                                  | Deliberately conservative cap.             |
| `default-margin-mode`      | `ISOLATED`                           | Only mode implemented.                     |
| `required-position-mode`   | `ONE_WAY`                            | HEDGE accounts refused at activation.      |
| `default-max-notional`     | `200.00`                             | Per-trade guardrail (quote currency).      |
| `default-max-active`       | `2`                                  | Max concurrent futures positions.          |
| `default-daily-loss-pct`   | `5.00`                               | Blocks new entries below drawdown.         |
| `min-stop-distance-pct`    | `0.30`                               | Minimum SL distance from entry.            |
| `liquidation-buffer-pct`   | `15.00`                              | SL must sit outside `liq × (1 − buffer%)`.  |
| `auto-execute`             | `false`                              | Extra kill switch — signal fan-out OFF.    |
| `reconcile-interval-ms`    | `30000`                              | Order reconciliation cadence.              |
| `balance-refresh-ms`       | `60000`                              | Account balance sync cadence.              |

## 4. Exchange adapter

`FuturesExchangeAdapter` is the sole door between local Futures code and
Binance. Two implementations:

- **`BinanceFuturesLiveAdapter`** — signed `/fapi/v1/*` HMAC-SHA256 calls,
  parses LOT_SIZE / PRICE_FILTER / MIN_NOTIONAL / MARKET_LOT_SIZE from
  `/fapi/v1/exchangeInfo`. Includes `setLeverage`, `setMarginMode`, and a
  position-mode probe (`/fapi/v1/positionSide/dual`).
- **`MockFuturesExchangeAdapter`** — in-process simulator with idempotent
  clientOrderId, `forceFill` test hook, leverage/margin capture. Used by
  every CI test — no real Futures traffic.

Selection is done in `FuturesTradingConfig` via `app.futures-trading.mode`.
Default = MOCK.

## 5. Risk engine

`FuturesRiskService.check(user, account, signal)` short-circuits on the first
failing rule with a deterministic `FuturesRiskReason`. Order:

1. Account exists and activated (`ACCOUNT_NOT_ACTIVE`).
2. `enabled` (`FUTURES_DISABLED`).
3. `acknowledged` (`ACKNOWLEDGEMENT_REQUIRED`).
4. Kill switch off (`KILL_SWITCH_ENABLED`).
5. `connectionStatus == CONNECTED`.
6. `signal.tradingMode == FUTURES` (`INVALID_SIGNAL`).
7. `signal.side ∈ {LONG, SHORT}` and `stopLoss` present (`STOP_LOSS_REQUIRED`).
8. Margin mode = ISOLATED (`UNSUPPORTED_MARGIN_MODE`).
9. Position mode = ONE_WAY (`UNSUPPORTED_POSITION_MODE`).
10. No same-symbol/side existing position (`POSITION_ALREADY_EXISTS`).
11. No opposite-side position on the same symbol (also blocks hedge stacking).
12. `active positions < maxActivePositions` (`MAX_POSITIONS_REACHED`).
13. Daily loss not exceeded (`DAILY_LOSS_LIMIT`).

Leverage validation lives in `checkLeverage` — separate so the engine can
validate a specific requested value against `account.maxLeverage` capped by
`props.maxLeverage`.

## 6. Sizing (risk-based, leverage-aware for margin)

```
riskAmount   = availableBalance × riskPct / 100
stopDistance = |entry − stop|
qty          = riskAmount / stopDistance
qty          = SymbolRules.normalizeQuantity(qty)
notional     = qty × entry
notional     ≤ maxNotionalPerTrade  (or scale down qty)
initialMargin = notional / leverage
initialMargin ≤ availableBalance
```

**Leverage does not increase risk budget.** It only reduces required margin —
the stop-loss still bounds the actual dollar loss.

Covered by `FuturesTradingSizingServiceTest` including the invariant
`riskAmount(lev=1) == riskAmount(lev=3)`.

## 7. Liquidation safety

`FuturesLiquidationService`:

- `LONG  liq ≈ entry × (1 − 1/leverage)`
- `SHORT liq ≈ entry × (1 + 1/leverage)`
- Require `|SL − entry| < |liq − entry| × (1 − liquidationBufferPct/100)`.
- Require `|SL − entry| ≥ entry × minStopDistancePct / 100`.

We don't try to reproduce Binance's exact maintenance-margin formula (their
account has the real values). This is a **pre-trade sanity gate**; Binance
will still refuse if it decides margin is insufficient at submission time.

## 8. Order state machine

```
CREATED
  │ createIntent (committed BEFORE any HTTP)
  ▼
SUBMITTING ──► SUBMITTED ──► ACKNOWLEDGED ──► PARTIALLY_FILLED ──► FILLED
   │                                              │
   │                                              └─► CANCEL_REQUESTED ──► CANCELLED
   │
   └── exchange failure: REJECTED / FAILED / UNKNOWN (reconciler recovers)
```

Every transition writes a `FuturesOrderLifecycleEvent`. FILLED is only
possible with an exchange execution report — never fabricated.

## 9. LONG vs SHORT

- **LONG open:** `side=BUY`, `positionSide=LONG`, `reduceOnly=false`.
- **LONG close:** `side=SELL`, `positionSide=LONG`, `reduceOnly=true`.
- **SHORT open:** `side=SELL`, `positionSide=SHORT`, `reduceOnly=false`.
- **SHORT close:** `side=BUY`, `positionSide=SHORT`, `reduceOnly=true`.

Manual close, protective SL, and any close-driven order carries
`reduceOnly=true` so it can never accidentally open a reverse position.

## 10. Idempotency

`FuturesClientOrderIdGenerator` produces `SBF-<23-char base64url>` where the
digest input includes `"F|"` + userId + signalId + symbol + positionSide +
purpose. Same signal always collapses to one order regardless of duplicate
events / restarts / retries. DB uniqueness on `(account, clientOrderId)`
catches races.

Note the `SBF-` prefix — Futures ids can never collide with SPOT `SB-` ids.

## 11. Protective SL

After entry FILLED / PARTIALLY_FILLED, the engine submits a
`STOP_MARKET, reduceOnly=true` order for the executed quantity. The
sibling order's status drives `FuturesPosition.protectionStatus`:

- `ACKNOWLEDGED` / `SUBMITTED` → `PROTECTED`
- non-live / throw → `PROTECTION_FAILED` (surfaced in UI as
  *"PROTECTION FAILED — position is UNPROTECTED"*)

TP2/TP3 are captured on the signal but **not** placed. Documented limitation.

## 12. Manual close

`POST /api/v1/futures-trading/positions/{id}/close`:

1. Verify caller owns the position (IDOR-safe 404).
2. Cancel the sibling SL (if any).
3. Fetch `SymbolRules`, normalize position qty.
4. Build a deterministic manual-close clientOrderId — idempotent.
5. Persist intent → commit → submit MARKET REDUCE_ONLY (SELL for LONG, BUY for SHORT).
6. On FILL, close the local `FuturesPosition`, realize P&L using actual
   exit fill price, aggregate fees.

## 13. Reconciliation

`FuturesReconciliationService`:

- `@Scheduled(fixedDelay=30s)` reconciles every non-terminal order via
  `getOrder(...)`. Updates status + executed + avg fill + fees + writes a
  `RECONCILED` event.
- `@Scheduled(fixedDelay=60s)` refreshes wallet / margin / unrealized P&L
  for every enabled account.
- **Never** places or cancels exchange orders. Only observes.

Restart recovery: state is 100 % in the DB; on boot the reconciler catches up.

## 14. Isolation (Phase 33)

Three-way structural test — `PaperLiveIsolationTest`:

- `service.paper.*` must not import `service.live.*`, `service.futures.*`,
  or `com.shyblack.cryptosignals.exchange.*`.
- `service.live.*` must not import `service.paper.*` or `service.futures.*`.
- `service.futures.*` must not import `service.paper.*` or `service.live.*`.

Fails the build if anyone accidentally crosses a boundary.

## 15. Transaction boundaries

`createIntent`, `markSubmitting`, `applyResult`, `recordFailure`, `cancel` are
each `@Transactional(propagation = REQUIRES_NEW)` and hold the tx for
microseconds. The exchange HTTP call in `submit(...)` runs **between**
transactions with no DB lock held.

## 16. UI (Phase 31)

- Reachable from Settings → **Futures Trading** (does not touch the SPOT
  Live Trading screen).
- Badge: `LIVE • FUTURES` in red. Balance card shows wallet, available,
  used margin, unrealized P&L, max leverage, margin mode, position mode,
  funding paid, daily P&L.
- Safety panel: Live futures trading toggle + kill switch.
- Open positions list: LONG/SHORT chip with leverage (e.g. `LONG 3x`),
  entry / SL / liq price / margin / fees / funding / realized P&L +
  protection status row + `CLOSE POSITION` button.
- Open orders list: purpose + type + reduce-only badge, executed vs
  requested, cancel action for non-terminal orders.
- Enabling live trading requires a modal *"I UNDERSTAND — ENABLE"*.

## 17. Tests

**Backend (32 new tests, all green):**

- `FuturesRiskServiceTest` — 12 rules incl. SPOT rejection, hedge/cross
  rejection, both-side stacking rejection, leverage-cap.
- `FuturesTradingSizingServiceTest` — LONG/SHORT sizing, notional cap,
  margin insufficiency, zero stop distance, **`riskAmount(lev=1) == riskAmount(lev=3)`**.
- `FuturesLiquidationServiceTest` — safe/close-to-liq/min-stop rules for
  LONG and SHORT.
- `FuturesClientOrderIdGeneratorTest` — deterministic id, SBF- prefix,
  distinct per positionSide.
- `MockFuturesExchangeAdapterTest` — LONG/SHORT market fill, duplicate id
  idempotency, STOP_MARKET ACK, `setLeverage`, `setMarginMode`, `forceFill`.
- `PaperLiveIsolationTest` — now enforces paper / live-spot / live-futures
  three-way isolation.

Backend suite growth: **from 198 to 231 tests, 230 pass**. Only failure is
the pre-existing `NewsApiIntegrationTest` flake unrelated to this branch.

**Frontend (3 new tests, all green):**

- No-account → shows Connect CTA.
- Connected LONG position → shows `LIVE • FUTURES`, `LONG 3x` chip,
  `Protected by SL`, and tapping `CLOSE POSITION` invokes
  `closePosition('p1')` with confirmation modal.
- Enabling live futures requires acknowledgement modal, triggers
  `activate(acknowledged: true)`.

Total Flutter suite: **20 / 20 pass**. `flutter analyze` — 0 new issues.

## 18. Phase-42 Verification Ledger

| Gate                              | Status |
| --------------------------------- | ------ |
| Signal → engine listener          | ✅ @TransactionalEventListener(AFTER_COMMIT), only TradingMode.FUTURES |
| LONG lifecycle                    | ✅ BUY entry → SELL REDUCE_ONLY close/SL |
| SHORT lifecycle                   | ✅ SELL entry → BUY REDUCE_ONLY close/SL |
| Explicit acknowledgement gate     | ✅ `acknowledged` field + endpoint refuses without it |
| Leverage validation               | ✅ `checkLeverage` + `setLeverage` |
| Margin mode                       | ✅ ISOLATED default, CROSS rejected |
| Position mode                     | ✅ ONE_WAY enforced, HEDGE rejected |
| Liquidation safety                | ✅ `FuturesLiquidationService` |
| Symbol validation                 | ✅ LOT_SIZE / MARKET_LOT_SIZE / PRICE_FILTER / MIN_NOTIONAL cached |
| Idempotency                       | ✅ `SBF-` deterministic clientOrderId + unique(account, clientOrderId) |
| Order state machine               | ✅ 13 states, no illegal transitions |
| Partial fills                     | ✅ `executedQuantity` from exchange, protection sizes to actual qty |
| Actual fill P&L                   | ✅ `FuturesPosition.exitPrice` from exchange; `grossPnl` uses executed qty |
| Fees + funding tracking           | ✅ Separate `tradingFees` / `fundingFees` columns |
| Protective SL                     | ✅ STOP_MARKET REDUCE_ONLY + ProtectionStatus |
| TP1                               | ⚠ Not auto-placed in this drop (documented) |
| TP2 / TP3                         | ⚠ Deferred |
| Reconciliation                    | ✅ 30 s order poll + 60 s balance sync |
| User-data WebSocket               | ⚠ Deferred — REST reconciler covers |
| Restart recovery                  | ✅ 100 % DB-backed state |
| Kill switch                       | ✅ per-account + config-level `autoExecute` |
| Daily loss limit                  | ✅ session-based, day rolls at UTC midnight |
| REST API                          | ✅ 14 endpoints, all IDOR-safe |
| Cross-user 404                    | ✅ enforced in controller + close service |
| Backend tests                     | ✅ 32 new, all green |
| Flutter tests                     | ✅ 3 new, all green |
| Paper / SPOT / FUTURES isolation  | ✅ enforced at build time |
| Runtime testnet verification      | NOT VERIFIED — see runbook |
| UI                                | ✅ Separate Futures screen, LIVE • FUTURES badge |

## 19. Known limitations

1. **TP1 auto-placement** is deferred. TP is captured on the signal but the
   engine does not currently submit a protective TP order — only SL.
2. **User-data WebSocket** not implemented; reconciliation via REST fills the
   gap with 30 s latency.
3. **Multi-target TPs** entirely out of scope for this drop.
4. **HEDGE mode** is not implemented and is refused at activation.
5. **CROSS margin** is not implemented and is refused at the risk gate.
6. **Rate limiting / retry** — adapter throws `retryable=true` on 429 / 5xx
   but there is no queue in front. Ops must monitor before high-frequency
   scenarios.
7. **Testnet runtime verification** is not performed by CI. Follow the
   procedure in `FUTURES_TRADING_RUNBOOK.md` before flipping mode to
   EXCHANGE in production.
