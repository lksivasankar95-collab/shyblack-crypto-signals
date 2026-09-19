# Settings & Exchange Credentials Module

Full-stack user settings + exchange API credential management, additive to the
existing news and Google-auth modules. Backend green (all suites) on `gradlew
build`; Flutter analyze/test are documented separately below (environment
limitation in this sandbox, not a code failure).

## Scope discipline

- **Settings scope** = this module (settings + exchange credential), its tests,
  and the minimal shared bootstrap it requires.
- **News scope** / **Google scope** = existing modules. Their tracked changes
  stay unstaged (excluded) in the same working tree and are NOT part of the
  settings commit.
- All settings files are **additive** (`??` untracked) except two tracked
  bootstrap lines (see "Bootstrap" below).

## Backend

### API

| Method | Path                         | Purpose                                        |
|--------|------------------------------|------------------------------------------------|
| GET    | `/api/v1/settings`           | Current user's settings (parsed from `User`)   |
| PATCH  | `/api/v1/settings`           | Partial update; only non-null fields applied   |
| GET    | `/api/v1/settings/meta`      | Selectable options + configured trading ceilings |
| GET    | `/api/v1/settings/exchanges` | Masked exchange API credentials                |
| POST   | `/api/v1/settings/exchanges` | Save/roll credentials (encrypted at rest)      |
| POST   | `/api/v1/settings/exchanges/{id}/test-connection` | Deterministic connectivity check |
| DELETE | `/api/v1/settings/exchanges/{id}`               | Remove a credential            |

Security: every endpoint resolves the principal from the Spring Security
context (`UserPrincipal`); the user id is never taken from the request body —
IDOR-safe. Endpoints are annotated `@SecurityRequirement("bearer-jwt")`.

### Entities & persistence

- `UserSettings` — user-owned settings row (unique constraint on owner).
- `ExchangeCredential` — API key/secret stored **encrypted at rest**;
  views only ever return masked values.

### Encryption

- `AesGcmEncryptor` — AES-256-GCM wrapper (static IV derivation, integrity tag).
- `ExchangeCredentialEncryptor` — masks/marshals credentials; **no raw secret
  ever leaves the service layer**.

### Config (`SecurityProperties`)

- Configurable trading ceilings (`riskPerTradeCeilingPct`,
  `maxPositionSizePct`, `maxLeverage`), validated at bind/update time;
  out-of-range values raise `SettingsValidationException` (409).

### Tests

- `SettingsServiceTest`, `ExchangeCredentialServiceTest`,
  `AesGcmEncryptorTest` — unit + Spring context. Green in `gradlew build`.

## Bootstrap (shared, additive)

`ShyblackBackendApplication` @EnableConfigurationProperties now also registers
`SettingsProperties`. The single existing `NewsProperties` bean, which the news
module already constructor-injects (`RssNewsProvider`, scheduler, ingestion
services), was missing from that annotation — the context therefore could not
load and blocked every integration suite on startup. Registering it is a
one-line additive bugfix to a shared bootstrap file and is included in this
commit with the settings work it unblocked; it does not change news behaviour.

## Frontend

Settings UI (Profile/Settings screens) is built with Riverpod:

- `settingsControllerProvider` (AsyncNotifier) — state + patch.
- `SettingsRepositoryImpl` → `SettingsLocalDataSource` (JSON persistence).
- Screens: `settings_screen.dart`, `profile_screen.dart`, widgets in
  `settings_widgets.dart`.

Remote sync against the endpoints above is provided by adding an HTTP
datasource implementing the same repository contract, then wiring it in a
settings-scoped provider (kept out of the Google/News–dirty DI file). The
current tracked wiring is local-first.

**Flutter analyze/test:** the Flutter/Dart SDK is not available in this build
environment (`flutter` not on PATH), so `flutter analyze`/`flutter test` could
not be executed here. This is an environment limitation, not a known code
failure; it must be run to completion on a machine with the Flutter SDK before
this is considered UI-verified.

## Verification (this run)

```
> Task :compileJava
> Task :test
BUILD SUCCESSFUL in 1h 44m 6s
8 actionable tasks (all backend suites incl. news + Google integration)
```
