package com.shyblack.cryptosignals.news.provider;

import java.time.Instant;
import java.util.List;

/**
 * Neutral, provider-agnostic representation of a fetched news article
 * before normalization/classification.
 */
public record RawNewsArticle(
		String externalId,
		String url,
		String title,
		String description,
		String content,
		String imageUrl,
		String author,
		String sourceName,
		Instant publishedAt,
		String language,
		List<String> categories
) {
}