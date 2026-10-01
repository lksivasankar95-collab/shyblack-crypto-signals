package com.shyblack.cryptosignals.service.backtest.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.research.NfmValidationAttribution;
import com.shyblack.cryptosignals.repository.research.NfmValidationAttributionRepository;
import com.shyblack.cryptosignals.service.backtest.strategy.BacktestStrategy;
import com.shyblack.cryptosignals.signal.nfm.NfmDecisionContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NfmAttributionPersistenceServiceTest {

	private final NfmValidationAttributionRepository repository =
			mock(NfmValidationAttributionRepository.class);
	private final NfmAttributionPersistenceService service =
			new NfmAttributionPersistenceService(repository);

	private static final UUID RUN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

	private static BigDecimal bd(String v) {
		return new BigDecimal(v);
	}

	private static NfmEventAttribution attribution() {
		NfmDecisionContext ctx = new NfmDecisionContext(List.of(), 80, "B", "CPI", "REPORT", "TIER_1",
				bd("0.4"), bd("1.5"), bd("2.0"), bd("0.0004"), null, "BULLISH", "LONG", null, 120L,
				null, null, null);
		BigDecimal p = bd("100");
		BacktestStrategy.Signal sig = new BacktestStrategy.Signal(PositionSide.LONG, p, bd("99"), bd("102"),
				bd("105"), bd("110"), "n", List.of(), ctx);
		List<PartialExitSimulator.ExitFill> fills = new ArrayList<>();
		fills.add(new PartialExitSimulator.ExitFill(bd("102"), bd("1"), "TP1", bd("0.01"), bd("2")));
		PartialExitSimulator.Lifecycle lc = new PartialExitSimulator.Lifecycle(PositionSide.LONG, p, bd("1"),
				BigDecimal.ZERO, bd("0.01"), bd("0.02"), bd("5"), bd("5"), "TAKE_PROFIT", true, fills);
		PartialExitBacktestEngine.Entry entry = new PartialExitBacktestEngine.Entry(sig, 3,
				Instant.parse("2024-01-01T05:00:00Z"), p, lc);
		return NfmEventAttributionBuilder.build("BTCUSDT", List.of(), List.of(entry)).get(0);
	}

	@Test
	void persistsNewAttribution() {
		when(repository.existsByRunIdAndSignalId(eq(RUN_ID), any())).thenReturn(false);
		int inserted = service.persistAll(RUN_ID, List.of(attribution()));
		assertThat(inserted).isEqualTo(1);
		ArgumentCaptor<NfmValidationAttribution> cap =
				ArgumentCaptor.forClass(NfmValidationAttribution.class);
		verify(repository).save(cap.capture());
		NfmValidationAttribution e = cap.getValue();
		assertThat(e.getRunId()).isEqualTo(RUN_ID);
		assertThat(e.getSignalId()).isNotNull();
		assertThat(e.getSignalScore()).isEqualTo(80);
		assertThat(e.getSignalGrade()).isEqualTo("B");
		assertThat(e.getMarketRegime()).isEqualTo("BULLISH");
		assertThat(e.getFunding()).isEqualByComparingTo("0.0004");
		assertThat(e.getTradeOutcome()).isEqualTo("WIN");
	}

	@Test
	void duplicateAttributionRejected() {
		when(repository.existsByRunIdAndSignalId(eq(RUN_ID), any())).thenReturn(true);
		int inserted = service.persistAll(RUN_ID, List.of(attribution()));
		assertThat(inserted).isZero();
		verify(repository, never()).save(any());
	}

	@Test
	void missingValuesRemainNullOnPersistence() {
		when(repository.existsByRunIdAndSignalId(eq(RUN_ID), any())).thenReturn(false);
		service.persistAll(RUN_ID, List.of(attribution()));
		ArgumentCaptor<NfmValidationAttribution> cap =
				ArgumentCaptor.forClass(NfmValidationAttribution.class);
		verify(repository).save(cap.capture());
		assertThat(cap.getValue().getLiquidation()).isNull();
		assertThat(cap.getValue().getExpected()).isNull();
		assertThat(cap.getValue().getSurprise()).isNull();
	}

	@Test
	void readsBackAttributionsForAnalytics() {
		NfmValidationAttribution e = new NfmValidationAttribution();
		e.setRunId(RUN_ID);
		e.setSignalId(UUID.randomUUID());
		e.setSymbol("BTCUSDT");
		e.setEventType("CPI");
		e.setSignalDirection("LONG");
		e.setSignalScore(80);
		e.setSignalGrade("B");
		e.setMarketRegime("BULLISH");
		e.setNetPnl(bd("5"));
		e.setTradeOutcome("WIN");
		e.setAttributionStatus("ATTRIBUTED");
		when(repository.findByRunIdOrderByCreatedAtAsc(RUN_ID)).thenReturn(List.of(e));

		List<NfmEventAttribution> out = service.attributions(RUN_ID);
		assertThat(out).hasSize(1);
		assertThat(out.get(0).eventType()).isEqualTo("CPI");
		assertThat(out.get(0).signalScore()).isEqualByComparingTo("80");
		assertThat(out.get(0).marketRegime()).isEqualTo("BULLISH");

		NfmValidationAnalytics.Report report = NfmValidationAnalytics.analyze(out);
		assertThat(report.byEventType()).extracting(NfmValidationAnalytics.EventTypeStats::eventType)
				.containsExactly("CPI");
		assertThat(report.byRegime()).extracting(NfmValidationAnalytics.RegimeStats::regime)
				.containsExactly("BULLISH");
	}
}
