package com.shyblack.cryptosignals.service.backtest.research;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Per-window (walk-forward / sensitivity / OOS) result derived from the shared
 * metrics calculator. Undefined values are null (never sentinels).
 */
public record ResearchWindowResult(
		String label,
		Instant start,
		Instant end,
		String paramsJson,
		int trades,
		int wins,
		int losses,
		BigDecimal winRatePct,
		BigDecimal netPnl,
		BigDecimal grossProfit,
		BigDecimal grossLoss,
		BigDecimal fees,
		BigDecimal expectancy,
		BigDecimal profitFactor,
		BigDecimal maxDrawdownPct,
		BigDecimal returnPct
) {
}
