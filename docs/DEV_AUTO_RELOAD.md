# Development Auto-Reload / Hot Update

Development-only workflow so backend source/schema changes reload automatically
without manually killing/starting Java.

## Root cause of the stale dev runtime
The dev backend had been started as a **one-off** process (a pre-built jar /
`bootRun`) with **no DevTools and no continuous compilation**. Editing Java
therefore never recompiled or reloaded the running JVM, leaving a stale process
(e.g. PID 4988 started 11:32) that did not reflect newer code/schema.

## What was added (dev-only)
`backend/build.gradle.kts`:

```kotlin
developmentOnly("org.springframework.boot:spring-boot-devtools")
```

- `developmentOnly` is **excluded from `bootJar`** (verified: no `devtools`
  entries in `cryptosignals-0.0.1-SNAPSHOT.jar`). It cannot ship to production.
- No dependency versions changed; Spring Boot BOM manages DevTools.

## Developer workflow (use this)
From `backend/`, start the dev backend **once** with continuous build:

```powershell
# Option A (single terminal): Gradle continuous build + DevTools restart
./gradlew bootRun --continuous

# Option B (two terminals):
./gradlew -t classes     # terminal 1: continuous compile
./gradlew bootRun        # terminal 2: app + DevTools restart
```

Then edit Java/resources under `src/main`:

```
source change → Gradle recompiles → classpath changes → DevTools restarts app
```

- No `kill`, no `java -jar`, no manual restart.
- Keep **one** instance running. Do not start a second server to "reload".

## Database (DEV auto-update)
- DEV (`application-dev.yml`): `spring.jpa.hibernate.ddl-auto=update` → after a
  restart triggered by the workflow above, Hibernate applies **additive** schema
  changes (new columns/tables). It does not drop columns/tables.
- PROD (`application-prod.yml`): `ddl-auto=validate` — **unchanged**; production
  requires explicit migration DDL. DevTools is absent in prod.

## Dev vs Prod separation
| | DEV | PROD |
|---|---|---|
| DevTools | present (`developmentOnly`) | not packaged |
| auto-restart | yes (via `bootRun` + continuous) | no |
| ddl-auto | `update` (additive) | `validate` |

## Live auto-reload verification
Not executed in this environment: a stale externally-managed backend occupies
`:8080`, and this task forbids managing/killing the external Java process.
Verification steps for the developer:
1. `./gradlew bootRun --continuous` (single instance).
2. Edit a harmless source line (e.g., a log message).
3. Confirm: Gradle recompiles → DevTools restarts → `/actuator/health` = `UP`.
4. Revert the change.

## Safety
- Real Binance/Futures orders: none.
- Spot/notification logic: unchanged.
- Production runtime: unchanged (validate, no DevTools).
