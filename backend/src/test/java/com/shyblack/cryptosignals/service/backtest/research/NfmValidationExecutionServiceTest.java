package com.shyblack.cryptosignals.service.backtest.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.research.MarketCandle;
import com.shyblack.cryptosignals.exception.BadRequestException;
import com.shyblack.cryptosignals.repository.research.MarketCandleRepository;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalCandle;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalEventProvider;
import com.shyblack.cryptosignals.service.backtest.strategy.BacktestStrategy;
import com.shyblack.cryptosignals.service.backtest.strategy.BacktestStrategyRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NfmValidationExecutionServiceTest {

	private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");
	private static final String SYMBOL = "BTCUSDT";

	private final MarketCandleRepository candleRepository = mock(MarketCandleRepository.class);
	private final HistoricalEventProvider eventProvider = mock(HistoricalEventProvider.class);
	private final BacktestStrategyRegistry strategyRegistry = mock(BacktestStrategyRegistry.class);
	private final NfmValidationResultPersistenceService persistenceService =
			mock(NfmValidationResultPersistenceService.class);
	private final NfmAttributionPersistenceService attributionPersistenceService =
			mock(NfmAttributionPersistenceService.class);

	private final NfmValidationExecutionService service = new NfmValidationExecutionService(
			candleRepository, eventProvider, strategyRegistry, persistenceService,
			attributionPersistenceService);

	private static final class Fixture implements BacktestStrategy {
		@Override public String id() { return "NFM_FUTURES"; }
		@Override public String version() { return "v1"; }
		@Override public int warmup() { return 0; }
		@Override public Optional<Signal> evaluate(List<HistoricalCandle> h, int i) {
			if (i != 2) return Optional.empty();
			double c = h.get(2).close().doubleValue();
			return Optional.of(new Signal(PositionSide.LONG, bd(c), bd(c * 0.995), bd(c * 1.002), "f"));
		}
	}

	private static BigDecimal bd(double v) {
		return BigDecimal.valueOf(v).setScale(8, RoundingMode.HALF_UP);
	}

	private static List<MarketCandle> candles(int n) {
		List<MarketCandle> list = new ArrayList<>();
		double price = 100.0;
		for (int i = 0; i < n; i++) {
			price *= 1.001;
			Instant open = START.plus(i, ChronoUnit.HOURS);
			MarketCandle c = new MarketCandle();
			c.setSymbol(SYMBOL);
			c.setTimeframe("1h");
			c.setOpenTime(open);
			c.setCloseTime(open.plus(1, ChronoUnit.HOURS));
			c.setOpen(bd(price));
			c.setHigh(bd(price));
			c.setLow(bd(price));
			c.setClose(bd(price));
			c.setVolume(BigDecimal.ONE);
			list.add(c);
		}
		return list;
	}

	private void stubData(List<MarketCandle> candleRows, boolean events) {
		when(candleRepository.findBySymbolAndTimeframeAndOpenTimeBetweenOrderByOpenTimeAsc(
				eq(SYMBOL), any(), any(), any())).thenReturn(candleRows);
		when(eventProvider.load(eq(SYMBOL), any(), any())).thenReturn(List.of());
		when(strategyRegistry.create(eq(NfmValidationExecutionService.STRATEGY_ID), any()))
				.thenReturn(new Fixture());
	}

	private static NfmValidationExecutionRequest request(String runType, Instant start, Instant end) {
		return new NfmValidationExecutionRequest(runType, List.of(SYMBOL), start, end, "1h",
				null, null, null, null, null, null, null, null, null, null, null, null, null);
	}

	private static NfmValidationExecutionRequest wfRequest(Instant start, Instant end) {
		return new NfmValidationExecutionRequest("WALK_FORWARD", List.of(SYMBOL), start, end, "1h",
				null, null, null, null, null, null, null, null, null, null, 2, 1, null);
	}

	private static NfmValidationExecutionRequest sensitivityRequest(Instant start, Instant end) {
		return new NfmValidationExecutionRequest("SENSITIVITY", List.of(SYMBOL), start, end, "1h",
				null, null, null, null, null, null, null, null, null, null, null, null,
				List.of(new NfmValidationExecutionRequest.SensitivityVariantRequest("v-min", "{\"minimumScore\":60}"),
						new NfmValidationExecutionRequest.SensitivityVariantRequest("v-rr", "{\"minRR\":2.0}")));
	}

	private static Instant end(int hours) {
		return START.plus(hours, ChronoUnit.HOURS);
	}

	@Test
	void baselineExecutionRequestRunsAndPersists() {
		stubData(candles(200), false);
		NfmValidationExecutionResponse r = service.execute(request("BASELINE", START, end(200)));
		assertThat(r.runs()).hasSize(1);
		assertThat(r.runs().get(0).executionStatus()).isEqualTo("DATA_COVERAGE_PARTIAL");
		assertThat(r.runs().get(0).tradeCount()).isGreaterThanOrEqualTo(1);
		verify(persistenceService, times(1)).persist(any(), any());
	}

	@Test
	void walkForwardExecutionRequestRuns() {
		stubData(candles(200), false);
		NfmValidationExecutionResponse r = service.execute(wfRequest(START, end(200)));
		assertThat(r.runs()).hasSize(1);
		assertThat(r.runs().get(0).executionStatus()).isEqualTo("DATA_COVERAGE_PARTIAL");
		verify(persistenceService).persist(any(), any());
	}

	@Test
	void oosExecutionRequestRuns() {
		stubData(candles(200), false);
		NfmValidationExecutionResponse r = service.execute(request("OOS", START, end(200)));
		assertThat(r.runs().get(0).executionStatus()).isEqualTo("DATA_COVERAGE_PARTIAL");
		verify(persistenceService).persist(any(), any());
	}

	@Test
	void sensitivityExecutionRequestRunsEachVariantWithItsOwnParams() {
		stubData(candles(200), false);
		NfmValidationExecutionResponse r = service.execute(sensitivityRequest(START, end(200)));
		assertThat(r.runs()).hasSize(1);
		assertThat(r.runs().get(0).executionStatus()).isEqualTo("DATA_COVERAGE_PARTIAL");

		ArgumentCaptor<String> params = ArgumentCaptor.forClass(String.class);
		verify(strategyRegistry, times(2)).create(eq(NfmValidationExecutionService.STRATEGY_ID),
				params.capture());
		assertThat(params.getAllValues()).contains("{\"minimumScore\":60}", "{\"minRR\":2.0}");
	}

	@Test
	void invalidRunTypeRejected() {
		assertThatThrownBy(() -> service.execute(request("NOT_A_TYPE", START, end(200))))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("invalid run type");
		verifyNoInteractions(persistenceService);
		verifyNoInteractions(candleRepository);
	}

	@Test
	void invalidDateRangeRejectedWithoutPersistence() {
		assertThatThrownBy(() -> service.execute(request("BASELINE", end(200), START)))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("invalid date range");
		verify(persistenceService, never()).persist(any(), any());
	}

	@Test
	void deterministicRunIdentityForSameRequest() {
		stubData(candles(200), false);
		NfmValidationExecutionResponse a = service.execute(request("BASELINE", START, end(200)));
		NfmValidationExecutionResponse b = service.execute(request("BASELINE", START, end(200)));
		assertThat(a.runs().get(0).runId()).isEqualTo(b.runs().get(0).runId());
		assertThat(a.runs().get(0).configurationHash()).isEqualTo(b.runs().get(0).configurationHash());
	}

	@Test
	void duplicateExecutionHandledIdempotently() {
		stubData(candles(200), false);
		UUID id = UUID.randomUUID();
		when(persistenceService.persist(any(), any())).thenReturn(id)
				.thenThrow(new IllegalStateException("Validation run already exists (immutable)"));
		NfmValidationExecutionResponse first = service.execute(request("BASELINE", START, end(200)));
		NfmValidationExecutionResponse second = service.execute(request("BASELINE", START, end(200)));
		assertThat(first.runs().get(0).duplicate()).isFalse();
		assertThat(second.runs().get(0).duplicate()).isTrue();
		assertThat(first.runs().get(0).runId()).isEqualTo(second.runs().get(0).runId());
	}

	@Test
	void blockedDataPreventsExecutionAndKeepsUnknownsNull() {
		stubData(List.of(), false);
		service.execute(request("BASELINE", START, end(200)));
		ArgumentCaptor<NfmValidationResult> captor = ArgumentCaptor.forClass(NfmValidationResult.class);
		verify(persistenceService).persist(captor.capture(), any());
		NfmValidationResult result = captor.getValue();
		assertThat(result.executionStatus()).isEqualTo(NfmValidationStatus.DATA_QUALITY_BLOCKED);
		assertThat(result.tradeCount()).isZero();
		assertThat(result.netPnl()).isNull();
		assertThat(result.winRatePct()).isNull();
		assertThat(result.expectancy()).isNull();
		assertThat(result.profitFactor()).isNull();
		assertThat(result.maxDrawdownPct()).isNull();
	}

	@Test
	void emptyEventsDoNotFabricateEventsAndKeepDatasetVersion() {
		stubData(candles(200), false);
		service.execute(request("BASELINE", START, end(200)));
		ArgumentCaptor<NfmValidationResult> captor = ArgumentCaptor.forClass(NfmValidationResult.class);
		verify(persistenceService).persist(captor.capture(), any());
		NfmValidationResult result = captor.getValue();
		assertThat(result.executionStatus()).isEqualTo(NfmValidationStatus.DATA_COVERAGE_PARTIAL);
		assertThat(result.eventDatasetVersion()).isEqualTo(NfmValidationExecutionService.DEFAULT_EVENT_DATASET);
		assertThat(result.notes()).contains("PARTIAL");
		assertThat(result.windows()).isEmpty();
	}

	@Test
	void runtimeUnavailableDoesNotCreateFakeResult() {
		stubData(List.of(), false);
		NfmValidationExecutionResponse r = service.execute(request("OOS", START, end(200)));
		assertThat(r.runs().get(0).executionStatus())
				.isEqualTo(NfmValidationStatus.DATA_QUALITY_BLOCKED.name());
		assertThat(r.runs().get(0).executionStatus()).isNotEqualTo("COMPLETED");
	}
}
