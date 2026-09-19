package com.shyblack.cryptosignals.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration knobs for the paper-trading simulation. All values ship with
 * conservative production-ready defaults; ops can override via env.
 *
 * feeRatePct           per-side commission (e.g. 0.10 = 0.10%).
 * slippagePct          adverse-price slippage applied to simulated fills.
 * initialBalance       starting equity for a new paper account (quote currency).
 * maxActivePositions   safety cap so a single user cannot deploy hundreds of positions.
 * riskPerTradePct      risk % of account per trade when sizing from stop distance.
 * defaultQuoteCurrency currency all balances/pnl are denominated in.
 */
@ConfigurationProperties(prefix = "app.paper-trading")
public record PaperTradingProperties(
		BigDecimal feeRatePct,
		BigDecimal slippagePct,
		BigDecimal initialBalance,
		int maxActivePositions,
		BigDecimal riskPerTradePct,
		String defaultQuoteCurrency
) {
	public PaperTradingProperties {
		if (feeRatePct == null) feeRatePct = new BigDecimal("0.10");
		if (slippagePct == null) slippagePct = new BigDecimal("0.05");
		if (initialBalance == null) initialBalance = new BigDecimal("10000.00");
		if (maxActivePositions <= 0) maxActivePositions = 10;
		if (riskPerTradePct == null) riskPerTradePct = new BigDecimal("2.00");
		if (defaultQuoteCurrency == null || defaultQuoteCurrency.isBlank()) defaultQuoteCurrency = "USDT";
	}
}
