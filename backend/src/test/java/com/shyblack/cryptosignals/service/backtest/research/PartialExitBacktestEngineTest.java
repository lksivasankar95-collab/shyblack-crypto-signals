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

class PartialExitBacktestEngineTest {

	private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");

	private static final class PartialFixture implements BacktestStrategy {
		@Override public String id() { return "fixture-partial"; }
		@Override public String version() { return "v1"; }
		@Override public int warmup() { return 0; }
		@Override public Optional<Signal> evaluate(List<HistoricalCandle> h, int i) {
			if (i != 2) return Optional.empty();
			double c = h.get(2).close().doubleValue();
			return Optional.of(new Signal(PositionSide.LONG, bd(c), bd(c * 0.995),
					bd(c * 1.002), bd(c * 1.004), bd(c * 1.006), "partial"));
		}
	}

	private static final class SingleTpFixture implements BacktestStrategy {
		@Override public String id() { return "fixture-single"; }
		@Override public String version() { return "v1"; }
		@Override public int warmup() { return 0; }
		@Override public Optional<Signal> evaluate(List<HistoricalCandle> h, int i) {
			if (i != 2) return Optional.empty();
			double c = h.get(2).close().doubleValue();
			return Optional.of(new Signal(PositionSide.LONG, bd(c), bd(c * 0.995), bd(c * 1.002), "single"));
		}
	}

	private static BigDecimal bd(double v) {
		return BigDecimal.valueOf(v).setScale(8, RoundingMode.HALF_UP);
	}

	private static List<HistoricalCandle> candles(int count) {
		List<HistoricalCandle> list = new ArrayList<>();
		double price = 100.0;
		for (int i = 0; i < count; i++) {
			price *= 1.001;
			Instant open = START.plus(i, ChronoUnit.HOURS);
			BigDecimal p = bd(price);
			list.add(new HistoricalCandle(open, p, p, p, p, BigDecimal.ONE, open.plus(1, ChronoUnit.HOURS)));
		}
		return list;
	}

	private static BacktestConfig config() {
		return new BacktestConfig("fixture-partial", "BTCUSDT", "1h", TradingMode.FUTURES, START,
				START.plus(200, ChronoUnit.HOURS), new BigDecimal("1000"), new BigDecimal("2"),
				new BigDecimal("0.1"), new BigDecimal("0.05"), 1, BacktestExecutionModel.NEXT_CANDLE_OPEN,
				BacktestSameCandlePolicy.SL_FIRST, null);
	}

	@Test
	void partialExitEngine_longTp1Tp2Tp3() {
		var result = PartialExitBacktestEngine.run(config(), new PartialFixture(), candles(200), List.of());
		assertThat(result.trades()).isEqualTo(1);
		assertThat(result.lifecycles().get(0).fills()).hasSize(3);
		assertThat(result.lifecycles().get(0).finalReason()).isEqualTo("TAKE_PROFIT");
		assertThat(result.netPnl()).isGreaterThan(BigDecimal.ZERO);
	}

	@Test
	void partialExitEngine_singleTpClosesFullyAtTp1() {
		var result = PartialExitBacktestEngine.run(config(), new SingleTpFixture(), candles(200), List.of());
		assertThat(result.trades()).isEqualTo(1);
		assertThat(result.lifecycles().get(0).fills()).hasSize(1);
		assertThat(result.lifecycles().get(0).finalReason()).isEqualTo("TAKE_PROFIT");
		assertThat(result.lifecycles().get(0).remainingQty()).isEqualByComparingTo("0");
	}

	@Test
	void partialExitEngine_isDeterministic() {
		var a = PartialExitBacktestEngine.run(config(), new PartialFixture(), candles(200), List.of());
		var b = PartialExitBacktestEngine.run(config(), new PartialFixture(), candles(200), List.of());
		assertThat(a.netPnl()).isEqualByComparingTo(b.netPnl());
		assertThat(a.trades()).isEqualTo(b.trades());
	}
}
