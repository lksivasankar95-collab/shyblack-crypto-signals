package com.shyblack.cryptosignals.service.backtest.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.enums.BacktestExecutionModel;
import com.shyblack.cryptosignals.entity.enums.BacktestSameCandlePolicy;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Phase-30 test: same inputs produce the same configuration hash. */
class BacktestDeterminismTest {

	private static BacktestConfig cfg() {
		return new BacktestConfig(
				"ema-rsi", "BTCUSDT", "1h", TradingMode.SPOT,
				Instant.parse("2024-01-01T00:00:00Z"),
				Instant.parse("2024-02-01T00:00:00Z"),
				new BigDecimal("10000"), new BigDecimal("1.00"),
				new BigDecimal("0.10"), new BigDecimal("0.05"), 1,
				BacktestExecutionModel.NEXT_CANDLE_OPEN,
				BacktestSameCandlePolicy.SL_FIRST);
	}

	@Test
	void sameConfig_producesSameHash() {
		assertThat(cfg().hash()).isEqualTo(cfg().hash());
	}

	@Test
	void differentTimeframe_producesDifferentHash() {
		BacktestConfig alt = new BacktestConfig(
				"ema-rsi", "BTCUSDT", "4h", TradingMode.SPOT,
				Instant.parse("2024-01-01T00:00:00Z"),
				Instant.parse("2024-02-01T00:00:00Z"),
				new BigDecimal("10000"), new BigDecimal("1.00"),
				new BigDecimal("0.10"), new BigDecimal("0.05"), 1,
				BacktestExecutionModel.NEXT_CANDLE_OPEN,
				BacktestSameCandlePolicy.SL_FIRST);
		assertThat(cfg().hash()).isNotEqualTo(alt.hash());
	}

	@Test
	void hash_is64HexChars_sha256() {
		assertThat(cfg().hash()).hasSize(64);
	}
}
