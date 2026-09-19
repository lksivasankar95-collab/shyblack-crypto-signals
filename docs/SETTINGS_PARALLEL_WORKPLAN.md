# Settings — Parallel Workplan & Workstream Contracts

Baseline: HEAD = origin/main = `a40f5ae` (verified first-party). Working tree clean except
settings-scope docs.

## Phase 0 — Repository Discovery (first-party counts at HEAD)

| Area | Files at HEAD | Notes |
|------|--------------|-------|
| Profile | 3 | `RiskProfile.java`, `profile_screen.dart`, Android manifest |
| Subscription | 1 | `SPOT-MORNING-PLAN-FORMULA.md` (doc only — no billing infra) |
| Security/Sessions/Devices | 40 | `SecurityConfig.java`, `AuthController`, `DeviceTokenController`, JWT, FCM `DeviceToken` suite |
| Exchange Accounts | 10 | `ExchangeCredential*`, encryptors, `ExchangeCredentialController` |
| Notifications | 22 | `NotificationPreferenceService`, `NotificationController`, `DeviceTokenController` (reuse, do NOT duplicate) |
| Signal Prefs | 218 | existing signal engine suite (strategy, score, grade, cooldown) |
| Market Prefs | 39 | `MarketController`, `MarketProperties`, `MarketWebSocketConfig`, dynamic universe |
| Theme | 2 | frontend riverpod theme (`app_colors.dart`, `app_theme.dart`) |
| Language | 0 | none (English-only; do NOT fake languages) |
| Data Mgmt | — | existing caches only (news/market/chart/temp); no fake backup |
| Help & Support | 0 | none — report problem only via real infrastructure; no fake submission |
| About | 1 | `coin_about.dart` |
| Logout | 1 | `logout_user.dart` + auth SDK revoke |

## Workstream Contracts

| Workstream | Scope (owns) | Reuse (never rewrite) | Forbidden (never touch) | Produces |
|-----------|--------------|------------------------|-------------------------|----------|
| A — Trading Mode & Account | settings trading screens/widgets/controllers, `TradingMode`-consuming runtime wiring | Riverpod 3, theme, auth session, signal engine mode-aware code | signal engine internals, auth SDK | Spot/Futures/Options selector; Paper default, Live gated + explicit confirm |
| B/D — Profile + Exchange Accounts | profile + exchange-account UI; exchange backend-owned files | `ExchangeCredential*`, `ExchangeCredentialEncryptor`, `AesGcmEncryptor`, existing controllers | google sign-in dirty files, auth core | masked state, connect/test/disconnect, no secret ever returned |
| C — Security + Devices | security/device settings + screens | `SecurityConfig`, `DeviceTokenController`, auth | google sign-in dirty files, auth core | PIN/biometric/session + device list/revoke, all user-scoped |
| E — Notifications | notification preference UI only | `NotificationPreferenceService`, `NotificationController`, FCM | FCM, `NotificationPreferenceService` internals | toggle rows that persist + feed real service |
| F — Signal Prefs | signal-preference settings + wiring | signal engine runtime consumers (score/grade/cooldown/dedup) | news + google files | only settings consumed by the engine; no decorative fields |
| G — Market Prefs | market preference settings | `MarketController`, `MarketProperties`, dynamic symbol universe, WebSocket config | WebSocket/REST source config, rate-limit policy | default market/symbols/refresh/persist; NO hardcoded 5-symbol universe |
| H — Theme + Language | theme setting persistence | existing riverpod theme providers | theme architecture core | System/Dark/Light persisted + applied w/o restart; language = English-only plumbing (no fake switching) |
| I — Data Management | cache-clear UI + endpoints | existing cache/services | signals, trades, account, subscription, credentials (NEVER listed as clearable) | confirm-gated destructive actions |
| J — Help/Support/About | support/about screens + wiring | `coin_about.dart`, existing support infra | fake submission | real support if API exists; version from real package metadata; no hardcoded versions |
| K — Logout | logout integration in settings | existing `logout_user.dart` + auth SDK | auth core, news/google files | full logout (token revoke, local state cleanup, nudge to login; never deletes permanent data) |

**Parallel rule:** each workstream writes ONLY its owned files. Any shared file (controller,
provider, router, model) is declared in `docs/SETTINGS_WORKSTREAM_CONTRACTS.md` and routed through
the Integration workstream — never edited from two workstreams. No agent resolves a shared-file
conflict by blindly accepting one side.

**Honesty gates (Phase 35/DoD — do NOT fake):**
- No fake paper/live activation; Live requires real backend + explicit confirmation.
- No fake subscription status/payment/support — not exposed as success when infra absent.
- No fake language switching (only English exists).
- No fake exchange connectivity (test only against real adapter result).
- No secrets: never returned in DTOs, never logged, never in frontend, never in git.
- Live trading: never default; destructive actions require confirmation.
