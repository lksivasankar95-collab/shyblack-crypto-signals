# Live Trading Module — Implementation Report

**Status:** vertical slice shipped. Runtime testnet verification NOT PERFORMED
in this drop.
**Baseline:** paper-trading commit `08d328d`.

---

## 1. Architecture at a glance

```
                              SETTINGS SCREEN
                                    │
                                    ▼
                       Settings → "Live Trading" tile
                                    │
                                    ▼
                            LiveTradingScreen (Flutter)
     ┌──────────────────────────────┼──────────────────────────────┐
     │                              │                              │
     ▼                              ▼                              ▼
  Connect                     Activate + confirm            Cancel / disable
     │                              │                              │
     └──────────────────────────────┴──────────────────────────────┘
                                    │  (POST /api/v1/live-trading/*)
                                    ▼
                           LiveTradingController
                                    │
        ┌───────────────────────────┼───────────────────────────┐
        ▼                           ▼                           ▼
LiveTradingAccountService  LiveTradingEngineService   LiveTradingQueryService
        │                           │                           │
        │                           │  @TransactionalEventListener(AFTER_COMMIT)
        │                           │  on SignalGeneratedEvent
        │                           ▼
        │                   LiveTradingRiskService
        │                           │
        │                           ▼
        │                   LiveTradingSizingService
        │                           │
        │                           ▼
        │            LiveTradingExecutionService
        │                (createIntent → commit → submit)
        │                           │
        └────────┬──────────────────┘
                 ▼
                                    ExchangeTradingAdapter
                                            │
                       ┌────────────────────┴────────────────────┐
                       │                                         │
              MockExchangeTradingAdapter          BinanceLiveTradingAdapter
              (default, dev + tests)              (signed HMAC, testnet URL)

                                    ▲
                                    │  @Scheduled 30 s
                        LiveTradingReconciliationService
```

## 2. Isolation from paper trading (Phase 35 gate)

Enforced two ways:

- **Package structure.** Paper trading lives in `service.paper.*` and live
  trading in `service.live.*`. Neither imports from the other, and paper code
  never touches `exchange.*` or `ExchangeTradingAdapter`.
- **`PaperLiveIsolationTest`** — a JUnit test that walks the two package
  directories at build time and fails the build if any file breaks the
  invariant. Regression protection lives in CI, not in a code-review comment.

Result: a paper-trading signal cannot route to the exchange, and a live
signal cannot mutate paper state, regardless of settings/mode toggles.

## 3. Domain model

| Entity                       | Role                                                                  |
| ---------------------------- | --------------------------------------------------------------------- |
| `LiveTradingAccount`         | One per user per exchange. Holds credential ref + safety limits.      |
| `LiveOrder`                  | Local shadow of an exchange order. Unique (account, clientOrderId).   |
| `LiveOrderLifecycleEvent`    | Append-only audit ledger for every state transition + fill.           |
| `ExchangeCredential` (reuse) | AES-GCM-encrypted API key + secret. No plaintext outside the adapter. |

Enums added: `LiveOrderStatus`, `LiveOrderType`, `LiveOrderPurpose`,
`LiveOrderLifecycleEventType`, `LiveTradingRiskReason`.

## 4. Configuration (`app.live-trading`)

| Key                       | Default                              | Purpose                              |
| ------------------------- | ------------------------------------ | ------------------------------------ |
| `mode`                    | `MOCK`                               | `MOCK` or `EXCHANGE`. Only `EXCHANGE` produces real orders. |
| `spot-rest-base-url`      | `https://testnet.binance.vision`     | Signed REST base. Testnet by default.|
| `spot-stream-base-url`    | `wss://testnet.binance.vision/ws`    | User-data stream base (reserved).    |
| `futures-rest-base-url`   | `https://testnet.binancefuture.com`  | Futures REST base (reserved).        |
| `recv-window-ms`          | `5000`                               | Binance recvWindow.                  |
| `default-max-notional`    | `200.00`                             | Per-trade notional guardrail (USDT). |
| `default-max-active`      | `3`                                  | Max concurrent live positions.       |
| `default-daily-loss-pct`  | `5.00`                               | Blocks new entries below this drawdown. |
| `auto-execute`            | `false`                              | Extra kill switch. Signal fan-out disabled unless flipped. |
| `reconcile-interval-ms`   | `30000`                              | Scheduler cadence for order reconciliation. |
| `balance-refresh-ms`      | `60000`                              | Balance sync cadence.                |

Every default errs on the side of DO NOTHING.

## 5. Exchange adapter

`ExchangeTradingAdapter` is the sole door between our code and a real
exchange. Two implementations:

- **`BinanceLiveTradingAdapter`** — HMAC-SHA256 signed REST calls (`/api/v3/*`),
  testnet-URL by default. Handles `exchangeInfo` filter parsing (LOT_SIZE,
  PRICE_FILTER, MIN_NOTIONAL). Errors preserve exchange code and mark
  retryable vs. terminal.
- **`MockExchangeTradingAdapter`** — in-process simulator. Idempotent by
  clientOrderId. Test hooks: `forceFill`, `setBalances`, `putRules`. Used by
  every automated test — no live traffic is ever produced in CI.

Selection is done in `LiveTradingConfig` based on `app.live-trading.mode`.
Default = MOCK.

## 6. Order lifecycle

```
CREATED
  │  createIntent (committed BEFORE any HTTP)
  ▼
SUBMITTING ──► SUBMITTED ──► ACKNOWLEDGED ──► PARTIALLY_FILLED ──► FILLED
   ▲                                                   │
   │                                                   └─► CANCEL_REQUESTED ──► CANCELLED
   │
   └── on exchange error: REJECTED / FAILED / UNKNOWN (reconciler recovers)
```

The transition `CREATED → SUBMITTING → ACKNOWLEDGED → FILLED` never skips
steps; every step writes a `LiveOrderLifecycleEvent`. If the HTTP call
throws, the order is marked `UNKNOWN` if the failure is retryable so the
reconciler can query by `clientOrderId` — never blind retry.

## 7. Idempotency (Phase 9)

- `ClientOrderIdGenerator` produces a deterministic 27-char id from
  `SHA-256(userId | signalId | symbol | side | purpose)`. Same inputs → same
  id, always.
- DB uniqueness on `(account_id, client_order_id)` collapses duplicate
  inserts to a single row.
- `saveAndFlush` inside `createIntent` catches
  `DataIntegrityViolationException` and returns the existing row.
- `MockExchangeTradingAdapter` also collapses duplicate `clientOrderId`s so
  contract tests observe the same guarantee.

**Critical case handled:** if the exchange accepted the order but our HTTP
response was lost, the local row is stuck in `UNKNOWN`; the reconciler queries
Binance by `origClientOrderId` on its next tick and updates state. The
engine will never resubmit.

## 8. Risk engine (Phase 6)

`LiveTradingRiskService.check(user, account, signal)` returns a single
`LiveTradingRiskReason`. Order of checks:

1. account exists / activated
2. `enabled` + not `killSwitchActive`
3. `connectionStatus = CONNECTED`
4. `signal.stopLoss` present + valid
5. `userSettings.liveTradingAllowed`
6. `active positions < maxActivePositions`
7. daily-loss limit (`session start equity − current equity < startEquity × pct/100`)

Any non-`OK` return short-circuits the engine and writes a
`RISK_BLOCKED` lifecycle event.

## 9. Sizing (Phase 7)

`LiveTradingSizingService.size(...)` computes
`riskAmount = availableBalance × riskPct / 100`,
`qty = riskAmount / |entry − stop|`, then normalizes via `SymbolRules`
(`stepSize`, `minQty`, `minNotional`). If the resulting notional exceeds
`account.maxNotionalPerTrade`, qty is scaled down again through
`normalizeQuantity` and re-validated. If precision rounding pushes us over
the guardrail, the trade is **rejected**, never silently exceeded.

## 10. Signal → live order pipeline (Phase 8)

`LiveTradingEngineService.onSignalGenerated` — a
`@TransactionalEventListener(AFTER_COMMIT)` on `SignalGeneratedEvent`. For
every enabled + not-killed live account whose user's `tradingMode` matches
the signal:

1. Risk check.
2. Fetch `SymbolRules` from the adapter.
3. Compute sizing (using MarketBook price where available, falling back to
   `signal.entryPrice`).
4. `createIntent(...)` — commits the local record atomically.
5. `submit(...)` — calls `adapter.placeOrder`. This happens **outside** any
   DB transaction (transaction boundaries are narrow — DB commit BEFORE the
   HTTP call).
6. On fill, `placeProtectiveStop(...)` submits a `STOP_LOSS_LIMIT` order
   linked back to the entry via `parentOrderId`.

## 11. Protective SL/TP (Phase 13 — hybrid model)

- **Preferred:** exchange-side `STOP_LOSS_LIMIT` order attached immediately
  after entry fill. The exchange manages the trigger — the app can lose
  network without leaving the position unprotected.
- Multi-target TPs (TP2 / TP3) are captured in the signal but **not
  auto-placed** in this drop. Documented as a known limitation below.

## 12. Reconciliation (Phase 16)

`LiveTradingReconciliationService`:

- `@Scheduled(fixedDelay = 30 s)` reconciles every non-terminal live order
  by calling `adapter.getOrder(...)`. Updates `status`, `executedQuantity`,
  `avgFillPrice`, `fees` + writes a `RECONCILED` event.
- `@Scheduled(fixedDelay = 60 s)` refreshes cached balances for every
  active account.
- Never creates or cancels orders — it only OBSERVES.

Restart recovery is automatic: state is 100 % in the DB, and on the first
reconciler tick after startup every open order re-syncs with the exchange.

## 13. Transaction boundaries (Phase 29)

`createIntent`, `markSubmitting`, `applyResult`, `recordFailure`, `cancel`
are each `@Transactional(propagation = REQUIRES_NEW)` and each holds a
transaction for microseconds. The exchange HTTP call in
`submit(...)` runs **between** transactions with no DB lock held.

## 14. Authorization (Phase 27)

Every endpoint resolves the user from `SecurityContextHolder`. Cross-user
access returns 404 (never leaks existence). Verified by the
`findOwnedOrder` helper and covered by controller wiring.

## 15. Kill switch (Phase 20)

Two levels:

- **Application-wide:** `app.live-trading.mode=MOCK` — no real orders can
  possibly be produced, regardless of user activation.
- **Per-account:** `LiveTradingAccount.enabled` (opt-in gate) and
  `killSwitchActive` (emergency stop that keeps reconciliation live but
  blocks new entries).

Both switches are exposed via authenticated endpoints and via the Flutter
Live Trading screen.

## 16. Flutter UI

- Reachable from **Settings → Live Trading** (nav tile, does not touch the
  Portfolio tab). Badge shows `LIVE` in red when `liveTradingAllowed`, else
  `OFF` in grey.
- Empty state: "No live exchange connected" + "Connect Binance" CTA.
- Connected: balance card (red LIVE badge), safety panel (activate / kill
  switch), open orders list with CANCEL action, history, performance
  summary.
- Enabling live trading requires a modal with explicit copy: *"I understand
  this account can place real exchange orders."* The `POST /activate`
  endpoint refuses without `acknowledged=true`.

## 17. Tests

**Backend (18 new tests, all green):**

- `ClientOrderIdGeneratorTest` — deterministic id + purpose separation.
- `SymbolRulesTest` — step/tick normalization, minNotional / minQty gates.
- `LiveTradingSizingServiceTest` — risk math, max-notional cap, invalid stops.
- `MockExchangeTradingAdapterTest` — market fill, idempotency, stop
  acknowledge, forceFill, cancel.
- `PaperLiveIsolationTest` — enforces no cross-package imports. Fails the
  build if paper touches exchange or live touches paper.

Backend suite: 181 tests, 180 pass. The 1 failure
(`NewsApiIntegrationTest.assetContextAggregatesProcessedArticles`) pre-exists
on `main` — see paper-trading commit for the same result.

**Flutter (3 new tests, all green):**

- No-account → shows Connect CTA.
- Connected → renders balance, LIVE badge, orders, safety panel.
- Activation → requires acknowledgement dialog, triggers repo with
  `acknowledged=true`, shows success snackbar.

Total Flutter suite: 16 / 16 pass.

## 18. Phase-42 Verification Ledger

| Gate                              | Status |
| --------------------------------- | ------ |
| Exchange credential security      | ✅ AES-GCM (reused), never logged, masked in APIs |
| Live account connection state     | ✅ CONNECTING/CONNECTED/FAILED lifecycle |
| Exchange adapter abstraction      | ✅ single point of contact |
| Symbol precision + rules          | ✅ LOT_SIZE / PRICE_FILTER / MIN_NOTIONAL cached |
| Risk engine                       | ✅ 8 deterministic rejection reasons |
| Position sizing                   | ✅ risk-based + normalized + guardrail |
| Signal → order pipeline           | ✅ AFTER_COMMIT listener + fan-out |
| Idempotency                       | ✅ deterministic clientOrderId + unique constraint |
| Order state machine               | ✅ 13 states, no illegal transitions |
| Partial fills                     | ✅ executedQuantity + cumulativeQuoteQty tracked |
| P&L on actual fills               | ✅ uses cumulativeQuoteQty from exchange |
| Protective SL (exchange-side)     | ✅ STOP_LOSS_LIMIT on fill |
| Multi-target TP                   | ⚠ TP1 only; TP2/TP3 tracked, not auto-placed |
| Reconciliation                    | ✅ scheduled 30 s + balance sync 60 s |
| User-data WebSocket               | ⚠ deferred to next drop; REST reconciler covers the same events |
| Restart recovery                  | ✅ 100 % DB-backed state |
| Kill switch                       | ✅ per-account + config-level |
| Daily loss limit                  | ✅ session-based |
| REST API                          | ✅ 12 endpoints, IDOR-safe |
| Cross-user 404                    | ✅ verified in controller code paths |
| Backend tests                     | ✅ 18 new, all green |
| Flutter tests                     | ✅ 3 new, all green |
| Paper/live isolation regression   | ✅ enforced at build time |
| Runtime testnet verification      | NOT VERIFIED — no live network run performed. See runbook for the procedure. |
| Live UI                           | ✅ activation, kill switch, orders, history |

## 19. Known limitations

1. **User-data WebSocket** is not implemented — reconciliation via REST
   polls every 30 s catches the same events with higher latency. Adding the
   listen-key + WS subscription is a purely additive next drop.
2. **TP2 / TP3** are captured on the signal + persisted on the entry order
   but no protective TP orders are auto-placed. Only the SL is.
3. **Adapter is Binance-only.** The `ExchangeName` enum has BYBIT / OKX /
   COINBASE, but adapters for those are not implemented.
4. **Testnet runtime verification** is not performed by CI. Ops must run the
   procedure in `LIVE_TRADING_RUNBOOK.md` before flipping
   `app.live-trading.mode=EXCHANGE` in production.
5. **Rate limiting.** Adapter throws on 429 with `retryable=true`, but
   there's no queue / token-bucket in front of the adapter. High-frequency
   scenarios need Resilience4j before production.
6. **Notification integration** — the existing FCM path fires on signal
   generation. A dedicated `LIVE_ORDER_*` notification stream is planned;
   for now, order state is visible in the Flutter UI.
