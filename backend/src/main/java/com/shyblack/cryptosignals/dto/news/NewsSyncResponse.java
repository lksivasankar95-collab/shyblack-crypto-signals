package com.shyblack.cryptosignals.dto.news;

import java.time.Instant;

public record NewsSyncResponse(
		String provider,
		int articlesFetched,
		int inserted,
		int duplicates,
		int rejected,
		int failed,
		Instant startedAt,
		Instant finishedAt,
		long durationMs
) {
}