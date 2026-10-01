package com.shyblack.cryptosignals.service.backtest.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.enums.BacktestExecutionModel;
import com.shyblack.cryptosignals.entity.enums.BacktestSameCandlePolicy;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.service.backtest.engine.BacktestConfig;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalCandle;
import com.shyblack.cryptosignals.service.backtest.strategy.BacktestStrategy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NfmValidationRunnerTest {

	private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");

	private static final class Fixture implements BacktestStrategy {
		@Override public String id() { return "fixture"; }
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

	private static List<HistoricalCandle> candles(int n) {
		List<HistoricalCandle> list = new ArrayList<>();
		double price = 100.0;
		for (int i = 0; i < n; i++) {
			price *= 1.001;
			Instant open = START.plus(i, ChronoUnit.HOURS);
			BigDecimal p = bd(price);
			list.add(new HistoricalCandle(open, p, p, p, p, BigDecimal.ONE, open.plus(1, ChronoUnit.HOURS)));
		}
		return list;
	}

	private static BacktestConfig base() {
		return new BacktestConfig("NFM_FUTURES", "BTCUSDT", "1h", TradingMode.FUTURES, START,
				START.plus(200, ChronoUnit.HOURS), new BigDecimal("1000"), new BigDecimal("2"),
				new BigDecimal("0.1"), new BigDecimal("0.05"), 1, BacktestExecutionModel.NEXT_CANDLE_OPEN,
				BacktestSameCandlePolicy.SL_FIRST, "NFM_FUTURES_V1");
	}

	private static NfmValidationResult run(NfmValidationRunType type, List<HistoricalCandle> candles) {
		return NfmValidationRunner.run(type, base(), Fixture::new, candles, List.of(),
				"NFM_RESEARCH_V1", "NFM_EVENTS_V1", "NFM_DERIV_V1", 3, 3, List.of());
	}

	@Test
	void baseline_runs_and_flags_partial_event_coverage_without_fabrication() {
		NfmValidationResult r = run(NfmValidationRunType.BASELINE, candles(200));
		assertThat(r.dataQuality()).isEqualTo("PASS");
		assertThat(r.executionStatus()).isEqualTo(NfmValidationStatus.DATA_COVERAGE_PARTIAL);
		assertThat(r.tradeCount()).isGreaterThanOrEqualTo(1);
		assertThat(r.notes()).contains("PARTIAL");
	}

	@Test
	void dataQualityFailure_blocksExecution() {
		List<HistoricalCandle> bad = List.of(
				new HistoricalCandle(START, bd(100), bd(99), bd(101), bd(100), BigDecimal.ONE,
						START.plus(1, ChronoUnit.HOURS)),
				new HistoricalCandle(START.plus(1, ChronoUnit.HOURS), bd(100), bd(101), bd(99), bd(100),
						BigDecimal.ONE, START.plus(2, ChronoUnit.HOURS)));
		NfmValidationResult r = run(NfmValidationRunType.BASELINE, bad);
		assertThat(r.executionStatus()).isEqualTo(NfmValidationStatus.DATA_QUALITY_BLOCKED);
		assertThat(r.tradeCount()).isZero();
	}

	@Test
	void runIdIsDeterministic_forSameConfigAndRunType() {
		NfmValidationResult a = run(NfmValidationRunType.BASELINE, candles(200));
		NfmValidationResult b = run(NfmValidationRunType.BASELINE, candles(200));
		assertThat(a.runId()).isEqualTo(b.runId());
		assertThat(a.configurationHash()).isEqualTo(b.configurationHash());
	}
}
