package com.shyblack.cryptosignals.config;

import com.shyblack.cryptosignals.exchange.ExchangeTradingAdapter;
import com.shyblack.cryptosignals.exchange.binance.BinanceLiveTradingAdapter;
import com.shyblack.cryptosignals.exchange.mock.MockExchangeTradingAdapter;
import com.shyblack.cryptosignals.security.ExchangeCredentialEncryptor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects the {@link ExchangeTradingAdapter} implementation from
 * {@code app.live-trading.mode}. Default is MOCK — real orders require an
 * explicit ops decision to flip this to EXCHANGE.
 */
@Configuration
@RequiredArgsConstructor
public class LiveTradingConfig {

	private static final Logger log = LoggerFactory.getLogger(LiveTradingConfig.class);

	private final LiveTradingProperties props;
	private final ExchangeCredentialEncryptor encryptor;

	@Bean
	public ExchangeTradingAdapter binanceTradingAdapter() {
		if (props.mode() == LiveTradingProperties.Mode.EXCHANGE) {
			log.warn("[LIVE] EXCHANGE mode active — signed Binance calls enabled (base={})",
					props.spotRestBaseUrl());
			return new BinanceLiveTradingAdapter(props, encryptor);
		}
		log.info("[LIVE] MOCK mode — no real exchange traffic will be produced");
		return new MockExchangeTradingAdapter();
	}
}
