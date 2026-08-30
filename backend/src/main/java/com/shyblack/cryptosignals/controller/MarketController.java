package com.shyblack.cryptosignals.controller;

import com.shyblack.cryptosignals.dto.market.KlineResponse;
import com.shyblack.cryptosignals.dto.market.MarketListResponse;
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
@Tag(name = "Markets", description = "Live USDT markets from in-memory Spot and USDT-M Futures stores "
		+ "(populated from Binance exchangeInfo + !ticker@arr). "
		+ "Filter with ?mode=SPOT (default), FUTURES, or OPTIONS. "
		+ "Real-time stream: ws://localhost:8080/ws/markets?mode=SPOT (not shown in Swagger).")
public class MarketController {

	private static final String MODE_DESCRIPTION = "SPOT (default), FUTURES, or OPTIONS. "
			+ "Matches the Settings trading-mode filter. OPTIONS returns an empty list until a real Options feed is wired.";

	private final MarketService marketService;

	@Operation(summary = "Snapshot of all tracked coins for a trading mode")
	@GetMapping
	public MarketListResponse list(
			@Parameter(description = MODE_DESCRIPTION, example = "SPOT")
			@RequestParam(required = false) String mode
	) {
		return marketService.list(mode);
	}

	@Operation(summary = "Top gainers by 24h percent change")
	@GetMapping("/gainers")
	public MarketListResponse gainers(
			@Parameter(description = MODE_DESCRIPTION, example = "SPOT")
			@RequestParam(required = false) String mode
	) {
		return marketService.gainers(mode);
	}

	@Operation(summary = "Top losers by 24h percent change")
	@GetMapping("/losers")
	public MarketListResponse losers(
			@Parameter(description = MODE_DESCRIPTION, example = "SPOT")
			@RequestParam(required = false) String mode
	) {
		return marketService.losers(mode);
	}

	@Operation(summary = "Latest ticker for one symbol")
	@GetMapping("/{symbol}")
	public MarketTickerResponse get(
			@PathVariable String symbol,
			@Parameter(description = MODE_DESCRIPTION, example = "SPOT")
			@RequestParam(required = false) String mode
	) {
		return marketService.get(symbol, mode);
	}

	@Operation(summary = "Candlestick/OHLCV data from Binance REST")
	@GetMapping("/{symbol}/klines")
	public List<KlineResponse> klines(
			@PathVariable String symbol,
			@Parameter(description = "Binance interval, e.g. 1m, 5m, 1h, 4h, 1d")
			@RequestParam(defaultValue = "1h") String interval,
			@RequestParam(defaultValue = "100") int limit,
			@Parameter(description = MODE_DESCRIPTION, example = "SPOT")
			@RequestParam(required = false) String mode
	) {
		return marketService.klines(symbol, interval, limit, mode);
	}
}
