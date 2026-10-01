package com.shyblack.cryptosignals.service.portfolio;

import com.shyblack.cryptosignals.exchange.binance.BinanceUserStreamConnector;
import com.shyblack.cryptosignals.security.ExchangeCredentialEncryptor;
import com.shyblack.cryptosignals.config.FuturesTradingProperties;
import com.shyblack.cryptosignals.config.LiveTradingProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the Binance user-data stream transport.
 *
 * <p>Kept in the portfolio package rather than in the live/futures trading configuration because the
 * stream only synchronises read snapshots and shares no execution concern with either module.
 *
 * <p>The connector opens nothing on construction: a stream exists only for an explicitly started
 * scope, so no connection is ever opened merely because the application started.
 */
@Configuration
public class PortfolioUserStreamConfig {

	@Bean
	public UserStreamConnector binanceUserStreamConnector(
			LiveTradingProperties liveProperties,
			FuturesTradingProperties futuresProperties,
			ExchangeCredentialEncryptor encryptor) {
		return new BinanceUserStreamConnector(liveProperties, futuresProperties, encryptor);
	}
}