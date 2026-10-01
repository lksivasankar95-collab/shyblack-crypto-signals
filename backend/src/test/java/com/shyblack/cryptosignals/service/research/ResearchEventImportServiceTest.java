package com.shyblack.cryptosignals.service.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shyblack.cryptosignals.config.ResearchImportProperties;
import com.shyblack.cryptosignals.repository.research.ResearchImportCheckpointRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

class ResearchEventImportServiceTest {

	@TempDir
	Path tempDir;

	private JdbcTemplate jdbc;
	private ResearchImportCheckpointRepository checkpointRepository;
	private final ObjectMapper mapper = new ObjectMapper();
	private ResearchDataImportService service;

	@BeforeEach
	void setUp() {
		jdbc = mock(JdbcTemplate.class);
		checkpointRepository = mock(ResearchImportCheckpointRepository.class);
		ResearchImportProperties props = new ResearchImportProperties(true, tempDir.toString(), 1000, "", 200);
		service = new ResearchDataImportService(jdbc, checkpointRepository, props, mapper);
		when(checkpointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(jdbc.batchUpdate(anyString(), anyList())).thenAnswer(inv -> {
			java.util.List<?> batch = inv.getArgument(1);
			int[] r = new int[batch.size()];
			java.util.Arrays.fill(r, 1);
			return r;
		});
	}

	private String write(String name, String content) throws IOException {
		Path p = tempDir.resolve(name);
		Files.writeString(p, content, StandardCharsets.UTF_8);
		return name;
	}

	private static final String VALID =
			"{\"external_event_id\":\"FOMC-2023-11-01\",\"event_time\":\"2023-11-01T18:00:00Z\","
					+ "\"confirmation_timestamp\":\"2023-11-01T18:00:00Z\",\"category\":\"MACRO\","
					+ "\"event_type\":\"FOMC\",\"event_stage\":\"OFFICIAL_CONFIRMATION\","
					+ "\"source\":\"Federal Reserve\",\"source_tier\":\"TIER_1\","
					+ "\"headline\":\"FOMC statement\",\"assets\":\"BTC:HIGH;ETH:HIGH\"}";

	@Test
	void validGovernedEvent_imports_withAssetMapping() throws IOException {
		String file = write("events.jsonl", VALID + "\n");
		when(checkpointRepository.findBySourceFile(any())).thenReturn(Optional.empty());

		ResearchImportSummary s = service.importFile(
				ResearchImportKind.EVENT, "NFM_EVENTS_V1", "governed-events", "BTCUSDT", null, file);

		assertThat(s.rowsRead()).isEqualTo(1);
		assertThat(s.rowsInserted()).isEqualTo(1);
		assertThat(s.rowsRejected()).isZero();
		assertThat(s.status()).isEqualTo("COMPLETED");
	}

	@Test
	void expectedActualSurpriseMayBeNull_andAreNotComputed() {
		// No expected/actual/surprise fields -> accepted with all null.
		assertThat(service.validateEvent(mapper.valueToTree(mapOf(VALID, "headline", "x")))).isNull();
		assertThat(service.parseInstant("2023-11-01T18:00:00Z"))
				.isEqualTo(Instant.parse("2023-11-01T18:00:00Z"));
	}

	@Test
	void duplicateExternalEventId_isDeduplicated() throws IOException {
		String file = write("events-dup.jsonl", VALID + "\n" + VALID + "\n");
		when(checkpointRepository.findBySourceFile(any())).thenReturn(Optional.empty());
		// Second insert hits ON CONFLICT DO NOTHING -> 0 affected.
		when(jdbc.batchUpdate(anyString(), anyList())).thenReturn(new int[]{1, 0});

		ResearchImportSummary s = service.importFile(
				ResearchImportKind.EVENT, "NFM_EVENTS_V1", "governed-events", "BTCUSDT", null, file);

		assertThat(s.rowsInserted()).isEqualTo(1);
		assertThat(s.rowsDuplicate()).isEqualTo(1);
	}

	@Test
	void invalidSourceTier_isRejected() throws IOException {
		String bad = VALID.replace("\"TIER_1\"", "\"TIER_9\"");
		String file = write("events-badtier.jsonl", bad + "\n");
		when(checkpointRepository.findBySourceFile(any())).thenReturn(Optional.empty());
		ResearchImportSummary s = service.importFile(
				ResearchImportKind.EVENT, "NFM_EVENTS_V1", "governed-events", "BTCUSDT", null, file);
		assertThat(s.rowsRejected()).isEqualTo(1);
		assertThat(s.status()).isEqualTo("PARTIAL");
	}

	@Test
	void invalidLifecycle_isRejected() {
		assertThat(service.validateEvent(mapper.valueToTree(
				mapOf(VALID, "event_stage", "NOT_A_STAGE")))).isEqualTo("invalid event_stage");
	}

	@Test
	void invalidCategory_isRejected() {
		assertThat(service.validateEvent(mapper.valueToTree(
				mapOf(VALID, "category", "NOT_A_CATEGORY")))).isEqualTo("invalid category");
	}

	@Test
	void invalidAssetMapping_isRejected() {
		assertThat(service.validateEvent(mapper.valueToTree(
				mapOf(VALID, "assets", "BTC:SUPER_HIGH")))).isEqualTo("invalid asset mapping");
		assertThat(service.validateEvent(mapper.valueToTree(
				mapOf(VALID, "assets", "BTC")))).isEqualTo("invalid asset mapping");
	}

	@Test
	void eventOutsideWindow_isRejected() {
		assertThat(service.validateEvent(mapper.valueToTree(
				mapOf(VALID, "event_time", "2019-01-01T00:00:00Z"))))
				.isEqualTo("event_time outside research window");
	}

	@Test
	void malformedJson_isRejected() throws IOException {
		String file = write("events-bad.jsonl", "{not json}\n");
		when(checkpointRepository.findBySourceFile(any())).thenReturn(Optional.empty());
		ResearchImportSummary s = service.importFile(
				ResearchImportKind.EVENT, "NFM_EVENTS_V1", "governed-events", "BTCUSDT", null, file);
		assertThat(s.rowsRejected()).isEqualTo(1);
	}

	private java.util.Map<String, Object> mapOf(String validJson, String key, String value) {
		try {
			java.util.Map<String, Object> m = mapper.readValue(validJson,
					new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
			m.put(key, value);
			return m;
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}
}
