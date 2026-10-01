package com.shyblack.cryptosignals.service.research;

import com.shyblack.cryptosignals.dto.research.DatasetQualityReport;
import com.shyblack.cryptosignals.dto.research.DatasetQualityReport.Check;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Read-only data-quality checks over an imported dataset version (spec Phase E).
 * Never mutates data; a check failure is reported, not auto-corrected.
 */
@Service
@RequiredArgsConstructor
public class ResearchDataValidationService {

	private final JdbcTemplate jdbc;

	public DatasetQualityReport validate(String datasetVersion) {
		List<Check> checks = new ArrayList<>();
		checks.add(candleIntegrity(datasetVersion));
		checks.add(candleDuplicates(datasetVersion));
		checks.add(candleCadence(datasetVersion));
		checks.add(oiCoverage(datasetVersion));
		checks.add(fundingConsistency(datasetVersion));
		checks.add(liquidationCoverage(datasetVersion));
		checks.add(eventQuality(datasetVersion));
		return new DatasetQualityReport(datasetVersion, checks, Instant.now());
	}

	private Check candleIntegrity(String dv) {
		Long total = scalarLong("SELECT count(*) FROM market_candle WHERE dataset_version=?", dv);
		Long invalid = scalarLong("SELECT count(*) FROM market_candle WHERE dataset_version=? AND ("
				+ "low > open OR low > close OR high < open OR high < close OR high < low "
				+ "OR open<=0 OR high<=0 OR low<=0 OR close<=0)", dv);
		if (total == null || total == 0) return new Check("candle.integrity", "WARN", "no candles imported");
		if (invalid != null && invalid > 0) return new Check("candle.integrity", "FAIL", invalid + " invalid OHLC rows");
		return new Check("candle.integrity", "PASS", total + " candles valid");
	}

	private Check candleDuplicates(String dv) {
		Long total = scalarLong("SELECT count(*) FROM market_candle WHERE dataset_version=?", dv);
		Long distinct = scalarLong("SELECT count(*) FROM (SELECT DISTINCT symbol,timeframe,open_time "
				+ "FROM market_candle WHERE dataset_version=?) t", dv);
		if (total == null || distinct == null) return new Check("candle.duplicates", "WARN", "not evaluated");
		if (total > distinct) return new Check("candle.duplicates", "FAIL", (total - distinct) + " duplicate keys");
		return new Check("candle.duplicates", "PASS", "no duplicate keys");
	}

	private Check candleCadence(String dv) {
		Long maxGap = scalarLong("SELECT COALESCE(max(gap_s),0) FROM (SELECT "
				+ "EXTRACT(EPOCH FROM (open_time - lag(open_time) OVER (PARTITION BY symbol,timeframe ORDER BY open_time))) gap_s "
				+ "FROM market_candle WHERE dataset_version=? AND timeframe='15m') g", dv);
		if (maxGap == null) return new Check("candle.cadence.15m", "WARN", "not evaluated");
		if (maxGap > 2700) return new Check("candle.cadence.15m", "WARN", "max 15m gap " + maxGap + "s (> 3x cadence)");
		return new Check("candle.cadence.15m", "PASS", "max 15m gap " + maxGap + "s");
	}

	private Check oiCoverage(String dv) {
		Long c = scalarLong("SELECT count(*) FROM market_open_interest WHERE dataset_version=?", dv);
		if (c == null || c == 0) {
			return new Check("open_interest.coverage", "WARN",
					"no OI rows — expected for pre-2020-09 (BTC) / pre-2021-12 (ETH); never treated as zero");
		}
		return new Check("open_interest.coverage", "PASS", c + " OI observations");
	}

	private Check fundingConsistency(String dv) {
		Long c = scalarLong("SELECT count(*) FROM market_funding_rate WHERE dataset_version=?", dv);
		Long intervals = scalarLong("SELECT count(DISTINCT funding_interval_hours) FROM market_funding_rate "
				+ "WHERE dataset_version=?", dv);
		if (c == null || c == 0) return new Check("funding.consistency", "WARN", "no funding rows");
		String detail = c + " rows, distinct interval-hours=" + (intervals == null ? "?" : intervals);
		if (intervals != null && intervals > 1) {
			return new Check("funding.consistency", "WARN", detail + " (funding interval changed over time)");
		}
		return new Check("funding.consistency", "PASS", detail);
	}

	private Check liquidationCoverage(String dv) {
		Long c = scalarLong("SELECT count(*) FROM market_liquidation WHERE dataset_version=?", dv);
		if (c == null || c == 0) {
			return new Check("liquidation.coverage", "WARN",
					"no historical liquidation data (Binance publishes none for USDT-M); stays UNKNOWN");
		}
		return new Check("liquidation.coverage", "PASS", c + " liquidation aggregates");
	}

	private Check eventQuality(String dv) {
		Long c = scalarLong("SELECT count(*) FROM news_events WHERE dataset_version=?", dv);
		if (c == null || c == 0) return new Check("events.quality", "WARN", "no events for dataset version");
		Long missingTier = scalarLong("SELECT count(*) FROM news_events WHERE dataset_version=? AND source_tier IS NULL", dv);
		Long missingAssets = scalarLong("SELECT count(*) FROM news_events e WHERE e.dataset_version=? AND NOT EXISTS "
				+ "(SELECT 1 FROM news_event_assets a WHERE a.news_event_id=e.id)", dv);
		Long dupExternal = scalarLong("SELECT COALESCE(SUM(cnt-1),0) FROM (SELECT external_event_id, COUNT(*) cnt "
				+ "FROM news_events WHERE dataset_version=? AND external_event_id IS NOT NULL "
				+ "GROUP BY external_event_id HAVING COUNT(*)>1) d", dv);
		Long outOfWindow = scalarLong("SELECT count(*) FROM news_events WHERE dataset_version=? AND "
				+ "(event_time IS NULL OR event_time < TIMESTAMPTZ '2023-09-01T00:00:00Z' "
				+ "OR event_time >= TIMESTAMPTZ '2026-10-01T00:00:00Z')", dv);
		Long expected = scalarLong("SELECT count(*) FROM news_events WHERE dataset_version=? AND expected_value IS NOT NULL", dv);
		Long actual = scalarLong("SELECT count(*) FROM news_events WHERE dataset_version=? AND actual_value IS NOT NULL", dv);
		Long surprise = scalarLong("SELECT count(*) FROM news_events WHERE dataset_version=? AND surprise_value IS NOT NULL", dv);
		String detail = "events=" + c + " expected=" + nz(expected) + " actual=" + nz(actual)
				+ " surprise=" + nz(surprise) + " missingTier=" + nz(missingTier)
				+ " missingAssets=" + nz(missingAssets) + " dupExternal=" + nz(dupExternal)
				+ " outOfWindow=" + nz(outOfWindow);
		boolean fail = nz(missingTier) > 0 || nz(missingAssets) > 0
				|| nz(dupExternal) > 0 || nz(outOfWindow) > 0;
		return new Check("events.quality", fail ? "FAIL" : "PASS", detail);
	}

	private static long nz(Long value) {
		return value == null ? 0 : value;
	}

	private Long scalarLong(String sql, Object... args) {
		try {
			return jdbc.queryForObject(sql, Long.class, args);
		} catch (Exception ex) {
			return null;
		}
	}
}
