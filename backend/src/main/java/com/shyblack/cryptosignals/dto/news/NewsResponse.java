package com.shyblack.cryptosignals.dto.news;

import com.shyblack.cryptosignals.entity.enums.NewsCategory;
import com.shyblack.cryptosignals.entity.enums.NewsImpact;
import com.shyblack.cryptosignals.entity.enums.NewsSentiment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NewsResponse(
		UUID id,
		String sourceName,
		String sourceUrl,
		String title,
		String summary,
		String imageUrl,
		String author,
		Instant publishedAt,
		NewsCategory category,
		NewsSentiment sentiment,
		Double sentimentScore,
		NewsImpact impactLevel,
		Double impactScore,
		Double newsScore,
		Double confidenceScore,
		List<NewsAssetResponse> assets
) {
}