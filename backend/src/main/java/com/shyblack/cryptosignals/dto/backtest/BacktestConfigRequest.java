package com.shyblack.cryptosignals.dto.backtest;

import com.shyblack.cryptosignals.entity.enums.BacktestExecutionModel;
import com.shyblack.cryptosignals.entity.enums.BacktestSameCandlePolicy;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public record BacktestConfigRequest(
		@NotBlank String strategyId,
		@NotBlank String symbol,
		@NotBlank String timeframe,
		@NotNull TradingMode tradingMode,
		@NotNull Instant startDate,
		@NotNull Instant endDate,
		@NotNull BigDecimal initialCapital,
		@NotNull BigDecimal riskPerTradePct,
		@NotNull BigDecimal feePct,
		@NotNull BigDecimal slippagePct,
		Integer leverage,
		BacktestExecutionModel executionModel,
		BacktestSameCandlePolicy sameCandlePolicy
) {
	public int leverageOrDefault() { return leverage == null ? 1 : leverage; }
	public BacktestExecutionModel executionModelOrDefault() {
		return executionModel == null ? BacktestExecutionModel.NEXT_CANDLE_OPEN : executionModel;
	}
	public BacktestSameCandlePolicy sameCandlePolicyOrDefault() {
		return sameCandlePolicy == null ? BacktestSameCandlePolicy.SL_FIRST : sameCandlePolicy;
	}
}
