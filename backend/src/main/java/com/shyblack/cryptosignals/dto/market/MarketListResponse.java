package com.shyblack.cryptosignals.dto.market;

import com.shyblack.cryptosignals.entity.enums.TradingMode;
import java.util.List;

public record MarketListResponse(
		TradingMode mode,
		String message,
		int count,
		List<MarketTickerResponse> tickers
) {
	public static MarketListResponse of(TradingMode mode, List<MarketTickerResponse> tickers) {
		return new MarketListResponse(mode, null, tickers.size(), tickers);
	}

	public static MarketListResponse optionsUnavailable() {
		return new MarketListResponse(
				TradingMode.OPTIONS,
				"Options data not yet available",
				0,
				List.of()
		);
	}
}
