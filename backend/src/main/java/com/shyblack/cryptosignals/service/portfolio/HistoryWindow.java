package com.shyblack.cryptosignals.service.portfolio;

import com.shyblack.cryptosignals.exception.BadRequestException;
import java.time.Duration;
import java.time.Instant;

/**
 * Explicit, bounded contract for a history query.
 *
 * <p>History endpoints are time-windowed and can be very large, so a window is always resolved
 * explicitly rather than implied:
 * <ul>
 *   <li>a default window of {@link #DEFAULT_WINDOW};</li>
 *   <li>a maximum span of {@link #MAX_WINDOW}, enforced, so an unbounded "all history" request is
 *       rejected rather than attempted;</li>
 *   <li>a maximum record count of {@link #MAX_RECORDS}.</li>
 * </ul>
 *
 * <p>The resolved window is returned with the results so a caller can always tell exactly which
 * period the data covers, and whether it was truncated.
 */
public record HistoryWindow(Instant from, Instant to, int limit) {

	public static final Duration DEFAULT_WINDOW = Duration.ofDays(7);

	/** Hard ceiling on a single request; a wider range must be paged by the caller. */
	public static final Duration MAX_WINDOW = Duration.ofDays(90);

	public static final int MAX_RECORDS = 1000;

	public static final int DEFAULT_RECORDS = 200;

	/**
	 * Resolves the requested window, applying the default and enforcing the ceiling.
	 *
	 * @param from requested inclusive start, or null for the default window
	 * @param to requested exclusive end, or null for now
	 * @param limit requested record cap, or null for the default
	 */
	public static HistoryWindow resolve(Instant from, Instant to, Integer limit) {
		Instant end = to == null ? Instant.now() : to;
		Instant start = from == null ? end.minus(DEFAULT_WINDOW) : from;

		if (!start.isBefore(end)) {
			throw new BadRequestException("History start must be before end");
		}
		if (Duration.between(start, end).compareTo(MAX_WINDOW) > 0) {
			throw new BadRequestException(
					"History range must not exceed " + MAX_WINDOW.toDays() + " days; page the history instead");
		}
		int cap = limit == null ? DEFAULT_RECORDS : limit;
		if (cap <= 0) {
			throw new BadRequestException("History limit must be positive");
		}
		if (cap > MAX_RECORDS) {
			throw new BadRequestException(
					"History limit must not exceed " + MAX_RECORDS + " records");
		}
		return new HistoryWindow(start, end, cap);
	}
}