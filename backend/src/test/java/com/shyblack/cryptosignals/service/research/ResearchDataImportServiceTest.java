package com.shyblack.cryptosignals.service.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shyblack.cryptosignals.config.ResearchImportProperties;
import com.shyblack.cryptosignals.entity.research.ResearchImportCheckpoint;
import com.shyblack.cryptosignals.repository.research.ResearchImportCheckpointRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class ResearchDataImportServiceTest {

	@TempDir
	Path tempDir;

	private JdbcTemplate jdbc;
	private ResearchImportCheckpointRepository checkpointRepository;
	private ResearchDataImportService service;

	@BeforeEach
	void setUp() {
		jdbc = mock(JdbcTemplate.class);
		checkpointRepository = mock(ResearchImportCheckpointRepository.class);
		ResearchImportProperties props = new ResearchImportProperties(true, tempDir.toString(), 1000, "", 200);
		service = new ResearchDataImportService(jdbc, checkpointRepository, props,
				new com.fasterxml.jackson.databind.ObjectMapper());
		when(checkpointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(jdbc.batchUpdate(anyString(), anyList())).thenAnswer(inv -> {
			java.util.List<?> batch = inv.getArgument(1);
			int[] result = new int[batch.size()];
			java.util.Arrays.fill(result, 1);
			return result;
		});
	}

	private String write(String name, String content) throws IOException {
		Path p = tempDir.resolve(name);
		Files.writeString(p, content, StandardCharsets.UTF_8);
		return name;
	}

	@Test
	void importsValidRows_andQuarantinesInvalid() throws IOException {
		String file = write("candles.csv",
				"open_time,open,high,low,close,volume,close_time\n"
						+ "1700000000000,100,101,99,100,10,1700000300000\n"
						+ "1700000300000,100,99,101,100,10,1700000600000\n"); // high<low -> reject
		when(checkpointRepository.findBySourceFile(any())).thenReturn(Optional.empty());

		ResearchImportSummary summary = service.importFile(
				ResearchImportKind.CANDLE, "ds-v1", "vision", "BTCUSDT", "15m", file);

		assertThat(summary.skipped()).isFalse();
		assertThat(summary.rowsRead()).isEqualTo(2);
		assertThat(summary.rowsRejected()).isEqualTo(1);
		assertThat(summary.rowsInserted()).isEqualTo(1);
		// One batch for the data insert + one batch for the quarantined reject.
		verify(jdbc, org.mockito.Mockito.atLeast(2)).batchUpdate(anyString(), anyList());
	}

	@Test
	void completedCheckpointWithSameChecksum_isSkipped_andJdbcNotTouched() throws IOException {
		String file = write("oi.csv", "1700000000000,BTCUSDT,1000,120000\n");
		String checksum = sha256(tempDir.resolve(file));
		ResearchImportCheckpoint cp = new ResearchImportCheckpoint();
		cp.setStatus("COMPLETED");
		cp.setChecksum(checksum);
		cp.setRowsImported(10L);
		when(checkpointRepository.findBySourceFile(any())).thenReturn(Optional.of(cp));

		ResearchImportSummary summary = service.importFile(
				ResearchImportKind.OPEN_INTEREST, "ds-v1", "vision", "BTCUSDT", null, file);

		assertThat(summary.skipped()).isTrue();
		assertThat(summary.status()).isEqualTo("SKIPPED");
		verify(jdbc, never()).batchUpdate(anyString(), anyList());
	}

	@Test
	void missingFile_isRejected() {
		assertThatThrownBy(() -> service.importFile(
				ResearchImportKind.CANDLE, "ds-v1", "vision", "BTCUSDT", "15m", "does-not-exist.csv"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void pathTraversal_isRejected() {
		assertThatThrownBy(() -> service.importFile(
				ResearchImportKind.CANDLE, "ds-v1", "vision", "BTCUSDT", "15m", "../escape.csv"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void parseInstant_metricsDatetime_isUtc() {
		Instant i = ResearchDataImportService.parseInstant("2025-10-07 00:00:00");
		assertThat(i).isEqualTo(Instant.parse("2025-10-07T00:00:00Z"));
		assertThat(i.atZone(ZoneOffset.UTC).getHour()).isZero();
	}

	@Test
	void parseInstant_epochMillis_unchanged() {
		assertThat(ResearchDataImportService.parseInstant("1693526400000"))
				.isEqualTo(Instant.ofEpochMilli(1693526400000L));
	}

	@Test
	void parseInstant_invalid_isNull() {
		assertThat(ResearchDataImportService.parseInstant("not-a-date")).isNull();
		assertThat(ResearchDataImportService.parseInstant("")).isNull();
		assertThat(ResearchDataImportService.parseInstant(null)).isNull();
	}

	@Test
	void openInterestDatetimeRows_import_areNotRejected() throws IOException {
		String file = write("oi-ok.csv",
				"create_time,symbol,sum_open_interest,sum_open_interest_value,count_toptrader_long_short_ratio,"
						+ "sum_toptrader_long_short_ratio,count_long_short_ratio,sum_taker_long_short_vol_ratio\n"
						+ "2025-10-07 00:00:00,BTCUSDT,100243.058,12494186585.40,0.69,1.82,0.56,1.15\n"
						+ "2025-10-07 00:05:00,BTCUSDT,100180.792,12491937936.12,0.69,1.81,0.56,1.97\n");
		when(checkpointRepository.findBySourceFile(any())).thenReturn(Optional.empty());

		ResearchImportSummary s = service.importFile(
				ResearchImportKind.OPEN_INTEREST, "ds-v1", "vision", "BTCUSDT", null, file);

		assertThat(s.rowsRead()).isEqualTo(2);
		assertThat(s.rowsInserted()).isEqualTo(2);
		assertThat(s.rowsRejected()).isZero();
		assertThat(s.status()).isEqualTo("COMPLETED");
	}

	@Test
	void openInterestInvalidTimestamp_isQuarantined_andCheckpointPartial() throws IOException {
		String file = write("oi-bad.csv",
				"create_time,symbol,sum_open_interest,sum_open_interest_value\n"
						+ "not-a-date,BTCUSDT,100,1\n");
		when(checkpointRepository.findBySourceFile(any())).thenReturn(Optional.empty());
		ArgumentCaptor<ResearchImportCheckpoint> cap = ArgumentCaptor.forClass(ResearchImportCheckpoint.class);

		ResearchImportSummary s = service.importFile(
				ResearchImportKind.OPEN_INTEREST, "ds-v1", "vision", "BTCUSDT", null, file);

		assertThat(s.rowsRejected()).isEqualTo(1);
		assertThat(s.rowsInserted()).isZero();
		assertThat(s.status()).isEqualTo("PARTIAL");
		verify(checkpointRepository, org.mockito.Mockito.atLeastOnce()).save(cap.capture());
		assertThat(cap.getValue().getStatus()).isEqualTo("PARTIAL");
	}

	@Test
	void completedCheckpointWithZeroRows_isNotSkipped_recovery() throws IOException {
		String file = write("oi-recover.csv", "2025-10-07 00:00:00,BTCUSDT,100,1\n");
		String checksum = sha256(tempDir.resolve(file));
		ResearchImportCheckpoint cp = new ResearchImportCheckpoint();
		cp.setStatus("COMPLETED");
		cp.setChecksum(checksum);
		cp.setRowsImported(0L); // historical bug state -> must be retried
		when(checkpointRepository.findBySourceFile(any())).thenReturn(Optional.of(cp));

		ResearchImportSummary s = service.importFile(
				ResearchImportKind.OPEN_INTEREST, "ds-v1", "vision", "BTCUSDT", null, file);

		assertThat(s.skipped()).isFalse();
	}

	private static String sha256(Path path) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}
}
