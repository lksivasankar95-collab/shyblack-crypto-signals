# Settings — Parallel Workstream Contracts

Mandate: independent, isolation-enforced workstreams. A workstream that needs a shared file must
NOT edit it directly — it routes the change to Integration. No two agents resolve a conflict by
blindly accepting one side.

## Ownership matrix (first-party discovery at HEAD a40f5ae)

| Workstream | Owns (files) | Reuses (never rewrites) | Forbidden (unchanged) | Produces | Dependencies |
| ---------- | ------------ | ----------------------- | --------------------- | -------- | ------------ |
| A Trading Mode/Account | trading/settings backend+f web | TradingMode.java (exists), session/auth context, market infra | real order execution absent -> must NOT create fake live orders | mode set/validate/persist + Paper-safe default; Live gated + confirmed | Auth, ExchangeCredential |
| B Profile | profile backend+f web | existing User/Employee entity, SecurityConfig, AuthContext, @AuthenticationPrincipal | duplicate User; bypass auth | profile read/update/edit scoped to current user, no IDOR | Auth |
| C Security+Devices | security/device files | AesGcmEncryptor, SecurityConfig, DeviceToken/device infra, auth sessions | rewrite SecurityConfig; PIN plaintext; session-token exposure | PIN/Biometric prefs (never plaintext, never returned), devices list/revoke/logout-all user-isolated | Auth, Session/site |
| D Exchange Accounts | exchange-settings files | ExchangeCredential*, encryptors (already 10 files), adapters | rewrite adapters; return API secret; log secret | connect/test/update/disconnect, masked key status, ownership only | ExchangeCredential, Auth |
| E Notifications | notification-settings UI | NotificationPreferenceService, NotificationController, FCM, DeviceToken (existing 22 files) | duplicate notification-pref logic; touch FCM | settings screen wired to REAL persistence, no fake submission | Notification service |
| F Signal Preferences | signal-settings files | existing signal engine (219 files), SignalValidator/when present | reimplement signal engine; decorative params with no consumer | settings -> signal engine runtime consumers; no fake params | Signal Engine |
| G Market Preferences | market-settings files | MarketProperties, MarketConfig, WS infra (39 files) | hardcode 5 symbols; duplicate WS connection; bypass rate limits | default market/symbols/watchlist/refresh/fallback wired to real runtime | Market |
| H Theme | theme files | app_theme.dart, app_colors.dart, existing Riverpod theme provider | second theme architecture; restart-required theme | System/Dark/Light persisted + applied | Theme providers |
| H Language | l10n plumbing | existing localization if any | fake multi-language; fake switch | English persistence only; honest | l10n |
| I Data Mgmt | data-management files | cache/backup services where present | delete signals/trades/account/subscription/credentials via generic clear | cache/temp clear (scoped), export/import only if infra exists | cache/backup |
| J Help+About | support/about files | existing help/support API if present | fake support submission; hardcoded versions | help/faq/about from real metadata, contact via real support only | support/about |
| K Logout | logout flow | logout_user.dart, auth logout, SDK revoke (existing 1 file) | delete permanent user data; break auth core | full logout: auth state + local auth state + device token/(sensitive temp cleanup) + nav to login | Auth |

## Shared-file rule

Model/controller/router/provider required by >1 workstream: add to ALLOCATED TABLE below once,
Integration performs the edit. No workstream writes a shared file directly.

## Gate

Commit = settings-scope files ONLY. No news/google GSI dirty files, no API keys/.env/secrets
(staged-set secret scan must be 0), no force push Thoroughly. Porcelain must be settings-only.

## Honesty charter

- Do not claim green without a first-party run.
- Do not claim live/production verification that never ran.
- Do not claim real orders/subscription/language/exchange without backend reality.
- No fake "Coming Soon" for implementable features; no fake tickets for absent backend.
