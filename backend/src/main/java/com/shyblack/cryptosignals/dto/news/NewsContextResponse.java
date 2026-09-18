package com.shyblack.cryptosignals.dto.news;

import com.shyblack.cryptosignals.entity.enums.NewsImpact;
import com.shyblack.cryptosignals.entity.enums.NewsSentiment;
import java.time.Instant;

/**
 * Aggregated news intelligence snapshot for a single asset,
 * consumed by the Signal Generation module as part of its signal inputs.
 */
public record NewsContextResponse(
		String symbol,
		Double newsScore,
		NewsSentiment sentiment,
		Double sentimentScore,
		NewsImpact impactLevel,
		Double confidenceScore,
		int articleCount,
		NewsResponse latestHighImpactNews,
		Instant calculatedAt
) {
}