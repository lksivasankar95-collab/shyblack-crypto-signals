package com.shyblack.cryptosignals.service.backtest.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shyblack.cryptosignals.dto.market.KlineResponse;
import com.shyblack.cryptosignals.dto.strategy.TrendPullbackConfig;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalCandle;
import com.shyblack.cryptosignals.signal.pullback.TimeframeAggregator;
import com.shyblack.cryptosignals.signal.pullback.TrendPullbackAnalyzer;
import com.shyblack.cryptosignals.signal.pullback.TrendPullbackAssessment;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Backtest adapter for the TREND_PULLBACK strategy.
 *
 * The backtest engine is single-timeframe, so the higher timeframe is derived
 * by aggregating the entry-timeframe candles it already has. Only fully
 * closed, wall-clock-aligned buckets are used, so no HTF candle can contain
 * information newer than the entry candle being evaluated — the same
 * look-ahead guarantee the engine gives the strategy.
 *
 * The decision itself is made by the very same
 * {@link TrendPullbackAnalyzer} the live signal service uses.
 */
@Component
public class TrendPullbackBacktestStrategy
        implements BacktestStrategy, ConfigurableBacktestStrategy {

    public static final String ID = "TREND_PULLBACK";
    public static final String VERSION = "v1";

    private final ObjectMapper objectMapper;
    private final TrendPullbackConfig config;

    /**
     * Index of the candle that last emitted a signal, or -1. Mirrors the live
     * service's candle-based cooldown. State is per-run because the registry
     * hands each run a fresh instance.
     */
    private int lastEmittedIndex = -1;

    @Autowired
    public TrendPullbackBacktestStrategy(ObjectMapper objectMapper) {
        this(objectMapper, TrendPullbackConfig.defaults());
    }

    private TrendPullbackBacktestStrategy(ObjectMapper objectMapper, TrendPullbackConfig config) {
        this.objectMapper = objectMapper;
        this.config = config;
    }

    @Override
    public BacktestStrategy create(String paramsJson) {
        return new TrendPullbackBacktestStrategy(
                objectMapper, TrendPullbackConfig.fromJson(objectMapper, paramsJson));
    }

    @Override public String id() { return ID; }
    @Override public String version() { return VERSION; }

    @Override
    public int warmup() {
        return windowCandles();
    }

    @Override
    public Optional<Signal> evaluate(List<HistoricalCandle> history, int currentIndex) {
        int ratio = TimeframeAggregator.ratio(config.getHtf(), config.getEntryTimeframe());
        int window = windowCandles();
        int from = Math.max(0, currentIndex + 1 - window);

        List<KlineResponse> entry = new ArrayList<>(currentIndex + 1 - from);
        for (int i = from; i <= currentIndex; i++) {
            entry.add(history.get(i).toKline());
        }
        List<KlineResponse> htf = TimeframeAggregator.aggregate(
                entry, config.getHtf(), config.getEntryTimeframe());

        TrendPullbackAssessment a = TrendPullbackAnalyzer.analyze(null, htf, entry, config);
        if (!a.actionable()) return Optional.empty();

        // Cooldown parity with live: a signal emitted within `cooldownCandles`
        // entry candles suppresses a new one. Cooldown is measured from the
        // candle that produced the previous signal.
        if (lastEmittedIndex >= 0
                && currentIndex - lastEmittedIndex < config.getCooldownCandles()) {
            return Optional.empty();
        }
        lastEmittedIndex = currentIndex;

        BigDecimal ref = bd(a.entry());
        BigDecimal stop = bd(a.stopLoss());
        BigDecimal tp1 = bd(a.tp1());
        String notes = String.format(
                "trendPullback score=%d rr=%.2f tp1=%.4f tp2=%.4f tp3=%.4f adx=%.1f rsi=%.1f",
                a.score(), a.riskReward(), a.tp1(), a.tp2(), a.tp3(),
                a.adx() == null ? 0 : a.adx(), a.rsiNow() == null ? 0 : a.rsiNow());

        return Optional.of(new Signal(PositionSide.LONG, ref, stop, tp1, notes));
    }

    private int windowCandles() {
        int ratio = TimeframeAggregator.ratio(config.getHtf(), config.getEntryTimeframe());
        return (config.getEmaSlowHtf() + config.getSlopeLookback() + 5) * ratio
                + Math.max(config.getEntryEma(), config.getPullbackEma())
                + config.getMaxSetupCandles() + config.getSwingLookback() + 10;
    }

    private static BigDecimal bd(Double value) {
        double v = value == null ? 0.0 : value;
        return BigDecimal.valueOf(v).setScale(8, RoundingMode.HALF_UP);
    }
}
