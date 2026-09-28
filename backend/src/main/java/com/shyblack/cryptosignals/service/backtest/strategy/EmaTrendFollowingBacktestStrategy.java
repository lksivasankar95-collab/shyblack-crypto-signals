package com.shyblack.cryptosignals.service.backtest.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shyblack.cryptosignals.dto.market.KlineResponse;
import com.shyblack.cryptosignals.dto.strategy.EMATrendFollowingConfig;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalCandle;
import com.shyblack.cryptosignals.signal.emafollowing.EmaTrendFollowingAnalyzer;
import com.shyblack.cryptosignals.signal.emafollowing.EmaTrendFollowingAssessment;
import com.shyblack.cryptosignals.signal.pullback.TimeframeAggregator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Backtest adapter for EMA_TREND_FOLLOWING. Derives the HTF from the entry
 * series (closed, wall-clock-aligned buckets) and shares the exact
 * {@link EmaTrendFollowingAnalyzer} with the live service.
 */
@Component
public class EmaTrendFollowingBacktestStrategy implements BacktestStrategy, ConfigurableBacktestStrategy {

	public static final String ID = "EMA_TREND_FOLLOWING";
	public static final String VERSION = "v1";

	private final ObjectMapper objectMapper;
	private final EMATrendFollowingConfig config;
	private int lastEmittedIndex = -1;

	@Autowired
	public EmaTrendFollowingBacktestStrategy(ObjectMapper objectMapper) {
		this(objectMapper, EMATrendFollowingConfig.defaults());
	}

	private EmaTrendFollowingBacktestStrategy(ObjectMapper objectMapper, EMATrendFollowingConfig config) {
		this.objectMapper = objectMapper;
		this.config = config;
	}

	@Override
	public BacktestStrategy create(String paramsJson) {
		return new EmaTrendFollowingBacktestStrategy(
				objectMapper, EMATrendFollowingConfig.fromJson(objectMapper, paramsJson));
	}

	@Override public String id() { return ID; }
	@Override public String version() { return VERSION; }
	@Override public int warmup() { return windowCandles(); }

	@Override
	public Optional<Signal> evaluate(List<HistoricalCandle> history, int currentIndex) {
		int window = windowCandles();
		int from = Math.max(0, currentIndex + 1 - window);
		List<KlineResponse> entry = new ArrayList<>(currentIndex + 1 - from);
		for (int i = from; i <= currentIndex; i++) {
			entry.add(history.get(i).toKline());
		}
		List<KlineResponse> htf = TimeframeAggregator.aggregate(
				entry, config.getHtfTimeframe(), config.getEntryTimeframe());

		EmaTrendFollowingAssessment a = EmaTrendFollowingAnalyzer.analyze(null, htf, entry, config);
		if (!a.actionable()) return Optional.empty();

		if (lastEmittedIndex >= 0 && currentIndex - lastEmittedIndex < config.getCooldownCandles()) {
			return Optional.empty();
		}
		lastEmittedIndex = currentIndex;

		BigDecimal ref = bd(a.entry());
		BigDecimal stop = bd(a.stopLoss());
		BigDecimal tp1 = bd(a.tp1());
		String notes = String.format(
				"emaTrendFollowing score=%d rr=%.2f tp1=%.4f tp2=%.4f tp3=%.4f rsi=%.1f atr%%=%.3f",
				a.score(), a.riskReward(), a.tp1(), a.tp2(), a.tp3(),
				a.rsi() == null ? 0 : a.rsi(), a.atrPct() == null ? 0 : a.atrPct());

		return Optional.of(new Signal(PositionSide.LONG, ref, stop, tp1, notes));
	}

	private int windowCandles() {
		int ratio = TimeframeAggregator.ratio(config.getHtfTimeframe(), config.getEntryTimeframe());
		int entryNeeded = Math.max(Math.max(config.getEntrySlowEma(), config.getRsiPeriod() + 1),
				Math.max(config.getAtrPeriod() + 1, config.getVolumePeriod()));
		return (config.getHtfSlowEma() + config.getTrendSlopeLookback() + 5) * ratio + entryNeeded + 10;
	}

	private static BigDecimal bd(Double value) {
		double v = value == null ? 0.0 : value;
		return BigDecimal.valueOf(v).setScale(8, RoundingMode.HALF_UP);
	}
}
