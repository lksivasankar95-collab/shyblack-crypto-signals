package com.shyblack.cryptosignals.dto.news;

import java.time.Instant;
import java.util.List;

public record NewsMetaResponse(
		long totalArticles,
		List<String> sources,
		List<String> categories,
		List<String> sentiments,
		List<String> impacts,
		Instant latestFetchedAt
) {
}