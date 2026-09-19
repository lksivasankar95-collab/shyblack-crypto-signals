# Live Trading Module — Implementation Report

**Status:** SPOT end-to-end shipped. Runtime testnet verification NOT PERFORMED
in this drop.
**Baseline:** paper-trading commit `08d328d`; live vertical slice `efec487`.
**Supported:** Binance **SPOT** (testnet URL by default).
**Not supported:** FUTURES, OPTIONS, MARGIN, LEVERAGE, naked SHORT selling —
each is explicitly rejected at the risk gate with a deterministic reason.

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

## 8. Risk engine (Phase 6 + SPOT-only)

`LiveTradingRiskService.check(user, account, signal)` returns a single
`LiveTradingRiskReason`. Order of checks:

1. account exists / activated
2. `enabled` + not `killSwitchActive`
3. `connectionStatus = CONNECTED`
4. **`signal.tradingMode == SPOT`** — otherwise `UNSUPPORTED_TRADING_MODE`
5. **`signal.side == LONG`** — SPOT has no naked short; otherwise `UNSUPPORTED_SIDE`
6. `signal.stopLoss` present + valid
7. `userSettings.liveTradingAllowed`
8. **no open ENTRY already exists for this symbol** — otherwise `EXISTING_POSITION`
9. `active positions < maxActivePositions`
10. daily-loss limit

Any non-`OK` return short-circuits the engine and writes a `RISK_BLOCKED`
lifecycle event.

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

## 11. Protective SL/TP (Phase 13/14 — hybrid model)

- **Preferred:** exchange-side `STOP_LOSS_LIMIT` order attached immediately
  after entry fill. The exchange manages the trigger — the app can lose
  network without leaving the position unprotected.
- **`ProtectionStatus` on the entry row** tracks whether the protective SELL
  is live: `PENDING` → `PROTECTED`, or `PROTECTION_FAILED` if placement
  errors or returns a non-live status. Failures are logged loudly and
  surfaced to the UI as *"PROTECTION FAILED — position is UNPROTECTED"* so a
  filled entry is never displayed as safe when it isn't.
- Multi-target TPs (TP2 / TP3) are captured in the signal but **not
  auto-placed** in this drop. Listed as a known limitation.

## 11a. Manual close (Phase 16)

`POST /api/v1/live-trading/positions/{entryOrderId}/close` places a real
SPOT SELL for the executed quantity of a filled entry. Flow:

1. Verify the entry is owned by the caller (IDOR-safe).
2. Verify status is `FILLED` or `PARTIALLY_FILLED`.
3. Cancel the sibling `STOP_LOSS_LIMIT` first so no dangling SL remains.
4. Normalize the executed qty via `SymbolRules` (stepSize / minQty).
5. Build a deterministic `clientOrderId` for the close leg — same idempotency
   contract as entries; double-tapping *Close* can never produce two SELLs.
6. Persist the intent, commit, then submit via
   `ExchangeTradingAdapter.placeOrder`. Terminal state comes from the
   exchange response, never from local optimism.

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

**Backend (35 tests across live/exchange/isolation, all green):**

- `ClientOrderIdGeneratorTest`, `SymbolRulesTest`, `LiveTradingSizingServiceTest`,
  `MockExchangeTradingAdapterTest`, `PaperLiveIsolationTest` (from prior drop).
- **New in this drop:**
  - `LiveTradingRiskServiceTest` — SPOT accepted, FUTURES/OPTIONS rejected
    with `UNSUPPORTED_TRADING_MODE`, SHORT-side rejected with
    `UNSUPPORTED_SIDE`, `EXISTING_POSITION` when an open entry exists,
    kill-switch / disabled / disconnected / invalid stop paths.
  - `LiveTradingCloseServiceTest` — closes filled entry with a real SPOT
    SELL, cancels sibling SL before submitting, IDOR check (returns 404 for
    another user's order), rejects non-ENTRY purposes, rejects non-filled
    entries, idempotent re-close returns the existing close row.

Backend suite: **198 tests, 197 pass**. The 1 failure
(`NewsApiIntegrationTest.assetContextAggregatesProcessedArticles`) pre-exists
on main — see paper-trading commit for the same result.

**Flutter (4 tests, all green):**

- No-account → shows Connect CTA.
- Connected → renders balance, `LIVE • SPOT` badge, orders, safety panel.
- **New:** Filled entry shows `CLOSE POSITION`, hides `CANCEL`, displays
  `Protected by SL`, tapping through the confirm dialog invokes
  `closePosition(entryId)`.
- Activation → requires acknowledgement modal, triggers `activate(acknowledged=true)`.

Total Flutter suite: **17 / 17 pass**. `flutter analyze` — 0 new issues.

## 17a. SPOT-only enforcement

Three explicit rejection reasons enforce the SPOT contract at the risk gate:

- `UNSUPPORTED_TRADING_MODE` — any `signal.tradingMode` other than SPOT is
  refused before the exchange adapter is ever called.
- `UNSUPPORTED_SIDE` — SPOT does not permit opening a naked SHORT position;
  a SHORT-side signal is refused.
- `EXISTING_POSITION` — a second BUY for a symbol whose prior entry is still
  open (`CREATED`, `SUBMITTING`, `SUBMITTED`, `ACKNOWLEDGED`,
  `PARTIALLY_FILLED`, `FILLED`) is refused. Stacking is impossible.

These are covered by `LiveTradingRiskServiceTest` and their absence would
break the build.

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
| Protective SL (exchange-side)     | ✅ STOP_LOSS_LIMIT on fill + ProtectionStatus |
| Manual close position             | ✅ POST /positions/{id}/close — cancels SL + real SPOT SELL |
| SPOT-only enforcement             | ✅ UNSUPPORTED_TRADING_MODE / UNSUPPORTED_SIDE |
| Existing-position check           | ✅ EXISTING_POSITION at risk gate |
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
