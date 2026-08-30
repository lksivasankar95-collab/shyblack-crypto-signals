# ShyBlack Crypto Signals — Project Rules

> Cursor: Always follow these rules for every task in this project, in addition to whatever specific prompt is given. If a prompt conflicts with these rules, flag it instead of silently deviating.

---

## 1. Language & Stack (STRICT)

- **Backend: Java ONLY.** Never generate Kotlin files, Kotlin dependencies, or Kotlin build config. If a `kotlin` folder or Kotlin dependency appears, remove it.
- **Frontend: Flutter + Dart**, using **Riverpod** for state management.
- **Database: PostgreSQL** (local instance, not Docker — DB name/user/password: `shyblack`/`shyblack`/`shyblack`).
- Do not introduce a different backend language, frontend framework, or database without being explicitly asked.

## 2. Architecture (STRICT)

- **Backend:** Layered — `Controller → Service → Repository → Entity`, with DTOs for all API responses (never expose entities directly). Global exception handling via `@ControllerAdvice`. Config via Spring Profiles.
- **Frontend:** Clean Architecture — `core / data / domain / presentation`. Never call an API directly from a widget — always go through data/domain layers.
- Follow this structure for every new module, no exceptions.

## 3. No AI Features

- This app does **not** use Gemini, OpenAI, Claude, or any other AI/LLM API at runtime. Do not add "AI Insights," chat, or AI-generated suggestions features unless explicitly requested again.

## 4. Design System (apply globally, never hardcode per-screen)

| Token | Value |
|---|---|
| Background | `#0D0D0D` |
| Cards | `#1A1A1A` |
| Accent / positive | `#00E676` (neon green) |
| Negative / loss | `#FF3B30` (losses only — never for anything else) |

- Fresh design only — do not reuse any old/legacy ShyBlack theme.
- All screens must match the reference mockup style: rounded inputs, green-outlined borders/cards, bold buttons, dark backgrounds.

## 5. Real-Time Data

- Prefer **WebSocket** over REST polling for live/streaming data (prices, signals) wherever practical.
- Binance public WebSocket/REST APIs for market data (no API key needed for public endpoints).
- Our backend should proxy external WebSocket connections — the Flutter app connects to **our** WebSocket endpoint, never directly to a third-party one.

## 6. Dev Workflow

- After every code change, trigger hot reload (`r`) or hot restart (`R`) on the running Flutter process automatically where possible, so manual browser refresh is the only extra step — don't leave the developer to manually stop/restart the server each time.
- Structural changes (main.dart, theming, assets) usually need a full hot **restart**, not just reload.
- Keep the project as a **single monorepo** (`backend/`, `frontend/`, `docs/`) — never split into separate repos.

## 7. Scope Discipline

- When asked to build one module (e.g. "Settings module" or "Markets module"), build only that module's screens/endpoints. Don't scaffold unrelated modules speculatively.
- Don't implement features marked "Phase 2" in `shyblack_master_spec.md` while Phase 1 is still in progress, unless explicitly asked.
- Seed data / mock data is fine for endpoints not yet wired to real logic — but always flag clearly in your response which parts are seeded/mocked vs. real.

## 8. Reference

- Full screen list, MVP phasing, and tech stack details: see `docs/shyblack_master_spec.md`.

## 9. Automation & Tab Management

### AUTO GIT PUSH
After completing any code change or task, automatically:
1. Stage the relevant files (never `.env`, credentials, or secrets).
2. Commit on `main` with a clear, descriptive message summarizing **why** / what was built or changed.
3. Push to the existing GitHub remote (`origin/main`).

Do this without being asked again. The developer should not have to say "commit" or "push" after each task.

### NO NEW CHROME TABS
Keep **exactly one** browser tab for the Flutter web app (`http://localhost:5555`).

**Hard limitation:** `flutter run -d chrome` always launches a **new** Chrome instance/tab on every **process** start. Flutter does not provide a flag to reuse an existing tab. That cannot be configured away.

**What we do instead:**
- Run the app as **`web-server`** (see `frontend/tool/run_chrome.ps1`) so Flutter **does not open Chrome** at all. Open `http://localhost:5555` **once** yourself and leave that tab open.
- After code edits, use **hot reload `r`** (small widget/logic changes) or **hot restart `R`** (`main.dart`, theme, assets, app structure). Both update the **same** tab in place and do **not** open a new tab.
- Never kill and re-run `flutter run` just to pick up Dart edits. If the process died and must be started again, do **not** use `-d chrome`; start `web-server` on port **5555** and **refresh the existing tab** (same URL). Do not launch Chrome from the CLI.

**If a Chrome-device session is already running:** press **`r`** in that terminal (or run `frontend/tool/reload.ps1 -Key r`) — do **not** start a second `flutter run -d chrome`.

