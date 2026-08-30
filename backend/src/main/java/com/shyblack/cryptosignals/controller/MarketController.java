package com.shyblack.cryptosignals.controller;

import com.shyblack.cryptosignals.dto.market.KlineResponse;
import com.shyblack.cryptosignals.dto.market.MarketTickerResponse;
import com.shyblack.cryptosignals.service.MarketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/markets")
@RequiredArgsConstructor
@SecurityRequirements
@Tag(name = "Markets", description = "Live coin prices from the in-memory Binance ticker store. "
		+ "Real-time stream: ws://localhost:8080/ws/markets (not shown in Swagger). "
		+ "Test with: websocat ws://localhost:8080/ws/markets   or   npx wscat -c ws://localhost:8080/ws/markets")
public class MarketController {

	private final MarketService marketService;

	@Operation(summary = "Snapshot of all tracked coins")
	@GetMapping
	public List<MarketTickerResponse> list() {
		return marketService.list();
	}

	@Operation(summary = "Top gainers by 24h percent change")
	@GetMapping("/gainers")
	public List<MarketTickerResponse> gainers() {
		return marketService.gainers();
	}

	@Operation(summary = "Top losers by 24h percent change")
	@GetMapping("/losers")
	public List<MarketTickerResponse> losers() {
		return marketService.losers();
	}

	@Operation(summary = "Latest ticker for one symbol")
	@GetMapping("/{symbol}")
	public MarketTickerResponse get(@PathVariable String symbol) {
		return marketService.get(symbol);
	}

	@Operation(summary = "Candlestick/OHLCV data from Binance REST")
	@GetMapping("/{symbol}/klines")
	public List<KlineResponse> klines(
			@PathVariable String symbol,
			@Parameter(description = "Binance interval, e.g. 1m, 5m, 1h, 4h, 1d")
			@RequestParam(defaultValue = "1h") String interval,
			@RequestParam(defaultValue = "100") int limit
	) {
		return marketService.klines(symbol, interval, limit);
	}
}
