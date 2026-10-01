package com.shyblack.cryptosignals.entity.enums;

import java.util.Optional;

/**
 * Account category of the unified Portfolio read model: the
 * {@code MAIN / SPOT / FUTURES / OPTIONS} dimension.
 *
 * <p>Distinct from {@link AccountMode} (PAPER vs LIVE) and from {@link TradingMode}, which is the
 * application-controlled market mode carried by a {@code Signal}. {@link #tradingMode()} bridges
 * the two for the categories that map one-to-one onto a market mode.
 */
public enum AccountCategory {

	/** Aggregated view across every category of one account mode. Not a market mode itself. */
	MAIN,

	/** Spot wallet and spot positions. */
	SPOT,

	/** USDT-M futures wallet, positions, margin and leverage. */
	FUTURES,

	/** Options wallet and positions. Reserved capability. */
	OPTIONS;

	/**
	 * The market mode backing this category.
	 *
	 * <p>Empty for {@link #MAIN}, which aggregates several categories and therefore has no single
	 * market mode. Returning empty rather than a fabricated value keeps the "no look-ahead, no
	 * invented data" rule intact.
	 */
	public Optional<TradingMode> tradingMode() {
		return switch (this) {
			case SPOT -> Optional.of(TradingMode.SPOT);
			case FUTURES -> Optional.of(TradingMode.FUTURES);
			case OPTIONS -> Optional.of(TradingMode.OPTIONS);
			case MAIN -> Optional.empty();
		};
	}

	/** True for {@link #MAIN}, the cross-category aggregate. */
	public boolean isAggregating() {
		return this == MAIN;
	}

	/** True when this category maps onto a single market mode. */
	public boolean isMarketScoped() {
		return !isAggregating();
	}
}