package com.shyblack.cryptosignals.news;

import com.shyblack.cryptosignals.entity.enums.NewsImpact;
import java.util.EnumMap;
import java.util.Map;

/**
 * Central numeric configuration for news scoring.
 */
public final class NewsScoringConstants {

	private NewsScoringConstants() {
	}

	/** Sentiment neighborhood that stays NEUTRAL. */
	public static final double SENTIMENT_NEUTRAL_BAND = 0.15;

	/** Impact levels carry an intensity weight used by the scorer. */
	public static final Map<NewsImpact, Double> IMPACT_WEIGHTS = new EnumMap<>(NewsImpact.class);

	static {
		IMPACT_WEIGHTS.put(NewsImpact.LOW, 0.25);
		IMPACT_WEIGHTS.put(NewsImpact.MEDIUM, 0.5);
		IMPACT_WEIGHTS.put(NewsImpact.HIGH, 0.75);
		IMPACT_WEIGHTS.put(NewsImpact.CRITICAL, 1.0);
	}

	/** Classification point thresholds for the impact rubric. */
	public static final double IMPACT_CRITICAL = 7.0;
	public static final double IMPACT_HIGH = 5.0;
	public static final double IMPACT_MEDIUM = 3.0;

	public static final double NEWS_SCORE_MAX = 10.0;

	public static final double SENTIMENT_SCORE_MAX = 1.0;

	public static double impactWeight(NewsImpact impact) {
		return IMPACT_WEIGHTS.getOrDefault(impact, IMPACT_WEIGHTS.get(NewsImpact.LOW));
	}
}