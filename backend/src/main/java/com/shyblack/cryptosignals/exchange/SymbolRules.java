package com.shyblack.cryptosignals.exchange;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Immutable exchange trading rules for a symbol, distilled from Binance
 * exchangeInfo filters (LOT_SIZE, PRICE_FILTER, MIN_NOTIONAL, MARKET_LOT_SIZE).
 */
public record SymbolRules(
		String symbol,
		BigDecimal minQty,
		BigDecimal maxQty,
		BigDecimal stepSize,
		BigDecimal minPrice,
		BigDecimal maxPrice,
		BigDecimal tickSize,
		BigDecimal minNotional
) {
	/** Snap {@code qty} down to the nearest multiple of {@link #stepSize}. */
	public BigDecimal normalizeQuantity(BigDecimal qty) {
		if (qty == null || qty.signum() <= 0) return BigDecimal.ZERO;
		if (stepSize == null || stepSize.signum() <= 0) return qty;
		BigDecimal steps = qty.divide(stepSize, 0, RoundingMode.DOWN);
		return steps.multiply(stepSize);
	}

	/** Snap {@code price} to the nearest multiple of {@link #tickSize}. */
	public BigDecimal normalizePrice(BigDecimal price) {
		if (price == null || price.signum() <= 0) return BigDecimal.ZERO;
		if (tickSize == null || tickSize.signum() <= 0) return price;
		BigDecimal ticks = price.divide(tickSize, 0, RoundingMode.HALF_UP);
		return ticks.multiply(tickSize);
	}

	public boolean meetsMinQty(BigDecimal qty) {
		if (qty == null) return false;
		if (minQty != null && qty.compareTo(minQty) < 0) return false;
		if (maxQty != null && maxQty.signum() > 0 && qty.compareTo(maxQty) > 0) return false;
		return true;
	}

	public boolean meetsMinNotional(BigDecimal qty, BigDecimal price) {
		if (minNotional == null || minNotional.signum() <= 0) return true;
		if (qty == null || price == null) return false;
		return qty.multiply(price).compareTo(minNotional) >= 0;
	}
}
