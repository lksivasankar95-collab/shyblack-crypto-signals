package com.shyblack.cryptosignals.service.backtest.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.enums.BacktestExecutionModel;
import com.shyblack.cryptosignals.entity.enums.BacktestSameCandlePolicy;
import com.shyblack.cryptosignals.entity.enums.NewsEventCategory;
import com.shyblack.cryptosignals.entity.enums.NewsEventStage;
import com.shyblack.cryptosignals.entity.enums.NewsEventType;
import com.shyblack.cryptosignals.entity.enums.NewsSourceTier;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.service.backtest.engine.BacktestConfig;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalCandle;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalEvent;
import com.shyblack.cryptosignals.service.backtest.strategy.BacktestStrategy;
import com.shyblack.cryptosignals.signal.nfm.NfmDecisionContext;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NfmDecisionContextExposureTest {

	private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");

	private static BigDecimal bd(String v) {
		return new BigDecimal(v);
	}

	private static NfmDecisionContext ctx(Integer score, String grade, BigDecimal reaction,
			BigDecimal volume, BigDecimal oi, String regime, String rejectReason) {
		return new NfmDecisionContext(List.of(), score, grade, "CPI", "REPORT", "TIER_1", reaction, volume,
				oi, null, null, regime, "LONG", rejectReason, 120L, null, null, null);
	}

	private static PartialExitBacktestEngine.Entry entry(String symbol, PositionSide side,
			NfmDecisionContext context, boolean tp1, boolean sl) {
		BigDecimal p = bd("100");
		BacktestStrategy.Signal sig = new BacktestStrategy.Signal(side, p, bd("99"), bd("102"), bd("105"),
				bd("110"), "n", List.of(), context);
		List<PartialExitSimulator.ExitFill> fills = new ArrayList<>();
		if (tp1) fills.add(new PartialExitSimulator.ExitFill(bd("102"), bd("1"), "TP1", bd("0.01"), bd("2")));
		if (sl) fills.add(new PartialExitSimulator.ExitFill(bd("99"), bd("1"), "STOP_LOSS", bd("0.01"), bd("-1")));
		BigDecimal net = tp1 ? bd("5") : bd("-3");
		PartialExitSimulator.Lifecycle lc = new PartialExitSimulator.Lifecycle(side, p, bd("1"),
				BigDecimal.ZERO, bd("0.01"), bd("0.02"), net, net, sl ? "STOP_LOSS" : "TAKE_PROFIT", true, fills);
		return new PartialExitBacktestEngine.Entry(sig, 3, START.plus(5, ChronoUnit.HOURS), p, lc);
	}

	private static HistoricalEvent event(UUID id, NewsEventType type) {
		return new HistoricalEvent(id, START.plus(2, ChronoUnit.HOURS), "BTCUSDT", type,
				NewsEventCategory.MACRO, NewsEventStage.REPORT, NewsSourceTier.TIER_1, null, null, null, null,
				"src", "h");
	}

	private static NfmEventAttribution attribute(NfmDecisionContext context) {
		return NfmEventAttributionBuilder.build("BTCUSDT", List.of(),
				List.of(entry("BTCUSDT", PositionSide.LONG, context, true, false))).get(0);
	}

	@Test
	void scoreAndGradeAreExposedUnchanged() {
		NfmEventAttribution a = attribute(ctx(82, "A", bd("0.4"), bd("1.5"), null, "BULLISH", null));
		assertThat(a.signalScore()).isEqualByComparingTo("82");
		assertThat(a.signalGrade()).isEqualTo("A");
		assertThat(NfmValidationAnalytics.analyze(List.of(a)).byDirection().get(0).averageScore())
				.isEqualByComparingTo("82");
	}

	@Test
	void reactionAndVolumeAreExposedUnchanged() {
		NfmEventAttribution a = attribute(ctx(70, "B", bd("0.4"), bd("1.5"), bd("2.25"), "BULLISH", null));
		assertThat(a.priceReaction()).isEqualByComparingTo("0.4");
		assertThat(a.volumeRatio()).isEqualByComparingTo("1.5");
		assertThat(a.oiChange()).isEqualByComparingTo("2.25");
	}

	@Test
	void missingOiFundingLiquidationRemainNull() {
		NfmEventAttribution a = attribute(ctx(70, "B", bd("0.4"), bd("1.5"), null, "BULLISH", null));
		assertThat(a.oiChange()).isNull();
		assertThat(a.funding()).isNull();
		assertThat(a.liquidation()).isNull();
	}

	@Test
	void regimeIsExposedUnchangedAndGrouped() {
		NfmEventAttribution a = attribute(ctx(70, "B", bd("0.4"), bd("1.5"), null, "BEARISH", null));
		assertThat(a.marketRegime()).isEqualTo("BEARISH");
		NfmValidationAnalytics.Report report = NfmValidationAnalytics.analyze(List.of(a));
		assertThat(report.byRegime()).extracting(NfmValidationAnalytics.RegimeStats::regime)
				.containsExactly("BEARISH");
	}

	@Test
	void rejectReasonPreservedAndMissingReasonStaysNull() {
		NfmEventAttribution withReason =
				attribute(ctx(40, "C", bd("0.1"), bd("0.5"), null, "NEUTRAL", "volume not confirmed"));
		assertThat(withReason.decisionContext().rejectReason()).isEqualTo("volume not confirmed");
		NfmEventAttribution noReason = attribute(ctx(70, "B", bd("0.4"), bd("1.5"), null, "NEUTRAL", null));
		assertThat(noReason.decisionContext().rejectReason()).isNull();
	}

	@Test
	void eventIdsRemainCausallyValid() {
		UUID id = UUID.randomUUID();
		PartialExitBacktestEngine.Entry e = entry("BTCUSDT", PositionSide.LONG,
				ctx(70, "B", bd("0.4"), bd("1.5"), null, "NEUTRAL", null), true, false);
		BacktestStrategy.Signal withIds = new BacktestStrategy.Signal(PositionSide.LONG, bd("100"), bd("99"),
				bd("102"), bd("105"), bd("110"), "n", List.of(id), e.signal().decisionContext());
		PartialExitBacktestEngine.Entry linked = new PartialExitBacktestEngine.Entry(withIds, e.entryIndex(),
				e.entryTime(), e.entryPrice(), e.lifecycle());
		NfmEventAttribution a = NfmEventAttributionBuilder.build("BTCUSDT", List.of(event(id, NewsEventType.CPI)),
				List.of(linked)).get(0);
		assertThat(a.attributedEventIds()).containsExactly(id);
		assertThat(a.attributionStatus()).isEqualTo("ATTRIBUTED");
	}

	@Test
	void gradeAndScoreBucketsAggregate() {
		NfmEventAttribution a = attribute(ctx(90, "A", bd("0.4"), bd("1.5"), null, "NEUTRAL", null));
		NfmEventAttribution c = attribute(ctx(66, "C", bd("0.3"), bd("1.2"), null, "NEUTRAL", null));
		NfmValidationAnalytics.Report report = NfmValidationAnalytics.analyze(List.of(a, c));
		assertThat(report.byGrade()).extracting(NfmValidationAnalytics.GradeStats::grade)
				.containsExactlyInAnyOrder("A", "C");
		assertThat(report.byScoreBucket()).extracting(NfmValidationAnalytics.ScoreBucketStats::bucket)
				.containsExactlyInAnyOrder("85-100", "65-69");
	}

	@Test
	void longAndShortPreserveIdenticalContextSemantics() {
		NfmEventAttribution l = NfmEventAttributionBuilder.build("BTCUSDT", List.of(),
				List.of(entry("BTCUSDT", PositionSide.LONG,
						ctx(80, "B", bd("0.4"), bd("1.5"), null, "NEUTRAL", null), true, false))).get(0);
		NfmEventAttribution s = NfmEventAttributionBuilder.build("BTCUSDT", List.of(),
				List.of(entry("BTCUSDT", PositionSide.SHORT,
						ctx(80, "B", bd("0.4"), bd("1.5"), null, "NEUTRAL", null), true, false))).get(0);
		assertThat(l.signalScore()).isEqualByComparingTo(s.signalScore());
		assertThat(l.priceReaction()).isEqualByComparingTo(s.priceReaction());
		assertThat(l.volumeRatio()).isEqualByComparingTo(s.volumeRatio());
	}

	private static List<HistoricalCandle> candles(int n) {
		List<HistoricalCandle> list = new ArrayList<>();
		double price = 100.0;
		for (int i = 0; i < n; i++) {
			price *= 1.001;
			Instant open = START.plus(i, ChronoUnit.HOURS);
			BigDecimal p = bd(Double.toString(price));
			list.add(new HistoricalCandle(open, p, p, p, p, BigDecimal.ONE, open.plus(1, ChronoUnit.HOURS)));
		}
		return list;
	}

	private static BacktestConfig config() {
		return new BacktestConfig("NFM_FUTURES", "BTCUSDT", "1h", TradingMode.FUTURES, START,
				START.plus(200, ChronoUnit.HOURS), bd("1000"), bd("2"), bd("0.05"), bd("0.03"), 1,
				BacktestExecutionModel.NEXT_CANDLE_OPEN, BacktestSameCandlePolicy.SL_FIRST, "NFM_FUTURES_V1");
	}

	@Test
	void contextDoesNotChangeTradingDecision() {
		BacktestStrategy plain = new Fixture(null);
		BacktestStrategy withContext = new Fixture(ctx(90, "A", bd("1.0"), bd("3.0"), bd("5"), "BULLISH", null));
		PartialExitBacktestEngine.Result a = PartialExitBacktestEngine.run(config(), plain, candles(200), List.of());
		PartialExitBacktestEngine.Result b =
				PartialExitBacktestEngine.run(config(), withContext, candles(200), List.of());
		assertThat(b.trades()).isEqualTo(a.trades());
		assertThat(b.wins()).isEqualTo(a.wins());
		assertThat(b.netPnl()).isEqualByComparingTo(a.netPnl());
	}

	private static final class Fixture implements BacktestStrategy {
		private final NfmDecisionContext context;
		Fixture(NfmDecisionContext context) { this.context = context; }
		@Override public String id() { return "NFM_FUTURES"; }
		@Override public String version() { return "v1"; }
		@Override public int warmup() { return 0; }
		@Override public Optional<Signal> evaluate(List<HistoricalCandle> h, int i) {
			if (i != 2) return Optional.empty();
			double c = h.get(2).close().doubleValue();
			BigDecimal p = BigDecimal.valueOf(c).setScale(8, RoundingMode.HALF_UP);
			BigDecimal tp = BigDecimal.valueOf(c * 1.002).setScale(8, RoundingMode.HALF_UP);
			BigDecimal sl = BigDecimal.valueOf(c * 0.995).setScale(8, RoundingMode.HALF_UP);
			return Optional.of(new Signal(PositionSide.LONG, p, sl, tp, null, null, "n", List.of(),
					context));
		}
	}

	@Test
	void noWinnerOrRankingFieldExists() {
		List<String> forbidden = List.of("winner", "best", "rank", "optimal", "superior");
		for (Class<?> type : List.of(NfmDecisionContext.class, NfmValidationAnalytics.Report.class,
				NfmValidationAnalytics.EventTypeStats.class, NfmValidationAnalytics.RegimeStats.class,
				NfmValidationAnalytics.GradeStats.class, NfmValidationAnalytics.ScoreBucketStats.class)) {
			for (Field f : type.getDeclaredFields()) {
				String name = f.getName().toLowerCase();
				for (String bad : forbidden) {
					assertThat(name).as("%s.%s", type.getSimpleName(), f.getName()).doesNotContain(bad);
				}
			}
		}
	}
}
