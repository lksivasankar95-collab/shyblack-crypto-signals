package com.shyblack.cryptosignals.service.backtest.research;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Descriptive-only aggregation over {@link NfmEventAttribution} records.
 * Undefined metrics are null (UNKNOWN), never zero. There is NO ranking, winner,
 * best/optimal field anywhere. Groupings are alphabetical/insertion ordered, not
 * ordered by performance.
 */
public final class NfmValidationAnalytics {

	private NfmValidationAnalytics() {
	}

	public record EventTypeStats(String eventType, int eventCount, int signalCount, int tradeCount,
			int longCount, int shortCount, int winCount, int lossCount,
			BigDecimal netPnl, BigDecimal grossProfit, BigDecimal grossLoss,
			BigDecimal winRate, BigDecimal expectancy, BigDecimal profitFactor,
			BigDecimal averageScore, BigDecimal averageReaction, BigDecimal averageDrawdown) {}

	public record SymbolStats(String symbol, int eventCount, int signalCount, int tradeCount,
			int longCount, int shortCount, int winCount, int lossCount,
			BigDecimal netPnl, BigDecimal winRate, BigDecimal expectancy, BigDecimal profitFactor,
			BigDecimal maxDrawdown) {}

	public record DirectionStats(String direction, int tradeCount, int winCount, int lossCount,
			BigDecimal winRate, BigDecimal netPnl, BigDecimal expectancy, BigDecimal profitFactor,
			BigDecimal averageScore, BigDecimal averageReaction) {}

	public record LifecycleStats(int trades, BigDecimal tp1HitRate, BigDecimal tp2HitRate,
			BigDecimal tp3HitRate, BigDecimal slRate) {}

	public record Report(List<EventTypeStats> byEventType, List<SymbolStats> bySymbol,
			List<DirectionStats> byDirection, LifecycleStats lifecycle, String surpriseStatus,
			String attributionCoverage, List<String> noTradeReasons) {}

	public static Report analyze(List<NfmEventAttribution> rows) {
		List<NfmEventAttribution> list = rows == null ? List.of() : rows;
		List<EventTypeStats> byType = group(list, r -> r.eventType() == null ? "UNKNOWN" : r.eventType())
				.entrySet().stream().map(e -> eventTypeStats(e.getKey(), e.getValue())).toList();
		List<SymbolStats> bySymbol = group(list, r -> r.symbol() == null ? "UNKNOWN" : r.symbol())
				.entrySet().stream().map(e -> symbolStats(e.getKey(), e.getValue())).toList();
		List<DirectionStats> byDirection = group(list, r -> r.signalDirection() == null ? "UNKNOWN"
						: r.signalDirection()).entrySet().stream()
				.map(e -> directionStats(e.getKey(), e.getValue())).toList();

		boolean anySurprise = list.stream().anyMatch(r -> r.surprise() != null);
		boolean anyAttributed = list.stream().anyMatch(r -> "ATTRIBUTED".equals(r.attributionStatus()));
		return new Report(byType, bySymbol, byDirection, lifecycle(list),
				anySurprise ? "MEASURED" : "SURPRISE_DATA_UNAVAILABLE",
				anyAttributed ? "MEASURED" : "EVENT_ATTRIBUTION_UNKNOWN",
				List.of());
	}

	public static LifecycleStats lifecycle(List<NfmEventAttribution> rows) {
		int trades = rows == null ? 0 : rows.size();
		if (trades == 0) {
			return new LifecycleStats(0, null, null, null, null);
		}
		BigDecimal tp1 = rate(rows, NfmEventAttribution::tp1Hit);
		BigDecimal tp2 = rate(rows, NfmEventAttribution::tp2Hit);
		BigDecimal tp3 = rate(rows, NfmEventAttribution::tp3Hit);
		BigDecimal sl = rate(rows, NfmEventAttribution::slHit);
		return new LifecycleStats(trades, tp1, tp2, tp3, sl);
	}

	private static EventTypeStats eventTypeStats(String type, List<NfmEventAttribution> rows) {
		Metrics m = Metrics.of(rows);
		long eventCount = rows.stream().map(NfmEventAttribution::eventId).filter(Objects::nonNull)
				.distinct().count();
		return new EventTypeStats(type, (int) eventCount, rows.size(), rows.size(), m.longs, m.shorts,
				m.wins, m.losses, m.netPnl, m.grossProfit, m.grossLoss, m.winRate, m.expectancy,
				m.profitFactor, null, null, null);
	}

	private static SymbolStats symbolStats(String symbol, List<NfmEventAttribution> rows) {
		Metrics m = Metrics.of(rows);
		long eventCount = rows.stream().map(NfmEventAttribution::eventId).filter(Objects::nonNull)
				.distinct().count();
		return new SymbolStats(symbol, (int) eventCount, rows.size(), rows.size(), m.longs, m.shorts,
				m.wins, m.losses, m.netPnl, m.winRate, m.expectancy, m.profitFactor, null);
	}

	private static DirectionStats directionStats(String direction, List<NfmEventAttribution> rows) {
		Metrics m = Metrics.of(rows);
		return new DirectionStats(direction, rows.size(), m.wins, m.losses, m.winRate, m.netPnl,
				m.expectancy, m.profitFactor, null, null);
	}

	private static <K> Map<K, List<NfmEventAttribution>> group(List<NfmEventAttribution> rows,
			Function<NfmEventAttribution, K> key) {
		return rows.stream().collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.toList()));
	}

	private static BigDecimal rate(List<NfmEventAttribution> rows,
			Function<NfmEventAttribution, Boolean> flag) {
		long hits = rows.stream().filter(r -> Boolean.TRUE.equals(flag.apply(r))).count();
		return BigDecimal.valueOf(hits).multiply(BigDecimal.valueOf(100))
				.divide(BigDecimal.valueOf(rows.size()), 4, RoundingMode.HALF_UP);
	}

	/** Per-group metric accumulator. Undefined values stay null. */
	private static final class Metrics {
		int longs, shorts, wins, losses;
		BigDecimal netPnl = BigDecimal.ZERO;
		BigDecimal grossProfit = BigDecimal.ZERO;
		BigDecimal grossLoss = BigDecimal.ZERO;
		BigDecimal winRate, expectancy, profitFactor;

		static Metrics of(List<NfmEventAttribution> rows) {
			Metrics m = new Metrics();
			int traded = 0;
			for (NfmEventAttribution r : rows) {
				if ("LONG".equals(r.signalDirection())) m.longs++;
				else if ("SHORT".equals(r.signalDirection())) m.shorts++;
				if (r.netPnl() != null) {
					traded++;
					m.netPnl = m.netPnl.add(r.netPnl());
					if (r.netPnl().signum() > 0) {
						m.wins++;
						m.grossProfit = m.grossProfit.add(r.netPnl());
					} else if (r.netPnl().signum() < 0) {
						m.losses++;
						m.grossLoss = m.grossLoss.add(r.netPnl().abs());
					}
				}
			}
			if (traded > 0) {
				m.winRate = BigDecimal.valueOf(m.wins).multiply(BigDecimal.valueOf(100))
						.divide(BigDecimal.valueOf(traded), 4, RoundingMode.HALF_UP);
				m.expectancy = m.netPnl.divide(BigDecimal.valueOf(traded), 8, RoundingMode.HALF_UP);
				m.profitFactor = m.grossLoss.signum() == 0 ? null
						: m.grossProfit.divide(m.grossLoss, 4, RoundingMode.HALF_UP);
			} else {
				m.netPnl = null;
			}
			return m;
		}
	}
}
