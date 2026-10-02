# Portfolio Phase 10 — LIVE Sync Lifecycle Report

Scope: close the single missing edge in the LIVE Portfolio lifecycle — nothing ever
started `LivePortfolioSyncService` / `LiveUserStreamManager`, so a LIVE Portfolio
stayed `NOT_CONNECTED` even with valid Binance credentials.

Nothing outside this edge was changed.

---

## 0. Status separation (as required)

| Dimension | Status |
|---|---|
| **CODE_COMPLETE** | **Yes.** Coordinator, properties, four trigger points, stale-scope repair, teardown. Compiles; `gradlew build -x test` green. |
| **TEST_COMPLETE** | **Yes.** **1138 backend tests pass, 0 failures, 2 pre-existing skips** (was 1094; **+44 new**). 145 Flutter tests pass. |
| **LOOPBACK_COMPLETE** | **Unchanged and still true.** All Binance adapter tests continue to run against `127.0.0.1` (`FakeExchangeHttp`). Verified green. |
| **RUNTIME_VALIDATION** | **BLOCKED_EXTERNAL_JVM.** No JVM was started, stopped, restarted or managed. |
| **REAL_BINANCE_VALIDATION** | **Not performed.** No real Binance account was contacted. No production credential was used. |

**No claim is made that "Binance Portfolio fully works."** What exists is code with
tests, proven against mocks and loopback only.

---

## 1. DISCOVERY

Full findings: `docs/PORTFOLIO_PHASE10_SYNC_DISCOVERY.md`.

### Git baseline recorded before any edit

`HEAD` = `b8c5994`, `git stash list` empty. The working tree was **already dirty in
two independent sets**:

- **Set A — Backtesting, unrelated:** 17 modified + 4 untracked files, including
  `application.yml`.
- **Set B — Portfolio Phase 1–9, this feature's lineage, uncommitted.**

Neither was touched, reverted, or staged by this task.

### What already worked and was reused unchanged

REST-before-stream ordering, reconnect-with-reconciliation, duplicate-drop
suppression, per-scope idempotency, PAPER/OPTIONS/MAIN refusal, event dedup, the
high-water mark, the listen-key lifecycle, secret hygiene, and failure
classification all existed and were already tested. **Only the trigger was
missing.**

---

## 2. ROOT_CAUSE

`LiveUserStreamManager`, `LivePortfolioSyncService` and `LivePortfolioReconcileService`
had **zero production callers**. An exhaustive search across `backend/src` matched
only the portfolio package itself, `PortfolioUserStreamConfig`,
`BinanceUserStreamConnector`, and tests.

There are exactly 8 `@Scheduled` methods in the backend, and **none touches the
portfolio read model**:

| Class | Target |
|---|---|
| `SpotSignalScheduler`, `FuturesSignalScheduler` | signals |
| `MarketCatalogService` | market catalog |
| `NewsIngestionScheduler` | news |
| `LiveTradingReconciliationService` (×2) | `live_orders`, `live_trading_accounts` |
| `FuturesReconciliationService` (×2) | `futures_orders`, `futures_trading_accounts` |

**Consequence:** `portfolio_exchange_balances` and `portfolio_exchange_positions` had
no periodic writer at all, so `PortfolioAccountReadService.liveSpotAvailability`
saw no connection row and reported `UNAVAILABLE`. A LIVE user with valid
credentials saw an account that never synchronised.

### A second, adjacent defect found

`LiveUserStreamEventProcessor` returns `RECONCILE_REQUIRED` for a spot
deposit/withdrawal (the event reports neither new free nor new locked) and sets the
scope to `STALE`. **Nothing anywhere reacted to `STALE`** — reconciliation only
happened on a socket drop, and a healthy socket never drops. So even with a start
trigger, a deposit would have left the Portfolio permanently stale.

Fixed in Phase 5 of this task (§8).

---

## 3. START_TRIGGER

Four trigger points, and no others. No new state was created; `ExchangeCredential`,
`PortfolioAccountConnection`, `AccountMode` and `AccountCategory` are all reused.

| # | Trigger | Location | Action |
|---|---|---|---|
| 1 | Credential accepted by the exchange | `ExchangeCredentialService.testConnection` success path | start both scopes |
| 2 | Settings account mode → LIVE | `SettingsService.update` | start both scopes |
| 3 | Process start | `PortfolioSyncResumeRunner` (`ApplicationRunner`) | resume already-connected scopes |
| 4 | Credential deleted / disconnected / revoked, or mode → PAPER | `ExchangeCredentialService`, `SettingsService` | stop both scopes |

**Eligibility is checked before any credential is looked up:**

- `AccountMode.LIVE` only — a paper account never starts a stream.
- `SPOT` and `FUTURES` only. `MAIN` is refused (aggregate read model, not an
  account) and `OPTIONS` is refused (reserved capability). Both are structurally
  refused, **not** configurable.

### Why `testConnection` is the correct hook

It is the only production path reaching `credential.setStatus(CONNECTED)` after a
real authenticated, signed, read-only exchange read. It already distinguishes
credential-attributable failure from transport failure, so a laptop losing Wi-Fi
never marks a good key broken. It places, cancels and modifies nothing.

### The resume path

Without trigger 3, every deploy would silently stop synchronising an account that
had been connected all along. `resumeAlreadyConnected()` is deliberately
conservative: only users whose stored credential is already `CONNECTED` **and**
whose account is in live mode. A restart can never manufacture trust for a key the
exchange has not accepted. Proven by
`resume: an unvalidated credential is never resumed on a restart`.

---

## 4. INITIAL_RECONCILIATION

**Ordering is enforced by existing code, not duplicated.** `LiveUserStreamManager.start`
already calls `reconcile(...)` and only reaches `connector.open(...)` when the report
succeeded; on failure it returns `ERROR` having opened nothing.

```
start
 ├─ mark SYNCING (coordinator)        ← in-flight, nothing proven yet
 ├─ REST reconcile                    ← LivePortfolioReconcileService
 │    ├─ SPOT   → /api/v3/account      → portfolio_exchange_balances
 │    └─ FUTURES→ /fapi/v2/positionRisk → portfolio_exchange_positions
 ├─ persist snapshots                  ← LivePortfolioSyncService (existing writers)
 ├─ update connection status            ← portfolio_account_connections
 └─ only then: open user-data stream
```

Persisted via the **existing** entities and services. The coordinator writes **no**
balance, position or fill itself, and never calls an adapter.

### Writer ownership respected

`live_trading_accounts` and `futures_trading_accounts` are owned by the existing 60s
live/futures reconciliation jobs. The coordinator goes through
`LivePortfolioSyncService` / `LivePortfolioReconcileService` and never touches them.

---

## 5. SPOT_SYNC

Unchanged and reused: `/api/v3/account` → `LivePortfolioSyncService.syncSpot` →
`portfolio_exchange_balances`, status on `portfolio_account_connections`. The user
stream applies `outboundAccountPosition` / `balanceUpdate` / `executionReport` through
the **existing** `LiveUserStreamEventProcessor`, unmodified.

Proof: `spot balances are persisted before any stream is opened` asserts the
persisted free/locked values, `AVAILABLE`, and a non-null `lastSyncedAt`.

---

## 6. FUTURES_SYNC

Unchanged and reused: `/fapi/v2/positionRisk` → `syncFutures` →
`portfolio_exchange_positions`, with `ACCOUNT_UPDATE` / `ORDER_TRADE_UPDATE` applied
by the existing processor. Mark price, liquidation price and leverage are preserved
from their last REST values by design.

Proof: `futures positions are persisted before any stream is opened`.

---

## 7. USER_DATA_STREAM

**No redesign.** `BinanceUserStreamConnector`, `LiveUserStreamManager`,
`LiveUserStreamEventProcessor`, `PortfolioExchangeEventApplied` and the high-water
mark are all used exactly as they were. Event formats untouched.

Idempotency preserved and re-proven end to end:

| Proof | Test |
|---|---|
| An event updates the snapshot | `an event applies to the snapshot` |
| Replaying it changes nothing | `replaying the identical event changes nothing` |
| An out-of-order event is rejected | `an event older than the high-water mark is rejected as stale` |

---

## 8. RECONNECT

Ordering preserved and re-proven:

| Requirement | Proof |
|---|---|
| A drop triggers REST reconciliation | `a drop triggers a REST reconciliation before anything resumes` |
| One disconnect ⇒ one reconciliation | `one disconnect causes exactly one reconciliation, via the once-only latch` |
| A misbehaving transport is not amplified | `a transport that signals one disconnect many times causes one reconciliation` |
| Restart replaces, never duplicates | `restarting after a stop mints exactly one fresh stream` |

The test double `FakeUserStreamConnector` mirrors the real connector's
compare-and-set drop latch, so a duplicate-signal test measures the composed system
rather than a bug the real transport does not have.

### The stale-scope repair (previously dead)

`reconcileStaleScopes()` reads only scopes flagged `STALE`, so it is a no-op when
idle. It re-reads through the existing REST baseline and never fabricates a value.

`G. a stale scope is reconciled without touching the stream` seeds a real baseline,
changes the balance (a deposit), marks the scope stale, and proves the **balance
itself** is repaired — not merely the flag cleared.

---

## 9. DISCONNECT

| Event | Behaviour | Proof |
|---|---|---|
| Credential deleted | both scopes stopped, sockets closed | `deleting the credential stops the lifecycle` |
| Credential disconnected / revoked | both scopes stopped | same code path as delete |
| Account mode → PAPER | both scopes stopped | `switching the account back to paper stops the lifecycle` |
| — | snapshot rows **retained** as last known-good | `teardown leaves the last known-good snapshot intact` |

Paper, OPTIONS and MAIN never create a Binance stream or acquire a LIVE connection
row — proven by `paper never starts a stream`, `OPTIONS never starts a stream`,
`MAIN never starts a stream`, and
`a paper account never gains a live connection row`.

User isolation proven by `one user's scopes are never visible to another's`.

---

## 10. CONCURRENCY

| Requirement | Proof |
|---|---|
| `startSync()` ×3 ⇒ one lifecycle | `three consecutive start requests open exactly one stream per scope` |
| Connect event + manual refresh ⇒ one socket | `a connection event racing a manual refresh still opens one stream` |
| 8 concurrent starts ⇒ one stream | `concurrent start requests for one scope still open a single stream` |
| Stop/start cleanly replaces | `restarting after a stop cleanly replaces the lifecycle` |
| No duplicate listen keys | enforced by the above: `openCount()` counts actual `open()` calls |

Implemented with two concurrent sets — `active` (live lifecycles) and `inFlight`
(attempts underway). A scope in `inFlight` is never entered twice, so no second
reconciliation or second listen key can be minted. A failed start is **not** added to
`active`, so a later trigger can retry rather than being permanently suppressed
(`a scope left inactive can still be retried once the exchange recovers`).

---

## 11. STATUS_HANDLING

Uses the **existing** status model only — `AccountAvailability`,
`ExchangeConnectionStatus`, `PortfolioAccountConnection`. No duplicate status state.

| Situation | Reported |
|---|---|
| No credential | `NOT_CONNECTED` / `NOT_CONNECTED` |
| Baseline in flight | `SYNCING` / `CONNECTING` |
| Baseline succeeded + stream opened | `AVAILABLE` / `CONNECTED`, `lastSyncedAt` set |
| Event proved a REST read is needed | `STALE` / `CONNECTED` (existing processor) |
| Baseline or reconciliation failed | `ERROR` / `FAILED`, `lastSyncedAt` **null** |
| Options | `UNSUPPORTED` (existing) |
| Paper | existing paper semantics, never a Binance connection |

### The headline rule

**`CONNECTED` is only ever reported alongside a real REST baseline**, enforced by the
invariant that `lastSyncedAt` is the only thing that ever sets it and is null on
every failure path. Asserted end to end by
`CONNECTED is only ever reported alongside a real REST baseline`, and negatively by
`a failed baseline records the failure rather than a success`
(`lastSyncedAt` null, status not `AVAILABLE`).

### A truthfulness gap closed

Nothing previously ever wrote `SYNCING`, so an in-flight baseline left a scope
showing its previous state. The coordinator now marks `SYNCING` before delegating.
Two safeguards: it never creates a connection row (the baseline read is about to, and
only that can fill in an honest status), and it never downgrades a scope that is
already `NOT_CONNECTED` or `UNSUPPORTED`, because those are more specific than
"still working on it".

---

## 12. FAILURE_HANDLING

| Failure | Behaviour | Proof |
|---|---|---|
| Credential rejected | existing classifier marks `FAILED`; no sync starts | `invalid credentials do not start sync` (no credential) / classifier untouched |
| Timeout / network | `retryable`, recorded as a sync failure; **credential untouched** | `a temporary failure never invalidates the stored credential` |
| Binance 5xx | temporary sync failure via existing `describe` | unchanged |
| WebSocket drop | stale → reconcile → resume | §8 |
| REST reconciliation failure | **stream never opened** | `no socket when the baseline read fails`, `no socket when the baseline read fails` |
| Fabricated zeros | never written | `a good snapshot survives a later failed reconciliation` |

**`syncCoordinator` is resolved leniently and every call site is wrapped**, so a
lifecycle problem can never turn a successful credential validation or a successful
settings update into a failed request. The credential is already valid and persisted,
so losing the read lifecycle is a degradation, not an error.

**A real bug was caught by the regression suite here.** The first implementation
called `currentUser(principal)` *outside* the try/catch; that lookup threw in
`BinanceConnectionValidationTest` and turned a genuinely successful validation into a
reported failure (5 tests). Fixed by taking the owner from the already-verified
`credential.getUser()`, which also removes a redundant database read.

---

## 13. TEST_RESULTS

### New suites (44 tests)

| Suite | Tests | Covers |
|---|---|---|
| `PortfolioSyncLifecycleCoordinatorTest` | 26 | triggers, scope eligibility, baseline-first ordering, idempotency, teardown, resume, stale repair, Settings trigger, no-trading |
| `PortfolioSyncFailureSafetyTest` | 10 | baseline failure, snapshot preservation, credential safety, reconnect, no secret leakage |
| `PortfolioSyncEventAndStatusTest` | 8 | event idempotency, high-water mark, truthful status, paper isolation |

### Test doubles added

`FakeUserStreamConnector` (in-process transport, models the once-per-disconnect latch,
never deduplicates so a duplicate-lifecycle bug stays visible) and
`FakeUserStreamConfig` (`@Primary` override of the real connector, so **no test can
reach an exchange socket** through this path).

### Required-proof checklist

| # | Proof | Where |
|---|---|---|
| 1 | Valid LIVE credential triggers sync | `successful validation starts sync`, `validation through the service starts sync` |
| 2 | Invalid credentials do not start sync | `no credential starts nothing`, `a revoked credential is not started`, `an unvalidated credential is never resumed` |
| 3 | PAPER does not start sync | `paper never starts a stream` |
| 4 | OPTIONS does not start sync | `OPTIONS never starts a stream` |
| 5 | MAIN does not start sync | `MAIN never starts a stream` |
| 6 | SPOT REST baseline first | `spot balances are persisted before any stream is opened` |
| 7 | FUTURES REST baseline first | `futures positions are persisted before any stream is opened` |
| 8 | WebSocket only after baseline | `the stream is never opened when the baseline could not be established` |
| 9 | Baseline failure prevents startup | `no socket when the baseline read fails` |
| 10 | Disconnect triggers reconciliation | `a drop triggers a REST reconciliation before anything resumes` |
| 11 | Reconnect creates a fresh stream | `restarting after a stop mints exactly one fresh stream` |
| 12 | Duplicate starts ⇒ one lifecycle | `three consecutive start requests…`, `concurrent start requests…` |
| 13 | Duplicate events ignored | `replaying the identical event changes nothing` |
| 14 | Different users isolated | `one user's scopes are never visible to another's` |
| 15 | Deactivation stops sync | `switching the account back to paper stops the lifecycle` |
| 16 | Credential deletion stops sync | `deleting the credential stops the lifecycle` |
| 17 | Temporary failure ≠ invalid credential | `a temporary failure never invalidates the stored credential` |
| 18 | Snapshot not replaced by fabricated zeros | `a good snapshot survives a later failed reconciliation` |
| 19 | CONNECTED never before baseline | `CONNECTED is only ever reported alongside a real REST baseline`, `a failed baseline records the failure rather than a success` |
| 20 | No order endpoint called | `no order endpoint is reachable from synchronization` |
| 21 | No ExecutionRouter path invoked | coordinator depends on no execution class; no execution test changed |
| 22 | Paper tests green | full suite green |
| 23 | Phase 8 execution tests green | targeted run green |
| 24 | Phase 9 adapter tests green | targeted run green |

### Commands and results

```
backend:  gradlew test                → BUILD SUCCESSFUL — 1138 tests, 0 failures, 2 skipped
backend:  gradlew build -x test       → BUILD SUCCESSFUL
backend:  gradlew test --tests "...portfolio.PortfolioSync*"   → BUILD SUCCESSFUL
backend:  execution + paper + exchange + isolation suites     → BUILD SUCCESSFUL
frontend: flutter test               → All tests passed (145 passed, 2 skipped)
```

Baseline was 1094 tests; **+44**.

---

## 14. FLUTTER_STATUS

**No Flutter file was changed.** Verified by inspection before deciding.

The screen already displays every state the lifecycle can report:

| State | Where it renders |
|---|---|
| syncing | `PortfolioAvailabilityChip` → `SYNCING` |
| connected | chip → `CONNECTED`; `SYNC STATUS` rows show `Connection`, `Last REST sync`, `Last stream event` |
| stale | chip → `STALE` **and** the `Data is stale` panel explaining the values must not be read as current |
| unavailable | `LIVE ACCOUNT / Connection unavailable` panel, which explicitly says no simulated data is shown in its place |

The account mode still comes from `AppSettings.tradingAccount` /
`User.accountType`. No `[PAPER] [LIVE]` selector was added. No Binance credential is
displayed anywhere on the Portfolio screen (asserted by
`no credential or listen key is ever displayed`).

The one gap on the backend side — nothing ever wrote `SYNCING` — was fixed in the
backend rather than papered over in the UI.

---

## 15. RUNTIME_STATUS

**`RUNTIME_VALIDATION = BLOCKED_EXTERNAL_JVM`**

No JVM was started, stopped, restarted or killed. The backend runtime is externally
managed and was not touched. Nothing here has been observed running.

---

## 16. REAL_BINANCE_STATUS

**Not validated.** No real Binance account was contacted. No production credential
was used. No real order was placed, cancelled or modified.

Adapter behaviour remains loopback-only (`127.0.0.1`), exactly as before this task.

---

## 17. FILES_CHANGED

### New — production
```
backend/src/main/java/com/shyblack/cryptosignals/config/PortfolioSyncProperties.java
backend/src/main/java/com/shyblack/cryptosignals/service/portfolio/PortfolioSyncLifecycleCoordinator.java
backend/src/main/java/com/shyblack/cryptosignals/service/portfolio/PortfolioStaleScopeScheduler.java
backend/src/main/java/com/shyblack/cryptosignals/service/portfolio/PortfolioSyncResumeRunner.java
```

### Modified — production
```
backend/src/main/java/com/shyblack/cryptosignals/ShyblackBackendApplication.java   (+ PortfolioSyncProperties registration)
backend/src/main/java/com/shyblack/cryptosignals/repository/UserRepository.java      (+ findByAccountType)
backend/src/main/java/com/shyblack/cryptosignals/service/ExchangeCredentialService.java  (4 lifecycle hooks)
backend/src/main/java/com/shyblack/cryptosignals/service/SettingsService.java       (account-mode hook)
```

### New — test
```
backend/src/test/java/com/shyblack/cryptosignals/service/portfolio/FakeUserStreamConnector.java
backend/src/test/java/com/shyblack/cryptosignals/service/portfolio/FakeUserStreamConfig.java
backend/src/test/java/com/shyblack/cryptosignals/service/portfolio/PortfolioSyncLifecycleCoordinatorTest.java
backend/src/test/java/com/shyblack/cryptosignals/service/portfolio/PortfolioSyncFailureSafetyTest.java
backend/src/test/java/com/shyblack/cryptosignals/service/portfolio/PortfolioSyncEventAndStatusTest.java
```

### New — docs
```
docs/PORTFOLIO_PHASE10_SYNC_DISCOVERY.md
docs/PORTFOLIO_PHASE10_SYNC_LIFECYCLE_REPORT.md
```

### Not modified
**`application.yml`.** This was a deliberate decision: the file already contains
unrelated Set A Backtesting changes, so editing it would entangle two work streams. All
defaults live in `PortfolioSyncProperties` Javadoc and, because Spring uses relaxed
binding, the feature is fully operable by environment variable:

| Property | Default | Effect |
|---|---|---|
| `app.portfolio.sync.enabled` / `APP_PORTFOLIO_SYNC_ENABLED` | **false** | master switch |
| `app.portfolio.sync.stale-reconcile-interval-ms` | `60000` | stale-scope repair cadence |
| `app.portfolio.sync.baseline-only-start-scopes` | `false` | REST baseline only, no socket |

**Off by default** means shipping this code cannot by itself open an exchange
stream, and the test profile cannot open a socket.

---

## 18. FILES_NOT_CHANGED

- **Spot strategies**, **futures strategies**, **NFM**, **backtesting** (engine,
  strategies, limits, persistence, screens, `application.yml`).
- **Paper Trading semantics** — engine, execution service, partial exits,
  `PaperPartialExitState`, P&L, sizing, accounting, position lifecycle.
- **Options execution** — still a reserved capability, still refused.
- **MAIN** — still non-executable and not user-navigable.
- **`ExecutionRouter`** and the entire Phase 8/9 execution safety boundary.
- **`LivePortfolioSyncService`, `LiveUserStreamManager`, `LiveUserStreamEventProcessor`,
  `LivePortfolioReconcileService`, `BinanceUserStreamConnector`,
  `PortfolioUserStreamConfig`, `PortfolioExchangeEventApplied`** — all reused as-is.
  No existing lifecycle component was modified.
- **`ExchangeCredential`** and `ExchangeCredentialEncryptor` — one table, unchanged.
  No duplicate credential was created.
- **Order placement / cancellation** anywhere.
- **Schema** — no migration, no Flyway, no Liquibase, no DDL change.
- **Flutter** — not one file changed.
- All Set A Backtesting files, including `application.yml`.

---

## 19. GIT_STATUS

**Nothing staged, committed or pushed.**

Per the task's Phase 14: *"Because repository rules may require commit/push, inspect
PROJECT_RULES.md and determine the applicable rule. If commit/push is required, commit
ONLY Phase 10 changes. If push conflicts with an explicit task-level instruction, do
NOT silently choose one. Report the conflict."*

**The conflict, reported rather than resolved:**

`docs/PROJECT_RULES.md` §9 mandates an automatic commit and push after every change
("The developer should not have to say 'commit' or 'push' after each task"). This task
instructs a commit decision but never authorises a push, and the previous session's
stop condition explicitly withheld pushing. The same PROJECT_RULES document also
requires flagging a conflict rather than silently deviating.

**Resolution: commit the Phase 10 files only; do not push. Both are reported here for
the developer to confirm.**

If committed, the staging set is exactly the 13 Phase 10 paths in §17. It must be
staged **by explicit path** — `git add -A` would sweep in all 21 Set A Backtesting
files and the whole Set B Phase 1–9 Portfolio change set, which are separate work.

---

## 20. REMAINING_BLOCKERS

1. **The feature ships disabled.** `app.portfolio.sync.enabled` defaults to `false`, so
   until an operator sets it the Portfolio behaves exactly as before. This is a
   deliberate safety default, not a defect, but it means the gap is closed in code and
   not yet switched on in any environment.
2. **`application.yml` carries no documented entry**, because that file already holds
   unrelated changes (§17). The properties are documented in the properties class and
   operable by environment variable, but an operator reading the config file will not
   find them. Worth adding once the Backtesting work is committed separately.
3. **No runtime verification.** `RUNTIME_VALIDATION = BLOCKED_EXTERNAL_JVM`. The
   ordering, refusals and idempotency are proven by tests against mocks, not observed
   against a running process.
4. **No real Binance validation.** The parse and classification assumptions that a real
   account could still falsify are unchanged from the pre-existing baseline: the
   `stopPrice`/`avgPrice` `"0"` sentinel, and `PENDING_CANCEL` → `CANCELED`.
5. **`BinanceUserStreamConnector.open()` is non-blocking.** `start()` returning
   `CONNECTED` means *a stream was registered*, not *the socket is up* — the connector
   schedules the connection and returns immediately. The required ordering (REST
   baseline strictly before the stream) **is** satisfied, but "stream readiness" must
   not be read into that return value, and the UI does not.
6. **`LiveUserStreamManager.start` is `synchronized` on the instance** while performing
   a REST read and a listen-key request. It therefore serialises every user's start,
   stop and drop handling across all scopes. This is a **pre-existing** Phase 4 design
   characteristic, unchanged here because altering it would put the carefully tested
   Phase 4 concurrency guarantees at risk for a scalability concern rather than a
   correctness one. It would matter at meaningful user counts.
7. **An idle stream is not detected as a gap.** `LiveUserStreamEventProcessor` establishes
   gaps only at reconnect, never from inactivity — by design. A silently dead socket
   that never fires a close frame would leave a scope looking fresh until its snapshot
   falls outside the 180-second freshness window. A heartbeat-based liveness check would
   close this.
8. **`resumeAlreadyConnected` scans all live users at startup** and starts a stream for
   each. Correct, but O(live users) at boot; a bounded batch would be kinder to a large
   deployment.
9. **Pre-existing unrelated items, deliberately untouched:** 2 skipped backend tests,
   2 skipped Flutter tests.

---

## 21. Summary of the fix

One class, `PortfolioSyncLifecycleCoordinator`, decides *when* a LIVE Portfolio scope
begins and stops synchronising. It performs no exchange I/O, writes no balance,
position, fill or order, and holds no secret. Everything it decides is delegated to
components that already existed and were already tested:

```
credential accepted ─┐
Settings → LIVE ──────┼─→ coordinator ─→ REST baseline ─→ user-data stream ─→ events
process start ────────┘        │              │                 │
                                │              │                 └─ reconnect: reconcile, then fresh stream
                                │              └─ fails ⇒ no socket is opened
                                └─ PAPER / OPTIONS / MAIN ⇒ refused before any credential lookup
```

Ordering is guaranteed — REST authoritative baseline first, WebSocket incremental
second — and a scope may only report `CONNECTED` once a real REST read has succeeded.