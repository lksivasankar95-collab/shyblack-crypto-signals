-- Research market-data schema (NFM historical dataset).
-- Production applies this file out-of-band (see docs/NFM_HISTORICAL_DATA.md).
-- Dev/test use Hibernate ddl-auto; production uses ddl-auto=validate, so these
-- tables MUST exist before the app validates. market_candle is PARTITIONED by
-- range on open_time (monthly) because 3 years of 1m BTC/ETH is ~3.2M rows.

CREATE TABLE IF NOT EXISTS market_candle (
    id               uuid        NOT NULL,
    symbol           varchar(20) NOT NULL,
    timeframe        varchar(10) NOT NULL,
    open_time        timestamptz NOT NULL,
    close_time       timestamptz,
    open             numeric(24,10) NOT NULL,
    high             numeric(24,10) NOT NULL,
    low              numeric(24,10) NOT NULL,
    close            numeric(24,10) NOT NULL,
    volume           numeric(30,10),
    quote_volume     numeric(30,10),
    trades           bigint,
    source_dataset   varchar(100),
    source_file      varchar(300),
    dataset_version  varchar(100),
    created_at       timestamptz NOT NULL,
    updated_at       timestamptz NOT NULL,
    CONSTRAINT pk_market_candle PRIMARY KEY (id, open_time),
    CONSTRAINT uk_market_candle_symbol_tf_time UNIQUE (symbol, timeframe, open_time)
) PARTITION BY RANGE (open_time);

CREATE INDEX IF NOT EXISTS ix_market_candle_symbol_tf_time ON market_candle (symbol, timeframe, open_time);
CREATE INDEX IF NOT EXISTS ix_market_candle_dataset ON market_candle (dataset_version);

-- Monthly partitions for the frozen research window (2023-09 .. 2026-10).
DO $$
DECLARE
    start_month date := DATE '2023-09-01';
    end_month   date := DATE '2026-10-01';
    cur         date := start_month;
    part_name   text;
BEGIN
    WHILE cur < end_month LOOP
        part_name := 'market_candle_' || to_char(cur, 'YYYY_MM');
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF market_candle FOR VALUES FROM (%L) TO (%L)',
            part_name, cur, cur + interval '1 month');
        cur := cur + interval '1 month';
    END LOOP;
END $$;

CREATE TABLE IF NOT EXISTS market_open_interest (
    id                         uuid        NOT NULL PRIMARY KEY,
    symbol                     varchar(20) NOT NULL,
    ts                         timestamptz NOT NULL,
    sum_open_interest          numeric(30,10),
    sum_open_interest_value    numeric(30,10),
    taker_long_short_vol_ratio numeric(18,8),
    source_dataset             varchar(100),
    source_file                varchar(300),
    dataset_version            varchar(100),
    created_at                 timestamptz NOT NULL,
    updated_at                 timestamptz NOT NULL,
    CONSTRAINT uk_market_oi_symbol_ts UNIQUE (symbol, ts)
);
CREATE INDEX IF NOT EXISTS ix_market_oi_symbol_ts ON market_open_interest (symbol, ts);

CREATE TABLE IF NOT EXISTS market_funding_rate (
    id                     uuid        NOT NULL PRIMARY KEY,
    symbol                 varchar(20) NOT NULL,
    funding_time           timestamptz NOT NULL,
    funding_interval_hours integer,
    last_funding_rate      numeric(18,10),
    mark_price             numeric(24,10),
    source_dataset         varchar(100),
    source_file            varchar(300),
    dataset_version        varchar(100),
    created_at             timestamptz NOT NULL,
    updated_at             timestamptz NOT NULL,
    CONSTRAINT uk_market_funding_symbol_time UNIQUE (symbol, funding_time)
);
CREATE INDEX IF NOT EXISTS ix_market_funding_symbol_time ON market_funding_rate (symbol, funding_time);

CREATE TABLE IF NOT EXISTS market_liquidation (
    id              uuid        NOT NULL PRIMARY KEY,
    symbol          varchar(20) NOT NULL,
    ts              timestamptz NOT NULL,
    long_volume     numeric(30,10),
    short_volume    numeric(30,10),
    source_dataset  varchar(100),
    source_file     varchar(300),
    dataset_version varchar(100),
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT uk_market_liquidation_symbol_ts UNIQUE (symbol, ts)
);
CREATE INDEX IF NOT EXISTS ix_market_liquidation_symbol_ts ON market_liquidation (symbol, ts);

CREATE TABLE IF NOT EXISTS research_dataset_version (
    id           uuid NOT NULL PRIMARY KEY,
    name         varchar(150) NOT NULL,
    window_start timestamptz,
    window_end   timestamptz,
    sources      varchar(1000),
    status       varchar(30),
    checksum     varchar(128),
    notes        varchar(2000),
    created_at   timestamptz NOT NULL,
    updated_at   timestamptz NOT NULL,
    CONSTRAINT uk_research_dataset_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS research_import_reject (
    id              uuid NOT NULL PRIMARY KEY,
    dataset_version varchar(100),
    source_file     varchar(300),
    line_number     bigint,
    reason          varchar(300),
    raw_line        varchar(2000),
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL
);

CREATE TABLE IF NOT EXISTS research_import_checkpoint (
    id              uuid NOT NULL PRIMARY KEY,
    dataset_version varchar(100),
    source_file     varchar(300) NOT NULL,
    kind            varchar(30),
    checksum        varchar(128),
    status          varchar(30),
    rows_imported   bigint,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT uk_research_checkpoint_file UNIQUE (source_file)
);
