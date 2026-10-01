# NFM Historical Data Infrastructure

Research data plumbing for the News Flow Momentum (NFM) Futures strategy.
**No trading logic, no NFM entry/exit changes, no walk-forward, no profitability claim.**

Frozen window: **2023-09-28 → 2026-09-28 (UTC)**.
Frozen decision architecture: 15m decision, 1m reaction/microstructure, 5m short
reaction, 1h confirmation/regime, 4h context, 24h outcome.

## 1. Tables (additive)

| Table | Cadence | Key | Notes |
|---|---|---|---|
| `market_candle` | 1m/5m/15m/1h/4h/1d | `(symbol,timeframe,open_time)` | **partitioned by RANGE(open_time)** monthly |
| `market_open_interest` | 5m | `(symbol,ts)` | Binance Vision `daily/metrics` |
| `market_funding_rate` | 8h | `(symbol,funding_time)` | `/fapi/v1/fundingRate` or Vision |
| `market_liquidation` | — | `(symbol,ts)` | **optional**; Binance has no USDT-M history |
| `research_dataset_version` | — | `name` | provenance + reproducibility anchor |
| `research_import_reject` | — | id | quarantine |
| `research_import_checkpoint` | — | `source_file` | idempotency + non-overlap |

All columns carry `source_dataset`, `source_file`, `dataset_version`; all times are
**UTC `timestamptz`**; `created_at`/`updated_at` come from the shared audited
`BaseEntity`.

## 2. Migration decision

- Dev/test: Hibernate `ddl-auto` (`update` / `create-drop`) creates the tables.
- **Production: `ddl-auto=validate`; there is no Flyway/Liquibase in the repo.**
  Apply `backend/src/main/resources/db/research/V1__research_market_data.sql`
  out-of-band (psql / CI migration step) **before** deploying. The script owns the
  `market_candle` partitioning; Hibernate never creates or alters partitions.
- Decision: apply the versioned SQL file via the deployment pipeline now; adopt
  Flyway only if/when a migration tool is added to the build. This is documented,
  not left to `ddl-auto`.

## 3. Import

- Offline/bulk only — **no live Binance API required** for the backfill.
- `ResearchDataImportService` uses `JdbcTemplate.batchUpdate` with
  `INSERT ... ON CONFLICT DO NOTHING` (bulk JDBC; no per-row JPA).
- Per-file SHA-256 checksum; a completed checkpoint for the same file+checksum is
  skipped (idempotent). A RUNNING checkpoint blocks overlapping imports.
- Invalid rows are quarantined in `research_import_reject`; valid rows import.
- Per-run summary: files, rows read/inserted/duplicate/rejected, duration.

## 4. Derivatives

`HistoricalDerivativesProvider.asOf(symbol, time)` returns a snapshot built only
from observations with `ts <= time`. Missing OI/funding/liquidation →
`UNKNOWN`/null, never zero. `NfmBacktestStrategy` consumes it (replacing the old
`DerivativesSnapshot.unavailable()`).

## 5. Event ingestion

`NewsEvent` gains `external_event_id`, `source_dataset`, `dataset_version`.
`NewsEventHistoricalEventProvider` uses bounded time-range queries; `eventTime`
remains the authoritative decision timestamp. The engine's no-look-ahead filter
(`event.time <= candle.closeTime`) is unchanged.

## 6. Data quality

`ResearchDataValidationService` reports OHLC integrity, cadence/gaps/duplicates,
OI cadence/coverage, funding interval/boundary consistency, and event
timestamp/tier/asset-mapping issues per dataset window.

## 7. Liquidation

No fabricated or inferred liquidation volume. State stays `UNKNOWN` unless a
third-party historical liquidation feed is licensed and imported into
`market_liquidation` (future optional source).
