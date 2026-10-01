package com.shyblack.cryptosignals.service;

import com.shyblack.cryptosignals.dto.market.KlineResponse;
import com.shyblack.cryptosignals.dto.strategy.NfmFuturesConfig;
import com.shyblack.cryptosignals.entity.NewsEvent;
import com.shyblack.cryptosignals.entity.NewsEventAsset;
import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.SignalNfmContext;
import com.shyblack.cryptosignals.entity.TradingStrategy;
import com.shyblack.cryptosignals.entity.enums.EntryType;
import com.shyblack.cryptosignals.entity.enums.NfmGrade;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.SignalGrade;
import com.shyblack.cryptosignals.entity.enums.SignalStatus;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.market.BinanceFuturesRestClient;
import com.shyblack.cryptosignals.market.DerivativesSnapshot;
import com.shyblack.cryptosignals.market.FuturesDerivativesService;
import com.shyblack.cryptosignals.market.MarketBook;
import com.shyblack.cryptosignals.market.MarketTicker;
import com.shyblack.cryptosignals.repository.NewsEventRepository;
import com.shyblack.cryptosignals.repository.SignalNfmContextRepository;
import com.shyblack.cryptosignals.repository.SignalRepository;
import com.shyblack.cryptosignals.service.strategy.StrategyResolver;
import com.shyblack.cryptosignals.signal.FuturesSignalEngine;
import com.shyblack.cryptosignals.signal.SignalConstants;
import com.shyblack.cryptosignals.signal.nfm.NfmAssessment;
import com.shyblack.cryptosignals.signal.nfm.NfmEventView;
import com.shyblack.cryptosignals.signal.nfm.NfmFuturesAnalyzer;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Live signal generation for the NFM_FUTURES strategy (spec §27–§28). Consumes
 * normalized news events, evaluates them against futures price/derivatives
 * data, deduplicates, and persists signals through the existing pipeline.
 *
 * <p>This service is selected by {@code engineKey=NFM_FUTURES} on the active
 * FUTURES strategy. It never writes SPOT signals.</p>
 */
@Service
public class NfmFuturesSignalService {

	private static final Logger log = LoggerFactory.getLogger(NfmFuturesSignalService.class);
	private static final String LOG_TAG = "[NFM]";

	private final NewsEventRepository eventRepository;
	private final BinanceFuturesRestClient futuresRestClient;
	private final FuturesDerivativesService derivativesService;
	private final FuturesSignalEngine futuresSignalEngine;
	private final MarketBook marketBook;
	private final SignalRepository signalRepository;
	private final SignalNfmContextRepository nfmContextRepository;
	private final StrategyResolver strategyResolver;
	private final ApplicationEventPublisher eventPublisher;

	public NfmFuturesSignalService(NewsEventRepository eventRepository,
			BinanceFuturesRestClient futuresRestClient, FuturesDerivativesService derivativesService,
			FuturesSignalEngine futuresSignalEngine, MarketBook marketBook, SignalRepository signalRepository,
			SignalNfmContextRepository nfmContextRepository, StrategyResolver strategyResolver,
			ApplicationEventPublisher eventPublisher) {
		this.eventRepository = eventRepository;
		this.futuresRestClient = futuresRestClient;
		this.derivativesService = derivativesService;
		this.futuresSignalEngine = futuresSignalEngine;
		this.marketBook = marketBook;
		this.signalRepository = signalRepository;
		this.nfmContextRepository = nfmContextRepository;
		this.strategyResolver = strategyResolver;
		this.eventPublisher = eventPublisher;
	}

	@Transactional
	public int runCycle(TradingStrategy strategy) {
		NfmFuturesConfig cfg = strategyResolver.parseNfmFuturesConfig(strategy);
		Instant now = Instant.now();
		Instant since = now.minus(cfg.getEventMaxAgeMinutes(), ChronoUnit.MINUTES);
		List<NewsEvent> events = eventRepository
				.findByTradeableTrueAndEventTimeAfterOrderByEventTimeDesc(since);
		if (events.isEmpty()) {
			log.info("{} cycle: no recent tradeable events", LOG_TAG);
			return 0;
		}

		Map<String, MarketTicker> futures = new HashMap<>();
		for (MarketTicker ticker : marketBook.futuresTickers().snapshot()) {
			futures.put(ticker.symbol(), ticker);
		}
		com.shyblack.cryptosignals.entity.enums.MarketRegime regime;
		try {
			regime = futuresSignalEngine.detectMarketRegime();
		} catch (Exception ex) {
			regime = com.shyblack.cryptosignals.entity.enums.MarketRegime.NEUTRAL;
		}

		int generated = 0;
		for (NewsEvent event : events) {
			for (NewsEventAsset asset : event.getAssets()) {
				String symbol = asset.getSymbol() == null ? null : asset.getSymbol().toUpperCase() + "USDT";
				if (symbol == null || !cfg.symbolAllowed(symbol)) {
					continue;
				}
				MarketTicker ticker = futures.get(symbol);
				if (ticker == null || ticker.volume24h().doubleValue() < SignalConstants.MIN_VOLUME_USDT) {
					continue;
				}
				try {
					if (processEventSymbol(strategy, cfg, event, asset, symbol, regime, now)) {
						generated++;
					}
				} catch (Exception ex) {
					log.warn("{} event={} symbol={} error={}", LOG_TAG, event.getId(), symbol, ex.getMessage());
				}
			}
		}
		log.info("{} cycle complete. events={} generated={}", LOG_TAG, events.size(), generated);
		return generated;
	}

	private boolean processEventSymbol(TradingStrategy strategy, NfmFuturesConfig cfg, NewsEvent event,
			NewsEventAsset asset, String symbol, com.shyblack.cryptosignals.entity.enums.MarketRegime regime,
			Instant now) {
		// Cooldown: same symbol + mode within the configured window (spec §26).
		if (recentSignalExists(symbol, cfg, now)) {
			log.debug("{} symbol={} skipped reason=COOLDOWN", LOG_TAG, symbol);
			return false;
		}

		List<KlineResponse> raw = futuresRestClient.klines(symbol, cfg.getReactionTimeframe(),
				cfg.getReactionCandleCount());
		List<KlineResponse> candles = TrendPullbackSignalService.closedCandles(raw, now);
		DerivativesSnapshot derivatives = derivativesService.snapshot(symbol);

		NfmEventView view = new NfmEventView(event.getId(), event.getEventType(), event.getEventCategory(),
				event.getEventStage(), event.getSourceTier(), asset.getRelevanceLevel(), event.getEventConfidence(),
				event.getExpectedValue(), event.getActualValue(), event.getSurpriseValue(),
				event.getSurpriseDirection(), event.getEventTime());

		NfmAssessment assessment = NfmFuturesAnalyzer.analyze(symbol, view, candles, derivatives, regime, cfg);
		log.debug("{} symbol={} event={} action={} score={} reason={}",
				LOG_TAG, symbol, event.getId(), assessment.action(), assessment.score(), assessment.reason());

		if (!assessment.actionable()) {
			return false;
		}
		// Exact-setup dedup (spec §25).
		if (assessment.setupId() != null && signalRepository.existsBySymbolAndTradingModeAndSetupId(
				symbol, TradingMode.FUTURES, assessment.setupId())) {
			log.info("{} symbol={} skipped reason=DUPLICATE_SETUP setup={}", LOG_TAG, symbol, assessment.setupId());
			return false;
		}

		Signal signal = buildSignal(strategy, event, asset, symbol, assessment);
		signal = signalRepository.save(signal);
		persistContext(signal, event, assessment, cfg);

		try {
			if (signal.getSignalGrade() == SignalGrade.STRONG_BUY || signal.getSignalGrade() == SignalGrade.BUY) {
				eventPublisher.publishEvent(new SignalGeneratedEvent(signal.getId()));
			}
		} catch (Exception ex) {
			log.warn("{} event publish failed symbol={} err={}", LOG_TAG, symbol, ex.getMessage());
		}
		log.info("{} saved {} {} signal: grade={} score={} event={}",
				LOG_TAG, signal.getStatus(), symbol, signal.getSignalGrade(), assessment.score(), event.getId());
		return true;
	}

	private Signal buildSignal(TradingStrategy strategy, NewsEvent event, NewsEventAsset asset,
			String symbol, NfmAssessment a) {
		SignalGrade grade = mapGrade(a.grade());
		Signal s = new Signal();
		s.setSymbol(symbol);
		s.setSide(a.action() == com.shyblack.cryptosignals.entity.enums.NfmAction.LONG
				? PositionSide.LONG : PositionSide.SHORT);
		s.setTradingMode(TradingMode.FUTURES);
		s.setMarketRegime(event.getMarketRegime());
		s.setScore(a.score());
		s.setSignalGrade(grade);
		s.setEntryType(EntryType.BREAKOUT);
		s.setConfidence(a.score());
		s.setEntryPrice(a.entry());
		s.setTargetPrice(a.tp1());
		s.setTargetPrice2(a.tp2());
		s.setTargetPrice3(a.tp3());
		s.setStopLoss(a.stopLoss());
		s.setRiskReward(a.riskReward());
		s.setSuggestedRiskPercent(new BigDecimal("2.00"));
		s.setStrategy(strategy.getName() + " — " + a.action().name()
				+ " " + event.getEventType().name());
		s.setStrategyId(strategy.getId());
		s.setStrategyVersion(strategy.getVersion());
		s.setStrategyWinRate(winRate(grade));
		s.setSetupId(a.setupId());
		s.setTechnicalSummary(trim(a.reason() + " | event=" + event.getEventType()
				+ " source=" + event.getSource() + " stage=" + event.getEventStage()
				+ " headline=" + event.getHeadline(), 2000));
		s.setDisclaimer("This is not financial advice. Event-driven futures strategy. "
				+ "News is the catalyst; market reaction is the confirmation.");
		s.setStatus(grade == SignalGrade.STRONG_BUY || grade == SignalGrade.BUY
				? SignalStatus.ACTIVE : SignalStatus.PENDING);
		return s;
	}

	private void persistContext(Signal signal, NewsEvent event, NfmAssessment a,
			NfmFuturesConfig cfg) {
		SignalNfmContext ctx = new SignalNfmContext();
		ctx.setSignal(signal);
		ctx.setNewsEventId(event.getId());
		ctx.setEventType(event.getEventType() == null ? null : event.getEventType().name());
		ctx.setEventCategory(event.getEventCategory() == null ? null : event.getEventCategory().name());
		ctx.setEventStage(event.getEventStage() == null ? null : event.getEventStage().name());
		ctx.setSourceTier(event.getSourceTier() == null ? null : event.getSourceTier().name());
		ctx.setSource(event.getSource());
		ctx.setEventTime(event.getEventTime());
		ctx.setExpectedValue(event.getExpectedValue());
		ctx.setActualValue(event.getActualValue());
		ctx.setSurpriseValue(event.getSurpriseValue());
		ctx.setSurpriseDirection(event.getSurpriseDirection());
		ctx.setPriceReactionPct(a.priceReactionPct());
		ctx.setVolumeMultiplier(a.volumeMultiplier());
		ctx.setOpenInterestChangePct(a.oiChangePct());
		ctx.setFundingState(a.fundingState() == null ? null : a.fundingState().name());
		ctx.setLiquidationState(a.liquidationState() == null ? null : a.liquidationState().name());
		ctx.setEventConfluenceScore(a.score());
		ctx.setMarketInterpretation(event.getMarketInterpretation());
		ctx.setConfigVersion(cfg.getConfigVersion());
		nfmContextRepository.save(ctx);
	}

	private boolean recentSignalExists(String symbol, NfmFuturesConfig cfg, Instant now) {
		if (cfg.getCooldownMinutes() <= 0) return false;
		Instant cutoff = now.minus(cfg.getCooldownMinutes(), ChronoUnit.MINUTES);
		return signalRepository.findBySymbolAndTradingModeAndStatusIn(
						symbol, TradingMode.FUTURES, List.of(SignalStatus.ACTIVE, SignalStatus.PENDING))
				.stream().anyMatch(x -> x.getCreatedAt() != null && x.getCreatedAt().isAfter(cutoff));
	}

	private static SignalGrade mapGrade(NfmGrade grade) {
		if (grade == null) return SignalGrade.WATCH;
		return switch (grade) {
			case A -> SignalGrade.STRONG_BUY;
			case B -> SignalGrade.BUY;
			case C -> SignalGrade.WATCH;
		};
	}

	private static BigDecimal winRate(SignalGrade grade) {
		return switch (grade) {
			case STRONG_BUY -> new BigDecimal("68.00");
			case BUY -> new BigDecimal("60.00");
			case WATCH -> new BigDecimal("52.00");
			default -> new BigDecimal("40.00");
		};
	}

	private static String trim(String value, int max) {
		if (value == null) return null;
		return value.length() <= max ? value : value.substring(0, max);
	}
}
