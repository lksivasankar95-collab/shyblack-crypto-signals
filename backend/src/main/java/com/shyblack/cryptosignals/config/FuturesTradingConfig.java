package com.shyblack.cryptosignals.config;

import com.shyblack.cryptosignals.exchange.futures.FuturesExchangeAdapter;
import com.shyblack.cryptosignals.exchange.futures.binance.BinanceFuturesLiveAdapter;
import com.shyblack.cryptosignals.exchange.futures.mock.MockFuturesExchangeAdapter;
import com.shyblack.cryptosignals.security.ExchangeCredentialEncryptor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the {@link FuturesExchangeAdapter} impl based on
 * {@code app.futures-trading.mode}. Default is MOCK.
 */
@Configuration
@RequiredArgsConstructor
public class FuturesTradingConfig {

	private static final Logger log = LoggerFactory.getLogger(FuturesTradingConfig.class);

	private final FuturesTradingProperties props;
	private final ExchangeCredentialEncryptor encryptor;

	@Bean
	public FuturesExchangeAdapter binanceFuturesAdapter() {
		if (props.mode() == FuturesTradingProperties.Mode.EXCHANGE) {
			log.warn("[FUTURES] EXCHANGE mode active — signed Binance Futures calls enabled (base={})",
					props.restBaseUrl());
			return new BinanceFuturesLiveAdapter(props, encryptor);
		}
		log.info("[FUTURES] MOCK mode — no real Futures traffic will be produced");
		return new MockFuturesExchangeAdapter();
	}
}
