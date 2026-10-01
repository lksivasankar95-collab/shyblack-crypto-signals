# NFM Event Source Capability Registry

Dataset: `NFM_EVENTS_2023_09_2026_09_V1` · Window `[2023-09-01, 2026-10-01)`.
Status values: IMPLEMENTED · NOT_IMPLEMENTED · BLOCKED.

| SOURCE | STATUS | DATE COVERAGE | TIMESTAMP QUALITY | EVENT COUNT | BLOCKER |
|---|---|---|---|---|---|
| U.S. BLS (Schedule of Releases, `/schedule/YYYY/MM_sched.htm`) | **IMPLEMENTED** | 2023-09 → 2026-09 (37/38 months; 2026-01 parser bug fixed, re-run pending restart) | EXACT (ET→UTC, DST-aware) | 113 imported (CPI 34, NFP 46, PPI 33) | none (code fix applied; needs backend restart to re-run 2026-01) |
| Federal Reserve (FOMC calendar/statements) | NOT_IMPLEMENTED | calendar page reachable (2023–2026) | UNKNOWN — page lists meeting **dates**, not release times | 0 | exact publication time not machine-verifiable from the page (2:00pm ET is a convention → would be UNKNOWN/ineligible) |
| BEA (GDP, PCE) | NOT_IMPLEMENTED | schedule pages reachable (`/news/schedule`) | unknown (time not confirmed) | 0 | per-release ETL not built this pass |
| U.S. Census (Retail Sales) | NOT_IMPLEMENTED | economic-indicators page reachable | unknown | 0 | per-release ETL not built this pass |
| DOL/ETA (Initial Jobless Claims) | NOT_IMPLEMENTED | not probed | unknown | 0 | adapter not built |
| ISM (PMI) | BLOCKED | n/a | n/a | 0 | ISM is a private body; no free auditable historical release-timestamp source |
| ECB / BoE / BoJ / BoC / RBA / SNB | NOT_IMPLEMENTED | official press RSS reachable but **current-only**; historical calendars are HTML per-source | unknown | 0 | need per-bank historical calendar parsing (not built this pass) |
| SEC / CFTC / Treasury (regulatory) | NOT_IMPLEMENTED | RSS reachable, current-only | EXACT for live only | 0 | historical structured archives not wired |
| Crypto structural (ETF/upgrades/listings/unlocks/stablecoin) | NOT_IMPLEMENTED | n/a | mixed | 0 | requires curated, sourced records (no single authoritative machine feed) |
| Security / exploits / depegs / outages | NOT_IMPLEMENTED | n/a | mixed | 0 | requires curated official incident reports; rumors must not become events |

## Notes
- One source being blocked does not stop others (the collector runs each adapter independently and records per-source status).
- Tier-4 (social/rumor) is never promoted to a confirmed event.
- `expected`/`actual`/`surprise` are populated only from auditable sources; currently all NULL (BLS schedule pages carry release time, not consensus/actuals).
