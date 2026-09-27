package com.shyblack.cryptosignals.service.backtest.historical;

import com.shyblack.cryptosignals.dto.market.KlineResponse;
import com.shyblack.cryptosignals.exception.MarketUpstreamException;
import com.shyblack.cryptosignals.market.BinanceRestClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Historical candles from Binance's public REST, using the existing
 * {@link BinanceRestClient#klines(String, String, Long, Long, int)} primitive.
 *
 * Binance caps a single klines response, so a requested range may span many
 * pages. This provider walks the range forward with an inclusive
 * {@code [startTime, endTime]} window per page, advancing past the newest
 * candle of the previous page each iteration:
 *
 * <ul>
 *   <li>pages are requested until the returned page is shorter than the
 *       per-request maximum (an incomplete final page) or the range is
 *       exhausted;</li>
 *   <li>candles are keyed by {@code openTime} in a {@link TreeMap}, which
 *       deduplicates page-boundary overlap AND guarantees chronological
 *       ordering;</li>
 *   <li>only candles with {@code openTime in [start, end)} are kept, so no
 *       future candle can leak in (no look-ahead);</li>
 *   <li>an upstream failure is allowed to propagate — a partially fetched
 *       range is never returned silently;</li>
 *   <li>a page that fails to advance the cursor, or exceeding the page
 *       budget, aborts the load instead of looping forever.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class BinanceHistoricalDataProvider implements HistoricalMarketDataProvider {

	private static final Logger log = LoggerFactory.getLogger(BinanceHistoricalDataProvider.class);

	private static final int MAX_CANDLES_PER_REQUEST = BinanceRestClient.MAX_KLINES_PER_REQUEST;
	/** Absolute safety valve against runaway pagination. */
	private static final int MAX_PAGES = 100_000;

	private final BinanceRestClient restClient;

	@Override
	public List<HistoricalCandle> load(String symbol, String timeframe, Instant start, Instant end) {
		if (start == null || end == null || !start.isBefore(end)) {
			return List.of();
		}
		long step = intervalMillis(timeframe);
		long startMs = start.toEpochMilli();
		long endExclusive = end.toEpochMilli();

		TreeMap<Long, HistoricalCandle> byOpen = new TreeMap<>();
		long from = startMs;
		int pages = 0;

		while (from < endExclusive) {
			if (++pages > MAX_PAGES) {
				throw new MarketUpstreamException("Historical pagination exceeded " + MAX_PAGES
						+ " pages for " + symbol);
			}
			long pageEnd = Math.min(from + (long) MAX_CANDLES_PER_REQUEST * step - 1,
					endExclusive - 1);
			List<KlineResponse> page = restClient.klines(symbol, timeframe, from, pageEnd,
					MAX_CANDLES_PER_REQUEST);
			if (page == null || page.isEmpty()) {
				// No more data at/after the cursor within the requested range.
				break;
			}

			long lastOpen = Long.MIN_VALUE;
			for (KlineResponse k : page) {
				long t = k.openTime();
				if (t > lastOpen) lastOpen = t;
				if (t < startMs || t >= endExclusive) continue;
				HistoricalCandle c = toCandle(k);
				if (c != null && c.isValid()) {
					byOpen.putIfAbsent(t, c); // dedup page-boundary overlap
				}
			}

			if (page.size() < MAX_CANDLES_PER_REQUEST) {
				break; // incomplete final page -> end of available data
			}

			// Next page starts at the candle AFTER the newest one just seen
			// (grid-aligned), so a full page never re-requests its own boundary.
			long next = lastOpen + step;
			if (next <= from) {
				throw new MarketUpstreamException("Historical pagination made no progress for "
						+ symbol + " (upstream returned a non-advancing page)");
			}
			from = next;
		}

		List<HistoricalCandle> out = new ArrayList<>(byOpen.values()); // TreeMap -> ascending
		log.info("[BinanceHist] {} {} loaded {} candles in [{}, {}] ({} pages)",
				symbol, timeframe, out.size(), start, end, pages);
		return out;
	}

	private static HistoricalCandle toCandle(KlineResponse k) {
		return new HistoricalCandle(
				Instant.ofEpochMilli(k.openTime()),
				k.open(), k.high(), k.low(), k.close(), k.volume(),
				Instant.ofEpochMilli(k.closeTime()));
	}

	/** Binance interval string -> duration millis (m/h/d/w, case-insensitive). */
	static long intervalMillis(String timeframe) {
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
		if (amount <= 0) {
			throw new IllegalArgumentException("Unsupported timeframe: " + timeframe);
		}
		long unitMillis = switch (unit) {
			case 'm' -> 60_000L;
			case 'h' -> 3_600_000L;
			case 'd' -> 86_400_000L;
			case 'w' -> 604_800_000L;
			default -> throw new IllegalArgumentException("Unsupported timeframe: " + timeframe);
		};
		return amount * unitMillis;
	}
}
