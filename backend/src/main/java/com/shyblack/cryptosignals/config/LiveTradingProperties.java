package com.shyblack.cryptosignals.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Live-trading configuration. Ships with paranoid defaults — live trading
 * is off unless every gate is opened explicitly.
 *
 * mode                 EXCHANGE = real Binance signed API. MOCK = in-process
 *                      simulator, safe for local dev + integration tests.
 * spotRestBaseUrl      Binance spot REST base. Defaults to testnet.
 * spotStreamBaseUrl    Binance spot user-data stream base. Defaults to testnet.
 * futuresRestBaseUrl   Binance USDT-M futures REST base. Defaults to testnet.
 * recvWindowMs         Signed-request recvWindow parameter.
 * defaultMaxNotional   Guardrail (in quote currency) for a single trade when
 *                      the account has no explicit override yet.
 * defaultMaxActive     Max concurrent active live positions per user (safety).
 * defaultDailyLossPct  Blocks new entries if realized P&L crosses this % of
 *                      day-start equity.
 * autoExecute          When false, signals never fan out to LIVE users even
 *                      if their account is active. Extra kill switch.
 */
@ConfigurationProperties(prefix = "app.live-trading")
public record LiveTradingProperties(
		Mode mode,
		String spotRestBaseUrl,
		String spotStreamBaseUrl,
		String futuresRestBaseUrl,
		long recvWindowMs,
		BigDecimal defaultMaxNotional,
		int defaultMaxActive,
		BigDecimal defaultDailyLossPct,
		boolean autoExecute
) {
	public enum Mode { MOCK, EXCHANGE }

	public LiveTradingProperties {
		if (mode == null) mode = Mode.MOCK;
		if (spotRestBaseUrl == null || spotRestBaseUrl.isBlank()) spotRestBaseUrl = "https://testnet.binance.vision";
		if (spotStreamBaseUrl == null || spotStreamBaseUrl.isBlank()) spotStreamBaseUrl = "wss://testnet.binance.vision/ws";
		if (futuresRestBaseUrl == null || futuresRestBaseUrl.isBlank()) futuresRestBaseUrl = "https://testnet.binancefuture.com";
		if (recvWindowMs <= 0) recvWindowMs = 5000;
		if (defaultMaxNotional == null) defaultMaxNotional = new BigDecimal("200.00");
		if (defaultMaxActive <= 0) defaultMaxActive = 3;
		if (defaultDailyLossPct == null) defaultDailyLossPct = new BigDecimal("5.00");
	}
}
