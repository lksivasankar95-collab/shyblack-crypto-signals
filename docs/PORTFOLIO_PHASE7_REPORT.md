# Phase 7 — Portfolio Information Layer

Read-only history, holdings and synchronization status for the unified Portfolio.

## Scope delivered

| Capability | Endpoint | Modes | Categories |
|---|---|---|---|
| Wallet holdings | `GET /api/v1/portfolio/{category}/holdings` | LIVE only | SPOT only |
| Orders / fills / income | `GET /api/v1/portfolio/{category}/history` | LIVE only | SPOT, FUTURES |
| Sync & reconciliation status | `GET /api/v1/portfolio/{category}/sync-status` | PAPER, LIVE | all |

`mode` defaults to `PAPER`, matching the Phase 5 contract. The caller is resolved from
the Spring Security context; no endpoint accepts a user id.

## Exchange endpoints read

| Scope | Endpoint | Types |
|---|---|---|
| Spot | `/api/v3/allOrders` | ORDER |
| Spot | `/api/v3/myTrades` | TRADE |
| Futures | `/fapi/v1/allOrders` | ORDER |
| Futures | `/fapi/v1/userTrades` | TRADE |
| Futures | `/fapi/v1/income` | INCOME (`REALIZED_PNL`) |
| Spot | `/api/v3/account` | holdings, via the Phase 3 balance snapshot |

## Design decisions

**History is read through, not persisted.** A history query is a bounded, point-in-time
question. Persisting it would create a second copy of exchange truth plus an unbounded
retention problem. Current-state snapshots (balances, positions) stay persisted exactly as
Phase 3 built them.

**Nothing is reconstructed.** Every record comes from an exchange endpoint. No fill is
derived from a signal, a local order, a position quantity or a price difference, and no
realized P&L is inferred from local state.

**Windows are explicit and bounded.** `HistoryWindow` defaults to 7 days, rejects a span
over 90 days, and caps records at 1000 (default 200). The resolved window is always returned
so a caller can see exactly what period the data covers.

**Truncation is admitted, never hidden.** A result that reaches the record cap returns
`complete: false` with an explanation. The UI renders this as a partial window.

**Ordering is total and deduplicated.** Records are newest first, ties broken on the natural
identity (`tradeId`, then `orderId`, then type+symbol+time), because a time-paginated
exchange query can repeat a boundary record. Duplicates collapse on that same identity.

**Availability is never a zero.** `NOT_CONNECTED`, `UNAVAILABLE`, `UNSUPPORTED`, `STALE` and
`ERROR` are distinct states, each with its own message. An empty wallet, an empty history and
an unavailable history are three different facts.

## Availability by scope

| Scope | Holdings | History |
|---|---|---|
| LIVE SPOT | AVAILABLE (from the balance snapshot) | ORDER, TRADE |
| LIVE FUTURES | UNSUPPORTED | ORDER, TRADE, INCOME |
| LIVE MAIN | UNSUPPORTED | UNAVAILABLE (would mix both wallets) |
| LIVE OPTIONS | UNSUPPORTED | UNSUPPORTED |
| PAPER (any) | UNSUPPORTED | UNSUPPORTED — served by the paper module |
| No credential | n/a | NOT_CONNECTED (never an empty list) |

Spot `allOrders` and `myTrades` require a `symbol`; omitting it is reported as an error rather
than guessed. Futures open orders, user trades and income may be account-wide.

## Realized P&L

Futures `REALIZED_PNL` income records are surfaced verbatim, and a fill carries the
exchange-reported `realizedPnl`. Lifetime realized P&L is **not** implemented: it would
require unbounded paging of a time-windowed endpoint, so no lifetime figure is claimed
anywhere. The windowed records are available and labelled instead.

Other income types (`COMMISSION`, `FUNDING_FEE`, …) are separate records and are never summed
into realized P&L.

## Sync status consistency

Availability is read from `PortfolioAccountConnection`, the row written by both the REST
snapshot sync and the user-stream event processor. The read model's availability is derived
from credential/account records instead, so mixing the two could report "connected, synced
just now" alongside "not connected". The connection row is therefore the authority whenever it
exists, and the read model is the fallback only.

No credential, listen key, signed query string or socket URL is exposed. A test asserts the
response body contains none of them.

## Validation

- Backend: 838 tests, 0 failures, 2 skipped (`./gradlew test`).
- Frontend: 59 portfolio tests pass; `flutter analyze` reports 9 pre-existing issues and none
  in Phase 7 files.
- `flutter test` (full suite): 127 pass, 2 skipped, 1 pre-existing failure —
  `test/backtesting_test.dart:140` omits the required `StrategyDescriptor.name` and
  `marketType`. Untouched, as agreed.

## Runtime validation

`RUNTIME_VALIDATION = BLOCKED_EXTERNAL_JVM`. The JVM on `:8080` is externally managed and was
not started, stopped, restarted or killed. All verification is loopback-based in tests. Real
Binance behaviour is unverified, and V1–V3 migrations remain unexecuted.

## Security notes (pre-existing, not addressed here)

The default settings encryption seed
`dev-only-settings-encryption-key-seed-change-me` is still present in `SettingsProperties` and
`application.yml` with no startup guard. Recommend a fail-fast check before production.