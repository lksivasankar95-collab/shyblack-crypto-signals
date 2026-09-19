package com.shyblack.cryptosignals.config;

import com.shyblack.cryptosignals.entity.enums.FuturesMarginMode;
import com.shyblack.cryptosignals.entity.enums.FuturesPositionMode;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration knobs for Binance USDT-M FUTURES trading. Paranoid defaults:
 * MOCK adapter, testnet URLs, low leverage cap, ISOLATED margin, ONE_WAY
 * position mode, auto-execute OFF.
 *
 * Live Futures traffic requires an explicit ops decision to flip mode.
 */
@ConfigurationProperties(prefix = "app.futures-trading")
public record FuturesTradingProperties(
		Mode mode,
		String restBaseUrl,
		String streamBaseUrl,
		long recvWindowMs,
		int maxLeverage,
		FuturesMarginMode defaultMarginMode,
		FuturesPositionMode requiredPositionMode,
		BigDecimal defaultMaxNotional,
		int defaultMaxActive,
		BigDecimal defaultDailyLossPct,
		BigDecimal minStopDistancePct,
		BigDecimal liquidationBufferPct,
		boolean autoExecute
) {
	public enum Mode { MOCK, EXCHANGE }

	public FuturesTradingProperties {
		if (mode == null) mode = Mode.MOCK;
		if (restBaseUrl == null || restBaseUrl.isBlank()) restBaseUrl = "https://testnet.binancefuture.com";
		if (streamBaseUrl == null || streamBaseUrl.isBlank()) streamBaseUrl = "wss://stream.binancefuture.com/ws";
		if (recvWindowMs <= 0) recvWindowMs = 5000;
		if (maxLeverage <= 0) maxLeverage = 3; // deliberately conservative
		if (defaultMarginMode == null) defaultMarginMode = FuturesMarginMode.ISOLATED;
		if (requiredPositionMode == null) requiredPositionMode = FuturesPositionMode.ONE_WAY;
		if (defaultMaxNotional == null) defaultMaxNotional = new BigDecimal("200.00");
		if (defaultMaxActive <= 0) defaultMaxActive = 2;
		if (defaultDailyLossPct == null) defaultDailyLossPct = new BigDecimal("5.00");
		if (minStopDistancePct == null) minStopDistancePct = new BigDecimal("0.30");
		if (liquidationBufferPct == null) liquidationBufferPct = new BigDecimal("15.00");
	}
}
