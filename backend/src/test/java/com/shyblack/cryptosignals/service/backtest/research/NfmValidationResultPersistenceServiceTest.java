package com.shyblack.cryptosignals.service.backtest.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shyblack.cryptosignals.entity.research.NfmValidationDetail;
import com.shyblack.cryptosignals.entity.research.NfmValidationRun;
import com.shyblack.cryptosignals.repository.research.NfmValidationDetailRepository;
import com.shyblack.cryptosignals.repository.research.NfmValidationRunRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NfmValidationResultPersistenceServiceTest {

	private final NfmValidationRunRepository runRepository = mock(NfmValidationRunRepository.class);
	private final NfmValidationDetailRepository detailRepository = mock(NfmValidationDetailRepository.class);
	private final NfmValidationResultPersistenceService service =
			new NfmValidationResultPersistenceService(runRepository, detailRepository);

	private static final UUID RUN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

	private static NfmValidationResult result(NfmValidationRunType runType, NfmValidationStatus status,
			BigDecimal netPnl, List<ResearchWindowResult> windows) {
		boolean blocked = status == NfmValidationStatus.DATA_QUALITY_BLOCKED
				|| status == NfmValidationStatus.RUNTIME_BLOCKED;
		return new NfmValidationResult(
				RUN_ID, "NFM_FUTURES", "NFM_FUTURES_V1", runType, List.of("BTCUSDT", "ETHUSDT"), "1h",
				Instant.parse("2023-09-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"),
				"cafebabe", "NFM_RESEARCH_V1", "NFM_EVENTS_V1", "NFM_DERIV_V1",
				blocked ? "BLOCKED" : "PASS", status,
				0, 0, 0, netPnl, null, null, null, null, null, netPnl == null ? null : BigDecimal.ONE,
				null, null, null, blocked ? "blocked" : "ok", windows, List.of());
	}

	private void allowNewRun() {
		when(runRepository.existsByRunId(any())).thenReturn(false);
		when(detailRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
	}

	@Test
	void completedResultPersists() {
		allowNewRun();
		UUID id = service.persist(result(NfmValidationRunType.BASELINE, NfmValidationStatus.COMPLETED,
				new BigDecimal("42.5"), List.of()), "{\"strategy\":\"NFM_FUTURES_V1\"}");

		ArgumentCaptor<NfmValidationRun> cap = ArgumentCaptor.forClass(NfmValidationRun.class);
		verify(runRepository).save(cap.capture());
		NfmValidationRun saved = cap.getValue();
		assertThat(id).isEqualTo(RUN_ID);
		assertThat(saved.getRunId()).isEqualTo(RUN_ID);
		assertThat(saved.getRunType()).isEqualTo("BASELINE");
		assertThat(saved.getValidationStatus()).isEqualTo("COMPLETED");
		assertThat(saved.getDataQualityStatus()).isEqualTo("PASS");
		assertThat(saved.getExecutionStatus()).isEqualTo("COMPLETED");
		assertThat(saved.getNetPnl()).isEqualByComparingTo("42.5");
		assertThat(saved.getSymbols()).isEqualTo("BTCUSDT,ETHUSDT");
		assertThat(saved.getConfigurationJson()).contains("NFM_FUTURES_V1");
	}

	@Test
	void dataQualityBlockedResultPersists() {
		allowNewRun();
		service.persist(result(NfmValidationRunType.BASELINE, NfmValidationStatus.DATA_QUALITY_BLOCKED,
				null, List.of()), null);
		ArgumentCaptor<NfmValidationRun> cap = ArgumentCaptor.forClass(NfmValidationRun.class);
		verify(runRepository).save(cap.capture());
		assertThat(cap.getValue().getValidationStatus()).isEqualTo("BLOCKED");
		assertThat(cap.getValue().getDataQualityStatus()).isEqualTo("BLOCKED");
	}

	@Test
	void runtimeBlockedResultPersists() {
		allowNewRun();
		service.persist(result(NfmValidationRunType.BASELINE, NfmValidationStatus.RUNTIME_BLOCKED,
				null, List.of()), null);
		ArgumentCaptor<NfmValidationRun> cap = ArgumentCaptor.forClass(NfmValidationRun.class);
		verify(runRepository).save(cap.capture());
		assertThat(cap.getValue().getExecutionStatus()).isEqualTo("RUNTIME_BLOCKED");
		assertThat(cap.getValue().getValidationStatus()).isEqualTo("BLOCKED");
	}

	@Test
	void unknownMetricsRemainNull() {
		allowNewRun();
		service.persist(result(NfmValidationRunType.BASELINE, NfmValidationStatus.DATA_QUALITY_BLOCKED,
				null, List.of()), null);
		ArgumentCaptor<NfmValidationRun> cap = ArgumentCaptor.forClass(NfmValidationRun.class);
		verify(runRepository).save(cap.capture());
		NfmValidationRun saved = cap.getValue();
		assertThat(saved.getNetPnl()).isNull();
		assertThat(saved.getGrossProfit()).isNull();
		assertThat(saved.getGrossLoss()).isNull();
		assertThat(saved.getFees()).isNull();
		assertThat(saved.getExpectancy()).isNull();
		assertThat(saved.getProfitFactor()).isNull();
		assertThat(saved.getMaxDrawdownPct()).isNull();
	}

	@Test
	void cannotOverwriteExistingRun() {
		when(runRepository.existsByRunId(RUN_ID)).thenReturn(true);
		assertThatThrownBy(() -> service.persist(
				result(NfmValidationRunType.BASELINE, NfmValidationStatus.COMPLETED, BigDecimal.ONE, List.of()),
				null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("immutable");
		verify(runRepository, never()).save(any());
	}

	@Test
	void configurationHashAndDatasetVersionsArePreserved() {
		allowNewRun();
		service.persist(result(NfmValidationRunType.BASELINE, NfmValidationStatus.COMPLETED,
				BigDecimal.ONE, List.of()), null);
		ArgumentCaptor<NfmValidationRun> cap = ArgumentCaptor.forClass(NfmValidationRun.class);
		verify(runRepository).save(cap.capture());
		assertThat(cap.getValue().getConfigurationHash()).isEqualTo("cafebabe");
		assertThat(cap.getValue().getMarketDatasetVersion()).isEqualTo("NFM_RESEARCH_V1");
		assertThat(cap.getValue().getEventDatasetVersion()).isEqualTo("NFM_EVENTS_V1");
		assertThat(cap.getValue().getDerivativesDatasetVersion()).isEqualTo("NFM_DERIV_V1");
	}

	@Test
	void walkForwardWindowsRemainIndividuallyQueryable() {
		NfmValidationDetail w = new NfmValidationDetail();
		w.setLabel("w0"); w.setTrades(5); w.setWins(3); w.setLosses(2);
		w.setNetPnl(new BigDecimal("10"));
		w.setTestStart(Instant.parse("2024-01-01T00:00:00Z"));
		w.setTestEnd(Instant.parse("2024-02-01T00:00:00Z"));
		when(detailRepository.findByRunIdAndKindOrderByOrdinalAsc(RUN_ID, "WINDOW"))
				.thenReturn(List.of(w));

		List<ResearchWindowResult> got = service.windows(RUN_ID);
		assertThat(got).hasSize(1);
		assertThat(got.get(0).label()).isEqualTo("w0");
		assertThat(got.get(0).trades()).isEqualTo(5);
		assertThat(got.get(0).wins()).isEqualTo(3);
		assertThat(got.get(0).netPnl()).isEqualByComparingTo("10");
	}

	@Test
	void sensitivityVariantsRemainIndividuallyQueryable() {
		NfmValidationDetail v = new NfmValidationDetail();
		v.setLabel("v1"); v.setVariantId("v1"); v.setParamsJson("{\"minScore\":60}");
		v.setNetPnl(new BigDecimal("7"));
		when(detailRepository.findByRunIdAndKindOrderByOrdinalAsc(RUN_ID, "VARIANT"))
				.thenReturn(List.of(v));

		List<ResearchWindowResult> got = service.variants(RUN_ID);
		assertThat(got).hasSize(1);
		assertThat(got.get(0).label()).isEqualTo("v1");
		assertThat(got.get(0).paramsJson()).contains("minScore");
	}

	@Test
	void oosResultIsDistinguishableFromBaseline() {
		allowNewRun();
		service.persist(result(NfmValidationRunType.OOS, NfmValidationStatus.COMPLETED,
				BigDecimal.ONE, List.of()), null);
		ArgumentCaptor<NfmValidationRun> cap = ArgumentCaptor.forClass(NfmValidationRun.class);
		verify(runRepository).save(cap.capture());
		assertThat(cap.getValue().getRunType()).isEqualTo("OOS");
	}

	@Test
	void noWinnerOrRankingFieldExists() {
		List<String> forbidden = List.of("winner", "best", "rank", "ranking", "optimal", "top",
				"superior", "score");
		for (Class<?> type : List.of(NfmValidationRun.class, NfmValidationDetail.class,
				NfmValidationResult.class, ResearchWindowResult.class)) {
			for (Field f : type.getDeclaredFields()) {
				String name = f.getName().toLowerCase();
				for (String bad : forbidden) {
					assertThat(name).as("%s.%s", type.getSimpleName(), f.getName()).doesNotContain(bad);
				}
			}
		}
	}

	@Test
	void deterministicSameConfigRunReturnsSameIdentity() {
		allowNewRun();
		service.persist(result(NfmValidationRunType.BASELINE, NfmValidationStatus.COMPLETED,
				BigDecimal.ONE, List.of()), "{}");
		ArgumentCaptor<NfmValidationRun> cap = ArgumentCaptor.forClass(NfmValidationRun.class);
		verify(runRepository).save(cap.capture());
		assertThat(cap.getValue().getRunId()).isEqualTo(RUN_ID);

		NfmValidationRun existing = new NfmValidationRun();
		existing.setRunId(RUN_ID);
		when(runRepository.existsByRunId(RUN_ID)).thenReturn(true);
		when(runRepository.findByRunId(RUN_ID)).thenReturn(java.util.Optional.of(existing));
		assertThat(service.findExisting(RUN_ID).orElseThrow().getRunId()).isEqualTo(RUN_ID);
	}
}
