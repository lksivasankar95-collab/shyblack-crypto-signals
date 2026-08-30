package com.shyblack.cryptosignals.service;

import com.shyblack.cryptosignals.dto.market.KlineResponse;
import com.shyblack.cryptosignals.market.BinanceFuturesRestClient;
import com.shyblack.cryptosignals.market.MarketBook;
import com.shyblack.cryptosignals.market.MarketTicker;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BinanceFuturesMarketService {

	private final MarketBook book;
	private final BinanceFuturesRestClient restClient;

	public List<MarketTicker> snapshot() {
		return book.futuresTickers().snapshot();
	}

	public List<MarketTicker> gainers(int limit) {
		return book.futuresTickers().gainers(limit);
	}

	public List<MarketTicker> losers(int limit) {
		return book.futuresTickers().losers(limit);
	}

	public List<KlineResponse> klines(String symbol, String interval, int limit) {
		return restClient.klines(symbol, interval, limit);
	}
}
