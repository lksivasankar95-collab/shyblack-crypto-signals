package com.shyblack.cryptosignals.service.backtest.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shyblack.cryptosignals.dto.market.KlineResponse;
import com.shyblack.cryptosignals.dto.strategy.NfmFuturesConfig;
import com.shyblack.cryptosignals.entity.enums.MarketRegime;
import com.shyblack.cryptosignals.entity.enums.NfmAction;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.market.DerivativesSnapshot;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalCandle;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalDerivativesProvider;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalEvent;
import com.shyblack.cryptosignals.signal.IndicatorEngine;
import com.shyblack.cryptosignals.signal.nfm.NfmAssessment;
import com.shyblack.cryptosignals.signal.nfm.NfmDecisionContext;
import com.shyblack.cryptosignals.signal.nfm.NfmEventView;
import com.shyblack.cryptosignals.signal.nfm.NfmFuturesAnalyzer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * NFM backtest strategy (spec §31–§35). Reuses the SAME
 * {@link NfmFuturesAnalyzer} the live pipeline uses, so backtest and live can
 * never disagree on decision logic.
 *
 * <p>Historical derivatives (OI / funding / liquidation) are not available, so
 * the analyzer receives an {@link DerivativesSnapshot#unavailable()} snapshot —
 * funding/liquidation are treated as UNKNOWN, never assumed zero.</p>
 *
 * <p>Look-ahead: the engine only passes events with {@code time <= candle.closeTime()};
 * this strategy additionally filters defensively within the given list.</p>
 */
@Component
public class NfmBacktestStrategy implements EventAwareBacktestStrategy, ConfigurableBacktestStrategy {

	public static final String ID = "NFM_FUTURES";
	public static final String VERSION = "v1";

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final HistoricalDerivativesProvider derivativesProvider;
	private final NfmFuturesConfig config;
	private int lastSignalIndex = Integer.MIN_VALUE;

	public NfmBacktestStrategy() {
		this(null, NfmFuturesConfig.defaults());
	}

	@org.springframework.beans.factory.annotation.Autowired
	public NfmBacktestStrategy(HistoricalDerivativesProvider derivativesProvider) {
		this(derivativesProvider, NfmFuturesConfig.defaults());
	}

	NfmBacktestStrategy(HistoricalDerivativesProvider derivativesProvider, NfmFuturesConfig config) {
		this.derivativesProvider = derivativesProvider;
		this.config = config == null ? NfmFuturesConfig.defaults() : config;
	}

	@Override public String id() { return ID; }
	@Override public String version() { return VERSION; }
	@Override public int warmup() { return Math.max(30, config.getAtrPeriod() + 2); }

	@Override
	public BacktestStrategy create(String paramsJson) {
		return new NfmBacktestStrategy(derivativesProvider, NfmFuturesConfig.fromJson(MAPPER, paramsJson));
	}

	@Override
	public Optional<Signal> evaluate(List<HistoricalCandle> history, int currentIndex,
			List<HistoricalEvent> eventsUpToNow) {
		if (history == null || history.isEmpty() || currentIndex + 1 < warmup()) {
			return Optional.empty();
		}
		HistoricalCandle current = history.get(currentIndex);
		if (current == null || current.closeTime() == null) {
			return Optional.empty();
		}

		// Cooldown between signals, expressed in candles off the configured minutes.
		int minGap = Math.max(1, config.getCooldownMinutes() / 5);
		if (lastSignalIndex != Integer.MIN_VALUE && currentIndex - lastSignalIndex < minGap) {
			return Optional.empty();
		}

		HistoricalEvent event = latestEligible(eventsUpToNow, current);
		if (event == null) {
			return Optional.empty();
		}

		List<KlineResponse> klines = toKlines(history);
		MarketRegime regime = regimeFrom(klines);
		NfmEventView view = new NfmEventView(event.id(), event.eventType(), event.eventCategory(),
				event.eventStage(), event.sourceTier(), event.relevance(), null,
				event.expectedValue(), event.actualValue(), event.surpriseValue(), null, event.time());

		NfmAssessment a = NfmFuturesAnalyzer.analyze(event.symbol(), view, klines,
				snapshot(event.symbol(), current.closeTime()), regime, config);
		if (!a.actionable() || a.entry() == null || a.stopLoss() == null || a.tp1() == null) {
			return Optional.empty();
		}
		lastSignalIndex = currentIndex;
		PositionSide side = a.action() == NfmAction.LONG ? PositionSide.LONG : PositionSide.SHORT;
		List<java.util.UUID> eventIds = eligibleEventIds(eventsUpToNow, current);
		NfmDecisionContext context = decisionContext(event, a, regime, eventIds, current);
		return Optional.of(new Signal(side, a.entry(), a.stopLoss(), a.tp1(), a.tp2(), a.tp3(),
				"NFM grade=" + a.grade() + " score=" + a.score() + " " + a.reason(), eventIds, context));
	}

	/** As-of derivatives snapshot; unavailable on any failure (never zero). */
	private DerivativesSnapshot snapshot(String symbol, java.time.Instant time) {
		if (derivativesProvider == null || symbol == null || time == null) {
			return DerivativesSnapshot.unavailable();
		}
		try {
			DerivativesSnapshot s = derivativesProvider.asOf(symbol, time);
			return s == null ? DerivativesSnapshot.unavailable() : s;
		} catch (Exception ex) {
			return DerivativesSnapshot.unavailable();
		}
	}

	private HistoricalEvent latestEligible(List<HistoricalEvent> events, HistoricalCandle current) {
		if (events == null || events.isEmpty()) {
			return null;
		}
		java.time.Instant newestAllowed = current.closeTime();
		java.time.Instant oldestAllowed = newestAllowed.minus(Duration.ofMinutes(config.getEventMaxAgeMinutes()));
		HistoricalEvent best = null;
		for (HistoricalEvent e : events) {
			if (e.time() == null) continue;
			if (e.time().isAfter(newestAllowed) || e.time().isBefore(oldestAllowed)) continue;
			if (best == null || e.time().isAfter(best.time())) {
				best = e;
			}
		}
		return best;
	}

	/**
	 * Observational snapshot built ONLY from values the analyzer already produced.
	 * No recalculation, no effect on the decision.
	 */
	private static NfmDecisionContext decisionContext(HistoricalEvent event, NfmAssessment a,
			MarketRegime regime, List<java.util.UUID> eventIds, HistoricalCandle current) {
		Long ageSeconds = event == null || event.time() == null || current == null
				|| current.closeTime() == null ? null
				: Duration.between(event.time(), current.closeTime()).getSeconds();
		return new NfmDecisionContext(
				eventIds == null ? List.of() : List.copyOf(eventIds),
				a.score(),
				a.grade() == null ? null : a.grade().name(),
				event == null || event.eventType() == null ? null : event.eventType().name(),
				event == null || event.eventStage() == null ? null : event.eventStage().name(),
				event == null || event.sourceTier() == null ? null : event.sourceTier().name(),
				a.priceReactionPct(),
				a.volumeMultiplier(),
				a.oiChangePct(),
				null, // funding rate value not exposed by the assessment (state only)
				null, // liquidation volume value not exposed by the assessment (state only)
				regime == null ? null : regime.name(),
				a.action() == null ? null : a.action().name(),
				a.actionable() ? null : a.reason(),
				ageSeconds,
				event == null ? null : event.expectedValue(),
				event == null ? null : event.actualValue(),
				event == null ? null : event.surpriseValue());
	}

	/**
	 * All events causally eligible for the current candle (same visibility rule
	 * as {@link #latestEligible}), preserving every relevant id. Additive
	 * analytics metadata only — the decision still uses the single latest event.
	 */
	private List<java.util.UUID> eligibleEventIds(List<HistoricalEvent> events, HistoricalCandle current) {
		if (events == null || events.isEmpty() || current == null || current.closeTime() == null) {
			return List.of();
		}
		List<java.util.UUID> ids = new ArrayList<>();
		for (HistoricalEvent e : events) {
			if (e.id() == null) continue;
			if (!com.shyblack.cryptosignals.service.backtest.research.NfmEventAttributionBuilder
					.causallyEligible(e, current.closeTime(), config.getEventMaxAgeMinutes())) continue;
			ids.add(e.id());
		}
		return ids;
	}

	private static List<KlineResponse> toKlines(List<HistoricalCandle> history) {
		List<KlineResponse> klines = new ArrayList<>(history.size());
		for (HistoricalCandle candle : history) {
			klines.add(candle.toKline());
		}
		return klines;
	}

	private static MarketRegime regimeFrom(List<KlineResponse> klines) {
		IndicatorEngine.Indicators ind = IndicatorEngine.compute(klines);
		double close = ind.lastClose();
		double ema50 = ind.lastEma50();
		double ema200 = ind.lastEma200();
		if (Double.isNaN(ema50) || Double.isNaN(ema200)) {
			return MarketRegime.NEUTRAL;
		}
		if (close > ema200 && ema50 > ema200) return MarketRegime.BULLISH;
		if (close < ema200 && ema50 < ema200) return MarketRegime.BEARISH;
		return MarketRegime.NEUTRAL;
	}
}
