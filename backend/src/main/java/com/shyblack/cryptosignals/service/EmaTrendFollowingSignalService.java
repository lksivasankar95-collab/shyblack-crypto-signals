package com.shyblack.cryptosignals.service;

import com.shyblack.cryptosignals.dto.market.KlineResponse;
import com.shyblack.cryptosignals.dto.strategy.EMATrendFollowingConfig;
import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.TradingStrategy;
import com.shyblack.cryptosignals.entity.enums.EntryType;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.SignalGrade;
import com.shyblack.cryptosignals.entity.enums.SignalStatus;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.market.BinanceRestClient;
import com.shyblack.cryptosignals.market.MarketBook;
import com.shyblack.cryptosignals.market.MarketTicker;
import com.shyblack.cryptosignals.repository.SignalRepository;
import com.shyblack.cryptosignals.service.strategy.StrategyResolver;
import com.shyblack.cryptosignals.signal.SignalConstants;
import com.shyblack.cryptosignals.signal.emafollowing.EmaTrendFollowingAnalyzer;
import com.shyblack.cryptosignals.signal.emafollowing.EmaTrendFollowingAssessment;
import com.shyblack.cryptosignals.signal.emafollowing.EmaTrendFollowingReason;
import com.shyblack.cryptosignals.signal.pullback.TimeframeAggregator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Live signal generation for the EMA_TREND_FOLLOWING strategy. Fetches the
 * configured higher + entry timeframes, discards the still-forming candle, runs
 * the shared {@link EmaTrendFollowingAnalyzer} and persists actionable signals.
 */
@Service
public class EmaTrendFollowingSignalService {

	private static final Logger log = LoggerFactory.getLogger(EmaTrendFollowingSignalService.class);
	private static final String LOG_TAG = "[EMA_TREND_FOLLOWING]";
	private static final int STRONG_BUY_SCORE = 85;

	private final BinanceRestClient restClient;
	private final MarketBook marketBook;
	private final SignalRepository signalRepository;
	private final StrategyResolver strategyResolver;
	private final ApplicationEventPublisher eventPublisher;

	public EmaTrendFollowingSignalService(BinanceRestClient restClient, MarketBook marketBook,
			SignalRepository signalRepository, StrategyResolver strategyResolver,
			ApplicationEventPublisher eventPublisher) {
		this.restClient = restClient;
		this.marketBook = marketBook;
		this.signalRepository = signalRepository;
		this.strategyResolver = strategyResolver;
		this.eventPublisher = eventPublisher;
	}

	@Transactional
	public int runCycle(TradingStrategy strategy) {
		EMATrendFollowingConfig cfg = strategyResolver.parseEmaTrendFollowingConfig(strategy);
		long cooldownMillis = (long) cfg.getCooldownCandles()
				* TimeframeAggregator.durationMillis(cfg.getEntryTimeframe());
		Instant now = Instant.now();
		int generated = 0;

		List<MarketTicker> tickers = marketBook.spotTickers().snapshot();
		log.info("[SignalCycle] EMA_TREND_FOLLOWING analyzing {} Spot symbols (HTF={} entry={})",
				tickers.size(), TimeframeAggregator.label(cfg.getHtfTimeframe()),
				TimeframeAggregator.label(cfg.getEntryTimeframe()));

		for (MarketTicker ticker : tickers) {
			String symbol = ticker.symbol();
			if (ticker.volume24h().doubleValue() < SignalConstants.MIN_VOLUME_USDT) {
				continue;
			}
			if (recentSignalExists(symbol, cooldownMillis, now)) {
				continue;
			}
			try {
				List<KlineResponse> htf = TrendPullbackSignalService.closedCandles(
						restClient.klines(symbol, cfg.getHtfTimeframe(), cfg.getHtfCandleCount()), now);
				List<KlineResponse> entry = TrendPullbackSignalService.closedCandles(
						restClient.klines(symbol, cfg.getEntryTimeframe(), cfg.getEntryCandleCount()), now);

				EmaTrendFollowingAssessment a = EmaTrendFollowingAnalyzer.analyze(symbol, htf, entry, cfg);
				log.debug("{} symbol={} {}", LOG_TAG, symbol, a.logLine());

				if (!a.actionable()) {
					continue;
				}
				if (a.setupId() != null && signalRepository
						.existsBySymbolAndTradingModeAndSetupId(symbol, TradingMode.SPOT, a.setupId())) {
					log.info("{} symbol={} reason=DUPLICATE_SETUP setup={}", LOG_TAG, symbol, a.setupId());
					continue;
				}

				Signal signal = buildSignal(symbol, a, strategy);
				signalRepository.save(signal);
				try {
					eventPublisher.publishEvent(new SignalGeneratedEvent(signal.getId()));
				} catch (Exception ex) {
					log.warn("[SignalCycle] Notification publish failed for {}: {}", symbol, ex.getMessage());
				}
				generated++;
				log.info("[SignalCycle] EMA_TREND_FOLLOWING saved {} signal: {} score={} grade={} setup={}",
						signal.getStatus(), symbol, a.score(), signal.getSignalGrade(), a.setupId());
			} catch (Exception ex) {
				log.warn("{} symbol={} error={}", LOG_TAG, symbol, ex.getMessage());
			}
		}
		log.info("[SignalCycle] EMA_TREND_FOLLOWING complete. generated={}", generated);
		return generated;
	}

	private Signal buildSignal(String symbol, EmaTrendFollowingAssessment a, TradingStrategy strategy) {
		SignalGrade grade = grade(a.score());
		Signal s = new Signal();
		s.setSymbol(symbol);
		s.setSide(PositionSide.LONG);
		s.setTradingMode(TradingMode.SPOT);
		s.setMarketRegime(a.trend());
		s.setScore(a.score());
		s.setSignalGrade(grade);
		s.setEntryType(EntryType.BREAKOUT);
		s.setConfidence(a.score());
		s.setEntryPrice(bd(a.entry(), 8));
		s.setTargetPrice(bd(a.tp1(), 8));
		s.setTargetPrice2(bd(a.tp2(), 8));
		s.setTargetPrice3(bd(a.tp3(), 8));
		s.setStopLoss(bd(a.stopLoss(), 8));
		s.setRiskReward(bd(a.riskReward(), 4));
		s.setSuggestedRiskPercent(new BigDecimal("2.00"));
		s.setStrategy("EMA Trend Following");
		s.setStrategyId(strategy.getId());
		s.setStrategyVersion(strategy.getVersion());
		s.setStrategyWinRate(winRate(grade));
		s.setTechnicalSummary(a.explanation());
		s.setDisclaimer("This is not financial advice. Always do your own research.");
		s.setSetupId(a.setupId());
		s.setStatus(grade == SignalGrade.STRONG_BUY || grade == SignalGrade.BUY
				? SignalStatus.ACTIVE : SignalStatus.PENDING);
		return s;
	}

	private boolean recentSignalExists(String symbol, long cooldownMillis, Instant now) {
		if (cooldownMillis <= 0) return false;
		Instant cutoff = now.minusMillis(cooldownMillis);
		return signalRepository.findBySymbolAndTradingModeAndStatusIn(
						symbol, TradingMode.SPOT, List.of(SignalStatus.ACTIVE, SignalStatus.PENDING))
				.stream().anyMatch(x -> x.getCreatedAt() != null && x.getCreatedAt().isAfter(cutoff));
	}

	static SignalGrade grade(int score) {
		return score >= STRONG_BUY_SCORE ? SignalGrade.STRONG_BUY : SignalGrade.BUY;
	}

	private static BigDecimal bd(Double value, int scale) {
		double v = value == null ? 0.0 : value;
		return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP);
	}

	private static BigDecimal winRate(SignalGrade grade) {
		return switch (grade) {
			case STRONG_BUY -> new BigDecimal("72.00");
			case BUY        -> new BigDecimal("65.00");
			case WATCH      -> new BigDecimal("55.00");
			default         -> new BigDecimal("45.00");
		};
	}
}
