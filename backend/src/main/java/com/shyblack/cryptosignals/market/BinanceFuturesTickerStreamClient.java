package com.shyblack.cryptosignals.market;

import com.shyblack.cryptosignals.config.MarketProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.markets.websocket-enabled", havingValue = "true", matchIfMissing = true)
public class BinanceFuturesTickerStreamClient implements AutoCloseable {

	private final BinanceArrayTickerStreamClient delegate;

	public BinanceFuturesTickerStreamClient(MarketProperties properties, MarketBook book) {
		this.delegate = new BinanceArrayTickerStreamClient(
				"futures",
				properties.futuresStreamUrl(),
				book.futuresTickers(),
				book.futuresSymbols()
		);
	}

	@EventListener(ApplicationReadyEvent.class)
	@Order(100)
	public void onReady() {
		delegate.start();
	}

	@PreDestroy
	@Override
	public void close() {
		delegate.close();
	}
}
