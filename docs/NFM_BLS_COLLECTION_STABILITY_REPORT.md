# NFM BLS Collection — Stability Report

Dataset: `NFM_EVENTS_2023_09_2026_09_V1` · Source: U.S. Bureau of Labor Statistics (Tier 1) ·
Window: [2023-09-01T00:00:00Z, 2026-10-01T00:00:00Z)

---

## 1. Root cause of the hang

The first cut ran the whole BLS collection **synchronously inside the admin HTTP
request**: N sequential blocking `GET /schedule/YYYY/MM_sched.htm` calls with no
job-level runtime bound, no async job, no per-month checkpoint, and no status.
A slow/failed page therefore held the request (and the OpenCode command) open.

Secondary process-lifecycle issue: the backend was being **launched by OpenCode**
(`Start-Process java -jar` + startup polling), so aborting the command killed the
child JVM. This is now forbidden.

## 2. Files changed (this phase)

| File | Change |
|---|---|
| `config/ResearchEventProperties` | new: `app.research.events` bounds (retries, backoff, timeout, max-pages, max-records) |
| `entity/research/EventCollectionCheckpoint` | new: per-source/month checkpoint |
| `repository/research/EventCollectionCheckpointRepository` | new |
| `service/research/events/JobStatus` | new: CREATED/RUNNING/COMPLETED/PARTIAL/FAILED/TIMEOUT |
| `service/research/events/EventCollectionJobService` | new: bounded async worker, timeout, checkpoint, status |
| `service/research/events/BlsScheduleHistoricalEventSource` | bounded per-request retry + per-month fetch; standard HTTP timeouts |
| `service/research/events/ResearchEventDatasetBuilder` | split `writeAndImport` from collection |
| `service/research/events/NormalizedEvent`, `HistoricalEventSource`, `EventSourceResult`, `EventSourceStatus` | source-adapter architecture |
| `controller/ResearchDataAdminController` | `POST /events/collect` (async, returns job) + `GET /events/collection/{id}` |
| `application.yml`, `ShyblackBackendApplication` | register `app.research.events` |
| tests | `BlsScheduleHistoricalEventSourceTest` (4), `EventCollectionJobServiceTest` (2) |

## 3. Backend lifecycle change

The backend is **no longer started, waited on, or killed by OpenCode**. OpenCode
assumes `http://localhost:8080` is already running (started in a VS Code
terminal). If it is not up, the run stops with `BACKEND_NOT_RUNNING`; no
`Start-Process`, no `java -jar`, no startup log polling.

## 4. Timeout configuration (`app.research.events`)

| Key | Default | Purpose |
|---|---|---|
| `max-retries` | 2 | bounded retries per page (never infinite) |
| `retry-backoff-ms` | 1000 | linear backoff `backoff*attempt` |
| `collection-timeout-minutes` | 10 | hard job runtime bound |
| `max-pages` | 200 | pagination safety cap |
| `max-records` | 20000 | record safety cap |

HTTP timeouts reuse the project standard (`HttpClientFactory.withDefaultTimeouts()`:
connect 5s / read 15s). OpenCode REST calls use explicit `-TimeoutSec`.

## 5. BLS collector behavior

- One page per month, bounded retry; on exhaustion the month is **skipped** and
  counted (`failed`), not retried forever.
- Deterministic month loop (`YearMonth` from→to) with page/record/deadline
  termination — no `while(true)`.
- ET→UTC conversion (DST-aware), `America/New_York`.
- Deterministic `external_event_id` (`BLS_<TYPE>_<yyyy-MM-dd>`).
- expected/actual/surprise left **null** (BLS page has no consensus/actual).

## 6. Job lifecycle

`POST /events/collect` → returns `jobId` + `status=CREATED` immediately.
Worker (`event-collection`, daemon thread) runs → `RUNNING` → terminal
`COMPLETED | PARTIAL | FAILED | TIMEOUT`. `GET /events/collection/{id}` exposes
status, timestamps, elapsedMs, recordsCollected/Imported/Rejected,
pagesProcessed, currentSource/currentMonth, per-source summary, message.

## 7. Checkpoint behavior

`research_event_collection_checkpoint` stores `last_completed_month` per
(source, dataset). On restart the loop resumes at `last_completed_month + 1`
month. Verified by `EventCollectionJobServiceTest.resume_skipsAlreadyCompletedMonths`.

## 8. Tests

- `BlsScheduleHistoricalEventSourceTest` — EST→UTC (13:30Z), EDT→UTC (12:30Z),
  month spillover, target-program filtering.
- `EventCollectionJobServiceTest` — start returns immediately / reaches terminal
  state; checkpoint resume skips completed months.
- Full backend suite: **466 PASS · 0 FAIL · 0 ERR · 2 SKIPPED**.
- Build: `test` + `bootJar` BUILD SUCCESSFUL.

## 9. Actual BLS collection result (one bounded run)

| Field | Value |
|---|---|
| endpoint | `POST /api/v1/admin/research/events/collect` |
| jobId | `0268705b-6a5a-4644-af7b-5205e33f70c8` |
| status | **PARTIAL** |
| elapsed | ~17.8 s |
| pages processed | 38 |
| months ok / failed | 37 / 1 |
| records collected | 118 |
| records imported | 113 |
| records rejected | 0 |
| duplicates removed by dedup | 5 (cross-month spillover) |

## 10. Database verification

| Metric | Value |
|---|---|
| `news_events` | **113** |
| `news_event_assets` | **226** (BTC+ETH per event) |
| source / tier | BLS / TIER_1 |
| types | CPI 34 · NFP 46 · PPI 33 |
| date range | 2023-10-01T12:30Z → 2026-09-13T12:30Z |
| expected / actual / surprise populated | 0 / 0 / 0 (all null — not fabricated) |
| checkpoint | bls / 2026-10 / PARTIAL / 118 |

## 11. Remaining blocker

Event coverage is now **non-zero but MACRO-only** (BLS: CPI/PPI/NFP). Still
required for a complete NFM event dataset: FOMC + other central banks (ECB/BoE/
BoJ/BoC/RBA/SNB), PCE/GDP/Retail Sales/Jobless Claims/ISM, and
crypto/regulatory/security events (with provenance), plus the `failed=1` month
(one BLS page) to be re-run. Expected/consensus remains null (paywalled) — no
surprise can be computed. **NFM backtest remains gated** until event coverage and
quality are sufficient.

## Confirmations
No fabricated events · no fabricated expected/actual/surprise · no look-ahead ·
no Spot/notification changes · backend not launched or killed by OpenCode.
