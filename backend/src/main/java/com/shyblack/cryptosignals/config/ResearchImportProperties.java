package com.shyblack.cryptosignals.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bulk research-data import settings. Import reads LOCAL files only — no live
 * Binance API is required for the historical backfill.
 */
@ConfigurationProperties(prefix = "app.research.import")
public record ResearchImportProperties(
		boolean enabled,
		String root,
		int batchSize,
		String manifest,
		int maxRejectsPerFile
) {
	public ResearchImportProperties {
		if (root == null || root.isBlank()) root = "research-import";
		if (batchSize <= 0) batchSize = 1000;
		if (manifest == null) manifest = "";
		if (maxRejectsPerFile <= 0) maxRejectsPerFile = 200;
	}
}
