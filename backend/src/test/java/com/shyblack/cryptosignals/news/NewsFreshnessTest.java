package com.shyblack.cryptosignals.news;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class NewsFreshnessTest {

	private static final Instant NOW = Instant.parse("2026-09-17T12:00:00Z");

	@Test
	void freshArticleScoresCloseToOne() {
		double weight = NewsFreshness.weight(NOW.minusSeconds(60), NOW, 48);
		assertThat(weight).isBetween(0.95, 1.0);
	}

	@Test
	void halfWindowScoresHalf() {
		double weight = NewsFreshness.weight(NOW.minusSeconds(24L * 3600), NOW, 48);
		assertThat(weight).isEqualTo(0.5);
	}

	@Test
	void olderThanWindowScoresZero() {
		double weight = NewsFreshness.weight(NOW.minusSeconds(50L * 3600), NOW, 48);
		assertThat(weight).isEqualTo(0.0);
	}

	@Test
	void futureOrUnknownDatesAreHandled() {
		assertThat(NewsFreshness.weight(NOW.plus(java.time.Duration.ofHours(1)), NOW, 48)).isEqualTo(1.0);
		assertThat(NewsFreshness.weight(null, NOW, 48)).isEqualTo(0.0);
		assertThat(NewsFreshness.weight(NOW.minus(java.time.Duration.ofHours(1)), null, 48)).isEqualTo(0.0);
	}
}