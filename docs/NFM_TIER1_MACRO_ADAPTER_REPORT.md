# NFM Tier-1 Macro Adapter Report (Phase 3)

Commit baseline: `ed8d605`. Window `[2023-09-01, 2026-10-01)`.
No fabrication. No strategy change.

## Sources implemented
1. **BLS** (existing) — monthly Schedule-of-Releases archives, EXACT ET→UTC times.
   - Parser fix: skip next-month spillover cells and guard impossible dates
     (`Invalid date 'FEBRUARY 30'` on 2026-01) so a single bad cell can no longer
     fail a whole month.
2. **Federal Reserve FOMC statements** (new `FederalReserveHistoricalEventSource`):
   - Enumerates statement pages from the FOMC calendar (`monetaryYYYYMMDDa.htm`).
   - Reads the exact release time **from the statement page itself**
     ("For release at 2:00 p.m. EST/EDT") → Instant (EST −5h / EDT −4h). No convention.
   - event_type `FOMC`, category `CENTRAL_BANK`, stage `OFFICIAL_CONFIRMATION`, tier 1,
     assets BTC+ETH, `timestamp_quality=EXACT`; expected/actual/surprise null.
   - 26 statements fall in the window (2023-09-20 … 2026-09-16).

## Sources investigated and excluded (no fabrication)
| Source | Finding | Status |
|---|---|---|
| BEA (PCE/GDP) | current `/news/schedule` has times; **historical archive has none** (date-only) | INELIGIBLE |
| Census (Retail Sales) | date-only pages (0 time tokens) | INELIGIBLE |
| DOL/ETA (Jobless Claims) | date-only pages (0 time tokens) | INELIGIBLE |
| Fed FOMC minutes | release date differs from filename meeting date | NOT_IMPLEMENTED (deferred) |
| ISM | private body | BLOCKED |

Using a standard "8:30 AM ET" convention or a midnight timestamp for these was
rejected as fabrication.

## Job lifecycle change
`EventCollectionJobService` now runs **any** `HistoricalEventSource`:
- BLS → month-by-month with per-month checkpoint/resume.
- Others → one bounded `collect(from,to)` call with source-level checkpoint.
- Hard job timeout, bounded retries, per-source status (SUCCESS/PARTIAL/FAILED),
  terminal states only (COMPLETED/PARTIAL/FAILED/TIMEOUT). Never blocks the caller.

## Tests
`FederalReserveHistoricalEventSourceTest` — calendar enumeration within window,
EST→UTC (19:00Z), EDT→UTC (18:00Z), missing time → null (not fabricated).
Full suite: **471 PASS · 0 FAIL · 0 ERR · 2 SKIPPED**. Build: `test` + `bootJar` SUCCESS.

## Execution status
Real collection of the new Fed source (and the BLS 2026-01 retry) requires the
externally-managed backend to be **restarted** (new jar). OpenCode must not
start/restart/kill the backend, so this step is **PENDING USER RESTART**.
After restart: `POST /api/v1/admin/research/events/collect` (BLS resumes at
2025-12 → retries 2026-01; Fed runs its statements).

## Database (current, pre-restart)
`news_events = 113` (BLS only), `news_event_assets = 226`. Fed = 0 (pending).

## Remaining blockers
BEA/Census/DOL exact historical times; FOMC minutes; central banks; crypto/
regulatory/security curated events. Dataset remains **EVENT_DATA_PARTIAL**.
