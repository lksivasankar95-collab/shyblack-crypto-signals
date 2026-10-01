# NFM Event Coverage Report

Window: **2023-09-01 → 2026-09-30** · Dataset: `NFM_EVENTS_2023_09_2026_09_V1`
Source of truth: live DB (read-only) + source capability probes.

## 1. Macro
| Source | Events | EXACT | APPROX | UNKNOWN | Assets | Rejected | Dups | Ineligible | Coverage | Tier |
|---|---|---|---|---|---|---|---|---|---|---|
| BLS Schedule of Releases | 113 | 113 | 0 | 0 | BTC+ETH | 0 | 0 | — | 2023-10 → 2026-09 | 1 |
| Federal Reserve FOMC statements | 0 (implemented, not executed) | — | — | — | BTC+ETH | — | — | — | 2023-09 → 2026-09 | 1 |
| BEA PCE / GDP | 0 | — | — | — | — | — | — | date-only (no exact time) | — | 1 |
| Census Retail Sales | 0 | — | — | — | — | — | — | date-only | — | 1 |
| DOL/ETA Jobless Claims | 0 | — | — | — | — | — | — | date-only | — | 1 |

## 2. Central Bank
| Source | Events | Exact | Ineligible | Notes |
|---|---|---|---|---|
| Fed FOMC (statements) | 0 (built; pending runtime) | — | — | page states "2:00 p.m. EST/EDT" → EXACT-capable |
| ECB / BoE / BoJ / BoC / RBA / SNB | 0 | 0 | **INELIGIBLE** | official pages expose no machine-readable exact time (0 time tokens; ECB/SNB unreachable) |

## 3. Crypto Structural
0 events. No authoritative keyless machine feed; requires curated records with provenance (or SEC EDGAR ingestion, not built).

## 4. Regulatory
0 events. SEC/CFTC/Treasury RSS are current-only; no keyless historical structured archive wired.

## 5. Security
0 events. Requires curated official incident reports with exact timestamps.

## 6. Market Shock
0 events. Same as security; liquidation history not offered by Binance.

## Totals
| Metric | Value |
|---|---|
| TOTAL REAL EVENTS | **113** |
| TOTAL ASSET MAPPINGS | **226** (BTC 113, ETH 113) |
| TOTAL EXACT | **113** |
| TOTAL APPROXIMATE | 0 |
| TOTAL UNKNOWN | 0 |
| TOTAL REJECTED | 0 |
| TOTAL DUPLICATES | 0 (unique `external_event_id`) |
| OUT-OF-WINDOW | 0 |
| NULL required fields | 0 |
| ORPHAN asset rows | 0 |

## By category
MACRO 113 · CENTRAL_BANK 0 · CRYPTO_STRUCTURAL 0 · REGULATION 0 · SECURITY 0 · MARKET_SHOCK 0.

## Quality gate
**EVENT_DATA_PARTIAL** — high-quality records (100% EXACT, integrity clean) but coverage is
macro-only and effectively single-active-source. Not READY.

## Remaining event-source work
1. Restart backend from current jar → run BLS retry (2026-01) + Fed statements.
2. Add exact-time sources only where genuinely available (BEA/Census/DOL/central banks are date-only → ineligible).
3. Curate crypto/regulatory/security events with provenance (or build SEC EDGAR ingestion).
