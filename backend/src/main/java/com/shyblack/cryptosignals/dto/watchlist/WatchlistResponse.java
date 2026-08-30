package com.shyblack.cryptosignals.dto.watchlist;

import java.time.Instant;
import java.util.UUID;

public record WatchlistResponse(
		UUID id,
		UUID userId,
		String symbol,
		Instant createdAt
) {
}
