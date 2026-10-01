package com.shyblack.cryptosignals.service.backtest.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.enums.NewsEventCategory;
import com.shyblack.cryptosignals.entity.enums.NewsEventStage;
import com.shyblack.cryptosignals.entity.enums.NewsEventType;
import com.shyblack.cryptosignals.entity.enums.NewsSourceTier;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalEvent;
import com.shyblack.cryptosignals.service.backtest.strategy.BacktestStrategy;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NfmEventAttributionAnalyticsTest {

	private static BigDecimal bd(String v) {
		return new BigDecimal(v);
	}

	private static HistoricalEvent event(UUID id, String time, NewsEventType type, BigDecimal surprise) {
		return new HistoricalEvent(id, java.time.Instant.parse(time), "BTCUSDT", type,
				NewsEventCategory.MACRO, NewsEventStage.REPORT, NewsSourceTier.TIER_1, null, null, null,
				surprise, "src", "h");
	}

	private static PartialExitBacktestEngine.Entry entry(String symbol, PositionSide side, List<UUID> ids,
			BigDecimal net, boolean tp1, boolean sl) {
		BigDecimal p = bd("100");
		BacktestStrategy.Signal sig = new BacktestStrategy.Signal(side, p, bd("99"), bd("102"), bd("105"),
				bd("110"), "n", ids);
		List<PartialExitSimulator.ExitFill> fills = new ArrayList<>();
		if (tp1) fills.add(new PartialExitSimulator.ExitFill(bd("102"), bd("1"), "TP1", bd("0.01"), bd("2")));
		if (sl) fills.add(new PartialExitSimulator.ExitFill(bd("99"), bd("1"), "STOP_LOSS", bd("0.01"), bd("-1")));
		PartialExitSimulator.Lifecycle lc = new PartialExitSimulator.Lifecycle(side, p, bd("1"),
				BigDecimal.ZERO, bd("0.01"), bd("0.02"), net, net, sl ? "STOP_LOSS" : "TAKE_PROFIT", true, fills);
		return new PartialExitBacktestEngine.Entry(sig, 3, java.time.Instant.parse("2024-01-01T05:00:00Z"),
				p, lc);
	}

	@Test
	void causalEligibilityAcceptsEventAtCloseAndRejectsFuture() {
		java.time.Instant close = java.time.Instant.parse("2024-01-01T05:00:00Z");
		assertThat(NfmEventAttributionBuilder.causallyEligible(
				event(UUID.randomUUID(), "2024-01-01T05:00:00Z", NewsEventType.CPI, null), close, 180))
				.isTrue();
		assertThat(NfmEventAttributionBuilder.causallyEligible(
				event(UUID.randomUUID(), "2024-01-01T05:00:01Z", NewsEventType.CPI, null), close, 180))
				.isFalse();
		assertThat(NfmEventAttributionBuilder.causallyEligible(
				event(UUID.randomUUID(), "2024-01-01T00:00:00Z", NewsEventType.CPI, null), close, 180))
				.isFalse();
	}

	@Test
	void multipleValidEventsArePreservedAndPrimaryIsLatest() {
		UUID cpi = UUID.randomUUID();
		UUID nfp = UUID.randomUUID();
		List<HistoricalEvent> events = List.of(
				event(cpi, "2024-01-01T04:00:00Z", NewsEventType.CPI, null),
				event(nfp, "2024-01-01T04:30:00Z", NewsEventType.NFP, null));
		List<NfmEventAttribution> out = NfmEventAttributionBuilder.build("BTCUSDT", events,
				List.of(entry("BTCUSDT", PositionSide.LONG, List.of(cpi, nfp), bd("5"), true, false)));
		NfmEventAttribution a = out.get(0);
		assertThat(a.attributedEventIds()).containsExactly(cpi, nfp);
		assertThat(a.eventId()).isEqualTo(nfp);
		assertThat(a.eventType()).isEqualTo("NFP");
		assertThat(a.attributionStatus()).isEqualTo("ATTRIBUTED");
	}

	@Test
	void unknownAttributionRemainsUnknown() {
		List<NfmEventAttribution> out = NfmEventAttributionBuilder.build("BTCUSDT", List.of(),
				List.of(entry("BTCUSDT", PositionSide.LONG, List.of(), bd("5"), true, false)));
		assertThat(out.get(0).attributionStatus()).isEqualTo("EVENT_ATTRIBUTION_UNKNOWN");
		assertThat(out.get(0).eventId()).isNull();
		assertThat(out.get(0).eventType()).isNull();
	}

	@Test
	void signalToTradeMappingIsCaptured() {
		UUID id = UUID.randomUUID();
		List<NfmEventAttribution> out = NfmEventAttributionBuilder.build("BTCUSDT",
				List.of(event(id, "2024-01-01T04:30:00Z", NewsEventType.CPI, null)),
				List.of(entry("BTCUSDT", PositionSide.SHORT, List.of(id), bd("-3"), false, true)));
		NfmEventAttribution a = out.get(0);
		assertThat(a.signalDirection()).isEqualTo("SHORT");
		assertThat(a.tradeEntryPrice()).isEqualByComparingTo("100");
		assertThat(a.tradeExitPrice()).isEqualByComparingTo("99");
		assertThat(a.slHit()).isTrue();
		assertThat(a.tp1Hit()).isFalse();
		assertThat(a.netPnl()).isEqualByComparingTo("-3");
		assertThat(a.tradeOutcome()).isEqualTo("LOSS");
		assertThat(a.signalStatus()).isEqualTo("CLOSED");
	}

	@Test
	void missingDerivativesAndSurpriseRemainNullAndUnavailable() {
		UUID id = UUID.randomUUID();
		List<NfmEventAttribution> out = NfmEventAttributionBuilder.build("BTCUSDT",
				List.of(event(id, "2024-01-01T04:30:00Z", NewsEventType.CPI, null)),
				List.of(entry("BTCUSDT", PositionSide.LONG, List.of(id), bd("5"), true, false)));
		NfmEventAttribution a = out.get(0);
		assertThat(a.oiChange()).isNull();
		assertThat(a.funding()).isNull();
		assertThat(a.liquidation()).isNull();
		assertThat(a.surprise()).isNull();
		assertThat(NfmValidationAnalytics.analyze(out).surpriseStatus())
				.isEqualTo("SURPRISE_DATA_UNAVAILABLE");
	}

	@Test
	void eventTypeAndSymbolAndDirectionAggregation() {
		UUID cpi = UUID.randomUUID();
		UUID nfp = UUID.randomUUID();
		List<HistoricalEvent> events = List.of(
				event(cpi, "2024-01-01T04:00:00Z", NewsEventType.CPI, bd("1.0")),
				event(nfp, "2024-01-01T04:30:00Z", NewsEventType.NFP, null));
		List<PartialExitBacktestEngine.Entry> entries = List.of(
				entry("BTCUSDT", PositionSide.LONG, List.of(cpi), bd("5"), true, false),
				entry("BTCUSDT", PositionSide.SHORT, List.of(nfp), bd("-2"), false, true));
		NfmValidationAnalytics.Report report =
				NfmValidationAnalytics.analyze(NfmEventAttributionBuilder.build("BTCUSDT", events, entries));

		assertThat(report.byEventType()).hasSize(2);
		assertThat(report.bySymbol()).hasSize(1);
		assertThat(report.bySymbol().get(0).symbol()).isEqualTo("BTCUSDT");
		assertThat(report.byDirection()).extracting(NfmValidationAnalytics.DirectionStats::direction)
				.containsExactlyInAnyOrder("LONG", "SHORT");
		assertThat(report.surpriseStatus()).isEqualTo("MEASURED");
		assertThat(report.attributionCoverage()).isEqualTo("MEASURED");
	}

	@Test
	void lifecycleHitRatesAreAggregated() {
		UUID id = UUID.randomUUID();
		List<NfmEventAttribution> out = NfmEventAttributionBuilder.build("BTCUSDT",
				List.of(event(id, "2024-01-01T04:30:00Z", NewsEventType.CPI, null)),
				List.of(entry("BTCUSDT", PositionSide.LONG, List.of(id), bd("5"), true, false),
						entry("BTCUSDT", PositionSide.LONG, List.of(id), bd("-2"), false, true)));
		NfmValidationAnalytics.LifecycleStats lc = NfmValidationAnalytics.lifecycle(out);
		assertThat(lc.trades()).isEqualTo(2);
		assertThat(lc.tp1HitRate()).isEqualByComparingTo("50");
		assertThat(lc.slRate()).isEqualByComparingTo("50");
		assertThat(lc.tp2HitRate()).isEqualByComparingTo("0");
	}

	@Test
	void noWinnerOrRankingFieldExists() {
		List<String> forbidden = List.of("winner", "best", "rank", "optimal", "superior");
		for (Class<?> type : List.of(NfmValidationAnalytics.Report.class,
				NfmValidationAnalytics.EventTypeStats.class, NfmValidationAnalytics.SymbolStats.class,
				NfmValidationAnalytics.DirectionStats.class, NfmValidationAnalytics.LifecycleStats.class,
				NfmEventAttribution.class)) {
			for (Field f : type.getDeclaredFields()) {
				String name = f.getName().toLowerCase();
				for (String bad : forbidden) {
					assertThat(name).as("%s.%s", type.getSimpleName(), f.getName()).doesNotContain(bad);
				}
			}
		}
	}
}
