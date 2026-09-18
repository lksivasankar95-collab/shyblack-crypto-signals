package com.shyblack.cryptosignals.news;

import com.shyblack.cryptosignals.entity.enums.NewsImpact;
import org.springframework.stereotype.Component;

/**
 * Combines sentiment, confidence, impact and freshness into the final news score.
 * <pre>
 *   newsScore = sentimentScore x impactWeight x confidence x freshness x 10
 * </pre>
 * bounded to [-10, +10] (see {@link NewsScoringConstants}).
 */
@Component
public class NewsScorer {

	public record ScoringResult(double newsScore) {
	}

	public ScoringResult score(
			double sentimentScore,
			double confidence,
			NewsImpact impact,
			double freshness
	) {
		double raw = sentimentScore
				* NewsScoringConstants.impactWeight(impact)
				* clamp01(confidence)
				* clamp01(freshness)
				* NewsScoringConstants.NEWS_SCORE_MAX;
		double bound = Math.max(-NewsScoringConstants.NEWS_SCORE_MAX,
				Math.min(NewsScoringConstants.NEWS_SCORE_MAX, raw));
		return new ScoringResult(Math.round(bound * 100.0) / 100.0);
	}

	private static double clamp01(double value) {
		if (Double.isNaN(value) || value < 0) {
			return 0.0;
		}
		return Math.min(1.0, value);
	}
}