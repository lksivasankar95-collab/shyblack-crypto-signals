# Futures Trading — Operational Runbook

## Distinguishing SPOT vs FUTURES

```
SPOT                   BUY  → asset holding → SELL
FUTURES LONG           BUY  → LONG position  → SELL REDUCE_ONLY
FUTURES SHORT          SELL → SHORT position → BUY  REDUCE_ONLY
```

If you're operating both modules on the same user, remember:

- SPOT uses `LiveTradingAccount` and `LiveOrder` (`SB-` clientOrderId prefix).
- FUTURES uses `FuturesTradingAccount`, `FuturesOrder`, `FuturesPosition`
  (`SBF-` clientOrderId prefix).
- Their execution paths never share code — `PaperLiveIsolationTest`
  enforces this at build time.

## Enabling live futures trading (first time)

1. Deploy with `FUTURES_TRADING_MODE=MOCK` (default). Nothing below routes
   real orders while this is `MOCK`.
2. Enable it on the Binance account:
   - Futures margin mode: **ISOLATED**.
   - Position mode: **ONE-WAY** (single-position mode, not hedge).
   - API key permissions include Futures trading.
3. In the app: Settings → Connect Exchange Accounts → add the Binance API
   key + secret.
4. In the app: Settings → Futures Trading → **Connect Binance Futures**.
   Backend calls `getAccount` and populates wallet / margin / position mode.
5. If the connect response shows position mode ≠ ONE-WAY the app blocks
   activation. Switch on the exchange dashboard, then retry.
6. In the app: flip *Live futures trading* switch. Modal:
   *"I UNDERSTAND — ENABLE"* → sets `acknowledged=true` and `enabled=true`.
7. In ops: flip `FUTURES_TRADING_MODE=EXCHANGE` and
   `FUTURES_AUTO_EXECUTE=true`. Only from this point can real orders be
   submitted.

## Testnet runtime verification (mandatory before production)

Use `https://testnet.binancefuture.com` — never production.

Verify each of these end to end:

1. Signed `getAccount` returns balances.
2. `setLeverage` succeeds.
3. `setMarginMode(ISOLATED)` succeeds (or returns -4046 already-set).
4. LONG signal: entry MARKET BUY → FILLED → protective STOP_MARKET SELL
   REDUCE_ONLY is ACKNOWLEDGED → `FuturesPosition.protectionStatus =
   PROTECTED`.
5. SHORT signal: same, mirrored.
6. Manual close: `POST /positions/{id}/close` → sibling SL cancelled →
   REDUCE_ONLY market → position CLOSED with realized P&L.
7. Kill the app during an open position, restart. Confirm reconciler
   catches actual state within 30 s.
8. Trigger the kill switch — new signals refuse; existing protective
   orders remain live on the exchange.
9. Cancel a pending order via the API — status transitions through
   `CANCEL_REQUESTED → CANCELLED` and reconciler agrees.

Capture and archive:

- clientOrderId, exchangeOrderId, symbol, side, positionSide,
  executed quantity, avg fill, order status, SL order id, final position
  status, realized P&L.

Only after this pass should the ops flip `FUTURES_TRADING_MODE=EXCHANGE`
with a **non-testnet base URL**.

## Common incidents

### Position mode mismatch
Symptom: activation refuses with *"Binance account is in HEDGE mode; switch
to ONE_WAY"*. Fix: on Binance dashboard → Futures → Preferences → Position
Mode → One-Way → apply → tap *Connect Binance Futures* again.

### Cross margin selected
Symptom: risk gate returns `UNSUPPORTED_MARGIN_MODE`. Fix: switch the
symbol to ISOLATED margin on the exchange **or** ensure
`account.marginMode = ISOLATED`. Cross margin is not implemented.

### Protection failed after entry filled
Symptom: `FuturesPosition.protectionStatus = PROTECTION_FAILED`, UI shows
*"position is UNPROTECTED"*. Investigate the lifecycle events; retry SL
placement manually (support tool), or manually close the position via
`POST /positions/{id}/close`.

### Stuck order in UNKNOWN
Symptom: `FuturesOrder.status = UNKNOWN` and reconciler hasn't cleared it
after several ticks. Cause: Binance accepted the order but our HTTP
response was lost. Investigate via Binance dashboard using
`clientOrderId`. Reconciler will pick up the actual state on the next
tick. Never manually flip the row to FILLED.

### Partial fill
Position quantity reflects **actual filled quantity**, not requested. The
protective SL is sized to the executed quantity at the time of fill.
Later fills of the same entry order will trigger a reconciler event.

### Kill switch
Ops or user activates it. Effect: `killSwitchActive = true` blocks new
entries at the risk gate. Existing protective orders stay live on the
exchange. Reconciliation continues. Manual close remains available.
Release: `DELETE /kill-switch`.

### Daily loss limit reached
`FuturesRiskService` returns `DAILY_LOSS_LIMIT`. Auto-resets at UTC
midnight when `sessionDate` rolls. To force a session roll during
troubleshooting, refresh balances via `POST /connection/validate`.

### Credential rotation
1. Create a new key on Binance with matching permissions.
2. Settings → Connect Exchange Accounts → delete the old credential row.
3. Add the new row.
4. Settings → Futures Trading → *Connect Binance Futures* again to
   re-link the credential + validate.
5. Reactivate live futures trading (fresh acknowledgement required).

### Emergency close all
1. Trigger kill switch → blocks new entries.
2. For each open position, call `POST /positions/{id}/close` — this
   cancels the sibling SL and submits a REDUCE_ONLY market close.
3. Confirm on Binance dashboard that positions and orders are flat.
