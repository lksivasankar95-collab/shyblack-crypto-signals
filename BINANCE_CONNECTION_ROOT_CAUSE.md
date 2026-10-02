# Binance Account Connection — Root Cause

Written from evidence gathered **before** any fix. Every claim below is either a direct code
quote or the output of a temporary loopback probe (`ConnectionProbeTest`, since deleted) that
drove the real adapter against a scripted exchange.

Paths are relative to `backend/src/main/java/com/shyblack/cryptosignals/` unless noted.

---

## 1. Current request flow

```
Flutter  Settings → Exchange Accounts screen
          exchange_accounts_screen.dart:92  _testConnection()
          settings_repository_impl.dart:50  testExchangeConnection(id)
          api_constants.dart:128            POST /v1/settings/exchanges/{id}/test-connection
   ↓
Controller  ExchangeCredentialController.java:51  testConnection(UUID id)
   ↓
Service     ExchangeCredentialService.java:89   testConnection(principal, id)
              → requireOwned(principal, id)        IDOR-safe lookup
              → adapter.validateCredentials(credential)
   ↓
Adapter     injected ExchangeTradingAdapter = the SPOT adapter only
            BinanceLiveTradingAdapter.java:79   validateCredentials
            BinanceLiveTradingAdapter.java:84   getAccountBalance
            BinanceLiveTradingAdapter.java:99   getBalances
              → signedGet("/api/v3/account")
   ↓
Signing     BinanceLiveTradingAdapter.java:371  signedRequest
              timestamp = System.currentTimeMillis()
              recvWindow = props.recvWindowMs()
              query = urlEncode(params)          insertion-ordered
              signature = HMAC-SHA256(secret, query)
              URL = base + path + "?" + query + "&signature=" + signature
              header X-MBX-APIKEY = apiKey
   ↓
Response    signedRequest → ExchangeAccountSnapshot(canTrade, USDT free/total)
   ↓
Service     CONNECTED on success, FAILED on ExchangeAdapterException
   ↓
DTO         ExchangeCredentialConnectionResponse(id, exchange, ok, message, latencyMs, testedAt, status)
   ↓
Flutter     exchange_accounts_screen.dart:96  reads result['success']
```

---

## 2. What is NOT broken

The task asked me to check these. I verified each and they are correct — so no fix touches them:

| Check | Evidence |
|---|---|
| Is `testConnection` still faked? | **No.** The 42 ms hardcoded success was replaced in `d15489d` (Phase 3). `git log -S "42"` returns only `0f738ce`, the original module commit. Today it measures real elapsed time and calls the adapter. |
| Is real credential validation called? | **Yes** — `adapter.validateCredentials(credential)` at `ExchangeCredentialService.java:93`. |
| API key in the correct header? | **Yes.** Probe: `header=probe-key` on `X-MBX-APIKEY`. |
| HMAC over the exact query string? | **Yes.** Probe: `rawQuery=timestamp=1790932787335&recvWindow=5000&signature=<sig>`; `BinanceSignatureUtil.hmacSha256Hex` emits lowercase hex, which Binance requires. |
| Timestamp correct? | **Yes.** Probe value is wall-clock epoch millis. |
| recvWindow correct? | **Yes.** Sent as `5000`, bound from `app.live-trading.recv-window-ms`. |
| HTTP method / path correct? | **Yes.** Probe: `GET /api/v3/account` — a real signed, read-only account read. |
| Secret decrypted before signing? | **Yes.** `encryptor.decrypt(...)` inside `signedRequest`, plaintext never leaves that method scope. |
| Secret returned in API responses? | **No.** `toView` masks via `encryptor.mask(encryptor.decrypt(...))`; the connection DTO carries no credential fields. |
| Phase 9 UNKNOWN / order handling touched? | **Not touched.** No order path is involved anywhere in this flow. |

Signing, transport and header handling are sound. The bugs are all above the adapter.

---

## 3. Defect D1 — the UI always reports failure (the user-visible symptom)

**Class:** `exchange_accounts_screen.dart` · **Line:** 96

```dart
final success = result['success'] as bool? ?? false;
```

The backend DTO field is **`ok`**, not `success`:

```java
public record ExchangeCredentialConnectionResponse(
        UUID id, ExchangeName exchange, boolean ok, String message, ...) {}
```

`result['success']` is therefore always `null`, `?? false` yields `false`, and the screen renders
the failure snackbar in red **even when the backend successfully authenticated against Binance**.
A user testing a valid key is told it failed. This is the "connection is not working" report.

**Expected:** a successful validation shows the success state.
**Actual:** it always shows failure. The backend status is persisted as `CONNECTED` either way,
so the list tile and the snackbar disagree.

---

## 4. Defect D2 — futures credentials are never validated

**Class:** `ExchangeCredentialService` · **Line:** 40

```java
private final ExchangeTradingAdapter adapter;   // the SPOT adapter
```

There is exactly one adapter injected, and it is the spot bean (`binanceTradingAdapter`). A Binance
credential is therefore **always** validated against `GET /api/v3/account`, even when the user
intends to trade futures. A key with futures-only permissions fails the spot check; a spot-only key
is reported `CONNECTED` while futures is entirely unverified.

`FuturesExchangeAdapter.validateCredentials` → `/fapi/v2/account` already exists
(`BinanceFuturesLiveAdapter`) but **no service calls it**. There is no route from the settings
endpoint to futures validation at all.

---

## 5. Defect D3 — non-adapter exceptions escape as HTTP 500

**Class:** `ExchangeCredentialService` · **Line:** 106

```java
} catch (ExchangeAdapterException ex) { ... }
```

Only `ExchangeAdapterException` is caught. A non-JSON body from a proxy or gateway produces a
Gson parse failure that is **not** an adapter exception:

```
PROBE malformed -> JsonSyntaxException <- MalformedJsonException
   msg=Use JsonReader.setStrictness(Strictness.LENIENT) to accept malformed JSON at line 1 column 16
```

That escapes the `@Transactional` method, becomes an HTTP 500, and Flutter shows
`Test failed: ...` with a parser message. The result is non-deterministic: the same button yields a
classified status for one failure and a stack trace for another.

The same escape hatch exists for the futures adapter's `getAccount`, which throws
`ExchangeAdapterException` only for named fields and lets Gson failures through.

---

## 6. Defect D4 — the raw Binance error body is forwarded to the user

**Class:** `ExchangeCredentialService` · **Line:** 115

```java
truncate("Exchange rejected the credentials: " + ex.getMessage())
```

Spring's `HttpStatusCodeException.getMessage()` embeds the **entire response body**. Probe:

```
PROBE invalidKey -> ExchangeAdapterException <- Unauthorized
   msg=401 Unauthorized: "{"code":-2015,"msg":"Invalid API-key, IP, or permissions for action."}"
PROBE -1021      -> msg=400 Bad Request: "{"code":-1021,"msg":"Timestamp for this request was 1000ms ahead."}"
```

That string is truncated to 200 characters and returned verbatim as the user-facing `message`,
which Flutter displays. Raw exchange payloads must not reach the client: they are uncontrolled,
can echo request context in some gateway configurations, and replace a deterministic message with
whatever the upstream happened to return.

---

## 7. Defect D5 — no machine-readable classification

**Class:** `ExchangeCredentialConnectionResponse`

`status` is `ExchangeConnectionStatus`, which has five lifecycle values
(`NOT_CONNECTED, CONNECTING, CONNECTED, FAILED, REVOKED`). On failure every cause collapses to
`FAILED`:

| Real condition | Required | Returned today |
|---|---|---|
| Wrong API key / secret | `INVALID_CREDENTIALS` | `FAILED` |
| Key valid, trading permission absent | `PERMISSION_DENIED` | `CONNECTED` (only a message hint) |
| `-1021` timestamp outside recvWindow | `TIMESTAMP_ERROR` | `FAILED` |
| `-1003` / HTTP 429 | `RATE_LIMITED` | `FAILED` |
| HTTP 418 IP ban | `RATE_LIMITED` | `FAILED` |
| Read timeout | `TIMEOUT` | `FAILED` |
| Connection refused / DNS | `NETWORK_ERROR` | `FAILED` |
| HTTP 5xx | `BINANCE_API_ERROR` | `FAILED` |
| Anything else | `UNKNOWN_ERROR` | `FAILED` |

A caller cannot tell "your key is wrong" from "retry in a minute" from "you are banned". A
client-side credential could not be distinguished from a transient network fault.

---

## 8. Defect D6 — a transient network fault permanently marks the credential FAILED

**Class:** `ExchangeCredentialService` · **Lines:** 106–118

Every caught exception sets `ExchangeConnectionStatus.FAILED` and saves. A 30-second read timeout
or one rate-limit response therefore persists `FAILED` on a credential whose key is perfectly
valid. Connection status should reflect **credential** health; transport health is not a statement
about the key. A laptop that loses Wi-Fi would leave every stored key marked failed.

---

## 9. Defect D7 — stale API documentation

**Class:** `ExchangeCredentialController` · **Line:** 49

```java
@Operation(summary = "Test an exchange connection (simulated, no real network call)")
```

The endpoint performs a real signed network call. The published OpenAPI summary is wrong and will
mislead any integrator.

---

## 10. Minimal fix

Scope is deliberately confined to the connection path. No strategy, execution, paper, portfolio or
migrations are touched.

1. **New `ConnectionValidationStatus` enum** with the nine required machine-readable values.
2. **New `ExchangeConnectionClassifier`** — a single place that maps any `Throwable` to
   `(status, safeMessage)` using Binance's documented codes and HTTP statuses. This is the fix for
   D3, D4 and D5: it never echoes an exchange body, and it covers non-adapter exceptions.
3. **Scope-aware validation** in `ExchangeCredentialService` — inject `FuturesExchangeAdapter`
   alongside the spot adapter and validate SPOT or FUTURES with the matching signed account read.
   Fixes D2. The stored `ExchangeCredential` is unchanged and still shared by both adapters.
4. **Response DTO gains `validationStatus` and `scope`.** `ok`, `status` and `message` are kept so
   the existing contract and existing tests remain valid.
5. **Persist `FAILED` only for credential-attributable failures**; leave the stored status
   untouched for transport, timeout and rate-limit faults. Fixes D6.
6. **Flutter reads `ok`** and surfaces `validationStatus`. Fixes D1.
7. **Controller summary corrected.** Fixes D7.

### Files that will change

| File | Why |
|---|---|
| `dto/settings/ConnectionValidationStatus.java` | **new** — machine-readable classification |
| `dto/settings/ExchangeCredentialConnectionResponse.java` | add `validationStatus`, `scope` |
| `service/ExchangeConnectionClassifier.java` | **new** — deterministic mapping, no body echo |
| `service/ExchangeCredentialService.java` | scope-aware validation + conditional persist |
| `controller/ExchangeCredentialController.java` | correct summary, optional `scope` param |
| `dto/settings/ExchangeCredentialView.java` | unchanged unless compile forces it |
| `frontend/.../exchange_accounts_screen.dart` | read `ok`, surface `validationStatus` |

### Files explicitly NOT changing

- `exchange/binance/BinanceLiveTradingAdapter.java`, `exchange/futures/binance/BinanceFuturesLiveAdapter.java`
  — signing, headers, endpoints and the Phase 9 fixes are verified correct.
- `service/live/**`, `service/futures/**` engines — no execution behaviour.
- `service/execution/**` — the router, gates, idempotency and Phase 9 order handling.
- Strategies, NFM, backtesting, paper trading — untouched.
- `entity/ExchangeCredential.java` — one credential per user per exchange, shared by spot and
  futures. No duplicate records are created.
- `AccountType`, `AccountMode`, `AccountCategory` — untouched.
- All migrations V1–V4 — no schema change is required.
- `SettingsProperties` default encryption seed — recorded as a separate blocker, not expanded into.