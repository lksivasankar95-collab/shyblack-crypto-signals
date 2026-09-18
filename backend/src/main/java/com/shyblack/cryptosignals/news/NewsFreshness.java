package com.shyblack.cryptosignals.news;

import java.time.Duration;
import java.time.Instant;

/**
 * Freshness weight based on the configured relevance window.
 * <p>A freshly published article scores ~1.0; relevance decays linearly to 0
 * as the article ages past the window. Missing timestamps score lowest since
 * their freshness cannot be verified.</p>
 */
public final class NewsFreshness {

	/** Freshness of a known age in hours; null means unknown. */
	public static double weight(Instant publishedAt, Instant now, int windowHours) {
		if (publishedAt == null || now == null) {
			return 0.0;
		}
		long ageMillis = Duration.between(publishedAt, now).toMillis();
		if (ageMillis <= 0) {
			return 1.0;
		}
		double windowHoursEffective = windowHours <= 0 ? 48 : windowHours;
		double ageHours = ageMillis / 3_600_000.0;
		if (ageHours >= windowHoursEffective) {
			return 0.0;
		}
		return round2(1.0 - (ageHours / windowHoursEffective));
	}

	static double round2(double value) {
		return Math.round(value * 100.0) / 100.0;
	}
}