package com.shyblack.cryptosignals.service;

import com.shyblack.cryptosignals.config.MarketProperties;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.exception.MarketUpstreamException;
import com.shyblack.cryptosignals.market.BinanceFuturesRestClient;
import com.shyblack.cryptosignals.market.BinanceRestClient;
import com.shyblack.cryptosignals.market.MarketBook;
import com.shyblack.cryptosignals.market.MarketTicker;
import com.shyblack.cryptosignals.market.UsdtSymbolDirectory;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.markets.catalog-enabled", havingValue = "true", matchIfMissing = true)
public class MarketCatalogService {

	private static final Logger log = LoggerFactory.getLogger(MarketCatalogService.class);

	private final MarketBook book;
	private final BinanceRestClient spotRest;
	private final BinanceFuturesRestClient futuresRest;
	private final MarketProperties properties;

	public MarketCatalogService(
			MarketBook book,
			BinanceRestClient spotRest,
			BinanceFuturesRestClient futuresRest,
			MarketProperties properties
	) {
		this.book = book;
		this.spotRest = spotRest;
		this.futuresRest = futuresRest;
		this.properties = properties;
	}

	@PostConstruct
	public void init() {
		refreshAll();
	}

	@Scheduled(
			initialDelayString = "${app.markets.exchange-info-refresh-ms:3600000}",
			fixedDelayString = "${app.markets.exchange-info-refresh-ms:3600000}"
	)
	public void scheduledRefresh() {
		refreshAll();
	}

	public void refreshAll() {
		refreshSpot();
		refreshFutures();
	}

	private void refreshSpot() {
		try {
			Map<String, String> names = spotRest.loadTradableUsdtSpotNames();
			apply(TradingMode.SPOT, names, () -> spotRest.loadSpot24hTickers(book.spotSymbols()));
		} catch (MarketUpstreamException ex) {
			log.warn("Spot catalog refresh failed (keeping previous snapshot): {}", ex.getMessage());
		}
	}

	private void refreshFutures() {
		try {
			Map<String, String> names = futuresRest.loadTradableUsdtMFuturesNames();
			apply(TradingMode.FUTURES, names, () -> futuresRest.loadFutures24hTickers(book.futuresSymbols()));
		} catch (MarketUpstreamException ex) {
			log.warn("Futures catalog refresh failed (keeping previous snapshot): {}", ex.getMessage());
		}
	}

	private void apply(TradingMode mode, Map<String, String> names, TickerLoader tickerLoader) {
		UsdtSymbolDirectory directory = book.symbols(mode);
		directory.replace(names);
		book.tickers(mode).retainOnly(directory.snapshot());
		List<MarketTicker> tickers = tickerLoader.load();
		book.tickers(mode).upsertAll(tickers);
		log.info("Refreshed {} {} universe: {} symbols, {} 24h tickers (refresh every {} ms)",
				mode,
				properties.quoteAssetOrUsdt(),
				names.size(),
				tickers.size(),
				properties.exchangeInfoRefreshMs());
	}

	@FunctionalInterface
	private interface TickerLoader {
		List<MarketTicker> load();
	}
}
