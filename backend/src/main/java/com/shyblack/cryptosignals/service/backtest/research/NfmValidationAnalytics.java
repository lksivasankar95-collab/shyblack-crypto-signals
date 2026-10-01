package com.shyblack.cryptosignals.service.backtest.research;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Descriptive-only aggregation over {@link NfmEventAttribution} records.
 * Undefined metrics are null (UNKNOWN), never zero. There is NO ranking, winner,
 * best/optimal field anywhere. Groupings are deterministic, never performance
 * ordered.
 */
public final class NfmValidationAnalytics {

	private NfmValidationAnalytics() {
	}

	public record EventTypeStats(String eventType, int eventCount, int signalCount, int tradeCount,
			int longCount, int shortCount, int winCount, int lossCount,
			BigDecimal netPnl, BigDecimal grossProfit, BigDecimal grossLoss,
			BigDecimal winRate, BigDecimal expectancy, BigDecimal profitFactor,
			BigDecimal averageScore, BigDecimal averageReaction, BigDecimal averageVolumeRatio,
			BigDecimal averageOiChange, BigDecimal averageDrawdown) {}

	public record SymbolStats(String symbol, int eventCount, int signalCount, int tradeCount,
			int longCount, int shortCount, int winCount, int lossCount,
			BigDecimal netPnl, BigDecimal winRate, BigDecimal expectancy, BigDecimal profitFactor,
			BigDecimal maxDrawdown) {}

	public record DirectionStats(String direction, int tradeCount, int winCount, int lossCount,
			BigDecimal winRate, BigDecimal netPnl, BigDecimal expectancy, BigDecimal profitFactor,
			BigDecimal averageScore, BigDecimal averageReaction, BigDecimal averageVolumeRatio,
			BigDecimal averageOiChange, BigDecimal averageFunding) {}

	public record RegimeStats(String regime, int eventCount, int signalCount, int tradeCount,
			int longCount, int shortCount, int winCount, int lossCount,
			BigDecimal netPnl, BigDecimal winRate, BigDecimal expectancy, BigDecimal profitFactor,
			BigDecimal maxDrawdown) {}

	public record GradeStats(String grade, int signalCount, int tradeCount, int winCount, int lossCount,
			BigDecimal winRate, BigDecimal netPnl, BigDecimal expectancy, BigDecimal profitFactor) {}

	public record ScoreBucketStats(String bucket, int signalCount, int tradeCount,
			BigDecimal winRate, BigDecimal netPnl, BigDecimal expectancy, BigDecimal profitFactor) {}

	public record LifecycleStats(int trades, BigDecimal tp1HitRate, BigDecimal tp2HitRate,
			BigDecimal tp3HitRate, BigDecimal slRate) {}

	public record Report(List<EventTypeStats> byEventType, List<SymbolStats> bySymbol,
			List<DirectionStats> byDirection, List<RegimeStats> byRegime, List<GradeStats> byGrade,
			List<ScoreBucketStats> byScoreBucket, LifecycleStats lifecycle, String surpriseStatus,
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
		List<RegimeStats> byRegime = group(list, r -> r.marketRegime() == null ? "UNKNOWN"
						: r.marketRegime()).entrySet().stream()
				.map(e -> regimeStats(e.getKey(), e.getValue())).toList();
		List<GradeStats> byGrade = group(list, r -> r.signalGrade() == null ? "UNKNOWN"
						: r.signalGrade()).entrySet().stream()
				.map(e -> gradeStats(e.getKey(), e.getValue())).toList();
		List<ScoreBucketStats> byBucket = group(list, r -> bucket(r.signalScore())).entrySet().stream()
				.map(e -> scoreBucketStats(e.getKey(), e.getValue())).toList();

		boolean anySurprise = list.stream().anyMatch(r -> r.surprise() != null);
		boolean anyAttributed = list.stream().anyMatch(r -> "ATTRIBUTED".equals(r.attributionStatus()));
		return new Report(byType, bySymbol, byDirection, byRegime, byGrade, byBucket, lifecycle(list),
				anySurprise ? "MEASURED" : "SURPRISE_DATA_UNAVAILABLE",
				anyAttributed ? "MEASURED" : "EVENT_ATTRIBUTION_UNKNOWN",
				List.of());
	}

	public static LifecycleStats lifecycle(List<NfmEventAttribution> rows) {
		int trades = rows == null ? 0 : rows.size();
		if (trades == 0) {
			return new LifecycleStats(0, null, null, null, null);
		}
		return new LifecycleStats(trades, rate(rows, NfmEventAttribution::tp1Hit),
				rate(rows, NfmEventAttribution::tp2Hit), rate(rows, NfmEventAttribution::tp3Hit),
				rate(rows, NfmEventAttribution::slHit));
	}

	private static EventTypeStats eventTypeStats(String type, List<NfmEventAttribution> rows) {
		Metrics m = Metrics.of(rows);
		return new EventTypeStats(type, distinctEvents(rows), rows.size(), rows.size(), m.longs, m.shorts,
				m.wins, m.losses, m.netPnl, m.grossProfit, m.grossLoss, m.winRate, m.expectancy,
				m.profitFactor, m.avgScore, m.avgReaction, m.avgVolume, m.avgOi, null);
	}

	private static SymbolStats symbolStats(String symbol, List<NfmEventAttribution> rows) {
		Metrics m = Metrics.of(rows);
		return new SymbolStats(symbol, distinctEvents(rows), rows.size(), rows.size(), m.longs, m.shorts,
				m.wins, m.losses, m.netPnl, m.winRate, m.expectancy, m.profitFactor, null);
	}

	private static DirectionStats directionStats(String direction, List<NfmEventAttribution> rows) {
		Metrics m = Metrics.of(rows);
		return new DirectionStats(direction, rows.size(), m.wins, m.losses, m.winRate, m.netPnl,
				m.expectancy, m.profitFactor, m.avgScore, m.avgReaction, m.avgVolume, m.avgOi, m.avgFunding);
	}

	private static RegimeStats regimeStats(String regime, List<NfmEventAttribution> rows) {
		Metrics m = Metrics.of(rows);
		return new RegimeStats(regime, distinctEvents(rows), rows.size(), rows.size(), m.longs, m.shorts,
				m.wins, m.losses, m.netPnl, m.winRate, m.expectancy, m.profitFactor, null);
	}

	private static GradeStats gradeStats(String grade, List<NfmEventAttribution> rows) {
		Metrics m = Metrics.of(rows);
		return new GradeStats(grade, rows.size(), rows.size(), m.wins, m.losses, m.winRate, m.netPnl,
				m.expectancy, m.profitFactor);
	}

	private static ScoreBucketStats scoreBucketStats(String bucket, List<NfmEventAttribution> rows) {
		Metrics m = Metrics.of(rows);
		return new ScoreBucketStats(bucket, rows.size(), rows.size(), m.winRate, m.netPnl, m.expectancy,
				m.profitFactor);
	}

	private static String bucket(BigDecimal score) {
		if (score == null) {
			return "UNKNOWN";
		}
		int s = score.intValue();
		if (s >= 85) return "85-100";
		if (s >= 75) return "75-84";
		if (s >= 70) return "70-74";
		if (s >= 65) return "65-69";
		return "<65";
	}

	private static int distinctEvents(List<NfmEventAttribution> rows) {
		return (int) rows.stream().map(NfmEventAttribution::eventId).filter(Objects::nonNull).distinct()
				.count();
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
		int longs, shorts, wins, losses, traded;
		BigDecimal netPnl = BigDecimal.ZERO;
		BigDecimal grossProfit = BigDecimal.ZERO;
		BigDecimal grossLoss = BigDecimal.ZERO;
		BigDecimal winRate, expectancy, profitFactor, avgScore, avgReaction, avgVolume, avgOi, avgFunding;

		static Metrics of(List<NfmEventAttribution> rows) {
			Metrics m = new Metrics();
			Sum score = new Sum();
			Sum reaction = new Sum();
			Sum volume = new Sum();
			Sum oi = new Sum();
			Sum funding = new Sum();
			for (NfmEventAttribution r : rows) {
				if ("LONG".equals(r.signalDirection())) m.longs++;
				else if ("SHORT".equals(r.signalDirection())) m.shorts++;
				if (r.netPnl() != null) {
					m.traded++;
					m.netPnl = m.netPnl.add(r.netPnl());
					if (r.netPnl().signum() > 0) {
						m.wins++;
						m.grossProfit = m.grossProfit.add(r.netPnl());
					} else if (r.netPnl().signum() < 0) {
						m.losses++;
						m.grossLoss = m.grossLoss.add(r.netPnl().abs());
					}
				}
				score.add(r.signalScore());
				reaction.add(r.priceReaction());
				volume.add(r.volumeRatio());
				oi.add(r.oiChange());
				funding.add(r.funding());
			}
			if (m.traded > 0) {
				m.winRate = BigDecimal.valueOf(m.wins).multiply(BigDecimal.valueOf(100))
						.divide(BigDecimal.valueOf(m.traded), 4, RoundingMode.HALF_UP);
				m.expectancy = m.netPnl.divide(BigDecimal.valueOf(m.traded), 8, RoundingMode.HALF_UP);
				m.profitFactor = m.grossLoss.signum() == 0 ? null
						: m.grossProfit.divide(m.grossLoss, 4, RoundingMode.HALF_UP);
			} else {
				m.netPnl = null;
			}
			m.avgScore = score.avg();
			m.avgReaction = reaction.avg();
			m.avgVolume = volume.avg();
			m.avgOi = oi.avg();
			m.avgFunding = funding.avg();
			return m;
		}
	}

	private static final class Sum {
		BigDecimal total = BigDecimal.ZERO;
		int count;

		void add(BigDecimal v) {
			if (v != null) {
				total = total.add(v);
				count++;
			}
		}

		BigDecimal avg() {
			return count == 0 ? null : total.divide(BigDecimal.valueOf(count), 6, RoundingMode.HALF_UP);
		}
	}
}
