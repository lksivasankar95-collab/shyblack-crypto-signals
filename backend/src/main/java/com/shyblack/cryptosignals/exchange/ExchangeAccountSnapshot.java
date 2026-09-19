package com.shyblack.cryptosignals.exchange;

import java.math.BigDecimal;
import java.time.Instant;

public record ExchangeAccountSnapshot(
		String quoteCurrency,
		BigDecimal availableBalance,
		BigDecimal totalBalance,
		boolean canTrade,
		Instant fetchedAt
) {}
