package com.shyblack.cryptosignals.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bounds for the official historical event collector. Every limit is a safety
 * bound so a collection job can never run unbounded.
 */
@ConfigurationProperties(prefix = "app.research.events")
public record ResearchEventProperties(
		int maxRetries,
		long retryBackoffMs,
		int collectionTimeoutMinutes,
		int maxPages,
		int maxRecords
) {
	public ResearchEventProperties {
		if (maxRetries < 0) maxRetries = 2;
		if (retryBackoffMs <= 0) retryBackoffMs = 1000;
		if (collectionTimeoutMinutes <= 0) collectionTimeoutMinutes = 10;
		if (maxPages <= 0) maxPages = 200;
		if (maxRecords <= 0) maxRecords = 20000;
	}
}
