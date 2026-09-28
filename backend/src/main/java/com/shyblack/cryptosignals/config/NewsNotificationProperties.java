package com.shyblack.cryptosignals.config;

import com.shyblack.cryptosignals.entity.enums.NewsImpact;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Notification eligibility policy for newly ingested news articles.
 * Low-priority articles are still persisted; this only gates notifications.
 */
@ConfigurationProperties(prefix = "app.news-notification")
public record NewsNotificationProperties(
		boolean enabled,
		NewsImpact minImpact,
		double minScore
) {
	public NewsNotificationProperties {
		if (minImpact == null) minImpact = NewsImpact.HIGH;
		if (minScore < 0) minScore = 0;
	}
}
