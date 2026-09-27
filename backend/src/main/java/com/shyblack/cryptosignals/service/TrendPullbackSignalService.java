package com.shyblack.cryptosignals.service;

import com.shyblack.cryptosignals.dto.market.KlineResponse;
import com.shyblack.cryptosignals.dto.strategy.TrendPullbackConfig;
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
import com.shyblack.cryptosignals.signal.pullback.TrendPullbackAnalyzer;
import com.shyblack.cryptosignals.signal.pullback.TrendPullbackAssessment;
import com.shyblack.cryptosignals.signal.pullback.TrendRejectReason;
import com.shyblack.cryptosignals.signal.pullback.TimeframeAggregator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Live signal generation for the TREND_PULLBACK strategy.
 *
 * Fetches the configured higher + entry timeframes from Binance, discards the
 * still-forming candle, runs the shared {@link TrendPullbackAnalyzer}, and
 * persists actionable signals through the existing {@link Signal} model.
 */
@Service
public class TrendPullbackSignalService {

    private static final Logger log = LoggerFactory.getLogger(TrendPullbackSignalService.class);
    private static final String LOG_TAG = "[TREND_PULLBACK]";
    private static final int STRONG_BUY_SCORE = 85;

    private final BinanceRestClient restClient;
    private final MarketBook marketBook;
    private final SignalRepository signalRepository;
    private final StrategyResolver strategyResolver;
    private final ApplicationEventPublisher eventPublisher;

    public TrendPullbackSignalService(BinanceRestClient restClient, MarketBook marketBook,
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
        TrendPullbackConfig cfg = strategyResolver.parseTrendPullbackConfig(strategy);
        long cooldownMillis = cooldownMillis(cfg);
        Instant now = Instant.now();
        int generated = 0;

        List<MarketTicker> tickers = marketBook.spotTickers().snapshot();
        log.info("[SignalCycle] TREND_PULLBACK analyzing {} Spot symbols (HTF={} entry={})",
                tickers.size(), TimeframeAggregator.label(cfg.getHtf()),
                TimeframeAggregator.label(cfg.getEntryTimeframe()));

        int considered = 0;
        for (MarketTicker ticker : tickers) {
            String symbol = ticker.symbol();
            if (ticker.volume24h().doubleValue() < SignalConstants.MIN_VOLUME_USDT) {
                continue;
            }
            considered++;
            if (recentSignalExists(symbol, cooldownMillis, now)) {
                log.debug("{} symbol={} skipped=cooldown", LOG_TAG, symbol);
                continue;
            }
            try {
                List<KlineResponse> htf = closedCandles(
                        restClient.klines(symbol, cfg.getHtf(), cfg.getHtfCandleCount()), now);
                List<KlineResponse> entry = closedCandles(
                        restClient.klines(symbol, cfg.getEntryTimeframe(), cfg.getEntryCandleCount()), now);

                TrendPullbackAssessment a = TrendPullbackAnalyzer.analyze(symbol, htf, entry, cfg);
                log.debug("{} symbol={} {}", LOG_TAG, symbol, a.logLine());

                if (!a.actionable()) {
                    if (a.rejectReason() != TrendRejectReason.NONE) {
                        log.debug("{} symbol={} reason={}", LOG_TAG, symbol, a.rejectReason());
                    }
                    continue;
                }
                if (a.setupId() != null && signalRepository
                        .existsBySymbolAndTradingModeAndSetupId(symbol, TradingMode.SPOT, a.setupId())) {
                    log.info("{} symbol={} reason=DUPLICATE_SETUP setup={}",
                            LOG_TAG, symbol, a.setupId());
                    continue;
                }

                Signal signal = buildSignal(symbol, a, ticker, strategy);
                signalRepository.save(signal);
                try {
                    eventPublisher.publishEvent(new SignalGeneratedEvent(signal.getId()));
                } catch (Exception ex) {
                    log.warn("[SignalCycle] Notification publish failed for {}: {}", symbol, ex.getMessage());
                }
                generated++;
                log.info("[SignalCycle] TREND_PULLBACK saved {} signal: {} score={} grade={} setup={}",
                        signal.getStatus(), symbol, a.score(), signal.getSignalGrade(), a.setupId());
            } catch (Exception ex) {
                log.warn("{} symbol={} error={}", LOG_TAG, symbol, ex.getMessage());
            }
        }
        log.info("[SignalCycle] TREND_PULLBACK complete. considered={} generated={}", considered, generated);
        return generated;
    }

    // ── Internals ───────────────────────────────────────────────────────────

    private Signal buildSignal(String symbol, TrendPullbackAssessment a,
            MarketTicker ticker, TradingStrategy strategy) {
        SignalGrade grade = grade(a.score());

        Signal s = new Signal();
        s.setSymbol(symbol);
        s.setSide(PositionSide.LONG);
        s.setTradingMode(TradingMode.SPOT);
        s.setMarketRegime(a.trend());
        s.setScore(a.score());
        s.setSignalGrade(grade);
        s.setEntryType(EntryType.BREAKOUT_RETEST);
        s.setConfidence(a.score());
        s.setEntryPrice(bd(a.entry(), 8));
        s.setTargetPrice(bd(a.tp1(), 8));
        s.setTargetPrice2(bd(a.tp2(), 8));
        s.setTargetPrice3(bd(a.tp3(), 8));
        s.setStopLoss(bd(a.stopLoss(), 8));
        s.setRiskReward(bd(a.riskReward(), 4));
        s.setSuggestedRiskPercent(new BigDecimal("2.00"));
        s.setStrategy("Trend Pullback");
        s.setStrategyId(strategy.getId());
        s.setStrategyVersion(strategy.getVersion());
        s.setStrategyWinRate(winRate(grade));
        s.setTechnicalSummary(a.explanation());
        s.setDisclaimer("This is not financial advice. Always do your own research.");
        s.setSetupId(a.setupId());
        s.setStatus(grade == SignalGrade.STRONG_BUY || grade == SignalGrade.BUY
                ? SignalStatus.ACTIVE
                : SignalStatus.PENDING);
        return s;
    }

    private boolean recentSignalExists(String symbol, long cooldownMillis, Instant now) {
        if (cooldownMillis <= 0) return false;
        Instant cutoff = now.minusMillis(cooldownMillis);
        List<Signal> existing = signalRepository.findBySymbolAndTradingModeAndStatusIn(
                symbol, TradingMode.SPOT, List.of(SignalStatus.ACTIVE, SignalStatus.PENDING));
        return existing.stream()
                .anyMatch(s -> s.getCreatedAt() != null && s.getCreatedAt().isAfter(cutoff));
    }

    private static long cooldownMillis(TrendPullbackConfig cfg) {
        return (long) cfg.getCooldownCandles()
                * TimeframeAggregator.durationMillis(cfg.getEntryTimeframe());
    }

    /** Drop any candle that has not closed yet — signals use closed candles only. */
    static List<KlineResponse> closedCandles(List<KlineResponse> candles, Instant now) {
        List<KlineResponse> out = new ArrayList<>(candles == null ? List.of() : candles);
        long nowMs = now.toEpochMilli();
        while (!out.isEmpty() && out.get(out.size() - 1).closeTime() > nowMs) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    static SignalGrade grade(int score) {
        if (score >= STRONG_BUY_SCORE) return SignalGrade.STRONG_BUY;
        return SignalGrade.BUY;
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
