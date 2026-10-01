package com.shyblack.cryptosignals.service.backtest.research;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Descriptive EVENT -&gt; SIGNAL -&gt; TRADE -&gt; OUTCOME attribution for one NFM trade.
 * Every unavailable value is null (UNKNOWN); nothing is fabricated. This is an
 * analytics record, not a trade/event entity — it reuses the engine's captured
 * signal and lifecycle and never re-derives decisions.
 */
public record NfmEventAttribution(
		String symbol,
		// event
		UUID eventId,
		String eventType,
		String eventStage,
		String sourceTier,
		Instant eventTimestamp,
		List<UUID> attributedEventIds,
		// signal
		String signalDirection,
		BigDecimal signalScore,
		String signalGrade,
		String signalStatus,
		// event data
		BigDecimal expected,
		BigDecimal actual,
		BigDecimal surprise,
		// reaction / derivatives context (null = UNKNOWN)
		BigDecimal priceReaction,
		BigDecimal volumeRatio,
		BigDecimal oiChange,
		BigDecimal funding,
		BigDecimal liquidation,
		String marketRegime,
		// trade
		BigDecimal tradeEntryPrice,
		BigDecimal tradeExitPrice,
		BigDecimal stopLoss,
		BigDecimal tp1,
		BigDecimal tp2,
		BigDecimal tp3,
		Boolean tp1Hit,
		Boolean tp2Hit,
		Boolean tp3Hit,
		Boolean slHit,
		BigDecimal grossPnl,
		BigDecimal fees,
		BigDecimal slippage,
		BigDecimal netPnl,
		String tradeOutcome,
		// ATTRIBUTED | EVENT_ATTRIBUTION_UNKNOWN | NO_TRADE
		String attributionStatus,
		// observational decision snapshot (null = not captured)
		com.shyblack.cryptosignals.signal.nfm.NfmDecisionContext decisionContext,
		/** Deterministic per-trade signal identity (symbol + entry index + entry time). */
		UUID signalId
) {
}
