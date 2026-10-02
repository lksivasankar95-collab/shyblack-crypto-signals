# Binance Account Connection — Fix Report

Scope: the Binance credential connection / "Test Connection" path only. No strategy, execution,
portfolio, paper or migration was touched. No order was placed, cancelled or modified.

---

## 1. ROOT-CAUSE

Full analysis with evidence: [`BINANCE_CONNECTION_ROOT_CAUSE.md`](BINANCE_CONNECTION_ROOT_CAUSE.md).

**The signing, transport, header and endpoint code was already correct.** A loopback probe against
the real adapter produced:

```
PROBE success -> canTrade=true path=/api/v3/account method=GET header=probe-key
   rawQuery=timestamp=1790932787335&recvWindow=5000&signature=<sig>
```

The 42 ms hardcoded result had already been removed in `d15489d`. The bugs were all above the
adapter, and the primary one was **not in the backend at all**.

| # | Defect | Location | Effect |
|---|---|---|---|
| **D1** | Flutter read `result['success']`; the DTO field is `ok` | `exchange_accounts_screen.dart:96` | **Every successful validation displayed as a failure.** This is the reported symptom. |
| **D2** | Service injected only `ExchangeTradingAdapter` (spot) | `ExchangeCredentialService:40` | Futures credentials were validated against `GET /api/v3/account`; `/fapi/v2/account` was never called from this endpoint. |
| **D3** | Only `ExchangeAdapterException` caught | `ExchangeCredentialService:106` | A non-JSON gateway body produced `JsonSyntaxException`, escaped as HTTP 500. |
| **D4** | Raw exchange body forwarded to the user | `ExchangeCredentialService:115` | `HttpStatusCodeException.getMessage()` embeds the whole response body. |
| **D5** | No machine-readable classification | `ExchangeCredentialConnectionResponse` | Key error, timeout, rate limit and ban all collapsed to `FAILED`. |
| **D6** | Every failure persisted `FAILED` | `ExchangeCredentialService:106` | A transient timeout permanently marked a valid key as failed. |
| **D7** | OpenAPI summary said "simulated, no real network call" | `ExchangeCredentialController:49` | Published documentation contradicted the code. |

Probe evidence for D3/D4:

```
PROBE malformed -> JsonSyntaxException <- MalformedJsonException
   msg=Use JsonReader.setStrictness(Strictness.LENIENT) ...
PROBE invalidKey -> msg=401 Unauthorized: "{"code":-2015,"msg":"Invalid API-key, IP, or permissions for action."}"
```

---

## 2. FIX_APPLIED

- New `ConnectionValidationStatus` — nine machine-readable outcomes plus `isCredentialAttributable()`,
  which is what separates "your key is wrong" from "the network hiccupped".
- New `ExchangeConnectionClassifier` — the single mapping point from any `Throwable` to
  `(status, safeMessage)`. Covers transport causes before adapter causes, so a read timeout is not
  misreported as a server error. Messages are authored in code; no exchange text is echoed.
- `ExchangeCredentialService.testConnection` is now scope-aware (`SPOT` / `FUTURES`), catches
  `Exception` rather than one subclass, and persists `FAILED` **only** for credential-attributable
  failures.
- `ExchangeCredentialConnectionResponse` gains `validationStatus`, `scope` and `canTrade`. `ok`,
  `status` and `message` are unchanged, so the existing contract and tests still hold; a
  compatibility constructor preserves the original positional form.
- Controller accepts an optional `?scope=` and its OpenAPI summary is corrected.
- Flutter reads `ok`, surfaces `validationStatus` as short guidance, and distinguishes a
  read-only key from a failure.

---

## 3. SPOT_VALIDATION

`GET /api/v3/account`, signed, read-only. Proven in `BinanceConnectionValidationTest`:

| Property | Assertion |
|---|---|
| Method / path | `GET` `/api/v3/account` |
| API key header | `X-MBX-APIKEY` equals the decrypted key |
| Signature | recomputed as `HMAC-SHA256(secret, exactQueryString)` and equal; 64 lowercase hex |
| Timestamp | within wall-clock bounds at call time |
| recvWindow | `5000`, bound from `app.live-trading.recv-window-ms` |
| Response | parsed into `canTrade` and USDT balances |

No order endpoint is touched. The suite asserts the exact set of paths a connection test may
call — `/api/v3/account`, `/fapi/v2/account`, `/fapi/v1/positionSide/dual` — so an accidental
mutating call would fail the test.

---

## 4. FUTURES_VALIDATION

`GET /fapi/v2/account` through the real `BinanceFuturesLiveAdapter`, with its existing read-only
`/fapi/v1/positionSide/dual` probe. Both scopes resolve the **same** `ExchangeCredential` row;
`bothScopesReuseTheSameStoredCredential` asserts that every saved row carries the same id and the
same `BINANCE` exchange, so no duplicate credential is created per market.

---

## 5. ERROR_MAPPING

| Condition | `validationStatus` | Persisted credential status |
|---|---|---|
| Exchange authenticated | `CONNECTED` | `CONNECTED` |
| HTTP 401 / code −2014, −2015 | `INVALID_CREDENTIALS` | `FAILED` |
| Code −1022 (bad signature) | `INVALID_SIGNATURE` | `FAILED` |
| Code −1021 | `TIMESTAMP_ERROR` | unchanged |
| HTTP 429 / 418 / code −1003 | `RATE_LIMITED` | unchanged |
| Read timeout | `TIMEOUT` | unchanged |
| DNS failure / connection refused | `NETWORK_ERROR` | unchanged |
| HTTP 5xx | `BINANCE_API_ERROR` | unchanged |
| Unrecognised HTTP 4xx | `BINANCE_CLIENT_ERROR` | unchanged |
| Non-JSON / unclassifiable | `UNKNOWN_ERROR` | unchanged |

Only the first three are credential-attributable, and only those write `FAILED`.

---

## 6. SECURITY

- No API key, secret, decrypted value, signature, `Authorization` header or raw exchange body
  appears in any response field. `noSecretAppearsInTheResponseForAnyFailureClass` runs six failure
  classes and asserts the rendered response contains none of them, including the computed HMAC.
- `theRawExchangeBodyIsNeverEchoedToTheClient` asserts the Binance message and HTTP status text
  are absent from the user-facing message.
- `thePersistedCredentialStillHoldsOnlyCiphertext` asserts the entity keeps ciphertext.
- The service never decrypts; the adapters decrypt inside `signedRequest` only.
- **Separate blocker, not expanded into:** `SettingsProperties.DEFAULT_ENCRYPTION_SECRET_KEY`
  remains `"dev-only-settings-encryption-key-seed-change-me"`. It does not prevent connection
  validation, so it was left alone as instructed. It should be fixed before production.

---

## 7. TEST_RESULTS

```
./gradlew test
  Suites: 113
  Tests: 1009
  Failures: 0
  Skipped: 2          (985 before this change; +24 new)

./gradlew build -x test
  BUILD SUCCESSFUL

Focused: BinanceConnectionValidationTest ............ 24 passed
         ExchangeCredentialServiceConnectionTest .... 10 passed
         Phase 9 adapter suites ....................... still green
         Phase 8/9 execution safety suites ........... still green

flutter analyze   9 issues, none new (pre-existing)
flutter test      127 passed, 2 skipped, 1 pre-existing failure
```

New tests cover all 16 required cases: valid spot read, invalid key, invalid secret/signature,
4xx mapping, 5xx mapping, timeout, correct signature, correct header, correct timestamp, correct
method/path/query, secret absent from the DTO, secret absent from logs, futures endpoint usage,
and no order endpoint called.

The single Flutter failure is the pre-existing `test/backtesting_test.dart:140` compile error
(missing `StrategyDescriptor.name` and `marketType`), untouched per scope and identical to the
baseline before this change.

---

## 8. RUNTIME_STATUS

```
CODE VALIDATION      verified — 1009 backend tests, 0 failures
LOOPBACK VALIDATION  verified — real adapters, real HTTP, real HMAC against 127.0.0.1
RUNTIME VALIDATION   BLOCKED_EXTERNAL_JVM — the JVM on :8080 was never started, stopped,
                     restarted or killed
REAL BINANCE         NOT PERFORMED — no production credential, no testnet key, no external call
```

**A passing loopback test is not Binance connectivity.** Nothing here demonstrates that a real
Binance key authenticates. That requires a credential and a network path this environment does
not provide, and I did not attempt either.

---

## 9. FILES_CHANGED

| File | Change |
|---|---|
| `dto/settings/ConnectionValidationStatus.java` | **new** — machine-readable outcomes |
| `service/ExchangeConnectionClassifier.java` | **new** — deterministic, non-leaking mapping |
| `dto/settings/ExchangeCredentialConnectionResponse.java` | `validationStatus`, `scope`, `canTrade` + compatibility constructor |
| `service/ExchangeCredentialService.java` | scope-aware validation, `Exception` catch, conditional persist |
| `controller/ExchangeCredentialController.java` | `?scope=` param, corrected OpenAPI summary |
| `test/.../BinanceConnectionValidationTest.java` | **new** — 24 loopback tests |
| `test/.../ExchangeCredentialServiceConnectionTest.java` | constructor wiring |
| `frontend/.../exchange_accounts_screen.dart` | read `ok`, surface `validationStatus`, read-only key case |
| `BINANCE_CONNECTION_ROOT_CAUSE.md` | **new** |
| `docs/BINANCE_ACCOUNT_CONNECTION_FIX_REPORT.md` | **new** (this file) |

---

## 10. FILES_NOT_CHANGED

- `BinanceLiveTradingAdapter`, `BinanceFuturesLiveAdapter` — signing verified correct; Phase 9 fixes untouched.
- `service/execution/**`, `service/live/**`, `service/futures/**` — no execution behaviour.
- Strategies, NFM, backtesting, paper trading.
- `entity/ExchangeCredential` — one credential per user per exchange, shared by both scopes.
- `AccountType`, `AccountMode`, `AccountCategory` — untouched.
- Migrations V1–V4 — **no schema change was required**; no V5 was created. No migration executed.
- `SettingsProperties` default seed — documented as a blocker, not refactored.
- JVM process — untouched.

---

## 11. GIT_COMMIT

`fix(binance): scope-aware connection validation with deterministic error classification`

---

## 12. REMAINING_BLOCKERS

1. **Real Binance validation is outstanding.** Everything above is code and loopback proof. No
   external credential was used, so "the connection works" remains unproven against Binance itself.
   The next step is a single manual run against a real key — not something to assert in CI.
2. **`RUNTIME_VALIDATION = BLOCKED_EXTERNAL_JVM`.** No restart was attempted, per instruction.
3. **Default encryption seed** in `SettingsProperties` / `application.yml`
   (`dev-only-settings-encryption-key-seed-change-me`) with no startup guard. Out of scope here;
   should be fail-fast before production.
4. **Pre-existing, untouched:** `BacktestService.java:81` HTTP 500 and
   `frontend/test/backtesting_test.dart:140` compile error.
5. **Futures validation makes two signed calls** (`/fapi/v2/account` plus the position-mode
   probe). Both are read-only, but the second swallows its own errors and defaults to `ONE_WAY`,
   so its result is not surfaced in the connection response. Acceptable for a connectivity check;
   flagged rather than changed.