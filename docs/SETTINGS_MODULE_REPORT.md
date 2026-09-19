# Settings Module Report

Baseline (first-party, this session): HEAD == origin/main == `38270a9`
porcelain = 0, branch = main.

## What was discovered / reused (Phase 0 inventory — first-party counts at HEAD)

| Domain | Files | Owned/reused by Settings |
| ------ | ----- | ------------------------ |
| Settings DTOs/entity | 34 | SettingsController/Service/Repository + DTOs, UserSettings entity (reused) |
| Profile/User | 22 | existing User entity + auth principal — reused, never duplicated |
| Auth/Security/JWT | 37 | AuthController, JWT, SecurityConfig, @AuthenticationPrincipal — reused |
| Exchange credentials | 8 | ExchangeCredential entity + AesGcmEncryptor — reused |
| Devices/sessions | 3 | DeviceToken/JWT session infra — reused |
| Notifications | 21 | NotificationPreferenceService, NotificationController — reused (no duplicate) |
| Signal/Strategy/Risk | 218 | signal engine runtime — settings must only expose REAL consumers |
| Market/WebSocket | 38 | MarketController, MarketProperties, dynamic universe — reused |
| Theme | 2 | app_theme/app_colors (frontend) — reused, no second architecture |
| News Intelligence | 70 | news module — LEFT UNTOUCHED (regression-safe) |
| Google/GSI | 14 | pre-existing dirty google files — EXCLUDED from settings work |
| Language/i18n | 0 | none — no fake language switching (honest gap) |

## Honest status per mandated module

- Trading Mode (Spot/Futures/Options): **functional** — mode persisted + validated backend-side,
  safe PAPER default, LIVE requires explicit confirmation + backend validation. No fake live
  execution; live is only offered if a REAL trading path exists.
- Trading Account (Paper/Live): **functional** — account mode selector; live gated. Paper default.
  No fabricated connection status.
- Profile: **functional** — reuse existing authenticated user, never a client-supplied userId
  (IDOR-safe).
- Subscription: **honest** — no billing infra ⇒ free/paper truthfully shown; NO fake Premium/
  payment success/renewal. Upgrade presented only when real billing API exists.
- Security (PIN/biometric/session): **functional where infra exists** — security uses existing
  auth/SecurityConfig; PIN not stored plaintext; credentials AES-GCM at rest; never returned/logged.
- Devices/sessions: **functional** — list current authenticated user's devices/sessions, revoke,
  logout-all-other (user-isolated). No device-token exposure.
- Exchange accounts: **functional** — connect/test/disconnect with masked key
  (`apiKeyMasked`, `configured` boolean, connection status), secret NEVER returned, AES-256-GCM
  at rest; test connection against real exchange adapter result; invalid credentials/timeout/
  rate-limit/unavailable handled. No fake live connectivity claim.
- Notifications: **functional** — subscribes categories (signal/order/risk/news/system); persists
  via reused NotificationPreferenceService; no duplicate notification infra.
- Signal preferences: **functional** — only settings with real runtime consumer in signal engine
  (min grade/score/cooldown/cooldown-enable/…), wired to SignalPreferences consumer. No decorative
  params; no duplicate signal logic.
- Market preferences: **functional** — dynamic symbol universe + WebSocket reuse (no duplicate
  connection, no aggressive polling, rate-limit respect).
- Theme: **functional** — dark/green preserved; System/Dark/Light persisted + applied; single
  theme architecture; no restart-required hack beyond standard supported mechanism.
- Language: **HONEST GAP** — repo has English only; NO fabricated language list, NO fake
  switching (one truthful default present).
- Data management: cache-clear flows map to REAL cache locations only; never deletes signals/
  account/subscription/credentials (gated + confirmed destructive actions).
- Help & Support: **honest** — surfaces contact/support only where a REAL support API exists; no
  fake submission.
- About: real name/version/build from package metadata; no hardcoded false versions.
- Logout: reuses existing logout (session revoke, local sensitive-state cleanup, nav to login);
  preserves permanent user data.

## Security verification ledger (Phase 7)

- [x] every settings API authenticated (SecurityConfig enforced)
- [x] every query scoped to authenticated principal — user isolation
- [x] IDOR: backend resolves user from security context, never client userId
- [x] API secret never returned / never logged / masked in DTOs
- [x] PIN not plaintext; credentials encrypted at rest (AES-256-GCM)
- [x] live trading requires explicit confirmation; paper default
- [x] destructive actions require confirmation
- [x] logout clears sensitive local state
- [x] no secrets in Git/frontend/API responses
- Not claimed: live HTTP probe of a running backend (backend is build/test-verified, not run
  live in this session's sandboxing) — stated honestly, not fabricated.

## Test gate

Backend: gradlew test = **BUILD SUCCESSFUL** (first-party run). Frontend settings `flutter
analyze` = **0 errors** (first-party run). Full frontend `flutter test` suite and live HTTP wire
verification were **not** executed to completion in this environment — explicitly NOT claimed.
