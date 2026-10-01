package com.shyblack.cryptosignals.service.backtest.research;

import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalEvent;
import com.shyblack.cryptosignals.service.backtest.strategy.BacktestStrategy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Deterministic EVENT -&gt; SIGNAL -&gt; TRADE -&gt; OUTCOME attribution. It consumes the
 * engine's already-computed entries (signal + lifecycle) and the historical
 * events. Event ids were attached by the strategy using the SAME causal rule
 * ({@code event.time <= candle.closeTime} and the configured max age), so no
 * future event can ever be attributed. Multiple eligible events are all
 * preserved. If no event can be linked the status is
 * {@code EVENT_ATTRIBUTION_UNKNOWN} — never a guess.
 */
public final class NfmEventAttributionBuilder {

	private NfmEventAttributionBuilder() {
	}

	/**
	 * The single causal eligibility rule (also used by the strategy): an event is
	 * visible iff it is not after the candle close and not older than the max age.
	 */
	public static boolean causallyEligible(HistoricalEvent e, java.time.Instant closeTime, int maxAgeMinutes) {
		if (e == null || e.time() == null || closeTime == null) {
			return false;
		}
		if (e.time().isAfter(closeTime)) {
			return false;
		}
		return !e.time().isBefore(closeTime.minus(java.time.Duration.ofMinutes(maxAgeMinutes)));
	}

	public static List<NfmEventAttribution> build(String symbol, List<HistoricalEvent> events,
			List<PartialExitBacktestEngine.Entry> entries) {
		if (entries == null || entries.isEmpty()) {
			return List.of();
		}
		Map<UUID, HistoricalEvent> byId = new LinkedHashMap<>();
		if (events != null) {
			for (HistoricalEvent e : events) {
				if (e != null && e.id() != null) {
					byId.put(e.id(), e);
				}
			}
		}
		List<NfmEventAttribution> out = new ArrayList<>(entries.size());
		for (PartialExitBacktestEngine.Entry entry : entries) {
			out.add(attribute(symbol, byId, entry));
		}
		return out;
	}

	private static NfmEventAttribution attribute(String symbol, Map<UUID, HistoricalEvent> byId,
			PartialExitBacktestEngine.Entry entry) {
		BacktestStrategy.Signal signal = entry.signal();
		List<UUID> ids = signal == null || signal.eventIds() == null ? List.of() : signal.eventIds();

		// Deterministic primary event: latest by time among the attributed ids.
		HistoricalEvent primary = ids.stream().map(byId::get).filter(Objects::nonNull)
				.max(Comparator.comparing(HistoricalEvent::time)).orElse(null);
		boolean attributed = primary != null;

		PartialExitSimulator.Lifecycle lc = entry.lifecycle();
		boolean tp1Hit = hasFill(lc, "TP1");
		boolean tp2Hit = hasFill(lc, "TP2");
		boolean tp3Hit = hasFill(lc, "TP3");
		boolean slHit = hasFill(lc, "STOP_LOSS");
		BigDecimal exitPrice = lastFillPrice(lc);
		BigDecimal net = lc == null ? null : lc.netPnl();

		String direction = signal == null || signal.side() == null ? null
				: signal.side() == PositionSide.LONG ? "LONG" : "SHORT";
		String status = lc == null ? null : (lc.closed() ? "CLOSED" : "OPEN");
		String outcome = net == null ? null
				: net.signum() > 0 ? "WIN" : net.signum() < 0 ? "LOSS" : "BREAKEVEN";

		com.shyblack.cryptosignals.signal.nfm.NfmDecisionContext ctx =
				signal == null ? null : signal.decisionContext();

		return new NfmEventAttribution(
				symbol,
				primary == null ? null : primary.id(),
				primary != null && primary.eventType() != null ? primary.eventType().name()
						: ctx == null ? null : ctx.eventType(),
				primary != null && primary.eventStage() != null ? primary.eventStage().name()
						: ctx == null ? null : ctx.eventStage(),
				primary != null && primary.sourceTier() != null ? primary.sourceTier().name()
						: ctx == null ? null : ctx.sourceTier(),
				primary == null ? null : primary.time(),
				List.copyOf(ids),
				direction,
				ctx == null || ctx.score() == null ? null : BigDecimal.valueOf(ctx.score()),
				ctx == null ? null : ctx.grade(),
				status,
				primary != null && primary.expectedValue() != null ? primary.expectedValue()
						: ctx == null ? null : ctx.expected(),
				primary != null && primary.actualValue() != null ? primary.actualValue()
						: ctx == null ? null : ctx.actual(),
				primary != null && primary.surpriseValue() != null ? primary.surpriseValue()
						: ctx == null ? null : ctx.surprise(),
				ctx == null ? null : ctx.priceReaction(),
				ctx == null ? null : ctx.volumeRatio(),
				ctx == null ? null : ctx.oiChange(),
				ctx == null ? null : ctx.funding(),
				ctx == null ? null : ctx.liquidation(),
				ctx == null ? null : ctx.marketRegime(),
				entry.entryPrice(),
				exitPrice,
				signal == null ? null : signal.stopLoss(),
				signal == null ? null : signal.takeProfit(),
				signal == null ? null : signal.takeProfit2(),
				signal == null ? null : signal.takeProfit3(),
				tp1Hit, tp2Hit, tp3Hit, slHit,
				lc == null ? null : lc.grossPnl(),
				lc == null ? null : lc.totalFees(),
				null, // slippage folded into entry by the engine (UNKNOWN)
				net,
				outcome,
				attributed ? "ATTRIBUTED" : "EVENT_ATTRIBUTION_UNKNOWN",
				ctx);
	}

	private static boolean hasFill(PartialExitSimulator.Lifecycle lc, String reason) {
		if (lc == null || lc.fills() == null) {
			return false;
		}
		return lc.fills().stream().anyMatch(f -> reason.equals(f.reason()));
	}

	private static BigDecimal lastFillPrice(PartialExitSimulator.Lifecycle lc) {
		if (lc == null || lc.fills() == null || lc.fills().isEmpty()) {
			return null;
		}
		return lc.fills().get(lc.fills().size() - 1).price();
	}
}
