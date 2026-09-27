package com.shyblack.cryptosignals.signal.pullback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shyblack.cryptosignals.dto.market.KlineResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimeframeAggregatorTest {

    private static final long MIN = 60_000L;

    @Test
    void durationMillis_parsesSupportedUnits() {
        assertThat(TimeframeAggregator.durationMillis("1m")).isEqualTo(MIN);
        assertThat(TimeframeAggregator.durationMillis("15m")).isEqualTo(15 * MIN);
        assertThat(TimeframeAggregator.durationMillis("1h")).isEqualTo(60 * MIN);
        assertThat(TimeframeAggregator.durationMillis("4h")).isEqualTo(240 * MIN);
        assertThat(TimeframeAggregator.durationMillis("1d")).isEqualTo(1440 * MIN);
    }

    @Test
    void durationMillis_isCaseInsensitiveAndRejectsUnknown() {
        assertThat(TimeframeAggregator.durationMillis("15M")).isEqualTo(15 * MIN);
        assertThat(TimeframeAggregator.durationMillis("1H")).isEqualTo(60 * MIN);
        assertThatThrownBy(() -> TimeframeAggregator.durationMillis("1x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TimeframeAggregator.durationMillis("nope"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ratio_isExactMultiple() {
        assertThat(TimeframeAggregator.ratio("1h", "15m")).isEqualTo(4);
        assertThat(TimeframeAggregator.ratio("4h", "1h")).isEqualTo(4);
        assertThat(TimeframeAggregator.ratio("1d", "4h")).isEqualTo(6);
        assertThatThrownBy(() -> TimeframeAggregator.ratio("1h", "45m"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aggregate_buildsClosedHtfCandles() {
        List<KlineResponse> entry = new ArrayList<>();
        long base = 1_700_000_000_000L / 3_600_000L * 3_600_000L; // hour-aligned
        for (int i = 0; i < 8; i++) {
            entry.add(c(base + i * 15 * MIN, 10 + i, 11 + i, 9 + i, 10.5 + i, 100 + i));
        }
        List<KlineResponse> htf = TimeframeAggregator.aggregate(entry, "1h", "15m");
        assertThat(htf).hasSize(2);
        KlineResponse first = htf.get(0);
        assertThat(first.openTime()).isEqualTo(base);
        assertThat(first.open().doubleValue()).isEqualTo(10);
        assertThat(first.high().doubleValue()).isEqualTo(14);   // max of 11..14
        assertThat(first.low().doubleValue()).isEqualTo(9);     // min of 9..12
        assertThat(first.close().doubleValue()).isEqualTo(13.5); // close of 00:45
        assertThat(first.volume().doubleValue()).isEqualTo(100 + 101 + 102 + 103);
    }

    @Test
    void aggregate_dropsTrailingPartialBucket() {
        List<KlineResponse> entry = new ArrayList<>();
        for (int i = 0; i < 6; i++) { // one full hour + 2 candles of next
            entry.add(c(1_700_000_000_000L + i * 15 * MIN, 10, 11, 9, 10, 1));
        }
        List<KlineResponse> htf = TimeframeAggregator.aggregate(entry, "1h", "15m");
        assertThat(htf).hasSize(1);
    }

    @Test
    void aggregate_dropsTruncatedLeadingBucket() {
        List<KlineResponse> entry = new ArrayList<>();
        long hour = 1_700_000_000_000L / 3_600_000L * 3_600_000L;
        // 3 candles in hour0 (starts mid-hour), 4 in hour1, 1 in hour2
        entry.add(c(hour + 15 * MIN, 10, 11, 9, 10, 1));
        entry.add(c(hour + 30 * MIN, 10, 11, 9, 10, 1));
        entry.add(c(hour + 45 * MIN, 10, 11, 9, 10, 1));
        for (int i = 0; i < 4; i++) entry.add(c(hour + 60 * MIN + i * 15 * MIN, 20, 21, 19, 20, 1));
        entry.add(c(hour + 120 * MIN, 30, 31, 29, 30, 1));

        List<KlineResponse> htf = TimeframeAggregator.aggregate(entry, "1h", "15m");
        assertThat(htf).hasSize(1);
        assertThat(htf.get(0).openTime()).isEqualTo(hour + 60 * MIN);
        assertThat(htf.get(0).open().doubleValue()).isEqualTo(20);
    }

    private static KlineResponse c(long openTime, double open, double high, double low,
            double close, double volume) {
        return new KlineResponse(openTime,
                BigDecimal.valueOf(open), BigDecimal.valueOf(high), BigDecimal.valueOf(low),
                BigDecimal.valueOf(close), BigDecimal.valueOf(volume), openTime + 15 * MIN - 1);
    }
}
