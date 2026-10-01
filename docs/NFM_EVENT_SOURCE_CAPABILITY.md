# NFM Event Source Capability Registry

Dataset: `NFM_EVENTS_2023_09_2026_09_V1` · Window `[2023-09-01, 2026-10-01)`.
Status: IMPLEMENTED · WORKING · PARTIAL · BLOCKED · INELIGIBLE · NOT_IMPLEMENTED.

| SOURCE | STATUS | ACCESS | COVERAGE | TIMESTAMP | TIER | EVENTS | BLOCKER |
|---|---|---|---|---|---|---|---|
| BLS Schedule of Releases (`/schedule/YYYY/MM_sched.htm`) | IMPLEMENTED (WORKING) | public HTML, UA only | 2023-09→2026-09 | **EXACT** (ET→UTC, DST-aware) | 1 | 113 | 2026-01 parser bug fixed; re-run pending backend restart |
| Federal Reserve FOMC statements | IMPLEMENTED | public HTML, UA only | 2023-09→2026-09 (26 statements) | **EXACT** — page states "For release at 2:00 p.m. EST/EDT" | 1 | 0 imported (pending restart) | code built; needs backend restart to run |
| Federal Reserve FOMC minutes | NOT_IMPLEMENTED | public HTML | — | release date ≠ filename meeting date (needs per-page date) | 1 | 0 | deferred |
| BEA — PCE / GDP | INELIGIBLE | public HTML | current schedule only | **date-only** for historical window (0 time tokens) | 1 | 0 | exact historical release time not available keyless; a midnight timestamp is forbidden |
| Census — Retail Sales | INELIGIBLE | public HTML | date-only | **date-only** (0 time tokens) | 1 | 0 | as above |
| DOL/ETA — Initial Jobless Claims | INELIGIBLE | public HTML/PDF | date-only | **date-only** (0 time tokens) | 1 | 0 | as above |
| ISM | BLOCKED | — | — | — | — | 0 | private body; no free auditable historical timestamp source |
| ECB/BoE/BoJ/BoC/RBA/SNB | NOT_IMPLEMENTED | RSS current-only; per-bank historical calendars | — | unknown | 1 | 0 | per-bank historical parsing not built |
| SEC/CFTC/Treasury (regulatory) | NOT_IMPLEMENTED | RSS current-only | — | EXACT live only | 1 | 0 | historical structured archives not wired |
| Crypto structural | NOT_IMPLEMENTED | — | — | mixed | — | 0 | curated records required |
| Security/exploits/depegs | NOT_IMPLEMENTED | — | — | mixed | — | 0 | curated official incident reports required |

## Policy
- Exact event timestamp is mandatory for backtest eligibility; **date-only sources are INELIGIBLE** (not converted to midnight).
- `expected`/`actual`/`surprise` are populated only from auditable sources; currently all NULL.
- One blocked source never stops others; each adapter runs independently and records its own status.
