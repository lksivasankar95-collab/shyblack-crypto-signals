package com.shyblack.cryptosignals.news;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.enums.NewsImpact;
import org.junit.jupiter.api.Test;

class NewsScorerTest {

	private final NewsScorer scorer = new NewsScorer();

	@Test
	void strongPositiveFreshHighConfidenceScoresHigh() {
		double score = scorer.score(0.9, 0.9, NewsImpact.CRITICAL, 1.0).newsScore();
		assertThat(score).isEqualTo(8.1);
	}

	@Test
	void fullyClampedToBounds() {
		assertThat(scorer.score(1.0, 1.0, NewsImpact.CRITICAL, 1.0).newsScore()).isEqualTo(10.0);
		assertThat(scorer.score(-1.0, 1.0, NewsImpact.CRITICAL, 1.0).newsScore()).isEqualTo(-10.0);
	}

	@Test
	void staleOrLowConfidenceDampensScore() {
		double stale = scorer.score(0.8, 1.0, NewsImpact.HIGH, 0.0).newsScore();
		assertThat(stale).isEqualTo(0.0);

		double lowConfidence = scorer.score(0.8, 0.0, NewsImpact.HIGH, 1.0).newsScore();
		assertThat(lowConfidence).isEqualTo(0.0);
	}

	@Test
	void impactLevelScalesIntensity() {
		double low = scorer.score(0.5, 1.0, NewsImpact.LOW, 1.0).newsScore();
		double high = scorer.score(0.5, 1.0, NewsImpact.HIGH, 1.0).newsScore();
		assertThat(high).isGreaterThan(low);
	}
}