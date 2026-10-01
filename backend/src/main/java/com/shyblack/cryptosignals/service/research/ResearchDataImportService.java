package com.shyblack.cryptosignals.service.research;

import com.shyblack.cryptosignals.config.ResearchImportProperties;
import com.shyblack.cryptosignals.entity.research.ResearchImportCheckpoint;
import com.shyblack.cryptosignals.entity.research.ResearchImportReject;
import com.shyblack.cryptosignals.repository.research.ResearchImportCheckpointRepository;
import com.shyblack.cryptosignals.repository.research.ResearchImportRejectRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Offline/bulk research-data importer. Reads LOCAL files only (no live Binance
 * API), uses bulk JDBC with {@code ON CONFLICT DO NOTHING} (idempotent),
 * verifies a per-file SHA-256 checksum, quarantines rejects, and guards against
 * overlapping runs via a checkpoint + in-process lock.
 */
@Service
@RequiredArgsConstructor
public class ResearchDataImportService {

	private static final Logger log = LoggerFactory.getLogger(ResearchDataImportService.class);

	private static final String CANDLE_SQL =
			"INSERT INTO market_candle (id,symbol,timeframe,open_time,close_time,open,high,low,close,volume,"
					+ "quote_volume,trades,source_dataset,source_file,dataset_version,created_at,updated_at) "
					+ "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
					+ "ON CONFLICT (symbol,timeframe,open_time) DO NOTHING";
	private static final String OI_SQL =
			"INSERT INTO market_open_interest (id,symbol,ts,sum_open_interest,sum_open_interest_value,"
					+ "taker_long_short_vol_ratio,source_dataset,source_file,dataset_version,created_at,updated_at) "
					+ "VALUES (?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (symbol,ts) DO NOTHING";
	private static final String FUNDING_SQL =
			"INSERT INTO market_funding_rate (id,symbol,funding_time,funding_interval_hours,last_funding_rate,"
					+ "mark_price,source_dataset,source_file,dataset_version,created_at,updated_at) "
					+ "VALUES (?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (symbol,funding_time) DO NOTHING";
	private static final String LIQUIDATION_SQL =
			"INSERT INTO market_liquidation (id,symbol,ts,long_volume,short_volume,source_dataset,source_file,"
					+ "dataset_version,created_at,updated_at) "
					+ "VALUES (?,?,?,?,?,?,?,?,?,?) ON CONFLICT (symbol,ts) DO NOTHING";

	private final JdbcTemplate jdbc;
	private final ResearchImportCheckpointRepository checkpointRepository;
	private final ResearchImportRejectRepository rejectRepository;
	private final ResearchImportProperties properties;

	private final AtomicBoolean running = new AtomicBoolean(false);

	public boolean isRunning() {
		return running.get();
	}

	public ResearchImportSummary importFile(ResearchImportKind kind, String datasetVersion, String sourceDataset,
			String symbol, String timeframe, String relativeOrAbsoluteFile) {
		long started = System.currentTimeMillis();
		Path path = resolve(relativeOrAbsoluteFile);
		String absolute = path.toAbsolutePath().toString();
		String checksum = sha256(path);

		Optional<ResearchImportCheckpoint> existing = checkpointRepository.findBySourceFile(absolute);
		if (existing.isPresent()
				&& "COMPLETED".equals(existing.get().getStatus())
				&& checksum.equals(existing.get().getChecksum())) {
			log.info("[ResearchImport] skip already-imported file={} checksum={}", absolute, checksum);
			return new ResearchImportSummary(absolute, kind, datasetVersion, checksum,
					0, 0, 0, 0, true, "SKIPPED", System.currentTimeMillis() - started);
		}
		if (!running.compareAndSet(false, true)) {
			return new ResearchImportSummary(absolute, kind, datasetVersion, checksum,
					0, 0, 0, 0, true, "BUSY", System.currentTimeMillis() - started);
		}
		ResearchImportCheckpoint checkpoint = existing.orElseGet(ResearchImportCheckpoint::new);
		try {
			checkpoint.setSourceFile(absolute);
			checkpoint.setDatasetVersion(datasetVersion);
			checkpoint.setKind(kind.name());
			checkpoint.setChecksum(checksum);
			checkpoint.setStatus("RUNNING");
			checkpointRepository.save(checkpoint);

			ResearchImportSummary summary = doImport(kind, datasetVersion, sourceDataset, symbol, timeframe,
					path, absolute, checksum, started);
			checkpoint.setStatus("COMPLETED");
			checkpoint.setRowsImported(summary.rowsInserted());
			checkpointRepository.save(checkpoint);
			log.info("[ResearchImport] {} file={} inserted={} duplicate={} rejected={}",
					kind, absolute, summary.rowsInserted(), summary.rowsDuplicate(), summary.rowsRejected());
			return summary;
		} catch (RuntimeException ex) {
			checkpoint.setStatus("FAILED");
			checkpointRepository.save(checkpoint);
			throw ex;
		} finally {
			running.set(false);
		}
	}

	private ResearchImportSummary doImport(ResearchImportKind kind, String datasetVersion, String sourceDataset,
			String symbol, String timeframe, Path path, String absolute, String checksum, long started) {
		List<String> lines;
		try {
			lines = Files.readAllLines(path, StandardCharsets.UTF_8);
		} catch (IOException ex) {
			throw new IllegalStateException("Cannot read import file: " + path, ex);
		}
		String sql = switch (kind) {
			case CANDLE -> CANDLE_SQL;
			case OPEN_INTEREST -> OI_SQL;
			case FUNDING_RATE -> FUNDING_SQL;
			case LIQUIDATION -> LIQUIDATION_SQL;
		};
		int batchSize = properties.batchSize();
		List<Object[]> batch = new ArrayList<>(batchSize);
		List<ResearchImportReject> rejects = new ArrayList<>();
		long rowsRead = 0;
		long rowsValid = 0;
		long inserted = 0;
		long lineNumber = 0;

		for (String raw : lines) {
			lineNumber++;
			if (raw == null || raw.isBlank()) continue;
			List<String> cols = ResearchCsv.split(raw);
			if (lineNumber == 1 && ResearchCsv.isHeader(cols)) continue;
			rowsRead++;
			String reason = validate(kind, cols);
			if (reason != null) {
				rejects.add(reject(datasetVersion, absolute, lineNumber, reason, raw));
				continue;
			}
			Object[] args = toArgs(kind, datasetVersion, sourceDataset, symbol, timeframe, cols, absolute);
			batch.add(args);
			rowsValid++;
			if (batch.size() >= batchSize) {
				inserted += flush(jdbc, sql, batch);
			}
		}
		inserted += flush(jdbc, sql, batch);
		if (!rejects.isEmpty()) {
			rejectRepository.saveAll(rejects);
		}
		long duplicate = rowsValid - inserted;
		return new ResearchImportSummary(absolute, kind, datasetVersion, checksum,
				rowsRead, inserted, duplicate, rejects.size(), false, "COMPLETED",
				System.currentTimeMillis() - started);
	}

	private static long flush(JdbcTemplate jdbc, String sql, List<Object[]> batch) {
		if (batch.isEmpty()) return 0;
		int[] results = jdbc.batchUpdate(sql, batch);
		long inserted = 0;
		for (int r : results) {
			if (r > 0 || r == -2) inserted += Math.max(r, 0); // -2 = success, unknown affected
		}
		batch.clear();
		return inserted;
	}

	private String validate(ResearchImportKind kind, List<String> c) {
		return switch (kind) {
			case CANDLE -> validateCandle(c);
			case OPEN_INTEREST -> validateOi(c);
			case FUNDING_RATE -> validateFunding(c);
			case LIQUIDATION -> validateLiquidation(c);
		};
	}

	private String validateCandle(List<String> c) {
		if (c.size() < 7) return "too few columns (" + c.size() + ")";
		Long openTime = parseLong(c.get(0));
		if (openTime == null) return "bad open_time";
		BigDecimal o = parseDecimal(c.get(1));
		BigDecimal h = parseDecimal(c.get(2));
		BigDecimal l = parseDecimal(c.get(3));
		BigDecimal close = parseDecimal(c.get(4));
		if (o == null || h == null || l == null || close == null) return "bad OHLC";
		if (o.signum() <= 0 || h.signum() <= 0 || l.signum() <= 0 || close.signum() <= 0) return "non-positive price";
		if (h.compareTo(l) < 0) return "high < low";
		if (l.compareTo(o.min(close)) > 0) return "low above body";
		if (h.compareTo(o.max(close)) < 0) return "high below body";
		return null;
	}

	private String validateOi(List<String> c) {
		if (c.size() < 4) return "too few columns (" + c.size() + ")";
		if (parseLong(c.get(0)) == null) return "bad create_time";
		if (parseDecimal(c.get(2)) == null) return "bad sum_open_interest";
		return null;
	}

	private String validateFunding(List<String> c) {
		if (c.size() < 3) return "too few columns (" + c.size() + ")";
		if (parseLong(c.get(0)) == null) return "bad calc_time";
		if (parseDecimal(c.get(2)) == null) return "bad last_funding_rate";
		return null;
	}

	private String validateLiquidation(List<String> c) {
		if (c.size() < 4) return "too few columns (" + c.size() + ")";
		if (parseLong(c.get(0)) == null) return "bad ts";
		return null;
	}

	private Object[] toArgs(ResearchImportKind kind, String datasetVersion, String sourceDataset, String symbol,
			String timeframe, List<String> c, String absolute) {
		Timestamp now = Timestamp.from(Instant.now());
		String sym = symbol == null ? null : symbol.toUpperCase();
		return switch (kind) {
			case CANDLE -> new Object[] {
					UUID.randomUUID(), sym, timeframe,
					ts(parseLong(c.get(0))), ts(parseLong(col(c, 6))),
					parseDecimal(c.get(1)), parseDecimal(c.get(2)), parseDecimal(c.get(3)), parseDecimal(c.get(4)),
					parseDecimal(col(c, 5)), parseDecimal(col(c, 7)), parseLong(col(c, 8)),
					sourceDataset, absolute, datasetVersion, now, now };
			case OPEN_INTEREST -> new Object[] {
					UUID.randomUUID(), sym, ts(parseLong(c.get(0))),
					parseDecimal(c.get(2)), parseDecimal(col(c, 3)), parseDecimal(col(c, 7)),
					sourceDataset, absolute, datasetVersion, now, now };
			case FUNDING_RATE -> new Object[] {
					UUID.randomUUID(), sym, ts(parseLong(c.get(0))),
					parseInt(col(c, 1)), parseDecimal(c.get(2)), null,
					sourceDataset, absolute, datasetVersion, now, now };
			case LIQUIDATION -> new Object[] {
					UUID.randomUUID(), sym, ts(parseLong(c.get(0))),
					parseDecimal(col(c, 2)), parseDecimal(col(c, 3)),
					sourceDataset, absolute, datasetVersion, now, now };
		};
	}

	private ResearchImportReject reject(String datasetVersion, String file, long line, String reason, String raw) {
		ResearchImportReject r = new ResearchImportReject();
		r.setDatasetVersion(datasetVersion);
		r.setSourceFile(file);
		r.setLineNumber(line);
		r.setReason(reason.length() > 300 ? reason.substring(0, 300) : reason);
		r.setRawLine(raw.length() > 2000 ? raw.substring(0, 2000) : raw);
		return r;
	}

	private Path resolve(String file) {
		Path root = Paths.get(properties.root()).toAbsolutePath().normalize();
		Path candidate = Paths.get(file);
		Path resolved = candidate.isAbsolute() ? candidate.normalize() : root.resolve(candidate).normalize();
		if (!resolved.startsWith(root)) {
			throw new IllegalArgumentException("Import path escapes the configured root: " + file);
		}
		if (!Files.isRegularFile(resolved)) {
			throw new IllegalArgumentException("Import file not found: " + resolved);
		}
		return resolved;
	}

	private static String sha256(Path path) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
		} catch (Exception ex) {
			throw new IllegalStateException("Cannot checksum file: " + path, ex);
		}
	}

	private static String col(List<String> c, int index) {
		return index < c.size() ? c.get(index) : null;
	}

	private static Long parseLong(String s) {
		if (s == null || s.isBlank()) return null;
		try {
			return Long.parseLong(s.trim());
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private static Integer parseInt(String s) {
		Long v = parseLong(s);
		return v == null ? null : v.intValue();
	}

	private static BigDecimal parseDecimal(String s) {
		if (s == null || s.isBlank()) return null;
		try {
			return new BigDecimal(s.trim());
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private static Timestamp ts(Long epochMillis) {
		return epochMillis == null ? null : Timestamp.from(Instant.ofEpochMilli(epochMillis));
	}
}
