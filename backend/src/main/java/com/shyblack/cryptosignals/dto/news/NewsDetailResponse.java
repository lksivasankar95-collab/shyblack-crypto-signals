package com.shyblack.cryptosignals.dto.news;

import com.shyblack.cryptosignals.entity.enums.NewsCategory;
import com.shyblack.cryptosignals.entity.enums.NewsImpact;
import com.shyblack.cryptosignals.entity.enums.NewsProcessingStatus;
import com.shyblack.cryptosignals.entity.enums.NewsSentiment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NewsDetailResponse(
		UUID id,
		String sourceName,
		String sourceUrl,
		String title,
		String summary,
		String content,
		String imageUrl,
		String author,
		Instant publishedAt,
		Instant fetchedAt,
		NewsCategory category,
		NewsSentiment sentiment,
		Double sentimentScore,
		NewsImpact impactLevel,
		Double impactScore,
		Double newsScore,
		Double confidenceScore,
		NewsProcessingStatus processingStatus,
		List<NewsAssetResponse> assets
) {
}