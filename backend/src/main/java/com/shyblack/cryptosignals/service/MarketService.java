package com.shyblack.cryptosignals.service;

import com.shyblack.cryptosignals.dto.market.KlineResponse;
import com.shyblack.cryptosignals.dto.market.MarketTickerResponse;
import com.shyblack.cryptosignals.exception.ResourceNotFoundException;
import com.shyblack.cryptosignals.market.BinanceRestClient;
import com.shyblack.cryptosignals.market.MarketTicker;
import com.shyblack.cryptosignals.market.MarketTickerStore;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MarketService {

	private static final int LEADERBOARD_LIMIT = 10;

	private final MarketTickerStore store;
	private final BinanceRestClient binanceRestClient;

	public List<MarketTickerResponse> list() {
		return store.snapshot().stream().map(this::toDto).toList();
	}

	public MarketTickerResponse get(String symbol) {
		return store.get(symbol)
				.map(this::toDto)
				.orElseThrow(() -> new ResourceNotFoundException("No live ticker for " + symbol));
	}

	public List<MarketTickerResponse> gainers() {
		return store.gainers(LEADERBOARD_LIMIT).stream().map(this::toDto).toList();
	}

	public List<MarketTickerResponse> losers() {
		return store.losers(LEADERBOARD_LIMIT).stream().map(this::toDto).toList();
	}

	public List<KlineResponse> klines(String symbol, String interval, int limit) {
		return binanceRestClient.klines(symbol, interval, Math.min(Math.max(limit, 1), 1000));
	}

	private MarketTickerResponse toDto(MarketTicker ticker) {
		return new MarketTickerResponse(
				ticker.symbol(),
				ticker.name(),
				ticker.price(),
				ticker.change24h(),
				ticker.changePercent24h(),
				ticker.volume24h(),
				ticker.high24h(),
				ticker.low24h(),
				ticker.updatedAt()
		);
	}
}
