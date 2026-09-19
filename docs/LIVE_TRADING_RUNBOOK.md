# Live Trading — Operational Runbook

## Enabling live trading (first time)

1. **In config / env:** set `LIVE_TRADING_MODE=MOCK` (the default). Nothing
   below routes real orders while this is `MOCK`.
2. **In the app:** user opens Settings → Connect Exchange Accounts, adds a
   Binance API key + secret (testnet keys work for validation).
3. **In the app:** user opens Settings → Live Trading → **Connect Binance**.
   Backend calls `adapter.validateCredentials` (signed `GET /api/v3/account`
   under EXCHANGE mode, or an idempotent mock under MOCK mode).
4. **In the app:** user flips the *Live trading* switch. A confirmation
   modal is shown; user must tap *I UNDERSTAND — ENABLE*.
5. **Backend:** `POST /activate {acknowledged: true}` — the backend rejects
   without the flag; sets `LiveTradingAccount.enabled = true`.
6. **Backend:** flip `LIVE_TRADING_MODE=EXCHANGE` and
   `LIVE_AUTO_EXECUTE=true` — this is an ops decision, not a user decision.

Until step 6, no real Binance order can ever be produced by this codebase.

## Testnet runtime verification (before flipping to production)

1. Deploy backend with `LIVE_TRADING_MODE=EXCHANGE`,
   `LIVE_BINANCE_SPOT_REST=https://testnet.binance.vision`,
   `LIVE_AUTO_EXECUTE=true`.
2. Create a Binance testnet account and API key.
3. In the app, connect the credential + activate.
4. Wait for the signal engine to emit a signal (or manually seed one via a
   staging script). Confirm:
   * `POST /api/v3/order` in Binance testnet dashboard shows the order.
   * `LiveOrder.status` transitions to `FILLED` within one reconciler tick.
   * A follow-up `STOP_LOSS_LIMIT` appears on testnet.
   * `LiveTradingAccount.cachedTotalBalance` decreases by the entry fee.
5. Kill the app in the middle of a fill. Restart. Confirm the reconciler
   catches the fill within 30 s and updates `LiveOrder`.
6. Trigger the kill switch. Confirm no new entries are opened; existing
   protective orders remain on the exchange.
7. Only after all six steps pass should the base URL be flipped to
   `https://api.binance.com`.

## Common incidents

### Exchange connection fails at `validate`

- Symptom: `LiveTradingAccount.connectionStatus = FAILED`,
  `lastValidationMessage` contains the exchange error.
- Fix: user re-enters credentials with correct permissions (Spot + Read).
  Rerun *Connect Binance* in the app. Never edit ciphertext in the DB.

### Order stuck in `UNKNOWN`

- Symptom: `LiveOrder.status = UNKNOWN` and reconciler hasn't cleared it
  after > 5 minutes.
- Investigate: query Binance directly with the `clientOrderId` — did the
  order actually make it? If yes, wait one more reconciler cycle (the local
  status will match). If no, cancel the local intent via
  `POST /orders/{id}/cancel`.
- Never manually flip the row to `FILLED` — always let the reconciler
  observe the exchange state.

### Partial fill left dangling

- Symptom: `LiveOrder.status = PARTIALLY_FILLED` for hours.
- Investigate: is the entry a `LIMIT` order that hasn't triggered? For
  MARKET orders this is unusual — probably a Binance liquidity gap.
- Options: wait for reconciler, cancel via UI, or investigate exchange
  side. The engine will not re-submit for the remaining quantity.

### Reconciliation mismatch

- Symptom: an alert says local `LiveOrder.executedQuantity` differs from
  exchange. This is normal until the next reconciler tick.
- If it persists > 2 minutes, check reconciler logs (`[Reconciler]` prefix).
  Usually caused by an exchange 5xx during `getOrder`. Retry manually with
  `POST /connection/validate` to force a balance refresh.

### WebSocket disconnect

- Not applicable in this drop — the market WS is not the source of live
  order truth. Reconciler polls REST every 30 s and covers this. When
  user-data WS is added, its disconnect path will trigger an explicit
  reconciliation sweep.

### Kill switch — emergency

- User flips it from the UI or ops calls
  `POST /api/v1/live-trading/kill-switch`.
- Effect: `killSwitchActive = true`. New signals are blocked at the risk
  engine; existing protective orders on the exchange remain live; manual
  close remains available. Reconciler keeps running.
- To release: user un-taps the switch in the UI, or ops calls
  `DELETE /api/v1/live-trading/kill-switch`.

### Daily loss limit reached

- Symptom: risk service returns `DAILY_LOSS_LIMIT`, engine stops opening
  entries.
- User's session equity has dropped below `sessionStartEquity × (1 − dailyLossLimitPct/100)`.
- The limit auto-resets when the account's `sessionDate` rolls to the next
  UTC day. To force a reset, hit `POST /connection/validate` — the account
  service will roll `sessionDate` if it doesn't match today.

### Credential rotation

1. In Binance dashboard, create a new key with matching permissions.
2. In the app, Settings → Connect Exchange Accounts → delete the old row
   (this triggers a real DB delete + adapter revoke).
3. Add the new row.
4. Return to Settings → Live Trading → Connect Binance to re-link the new
   credential to the live account.
5. Reactivate live trading (needs a fresh acknowledgement).

### Emergency close all

Not implemented as a one-click action in this drop. To close everything:

1. Trigger kill switch → blocks new entries.
2. From the UI, cancel each pending order manually. (This calls
   `adapter.cancelOrder`.)
3. For filled positions with pending protective orders, cancel the
   protective SL (this leaves the position open on the exchange with no
   local protection — do this only if you plan to close manually via the
   exchange dashboard afterwards).
4. On the exchange dashboard, sell/close the position.
5. The reconciler will observe the resulting `FILLED` cancellations on the
   next tick.
