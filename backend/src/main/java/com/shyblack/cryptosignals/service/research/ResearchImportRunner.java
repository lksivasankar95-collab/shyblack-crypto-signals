package com.shyblack.cryptosignals.service.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shyblack.cryptosignals.config.ResearchImportProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Manifest-driven bulk import. Disabled by default ({@code
 * app.research.import.enabled=false}) so production startup is unaffected. When
 * enabled it reads a manifest JSON (produced by the acquisition tool) and
 * imports each file through the existing {@link ResearchDataImportService} —
 * idempotent, checksum-guarded, quarantine-preserving.
 */
@Component
public class ResearchImportRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ResearchImportRunner.class);

	private final ResearchImportProperties properties;
	private final ResearchDataImportService importService;
	private final ObjectMapper objectMapper;

	public ResearchImportRunner(ResearchImportProperties properties,
			ResearchDataImportService importService, ObjectMapper objectMapper) {
		this.properties = properties;
		this.importService = importService;
		this.objectMapper = objectMapper;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!properties.enabled()) {
			return;
		}
		if (properties.manifest() == null || properties.manifest().isBlank()) {
			log.warn("[ResearchImport] enabled but no manifest configured");
			return;
		}
		Path manifest = Paths.get(properties.manifest());
		if (!Files.isRegularFile(manifest)) {
			log.warn("[ResearchImport] manifest not found: {}", manifest);
			return;
		}
		try {
			JsonNode root = objectMapper.readTree(manifest.toFile());
			if (!root.isArray()) {
				log.error("[ResearchImport] manifest is not a JSON array: {}", manifest);
				return;
			}
			long files = 0, inserted = 0, rejected = 0, duplicates = 0;
			for (JsonNode e : root) {
				ResearchImportKind kind = ResearchImportKind.valueOf(e.get("kind").asText());
				String datasetVersion = text(e, "datasetVersion");
				String sourceDataset = e.hasNonNull("sourceDataset")
						? e.get("sourceDataset").asText() : "binance-vision";
				String symbol = text(e, "symbol");
				String timeframe = e.hasNonNull("timeframe") ? e.get("timeframe").asText() : null;
				String file = text(e, "file");
				try {
					ResearchImportSummary s = importService.importFile(
							kind, datasetVersion, sourceDataset, symbol, timeframe, file);
					files++;
					inserted += s.rowsInserted();
					rejected += s.rowsRejected();
					duplicates += s.rowsDuplicate();
				} catch (Exception ex) {
					log.warn("[ResearchImport] file failed file={} err={}", file, ex.getMessage());
				}
			}
			log.info("[ResearchImport] manifest complete files={} inserted={} duplicate={} rejected={}",
					files, inserted, duplicates, rejected);
		} catch (Exception ex) {
			log.error("[ResearchImport] manifest import failed: {}", ex.getMessage(), ex);
		}
	}

	private static String text(JsonNode node, String field) {
		JsonNode v = node.get(field);
		return v == null || v.isNull() ? null : v.asText();
	}
}
