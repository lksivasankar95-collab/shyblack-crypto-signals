package com.shyblack.cryptosignals.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.markets")
public record MarketProperties(
		boolean websocketEnabled,
		String restBaseUrl,
		String streamUrl,
		List<String> symbols
) {
}
