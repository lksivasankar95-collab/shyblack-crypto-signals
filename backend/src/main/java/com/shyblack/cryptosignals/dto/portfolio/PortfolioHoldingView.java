package com.shyblack.cryptosignals.dto.portfolio;

import java.math.BigDecimal;

/**
 * One asset held in an exchange wallet.
 *
 * <p>This is a WALLET HOLDING, not a trading position. Binance Spot has no conventional
 * open-position concept, so a holding must never be presented as one, and the distinction is carried
 * in the type rather than left to the UI.
 *
 * <p>No valuation is provided. Converting a balance into a quote currency would require a trusted
 * price feed, and an unpriced holding is far more honest than a fabricated one.
 */
public record PortfolioHoldingView(
		String asset,
		BigDecimal free,
		BigDecimal locked,
		/** Free + locked, or null when either component is unknown. */
		BigDecimal total) {}
