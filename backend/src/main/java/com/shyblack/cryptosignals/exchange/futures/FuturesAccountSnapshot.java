package com.shyblack.cryptosignals.exchange.futures;

import com.shyblack.cryptosignals.entity.enums.FuturesMarginMode;
import com.shyblack.cryptosignals.entity.enums.FuturesPositionMode;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Futures account snapshot from Binance. Distinct from spot's
 * {@code ExchangeAccountSnapshot} — Futures has margin, unrealized P&L,
 * position mode, and no "spot balance".
 */
public record FuturesAccountSnapshot(
		String marginAsset,
		BigDecimal walletBalance,
		BigDecimal availableBalance,
		BigDecimal marginBalance,
		BigDecimal usedMargin,
		BigDecimal maintenanceMargin,
		BigDecimal unrealizedPnl,
		FuturesPositionMode positionMode,
		FuturesMarginMode marginMode,
		boolean canTrade,
		Instant fetchedAt
) {}
