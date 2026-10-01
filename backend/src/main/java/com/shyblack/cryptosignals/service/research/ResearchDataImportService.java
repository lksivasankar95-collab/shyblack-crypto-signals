package com.shyblack.cryptosignals.service.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.shyblack.cryptosignals.config.ResearchImportProperties;
import com.shyblack.cryptosignals.entity.enums.NewsEventCategory;
import com.shyblack.cryptosignals.entity.enums.NewsEventStage;
import com.shyblack.cryptosignals.entity.enums.NewsEventType;
import com.shyblack.cryptosignals.entity.enums.NewsImpact;
import com.shyblack.cryptosignals.entity.enums.NewsSourceTier;
import com.shyblack.cryptosignals.entity.research.ResearchImportCheckpoint;
import com.shyblack.cryptosignals.repository.research.ResearchImportCheckpointRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
	private static final String REJECT_SQL =
			"INSERT INTO research_import_reject (id,dataset_version,source_file,line_number,reason,raw_line,"
					+ "created_at,updated_at) VALUES (?,?,?,?,?,?,?,?)";
	private static final String EVENT_SQL =
			"INSERT INTO news_events (id,external_event_id,event_time,release_timestamp,event_category,event_type,"
					+ "event_stage,source,source_tier,headline,summary,market_interpretation,expected_value,actual_value,"
					+ "surprise_value,surprise_direction,source_dataset,dataset_version,event_impact,tradeable,"
					+ "created_at,updated_at) "
					+ "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
					+ "ON CONFLICT (external_event_id) DO NOTHING";
	private static final String EVENT_ASSET_SQL =
			"INSERT INTO news_event_assets (id,news_event_id,symbol,relevance_level,relevance_score,created_at,"
					+ "updated_at) VALUES (?,?,?,?,?,?,?)";

	/** Frozen governed-event research window (inclusive start, exclusive end). */
	private static final Instant WINDOW_START = Instant.parse("2023-09-01T00:00:00Z");
	private static final Instant WINDOW_END = Instant.parse("2026-10-01T00:00:00Z");

	/** Binance Vision metrics {@code create_time} is a UTC datetime, not epoch millis. */
	private static final DateTimeFormatter METRICS_TIMESTAMP =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final JdbcTemplate jdbc;
	private final ResearchImportCheckpointRepository checkpointRepository;
	private final ResearchImportProperties properties;
	private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

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
		// A file is only skippable when it truly succeeded: COMPLETED, matching
		// checksum, AND at least one row actually imported. This makes the
		// historical OI bug (COMPLETED with rowsImported=0) auto-recover on rerun
		// without touching successful candle/funding checkpoints.
		if (existing.isPresent()
				&& "COMPLETED".equals(existing.get().getStatus())
				&& checksum.equals(existing.get().getChecksum())
				&& existing.get().getRowsImported() != null
				&& existing.get().getRowsImported() > 0) {
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
			// Only a file with zero rejected rows is COMPLETED (and thus skippable
			// on rerun). Any rejected rows => PARTIAL so the file is retried after
			// a parser/format fix; valid rows already inserted are idempotent.
			checkpoint.setStatus(summary.rowsRejected() == 0 ? "COMPLETED" : "PARTIAL");
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
		if (kind == ResearchImportKind.EVENT) {
			return doImportEvents(lines, datasetVersion, sourceDataset, absolute, checksum, started);
		}
		String sql = switch (kind) {
			case CANDLE -> CANDLE_SQL;
			case OPEN_INTEREST -> OI_SQL;
			case FUNDING_RATE -> FUNDING_SQL;
			case LIQUIDATION -> LIQUIDATION_SQL;
			case EVENT -> throw new IllegalStateException("EVENT handled separately");
		};
		int batchSize = properties.batchSize();
		List<Object[]> batch = new ArrayList<>(batchSize);
		List<Object[]> rejectBatch = new ArrayList<>();
		long rowsRead = 0;
		long rowsValid = 0;
		long inserted = 0;
		long rejected = 0;
		long lineNumber = 0;
		int rejectCap = properties.maxRejectsPerFile();

		for (String raw : lines) {
			lineNumber++;
			if (raw == null || raw.isBlank()) continue;
			List<String> cols = ResearchCsv.split(raw);
			if (lineNumber == 1 && ResearchCsv.isHeader(cols)) continue;
			rowsRead++;
			String reason = validate(kind, cols);
			if (reason != null) {
				rejected++;
				// Quarantine raw samples up to a cap (counts are never discarded).
				if (rejectBatch.size() < rejectCap) {
					rejectBatch.add(rejectRow(datasetVersion, absolute, lineNumber, reason, raw));
				}
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
		if (!rejectBatch.isEmpty()) {
			try {
				jdbc.batchUpdate(REJECT_SQL, rejectBatch);
			} catch (Exception ex) {
				log.warn("[ResearchImport] reject persistence failed file={} err={}", absolute, ex.getMessage());
			}
		}
		long duplicate = rowsValid - inserted;
		return new ResearchImportSummary(absolute, kind, datasetVersion, checksum,
				rowsRead, inserted, duplicate, rejected, false,
				rejected == 0 ? "COMPLETED" : "PARTIAL",
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

	// ── Governed historical events (JSONL) ──────────────────────────────────

	/**
	 * Imports governed historical events from a JSONL file (one event per line),
	 * mapping into the existing {@code news_events} / {@code news_event_assets}
	 * model. Expected/actual/surprise are taken verbatim from the source and left
	 * null when absent — never computed here. Dedup is by {@code external_event_id}
	 * ({@code ON CONFLICT DO NOTHING}).
	 */
	private ResearchImportSummary doImportEvents(List<String> lines, String datasetVersion, String sourceDataset,
			String absolute, String checksum, long started) {
		int rejectCap = properties.maxRejectsPerFile();
		Timestamp now = Timestamp.from(Instant.now());
		List<Object[]> eventBatch = new ArrayList<>();
		List<List<Object[]>> assetsByEvent = new ArrayList<>();
		List<Object[]> rejectBatch = new ArrayList<>();
		long rowsRead = 0, rejected = 0, lineNumber = 0;

		for (String raw : lines) {
			lineNumber++;
			if (raw == null || raw.isBlank()) continue;
			String trimmed = raw.trim();
			if (trimmed.startsWith("#")) continue;
			rowsRead++;
			JsonNode node;
			try {
				node = objectMapper.readTree(trimmed);
			} catch (Exception ex) {
				rejected++;
				if (rejectBatch.size() < rejectCap) {
					rejectBatch.add(rejectRow(datasetVersion, absolute, lineNumber, "malformed json", raw));
				}
				continue;
			}
			String reason = validateEvent(node);
			if (reason != null) {
				rejected++;
				if (rejectBatch.size() < rejectCap) {
					rejectBatch.add(rejectRow(datasetVersion, absolute, lineNumber, reason, raw));
				}
				continue;
			}
			UUID eventId = UUID.randomUUID();
			eventBatch.add(eventArgs(eventId, node, datasetVersion, sourceDataset, now));
			assetsByEvent.add(assetArgs(eventId, node, now));
		}

		long inserted = 0;
		if (!eventBatch.isEmpty()) {
			int[] results = jdbc.batchUpdate(EVENT_SQL, eventBatch);
			for (int i = 0; i < results.length; i++) {
				if (results[i] > 0) {
					inserted++;
					List<Object[]> assets = assetsByEvent.get(i);
					if (!assets.isEmpty()) {
						jdbc.batchUpdate(EVENT_ASSET_SQL, assets);
					}
				}
			}
		}
		if (!rejectBatch.isEmpty()) {
			try {
				jdbc.batchUpdate(REJECT_SQL, rejectBatch);
			} catch (Exception ex) {
				log.warn("[ResearchImport] event reject persistence failed file={} err={}", absolute, ex.getMessage());
			}
		}
		long duplicate = (long) eventBatch.size() - inserted;
		return new ResearchImportSummary(absolute, ResearchImportKind.EVENT, datasetVersion, checksum,
				rowsRead, inserted, duplicate, rejected, false,
				rejected == 0 ? "COMPLETED" : "PARTIAL", System.currentTimeMillis() - started);
	}

	String validateEvent(JsonNode n) {
		if (text(n, "external_event_id") == null) return "missing external_event_id";
		Instant t = parseInstant(text(n, "event_time"));
		if (t == null) return "bad event_time";
		if (t.isBefore(WINDOW_START) || !t.isBefore(WINDOW_END)) return "event_time outside research window";
		if (!isEnum(NewsEventCategory.class, text(n, "category"))) return "invalid category";
		if (!isEnum(NewsEventType.class, text(n, "event_type"))) return "invalid event_type";
		String stage = text(n, "event_stage");
		if (stage != null && !isEnum(NewsEventStage.class, stage)) return "invalid event_stage";
		if (text(n, "source") == null) return "missing source";
		if (!isEnum(NewsSourceTier.class, text(n, "source_tier"))) return "invalid source_tier";
		String conf = text(n, "confirmation_timestamp");
		if (conf != null && parseInstant(conf) == null) return "bad confirmation_timestamp";
		for (String field : new String[] {"expected", "actual", "surprise"}) {
			if (!numericOrNull(n, field)) return "bad " + field;
		}
		String assets = text(n, "assets");
		if (assets == null || assets.isBlank()) return "missing assets";
		for (String part : assets.split(";")) {
			String[] kv = part.split(":");
			if (kv.length != 2 || kv[0].isBlank() || !isEnum(NewsImpact.class, kv[1].trim())) {
				return "invalid asset mapping";
			}
		}
		return null;
	}

	private Object[] eventArgs(UUID eventId, JsonNode n, String datasetVersion, String sourceDataset, Timestamp now) {
		String stage = text(n, "event_stage");
		NewsImpact impact = maxAssetRelevance(n);
		String tier = text(n, "source_tier").toUpperCase();
		boolean tradeable = !"TIER_4".equals(tier) && impact.ordinal() >= NewsImpact.MEDIUM.ordinal();
		return new Object[] {
				eventId,
				text(n, "external_event_id"),
				ts(parseInstant(text(n, "event_time"))),
				ts(parseInstant(text(n, "confirmation_timestamp"))),
				text(n, "category").toUpperCase(),
				text(n, "event_type").toUpperCase(),
				stage == null ? NewsEventStage.REPORT.name() : stage.toUpperCase(),
				text(n, "source"),
				tier,
				text(n, "headline"),
				text(n, "summary"),
				text(n, "market_interpretation"),
				decOrNull(n, "expected"),
				decOrNull(n, "actual"),
				decOrNull(n, "surprise"),
				text(n, "surprise_direction"),
				sourceDataset == null ? "governed-events" : sourceDataset,
				datasetVersion,
				impact.name(),
				tradeable,
				now, now };
	}

	private List<Object[]> assetArgs(UUID eventId, JsonNode n, Timestamp now) {
		List<Object[]> out = new ArrayList<>();
		for (String part : text(n, "assets").split(";")) {
			String[] kv = part.split(":");
			out.add(new Object[] {
					UUID.randomUUID(), eventId, kv[0].trim().toUpperCase(),
					kv[1].trim().toUpperCase(), null, now, now });
		}
		return out;
	}

	private static NewsImpact maxAssetRelevance(JsonNode n) {
		NewsImpact max = NewsImpact.LOW;
		String assets = text(n, "assets");
		if (assets == null) return max;
		for (String part : assets.split(";")) {
			String[] kv = part.split(":");
			if (kv.length == 2) {
				try {
					NewsImpact impact = NewsImpact.valueOf(kv[1].trim().toUpperCase());
					if (impact.ordinal() > max.ordinal()) max = impact;
				} catch (IllegalArgumentException ignored) {
					// validation rejects; ignore here
				}
			}
		}
		return max;
	}

	private static boolean numericOrNull(JsonNode n, String field) {
		JsonNode v = n.get(field);
		if (v == null || v.isNull()) return true;
		if (v.isNumber()) return true;
		try {
			new BigDecimal(v.asText());
			return true;
		} catch (NumberFormatException ex) {
			return false;
		}
	}

	private static BigDecimal decOrNull(JsonNode n, String field) {
		JsonNode v = n.get(field);
		if (v == null || v.isNull()) return null;
		try {
			return new BigDecimal(v.asText());
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private static String text(JsonNode n, String field) {
		JsonNode v = n.get(field);
		if (v == null || v.isNull()) return null;
		String s = v.asText();
		return s == null || s.isBlank() ? null : s;
	}

	private static boolean isEnum(Class<? extends Enum<?>> type, String value) {
		if (value == null) return false;
		for (Enum<?> constant : type.getEnumConstants()) {
			if (constant.name().equalsIgnoreCase(value.trim())) return true;
		}
		return false;
	}

	private String validate(ResearchImportKind kind, List<String> c) {
		return switch (kind) {
			case CANDLE -> validateCandle(c);
			case OPEN_INTEREST -> validateOi(c);
			case FUNDING_RATE -> validateFunding(c);
			case LIQUIDATION -> validateLiquidation(c);
			case EVENT -> throw new IllegalStateException("EVENT handled separately");
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
		// Metrics create_time may be epoch millis OR 'yyyy-MM-dd HH:mm:ss' (UTC).
		if (parseInstant(c.get(0)) == null) return "bad create_time";
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
					UUID.randomUUID(), sym, ts(parseInstant(c.get(0))),
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
			case EVENT -> throw new IllegalStateException("EVENT handled separately");
		};
	}

	private Object[] rejectRow(String datasetVersion, String file, long line, String reason, String raw) {
		Timestamp now = Timestamp.from(Instant.now());
		return new Object[] {
				UUID.randomUUID(), datasetVersion, file, line,
				trunc(reason, 300), trunc(raw, 2000), now, now };
	}

	private static String trunc(String s, int max) {
		if (s == null) return null;
		return s.length() <= max ? s : s.substring(0, max);
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

	private static Timestamp ts(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}

	/**
	 * Accepts either epoch milliseconds (candles/funding) or the Binance Vision
	 * metrics datetime {@code yyyy-MM-dd HH:mm:ss}, interpreted as UTC. Returns
	 * null for anything unparseable so the row is rejected, not guessed.
	 */
	static Instant parseInstant(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String value = raw.trim();
		Long millis = parseLong(value);
		if (millis != null) {
			return Instant.ofEpochMilli(millis);
		}
		try {
			return LocalDateTime.parse(value, METRICS_TIMESTAMP).toInstant(ZoneOffset.UTC);
		} catch (DateTimeParseException ex) {
			// fall through to ISO-8601 (e.g. governed event timestamp)
		}
		try {
			return Instant.parse(value);
		} catch (DateTimeParseException ex) {
			return null;
		}
	}
}
