# Phase 9 — Pre-Implementation Adapter Execution Path Audit

Written before any Phase 9 code, for the same reason as the Phase 8 audit: to record
what the adapter boundary actually does, so tests assert real behaviour rather than
an assumed contract.

Paths are relative to `backend/src/main/java/com/shyblack/cryptosignals/`.

---

## 1. The full chain

```
ExecutionRouter.onSignalGenerated        (Phase 8, service.execution)
  └─ LiveTradingEngineService.executeForAccount(account, signal)
       └─ LiveTradingRiskService.check          (gate, reused unchanged)
       └─ ExchangeTradingAdapter.getSymbolRules
       └─ LiveTradingSizingService.size
       └─ LiveTradingExecutionService.createIntent   → live_orders row, CREATED
       └─ LiveTradingExecutionService.submit
            ├─ markSubmitting                    → SUBMITTING, submittedAt
            └─ ExchangeTradingAdapter.placeOrder
                 └─ BinanceLiveTradingAdapter.placeOrder   → POST {base}/api/v3/order
       └─ on FILLED/PARTIALLY_FILLED: placeProtectiveStop → second placeOrder

ExecutionRouter.onSignalGenerated
  └─ FuturesEngineService.executeForAccount(account, signal)
       └─ FuturesRiskService.check / checkLeverage
       └─ FuturesTradingSizingService.size
       └─ FuturesLiquidationService.assess
       └─ adapter.setLeverage / setMarginMode
       └─ FuturesExecutionService.createIntent → futures_orders row, CREATED
       └─ FuturesExecutionService.submit
            └─ FuturesExchangeAdapter.placeOrder
                 └─ BinanceFuturesLiveAdapter.placeOrder → POST {base}/fapi/v1/order
```

---

## 2. Spot request construction (`BinanceLiveTradingAdapter:189`)

```java
params.put("symbol", request.symbol());
params.put("side", request.side().name());          // LONG / SHORT
params.put("type", binanceType(request.type()));   // MARKET|LIMIT|STOP_LOSS_LIMIT|TAKE_PROFIT_LIMIT
params.put("newClientOrderId", request.clientOrderId());
params.put("quantity", request.quantity().stripTrailingZeros().toPlainString());
if (request.price() != null && request.type() != LiveOrderType.MARKET) {
    params.put("price", ...);
    params.put("timeInForce", "GTC");
}
if (request.stopPrice() != null) params.put("stopPrice", ...);
params.put("newOrderRespType", "FULL");
```

`signedRequest` then appends `timestamp` + `recvWindow`, URL-encodes the map in
insertion order, signs the encoded query with HMAC-SHA256, appends
`&signature=…`, and sets `X-MBX-APIKEY`.

**Observations to verify, not assume:**

1. **`side` is sent as `LONG`/`SHORT`, not `BUY`/`SELL`.** The application's
   `PositionSide` enum is `{LONG, SHORT}`. Binance's spot API accepts only `BUY`
   and `SELL`. The futures adapter maps explicitly
   (`req.side() == PositionSide.LONG ? "BUY" : "SELL"`); the spot adapter does
   not. Since the spot engine is long-only, every real spot order would send
   `side=LONG`. This must be tested against the actual generated request, not
   reasoned about.
2. **The signature covers the query string but the API key travels in a header**,
   which is correct, and the signed payload excludes `signature` itself, which is
   also correct.
3. `newOrderRespType=FULL` for spot, so `fills[]` is present and real commission
   is available.
4. `STOP_LOSS_LIMIT` is sent with `timeInForce=GTC` and a `stopPrice`, but never
   with the `STOP_LOSS_LIMIT`-specific requirement of a valid `price`/`stopPrice`
   pair — untested today.

---

## 3. Futures request construction (`BinanceFuturesLiveAdapter:228`)

```java
params.put("symbol", req.symbol());
params.put("side", req.side() == PositionSide.LONG ? "BUY" : "SELL");   // correct
params.put("type", binanceType(req.type()));   // MARKET|LIMIT|STOP_MARKET|TAKE_PROFIT_MARKET
params.put("newClientOrderId", req.clientOrderId());
params.put("quantity", req.quantity().stripTrailingZeros().toPlainString());
if (req.reduceOnly()) params.put("reduceOnly", "true");
if (req.stopPrice() != null) params.put("stopPrice", ...);
params.put("newOrderRespType", "RESULT");
// positionSide deliberately omitted: ONE_WAY mode defaults to BOTH
```

**Observations:**

1. Side mapping is correct here, unlike spot.
2. **`LIMIT` never sends `price` or `timeInForce`.** A Binance futures LIMIT
   order requires `timeInForce` and a `price`. Only `MARKET`, `STOP_MARKET` and
   `TAKE_PROFIT_MARKET` are actually used by the current engines
   (`FuturesEngineService:155`, `:217`, `FuturesCloseService:87`), so the defect is
   latent rather than active — but it must be tested and documented, not left
   implied.
3. `newOrderRespType=RESULT` returns no `fills[]`, so **fee is always zero**
   (see §5).

---

## 4. Signing

`BinanceSignatureUtil.hmacSha256Hex(secret, payload)` →
`HexFormat.of().formatHex(...)` — lowercase hex, which Binance requires.
`BinanceFuturesSignatureUtil` is the futures twin.

Both adapters decrypt the secret inside `signedRequest`, sign, then null the local
references in a `finally` block. Neither logs the query string, the signature or
the API key. Both log only method, path, HTTP status and exchange code.

---

## 5. Fees — a known Phase 8 finding, confirmed

`BinanceFuturesLiveAdapter.parse` hardcodes:

```java
BigDecimal fee = BigDecimal.ZERO;
// Binance Futures RESULT doesn't include fills[]; commission comes via user-data
// stream (not implemented). For now, fees are set later by reconciliation.
```

`BinanceLiveTradingAdapter.parseOrderResponse` starts from `BigDecimal.ZERO` and
sums `fills[].commission`, leaving it `ZERO` when `fills` is absent.

**This is the fake-zero problem.** A `ZERO` fee is indistinguishable from "Binance
reported a zero fee", which is never true. `FuturesExecutionService.applyResult`
then guards with `if (result.fee() != null && result.fee().signum() > 0)`, so the
zero is discarded and `FuturesOrder.fees` keeps its own default — the damage is
contained there, but `LiveTradingExecutionService.applyResult` calls
`order.setFees(nonNull(result.fee()))`, and `nonNull` maps `null → ZERO`. So a
missing fee becomes a stored `0`.

Phase 9 requirement Z is therefore: **absent fee must stay absent.** The
adapters must return `null`, not `ZERO`.

---

## 6. Symbol normalization

There is **no normalization** in either write path:
`params.put("symbol", request.symbol())` verbatim. The read paths *do* normalize
(`symbol.toUpperCase()` in `getAllOrders`/`getTrades`). So the write and read
contracts disagree.

`SymbolRules.symbol` is populated from `exchangeInfo`, which returns uppercase, and
`LiveTradingExecutionService.createIntent` sets `order.setSymbol(rules.symbol())`
— so today the write path receives an already-uppercase symbol by construction. That
is an accident of the call chain, not a guarantee.

Phase 9 must therefore test the actual contract: **no normalization is performed
on write**, and a lowercase symbol would be sent verbatim. Adding normalization is
out of scope unless the existing contract requires it.

---

## 7. Exchange filters

`SymbolRules` carries `minQty, maxQty, stepSize, minPrice, maxPrice, tickSize,
minNotional`, distilled from `exchangeInfo` filters by
`BinanceLiveTradingAdapter.parseSymbolRules`.

- `normalizeQuantity` snaps **down** (`RoundingMode.DOWN`) — correct: never rounds
  up into a larger risk position.
- `normalizePrice` snaps with `RoundingMode.HALF_UP` — deterministic, matches
  tick size.
- `meetsMinQty`, `meetsMinNotional` exist.

**Who calls them?** `LiveTradingEngineService` calls `rules.normalizePrice` for the
stop and limit price only. Nothing validates `quantity` against `minQty` or
`stepSize` before `placeOrder`; sizing produces the quantity and it is sent as-is.
`FuturesTradingSizingService` is the futures equivalent and is likewise untested
at the filter boundary.

Phase 9 must test what actually happens rather than what the record offers.

---

## 8. Response mapping

| Binance status | Spot → `LiveOrderStatus` | Futures → `FuturesOrderStatus` |
|---|---|---|
| `NEW` | `ACKNOWLEDGED` | `ACKNOWLEDGED` |
| `PARTIALLY_FILLED` | `PARTIALLY_FILLED` | `PARTIALLY_FILLED` |
| `FILLED` | `FILLED` | `FILLED` |
| `CANCELED`, `PENDING_CANCEL` | `CANCELLED` | `CANCELLED` |
| `REJECTED` | `REJECTED` | `REJECTED` |
| `EXPIRED` | `EXPIRED` | `EXPIRED` |
| anything else, or absent | `UNKNOWN` | `UNKNOWN` |

**HTTP 200 is never mapped to `FILLED`** — the status string alone decides. This is
correct and must be locked in by tests.

An HTTP error becomes `ExchangeAdapterException(message, cause, retryable,
httpStatus, code)`, where `retryable` is true only for 5xx and 429.

---

## 9. UNKNOWN outcome handling

`LiveTradingExecutionService.recordFailure`:

```java
LiveOrderStatus status = ex.exchangeCode() != null && ex.exchangeCode() == -1013
        ? LiveOrderStatus.REJECTED
        : (ex.retryable() ? LiveOrderStatus.UNKNOWN : LiveOrderStatus.FAILED);
```

`FuturesExecutionService.recordFailure`: `retryable ? UNKNOWN : FAILED`.

So an ambiguous transport failure yields `UNKNOWN`, never an automatic resubmit.
There is no retry loop anywhere; `submit` is called once by the engine. **The
correctness property holds by construction**, and Phase 9 must prove it rather
than assume it.

Note: `-1013` is Binance's *filter failure* code for spot rejections, handled
explicitly. Other rejection codes (e.g. `-2010` insufficient balance) fall to
`FAILED` unless `retryable`. That is defensible but worth documenting.

---

## 10. Client order ID

`ClientOrderIdGenerator.forSignal` → `"SB-" + sha256(userId|signalId|symbol|side|purpose)` truncated
to 24 base64url chars (32 total). `FuturesClientOrderIdGenerator.forSignal` → `"SBF-" + 23 chars`
with a leading `"F|"` in the canonical form so the two spaces cannot collide.

Both are deterministic, which is what allows correlation across
`Signal` → `ExecutionDecisionRecord` → `LiveOrder` → exchange order, and what makes
the `UNIQUE (account_id, client_order_id)` constraint meaningful.

Binance's `newClientOrderId` allows `^[\.A-Z\:/a-z0-9_-]{1,36}$`. base64url uses
`-` and `_`, both permitted. Length is 32 and 27. **Both are untested against the
constraint.**

---

## 11. Test infrastructure

`build.gradle.kts` has **no MockWebServer and no WireMock**. `testImplementation`
lists Spring Boot test slices and `mockito-junit-jupiter` only.

The adapters build their own `RestClient` from a `restBaseUrl` property, so a test
can point them at a local `com.sun.net.httpserver.HttpServer` on loopback without
adding a dependency. That is the approach Phase 9 takes: real HTTP, real HMAC,
real JSON parsing, zero network egress, no new build dependency.

---

## 12. Defects this audit surfaced

To be verified by test, then fixed only where the fix is small and provably safe:

| # | Defect | Where |
|---|---|---|
| 1 | Spot `side` sent as `LONG`/`SHORT`; Binance requires `BUY`/`SELL` | `BinanceLiveTradingAdapter:192` |
| 2 | Absent fee becomes a stored `ZERO` instead of staying absent | both adapters + `nonNull` in both execution services |
| 3 | Futures `LIMIT` sends no `price` and no `timeInForce` | `BinanceFuturesLiveAdapter:228` |
| 4 | Write paths perform no symbol normalization while read paths do | both adapters |
| 5 | No quantity filter validation before submission | `LiveTradingEngineService`, `FuturesTradingSizingService` |