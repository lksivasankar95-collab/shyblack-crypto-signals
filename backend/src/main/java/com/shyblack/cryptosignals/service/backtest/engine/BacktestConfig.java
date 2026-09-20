package com.shyblack.cryptosignals.service.backtest.engine;

import com.shyblack.cryptosignals.entity.enums.BacktestExecutionModel;
import com.shyblack.cryptosignals.entity.enums.BacktestSameCandlePolicy;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Canonical, immutable configuration passed to the engine. Also computes
 * the deterministic {@link #hash()} that ends up on the run row so a
 * caller can prove "same config → same run".
 */
public record BacktestConfig(
		String strategyId,
		String symbol,
		String timeframe,
		TradingMode tradingMode,
		Instant startDate,
		Instant endDate,
		BigDecimal initialCapital,
		BigDecimal riskPerTradePct,
		BigDecimal feePct,
		BigDecimal slippagePct,
		int leverage,
		BacktestExecutionModel executionModel,
		BacktestSameCandlePolicy sameCandlePolicy
) {

	public String hash() {
		String canonical = String.join("|",
				strategyId, symbol, timeframe, tradingMode.name(),
				startDate.toString(), endDate.toString(),
				initialCapital.toPlainString(),
				riskPerTradePct.toPlainString(),
				feePct.toPlainString(),
				slippagePct.toPlainString(),
				Integer.toString(leverage),
				executionModel.name(),
				sameCandlePolicy.name());
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(md.digest(canonical.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}
}
