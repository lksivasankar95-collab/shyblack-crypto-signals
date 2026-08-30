package com.shyblack.cryptosignals.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.markets")
public record MarketProperties(
		boolean websocketEnabled,
		boolean catalogEnabled,
		String restBaseUrl,
		String streamUrl,
		String futuresRestBaseUrl,
		String futuresStreamUrl,
		String quoteAsset,
		long exchangeInfoRefreshMs
) {
	public String quoteAssetOrUsdt() {
		return quoteAsset == null || quoteAsset.isBlank() ? "USDT" : quoteAsset.trim().toUpperCase();
	}
}
