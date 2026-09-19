package com.shyblack.cryptosignals.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.news")
public record NewsProperties(
		boolean enabled,
		String syncCron,
		long requestTimeoutSeconds,
		int pageSize,
		int maxArticlesPerSync,
		int freshnessWindowHours,
		String apiKey,
		List<String> feeds
) {
	/**
	 * True when at least one news feed is configured to pull from.
	 */
	public boolean hasFeeds() {
		return feeds != null && feeds.stream().anyMatch(feed -> feed != null && !feed.isBlank());
	}

	public List<String> activeFeeds() {
		return feeds == null ? List.of() : feeds.stream()
				.filter(feed -> feed != null && !feed.isBlank())
				.toList();
	}
}