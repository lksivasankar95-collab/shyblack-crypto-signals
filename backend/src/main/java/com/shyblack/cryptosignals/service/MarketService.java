package com.shyblack.cryptosignals.service;

import com.shyblack.cryptosignals.dto.market.KlineResponse;
import com.shyblack.cryptosignals.dto.market.MarketListResponse;
import com.shyblack.cryptosignals.dto.market.MarketTickerResponse;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.exception.BadRequestException;
import com.shyblack.cryptosignals.exception.ResourceNotFoundException;
import com.shyblack.cryptosignals.market.BinanceRestClient;
import com.shyblack.cryptosignals.market.MarketBook;
import com.shyblack.cryptosignals.market.MarketTicker;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MarketService {

	private static final int LEADERBOARD_LIMIT = 10;

	private final MarketBook book;
	private final BinanceRestClient binanceRestClient;
	private final BinanceFuturesMarketService futuresMarketService;
	private final BinanceOptionsMarketService optionsMarketService;

	public MarketListResponse list(String modeParam) {
		TradingMode mode = parseMode(modeParam);
		if (mode == TradingMode.OPTIONS) {
			optionsMarketService.list();
			return MarketListResponse.optionsUnavailable();
		}
		return MarketListResponse.of(mode, book.tickers(mode).snapshot().stream().map(this::toDto).toList());
	}

	public MarketTickerResponse get(String symbol, String modeParam) {
		TradingMode mode = parseMode(modeParam);
		if (mode == TradingMode.OPTIONS) {
			throw new ResourceNotFoundException(BinanceOptionsMarketService.UNAVAILABLE_MESSAGE);
		}
		return book.tickers(mode).get(symbol)
				.map(this::toDto)
				.orElseThrow(() -> new ResourceNotFoundException("No live ticker for " + symbol + " (" + mode + ")"));
	}

	public MarketListResponse gainers(String modeParam) {
		TradingMode mode = parseMode(modeParam);
		if (mode == TradingMode.OPTIONS) {
			optionsMarketService.list();
			return MarketListResponse.optionsUnavailable();
		}
		return MarketListResponse.of(mode, book.tickers(mode).gainers(LEADERBOARD_LIMIT).stream().map(this::toDto).toList());
	}

	public MarketListResponse losers(String modeParam) {
		TradingMode mode = parseMode(modeParam);
		if (mode == TradingMode.OPTIONS) {
			optionsMarketService.list();
			return MarketListResponse.optionsUnavailable();
		}
		return MarketListResponse.of(mode, book.tickers(mode).losers(LEADERBOARD_LIMIT).stream().map(this::toDto).toList());
	}

	public List<KlineResponse> klines(String symbol, String interval, int limit, String modeParam) {
		TradingMode mode = parseMode(modeParam);
		int capped = Math.min(Math.max(limit, 1), 1000);
		return switch (mode) {
			case SPOT -> binanceRestClient.klines(symbol, interval, capped);
			case FUTURES -> futuresMarketService.klines(symbol, interval, capped);
			case OPTIONS -> throw new BadRequestException(BinanceOptionsMarketService.UNAVAILABLE_MESSAGE);
		};
	}

	public static TradingMode parseMode(String raw) {
		if (raw == null || raw.isBlank()) {
			return TradingMode.SPOT;
		}
		try {
			return TradingMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			throw new BadRequestException("Invalid mode '" + raw + "'. Use SPOT, FUTURES, or OPTIONS.");
		}
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
