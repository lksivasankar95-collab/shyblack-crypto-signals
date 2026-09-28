package com.shyblack.cryptosignals.service.backtest.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalCandle;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EmaTrendFollowingBacktestStrategyTest {

    private static final long STEP = 900_000L;
    private static final long START = 1_699_999_200_000L;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String EASY = """
            {"htfFastEma":5,"htfSlowEma":15,"trendSlopeLookback":2,"entryFastEma":5,"entrySlowEma":10,
             "rsiPeriod":5,"atrPeriod":5,"volumePeriod":5,"rsiFilterEnabled":false,
             "volumeFilterEnabled":false,"atrFilterEnabled":false,"minimumScore":0}
            """;

    @Test
    void exposesStableIdentity_andWarmup() {
        EmaTrendFollowingBacktestStrategy s = new EmaTrendFollowingBacktestStrategy(MAPPER);
        assertThat(s.id()).isEqualTo("EMA_TREND_FOLLOWING");
        assertThat(s.version()).isEqualTo("v1");
        assertThat(s.warmup()).isGreaterThan(20);
    }

    @Test
    void create_parsesBareAndNestedConfig() {
        EmaTrendFollowingBacktestStrategy base = new EmaTrendFollowingBacktestStrategy(MAPPER);
        assertThat(base.create(EASY)).isNotSameAs(base);
        assertThat(base.create("{\"emaTrendFollowing\":" + EASY + "}")).isNotNull();
        assertThat(base.create(null)).isNotNull();
    }

    @Test
    void evaluate_isDeterministicAndIgnoresCandlesBeyondCurrentIndex() {
        EmaTrendFollowingBacktestStrategy base = new EmaTrendFollowingBacktestStrategy(MAPPER);
        int idx = 399;
        List<HistoricalCandle> history = risingSeries(400);

        Optional<BacktestStrategy.Signal> first = base.create(EASY).evaluate(history, idx);
        Optional<BacktestStrategy.Signal> second = base.create(EASY).evaluate(history, idx);
        assertThat(second).isEqualTo(first);

        List<HistoricalCandle> extended = new ArrayList<>(history);
        double[] crash = new double[100];
        for (int i = 0; i < crash.length; i++) crash[i] = 10;
        extended.addAll(toCandles(crash, 0.05, 100, START + 400L * STEP));
        Optional<BacktestStrategy.Signal> withFuture = base.create(EASY).evaluate(extended, idx);
        assertThat(withFuture).isEqualTo(first);
    }

    private static List<HistoricalCandle> risingSeries(int length) {
        double[] closes = new double[length];
        for (int i = 0; i < length; i++) closes[i] = 100 + i * 0.05;
        return toCandles(closes, 0.05, 100, START);
    }

    private static List<HistoricalCandle> toCandles(double[] closes, double spread, double volume, long start) {
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
