package com.shyblack.cryptosignals.exchange;

import com.shyblack.cryptosignals.entity.enums.LiveOrderType;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import java.math.BigDecimal;

public record PlaceOrderRequest(
		String symbol,
		PositionSide side,
		LiveOrderType type,
		BigDecimal quantity,
		BigDecimal price,
		BigDecimal stopPrice,
		String clientOrderId
) {}
