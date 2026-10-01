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
import com.shyblack.cryptosignals.entity.research.ResearchImportReject;
import com.shyblack.cryptosignals.repository.research.ResearchImportCheckpointRepository;
import com.shyblack.cryptosignals.repository.research.ResearchImportRejectRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

class ResearchDataImportServiceTest {

	@TempDir
	Path tempDir;

	private JdbcTemplate jdbc;
	private ResearchImportCheckpointRepository checkpointRepository;
	private ResearchImportRejectRepository rejectRepository;
	private ResearchDataImportService service;

	@BeforeEach
	void setUp() {
		jdbc = mock(JdbcTemplate.class);
		checkpointRepository = mock(ResearchImportCheckpointRepository.class);
		rejectRepository = mock(ResearchImportRejectRepository.class);
		ResearchImportProperties props = new ResearchImportProperties(true, tempDir.toString(), 1000);
		service = new ResearchDataImportService(jdbc, checkpointRepository, rejectRepository, props);
		when(checkpointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(rejectRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
		when(jdbc.batchUpdate(anyString(), anyList())).thenReturn(new int[]{1});
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
		verify(rejectRepository).saveAll(any());
	}

	@Test
	void completedCheckpointWithSameChecksum_isSkipped_andJdbcNotTouched() throws IOException {
		String file = write("oi.csv", "1700000000000,BTCUSDT,1000,120000\n");
		String checksum = sha256(tempDir.resolve(file));
		ResearchImportCheckpoint cp = new ResearchImportCheckpoint();
		cp.setStatus("COMPLETED");
		cp.setChecksum(checksum);
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

	private static String sha256(Path path) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}
}
