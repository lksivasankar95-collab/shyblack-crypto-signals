package com.shyblack.cryptosignals.exchange.futures;

import com.shyblack.cryptosignals.entity.enums.FuturesOrderType;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import java.math.BigDecimal;

public record PlaceFuturesOrderRequest(
		String symbol,
		PositionSide side,          // BUY(LONG) / SELL(SHORT) at the exchange
		PositionSide positionSide,  // LONG or SHORT (which position it affects)
		FuturesOrderType type,
		boolean reduceOnly,
		BigDecimal quantity,
		BigDecimal price,
		BigDecimal stopPrice,
		String clientOrderId
) {}
