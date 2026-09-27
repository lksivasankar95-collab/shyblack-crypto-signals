package com.shyblack.cryptosignals.signal.pullback;

import com.shyblack.cryptosignals.dto.market.KlineResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Derives higher-timeframe candles from a lower-timeframe series.
 *
 * The backtest engine is single-timeframe, so the TREND_PULLBACK strategy
 * builds its HTF view by aggregating the entry-timeframe candles it already
 * has. Aggregation is bucket-aligned to the wall clock and only emits
 * buckets that are fully closed as of the newest supplied candle — this is
 * what keeps it look-ahead safe. For aligned intervals (e.g. 15m → 1h) the
 * aggregated OHLCV is identical to Binance's own higher-timeframe candle, so
 * live (which fetches HTF directly) and backtest agree.
 */
public final class TimeframeAggregator {

    private TimeframeAggregator() {}

    public static long durationMillis(String timeframe) {
        if (timeframe == null || timeframe.isBlank()) {
            throw new IllegalArgumentException("timeframe required");
        }
        String tf = timeframe.trim();
        char unit = Character.toLowerCase(tf.charAt(tf.length() - 1));
        int amount;
        try {
            amount = Integer.parseInt(tf.substring(0, tf.length() - 1));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Unsupported timeframe: " + timeframe);
        }
        if (amount <= 0) throw new IllegalArgumentException("Unsupported timeframe: " + timeframe);
        long unitMillis = switch (unit) {
            case 'm' -> 60_000L;
            case 'h' -> 3_600_000L;
            case 'd' -> 86_400_000L;
            case 'w' -> 604_800_000L;
            default -> throw new IllegalArgumentException("Unsupported timeframe: " + timeframe);
        };
        return amount * unitMillis;
    }

    /** HTF / entry ratio — must be a whole number of entry bars. */
    public static int ratio(String htf, String entry) {
        long h = durationMillis(htf);
        long e = durationMillis(entry);
        if (h % e != 0) {
            throw new IllegalArgumentException(
                    "HTF " + htf + " is not a multiple of entry timeframe " + entry);
        }
        return (int) (h / e);
    }

    /**
     * Aggregate {@code entryCandles} (ascending, oldest first) into HTF
     * candles. Only fully-closed buckets are returned.
     */
    public static List<KlineResponse> aggregate(
            List<KlineResponse> entryCandles, String htf, String entryTimeframe) {
        long htfMillis = durationMillis(htf);
        long entryMillis = durationMillis(entryTimeframe);
        int expected = (int) (htfMillis / entryMillis);
        int n = entryCandles.size();
        List<KlineResponse> out = new ArrayList<>();
        int i = 0;
        while (i < n) {
            long bucketStart = Math.floorDiv(entryCandles.get(i).openTime(), htfMillis) * htfMillis;
            double high = Double.NEGATIVE_INFINITY;
            double low = Double.POSITIVE_INFINITY;
            double volume = 0;
            int count = 0;
            int j = i;
            while (j < n
                    && Math.floorDiv(entryCandles.get(j).openTime(), htfMillis) * htfMillis == bucketStart) {
                KlineResponse c = entryCandles.get(j);
                high = Math.max(high, c.high().doubleValue());
                low = Math.min(low, c.low().doubleValue());
                volume += c.volume().doubleValue();
                count++;
                j++;
            }
            // A bucket is closed when it is full, or when a newer bucket exists
            // AND this bucket is wall-clock aligned (i.e. not a truncated
            // leading bucket from a windowed slice).
            boolean aligned = bucketStart == entryCandles.get(i).openTime();
            boolean closed = (count == expected) || (j < n && aligned);
            if (closed && count > 0) {
                KlineResponse first = entryCandles.get(i);
                KlineResponse last = entryCandles.get(j - 1);
                out.add(new KlineResponse(
                        bucketStart,
                        first.open(),
                        BigDecimal.valueOf(high),
                        BigDecimal.valueOf(low),
                        last.close(),
                        BigDecimal.valueOf(volume),
                        bucketStart + htfMillis - 1));
            }
            i = j;
        }
        return out;
    }

    /** Normalizes for logging/labels, e.g. "1h" → "1H". */
    public static String label(String timeframe) {
        return timeframe == null ? "" : timeframe.trim().toUpperCase(Locale.ROOT);
    }
}
