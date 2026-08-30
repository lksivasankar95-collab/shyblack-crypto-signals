package com.shyblack.cryptosignals.service;

import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.market.MarketTicker;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Placeholder until Binance Options (or another options feed) is integrated.
 * Binance's public Options API is not fully open like Spot / USDT-M Futures.
 * Do not invent Options prices here.
 */
@Service
public class BinanceOptionsMarketService {

	private static final Logger log = LoggerFactory.getLogger(BinanceOptionsMarketService.class);

	public static final String UNAVAILABLE_MESSAGE = "Options data not yet available";

	public List<MarketTicker> list() {
		log.info("Options markets requested; {} (TradingMode={})", UNAVAILABLE_MESSAGE, TradingMode.OPTIONS);
		return List.of();
	}
}
