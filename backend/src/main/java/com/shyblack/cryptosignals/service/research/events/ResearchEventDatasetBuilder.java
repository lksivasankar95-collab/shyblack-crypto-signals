package com.shyblack.cryptosignals.service.research.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shyblack.cryptosignals.config.ResearchImportProperties;
import com.shyblack.cryptosignals.service.research.ResearchDataImportService;
import com.shyblack.cryptosignals.service.research.ResearchImportKind;
import com.shyblack.cryptosignals.service.research.ResearchImportSummary;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs all registered {@link HistoricalEventSource}s over the research window,
 * de-duplicates by {@code external_event_id}, writes a governed EVENT JSONL +
 * source manifest under the research import root, then imports it through the
 * existing {@link ResearchDataImportService} (no parallel import framework).
 *
 * <p>If no source yields events the JSONL is empty and the importer records it;
 * nothing is fabricated.</p>
 */
@Service
public class ResearchEventDatasetBuilder {

	private static final Logger log = LoggerFactory.getLogger(ResearchEventDatasetBuilder.class);
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
			.withZone(ZoneOffset.UTC);

	private final List<HistoricalEventSource> sources;
	private final ResearchDataImportService importService;
	private final ResearchImportProperties properties;
	private final ObjectMapper objectMapper;

	public ResearchEventDatasetBuilder(List<HistoricalEventSource> sources,
			ResearchDataImportService importService, ResearchImportProperties properties,
			ObjectMapper objectMapper) {
		this.sources = sources;
		this.importService = importService;
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	public record SourceSummary(String source, String status, int events, String note) {}

	public record EventBuildSummary(
			String datasetVersion, int totalEvents, long inserted, long duplicate, long rejected,
			String jsonlFile, List<SourceSummary> sources) {}

	public EventBuildSummary buildAndImport(String datasetVersion, Instant from, Instant to) {
		Map<String, NormalizedEvent> dedup = new LinkedHashMap<>();
		List<SourceSummary> summaries = new ArrayList<>();
		for (HistoricalEventSource source : sources) {
			try {
				EventSourceResult result = source.collect(from, to);
				for (NormalizedEvent e : result.events()) {
					dedup.putIfAbsent(e.externalEventId(), e);
				}
				summaries.add(new SourceSummary(source.sourceKey(), result.status().name(),
						result.events().size(), result.note()));
			} catch (Exception ex) {
				log.warn("[EventDataset] source {} failed: {}", source.sourceKey(), ex.getMessage());
				summaries.add(new SourceSummary(source.sourceKey(), EventSourceStatus.FAILED.name(), 0,
						ex.getMessage()));
			}
		}
		return writeAndImport(new ArrayList<>(dedup.values()), datasetVersion, from, to, summaries);
	}

	/** Writes a governed JSONL for already-collected events and imports it. */
	public EventBuildSummary writeAndImport(List<NormalizedEvent> collected, String datasetVersion,
			Instant from, Instant to, List<SourceSummary> summaries) {
		Map<String, NormalizedEvent> dedup = new LinkedHashMap<>();
		for (NormalizedEvent e : collected) {
			dedup.putIfAbsent(e.externalEventId(), e);
		}
		Path root = Paths.get(properties.root()).toAbsolutePath().normalize();
		Path dir = root.resolve(datasetVersion).resolve("EVENT");
		Path jsonl = dir.resolve("events.jsonl");
		try {
			Files.createDirectories(dir);
			try (BufferedWriter writer = Files.newBufferedWriter(jsonl, StandardCharsets.UTF_8)) {
				for (NormalizedEvent e : dedup.values()) {
					writer.write(objectMapper.writeValueAsString(e.toMap()));
					writer.newLine();
				}
			}
			writeManifest(dir, datasetVersion, from, to, summaries, dedup.size());
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to write event dataset: " + ex.getMessage(), ex);
		}

		String relative = datasetVersion + "/EVENT/events.jsonl";
		ResearchImportSummary imported = importService.importFile(
				ResearchImportKind.EVENT, datasetVersion, "official-events", "BTCUSDT", null, relative);

		log.info("[EventDataset] {} sources={} events={} inserted={} duplicate={} rejected={}",
				datasetVersion, sources.size(), dedup.size(), imported.rowsInserted(),
				imported.rowsDuplicate(), imported.rowsRejected());
		return new EventBuildSummary(datasetVersion, dedup.size(), imported.rowsInserted(),
				imported.rowsDuplicate(), imported.rowsRejected(), jsonl.toString(), summaries);
	}

	private void writeManifest(Path dir, String datasetVersion, Instant from, Instant to,
			List<SourceSummary> summaries, int totalEvents) throws Exception {
		Map<String, Object> manifest = new LinkedHashMap<>();
		manifest.put("dataset_version", datasetVersion);
		manifest.put("window_start", STAMP.format(from));
		manifest.put("window_end", STAMP.format(to));
		manifest.put("generated_at", STAMP.format(Instant.now()));
		manifest.put("generated_by", LocalDate.now(ZoneOffset.UTC).toString());
		manifest.put("total_events", totalEvents);
		manifest.put("sources", summaries);
		Files.writeString(dir.resolve("manifest-events.json"),
				objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest),
				StandardCharsets.UTF_8);
	}
}
