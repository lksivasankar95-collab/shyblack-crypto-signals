# NFM Event Coverage Report

Dataset: `NFM_EVENTS_2023_09_2026_09_V1` · Source: U.S. BLS (Tier 1) · Window `[2023-09-01, 2026-10-01)`.
Generated from the live database (read-only).

## Total
- `news_events` = **113**
- `news_event_assets` = **226** (2 per event: BTCUSDT + ETHUSDT)

## By year
| Year | Events |
|---|---|
| 2023 | 10 |
| 2024 | 44 |
| 2025 | 35 |
| 2026 | 24 |

## By category
| Category | Events |
|---|---|
| MACRO | 113 |
| CENTRAL_BANK | 0 |
| CRYPTO_STRUCTURAL | 0 |
| REGULATION | 0 |
| SECURITY | 0 |
| MARKET_SHOCK | 0 |

## By source tier
| Tier | Events |
|---|---|
| TIER_1 | 113 |
| TIER_2 | 0 |
| TIER_3 | 0 |
| TIER_4 | 0 |

## By asset
| Asset | Events |
|---|---|
| BTCUSDT | 113 |
| ETHUSDT | 113 |

## By event type
| Type | Events |
|---|---|
| CPI | 34 |
| NFP | 46 |
| PPI | 33 |

## Timestamp quality
| Quality | Events |
|---|---|
| EXACT | 113 |
| APPROXIMATE | 0 |
| UNKNOWN | 0 |

## Coverage assessment
- Temporal: 2023-10-01 → 2026-09-13 (a small gap at the very start of the window).
- Source diversity: **1** source (BLS).
- Macro: partial (CPI/PPI/NFP only; no PCE/GDP/Retail/Jobless Claims/ISM).
- Central bank: none. Crypto/regulatory/security: none.
- expected/actual/surprise: 0/0/0 (not fabricated).

## Dataset gate
**EVENT_DATA_PARTIAL** — real, exact-timestamp macro events exist, but coverage is
macro-only and single-source. Not sufficient to represent full historical event
coverage for an NFM baseline; a clearly scoped macro-only backtest is possible but
must not be presented as complete historical coverage.

## Next actions (no fabrication)
1. Restart the externally-managed backend, then re-run the BLS collection (checkpoint
   reset to `2025-12` so the fixed parser retries `2026-01`) → expect BLS COMPLETE.
2. Add adapters for BEA (GDP/PCE), Census (Retail Sales), DOL/ETA (Jobless Claims)
   where exact release times are verifiable (Tier-1).
3. Add Fed FOMC + other central banks only where exact publication time is auditable
   (otherwise mark UNKNOWN and exclude from event-time-sensitive backtests).
4. Curate crypto/regulatory/security events with explicit provenance.
