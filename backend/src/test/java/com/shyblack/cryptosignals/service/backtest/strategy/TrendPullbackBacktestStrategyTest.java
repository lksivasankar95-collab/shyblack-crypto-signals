package com.shyblack.cryptosignals.service.backtest.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shyblack.cryptosignals.entity.BacktestRun;
import com.shyblack.cryptosignals.entity.enums.BacktestExecutionModel;
import com.shyblack.cryptosignals.entity.enums.BacktestSameCandlePolicy;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.service.backtest.engine.BacktestConfig;
import com.shyblack.cryptosignals.service.backtest.engine.BacktestEngine;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalCandle;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TrendPullbackBacktestStrategyTest {

    private static final long STEP = 900_000L;
    private static final long START = 1_699_999_200_000L; // aligned to the hour

    private static final String EASY_PARAMS = """
            {"emaFastHtf":5,"emaSlowHtf":15,"adxPeriod":5,"minAdx":1,"requirePositiveSlope":false,
             "pullbackEma":5,"entryEma":10,"zoneMode":"EMA20","maxPullbackDistanceAtr":10,
             "rsiPeriod":5,"rsiMin":0,"rsiMax":100,"requireRecovery":false,
             "volumeFilterEnabled":false,"volumeSmaPeriod":5,"atrPeriod":5,"slAtrBuffer":0.2,
             "maxSlAtr":20,"minRR":0.5,"maxSetupCandles":12,"swingLookback":2,
             "candleConfirmationEnabled":false,"minimumScore":0}
            """;

    private static final String EASY_NO_COOLDOWN =
            "{\"cooldownCandles\":0," + EASY_PARAMS.substring(1);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void exposesStableIdentity() {
        TrendPullbackBacktestStrategy s = new TrendPullbackBacktestStrategy(MAPPER);
        assertThat(s.id()).isEqualTo("TREND_PULLBACK");
        assertThat(s.version()).isEqualTo("v1");
        assertThat(s.warmup()).isGreaterThan(50);
    }

    @Test
    void create_parsesBareAndNestedConfig() {
        TrendPullbackBacktestStrategy base = new TrendPullbackBacktestStrategy(MAPPER);
        assertThat(base.create(EASY_PARAMS)).isNotSameAs(base);

        String nested = "{\"pullback\":" + EASY_PARAMS + "}";
        assertThat(base.create(nested)).isNotNull();
        assertThat(base.create(null)).isNotNull();
    }

    @Test
    void evaluate_isDeterministicAndIgnoresCandlesBeyondCurrentIndex() {
        TrendPullbackBacktestStrategy base = new TrendPullbackBacktestStrategy(MAPPER);
        int idx = 297; // breakout candle
        List<HistoricalCandle> history = bullishPullbackSeries(300);

        // Fresh instance per call proves determinism independent of run state.
        Optional<BacktestStrategy.Signal> first = base.create(EASY_PARAMS).evaluate(history, idx);
        Optional<BacktestStrategy.Signal> second = base.create(EASY_PARAMS).evaluate(history, idx);
        assertThat(second).isEqualTo(first);
        assertThat(first).isPresent();
        assertThat(first.get().side()).isEqualTo(PositionSide.LONG);
        assertThat(first.get().stopLoss()).isLessThan(first.get().referencePrice());
        assertThat(first.get().takeProfit()).isGreaterThan(first.get().referencePrice());

        // Append radically different future candles — the decision at index idx
        // must not change (no look-ahead, structurally).
        List<HistoricalCandle> extended = new ArrayList<>(history);
        double[] crash = new double[200];
        for (int i = 0; i < crash.length; i++) crash[i] = 10;
        extended.addAll(toCandles(crash, 0.05, 100, START + 300L * STEP));
        Optional<BacktestStrategy.Signal> withFuture =
                base.create(EASY_PARAMS).evaluate(extended, idx);
        assertThat(withFuture).isEqualTo(first);
    }

    @Test
    void cooldown_suppressesSecondEligibleSetupWithinWindow() {
        TrendPullbackBacktestStrategy base = new TrendPullbackBacktestStrategy(MAPPER);
        List<HistoricalCandle> history = twoBreakoutSeries(300);
        BacktestStrategy strategy = base.create(EASY_PARAMS); // cooldownCandles = 4

        assertThat(strategy.evaluate(history, 295)).isPresent(); // first eligible setup
        assertThat(strategy.evaluate(history, 297)).isEmpty();   // second, inside cooldown

        // The 297 setup is independently eligible — a fresh run emits it.
        assertThat(base.create(EASY_PARAMS).evaluate(history, 297)).isPresent();
    }

    @Test
    void cooldownZero_doesNotSuppressSecondEligibleSetup() {
        TrendPullbackBacktestStrategy base = new TrendPullbackBacktestStrategy(MAPPER);
        List<HistoricalCandle> history = twoBreakoutSeries(300);
        BacktestStrategy strategy = base.create(EASY_NO_COOLDOWN);

        assertThat(strategy.evaluate(history, 295)).isPresent();
        assertThat(strategy.evaluate(history, 297)).isPresent();
    }

    @Test
    void endToEnd_emitsTradesWithFeesApplied() {
        BacktestStrategy strategy = new TrendPullbackBacktestStrategy(MAPPER).create(EASY_PARAMS);
        List<HistoricalCandle> candles = bullishPullbackSeries(300);

        BacktestConfig cfg = new BacktestConfig(
                "TREND_PULLBACK", "TESTUSDT", "15m", TradingMode.SPOT,
                Instant.ofEpochMilli(START), Instant.ofEpochMilli(START + 300 * STEP),
                BigDecimal.valueOf(10_000), BigDecimal.valueOf(1),
                BigDecimal.valueOf(0.1), BigDecimal.valueOf(0.05), 1,
                BacktestExecutionModel.NEXT_CANDLE_OPEN, BacktestSameCandlePolicy.SL_FIRST,
                EASY_PARAMS);

        BacktestRun run = new BacktestRun();
        BacktestEngine.Result result = BacktestEngine.run(run, cfg, strategy, candles, null);

        assertThat(result.signals()).isNotEmpty();
        assertThat(result.trades()).isNotEmpty();
        assertThat(result.trades())
                .allSatisfy(t -> assertThat(t.getEntryFee()).isGreaterThan(BigDecimal.ZERO));
    }

    // ── Fixtures ────────────────────────────────────────────────────────────

    /**
     * Long uptrend, then a pullback and a breakout three candles before the
     * end — so the engine has a next candle at which to fill the entry.
     */
    private static List<HistoricalCandle> bullishPullbackSeries(int length) {
        double[] closes = new double[length];
        int breakIdx = length - 3;
        int pullStart = breakIdx - 9;
        for (int i = 0; i < pullStart; i++) closes[i] = 100 + i * 0.05;
        double peak = closes[pullStart - 1];
        for (int i = pullStart; i < breakIdx; i++) {
            closes[i] = peak - (i - pullStart + 1) * 0.15;
        }
        closes[breakIdx] = peak + 1.0;              // breakout above the pre-pullback high
        closes[breakIdx + 1] = closes[breakIdx] + 0.1;
        closes[breakIdx + 2] = closes[breakIdx + 1] + 0.1;
        return toCandles(closes, 0.05, 100, START);
    }

    /**
     * Uptrend → pullback → breakout (index len-5) → sharp new pullback
     * (len-4) → second breakout (len-3), with two trailing candles. Yields two
     * independently-eligible setups two candles apart.
     */
    private static List<HistoricalCandle> twoBreakoutSeries(int length) {
        double[] closes = new double[length];
        int a = length - 5;
        int pullStart = a - 9;
        for (int i = 0; i < pullStart; i++) closes[i] = 100 + i * 0.05;
        double peak = closes[pullStart - 1];
        for (int i = pullStart; i < a; i++) closes[i] = peak - (i - pullStart + 1) * 0.15;
        closes[a] = peak + 1.0;
        closes[a + 1] = peak - 0.8;
        closes[a + 2] = peak + 1.2;
        closes[a + 3] = peak + 1.3;
        closes[a + 4] = peak + 1.4;
        return toCandles(closes, 0.05, 100, START);
    }

    private static List<HistoricalCandle> toCandles(double[] closes, double spread,
            double volume, long start) {
        List<HistoricalCandle> out = new ArrayList<>();
        for (int i = 0; i < closes.length; i++) {
            double open = i == 0 ? closes[0] : closes[i - 1];
            double high = Math.max(open, closes[i]) + spread;
            double low = Math.min(open, closes[i]) - spread;
            long t = start + i * STEP;
            out.add(new HistoricalCandle(Instant.ofEpochMilli(t),
                    BigDecimal.valueOf(open), BigDecimal.valueOf(high), BigDecimal.valueOf(low),
                    BigDecimal.valueOf(closes[i]), BigDecimal.valueOf(volume),
                    Instant.ofEpochMilli(t + STEP - 1)));
        }
        return out;
    }
}
