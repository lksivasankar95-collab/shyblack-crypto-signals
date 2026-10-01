package com.shyblack.cryptosignals.exchange.futures;

import com.shyblack.cryptosignals.entity.enums.FuturesMarginMode;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One authoritative open futures position as reported by the exchange position-risk endpoint.
 *
 * <p>This is the exchange's own view. It is deliberately independent of local signals, local orders
 * and the locally recorded {@code FuturesPosition} shadow used by the execution engine.
 *
 * <p>Every money field is nullable because the exchange may omit one (for example a liquidation
 * price of "0" is reported as the literal string "0" and is normalised to null by the adapter, since
 * Binance uses 0 to mean "not applicable" rather than "liquidated at zero"). An absent value is never
 * converted to zero.
 */
public record FuturesExchangePosition(
		String symbol,
		PositionSide positionSide,
		BigDecimal positionAmount,
		BigDecimal entryPrice,
		BigDecimal markPrice,
		BigDecimal liquidationPrice,
		Integer leverage,
		FuturesMarginMode marginMode,
		BigDecimal isolatedMargin,
		BigDecimal notional,
		BigDecimal unrealizedProfit,
		Instant fetchedAt) {

	/**
	 * True when the exchange reports a non-zero amount. An amount of exactly zero means the position
	 * is flat on the exchange, which is a real closed state rather than missing data.
	 */
	public boolean isOpen() {
		return positionAmount != null && positionAmount.signum() != 0;
	}

	/** Absolute size of the position; the sign carries the direction. */
	public BigDecimal quantity() {
		return positionAmount == null ? null : positionAmount.abs();
	}
}