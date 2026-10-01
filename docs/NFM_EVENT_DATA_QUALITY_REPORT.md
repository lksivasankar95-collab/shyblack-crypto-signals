# NFM Event Data Quality Report

Dataset: `NFM_EVENTS_2023_09_2026_09_V1` · Source of truth: live database (read-only).

## Totals
| Metric | Value |
|---|---|
| total events | 113 |
| valid events | 113 |
| invalid / rejected | 0 |
| duplicates (external_event_id) | 0 (unique constraint) |
| event assets | 226 (BTC+ETH per event) |

## Timestamp quality
| Quality | Events |
|---|---|
| EXACT | 113 |
| APPROXIMATE | 0 |
| UNKNOWN | 0 |

## Quality checks (import-time enforced)
- event_time present, within `[2023-09-01, 2026-10-01)` — enforced by importer (`outside research window` → reject).
- category / event_type / event_stage validated against enums — enforced.
- source + source_tier present and valid — enforced.
- asset mapping present and valid — enforced.
- deterministic `external_event_id` + `ON CONFLICT DO NOTHING` — dedup.
- malformed rows quarantined in `research_import_reject` (0 this dataset).

## Distribution
- By type: CPI 34 · NFP 46 · PPI 33.
- By year: 2023=10 · 2024=44 · 2025=35 · 2026=24.
- By category: MACRO 113 (others 0).
- By tier: TIER_1 113.
- By asset: BTCUSDT 113 · ETHUSDT 113.

## Assessment
Quality of the present records is high (100% exact timestamps, 0 duplicates, 0
malformed). **Coverage**, not quality, is the gap: only MACRO (BLS CPI/PPI/NFP).
Fed statements are implemented but not yet collected (pending backend restart).
