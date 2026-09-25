package com.shyblack.cryptosignals.service;

import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.TradingStrategy;
import com.shyblack.cryptosignals.entity.enums.*;
import com.shyblack.cryptosignals.market.MarketBook;
import com.shyblack.cryptosignals.market.MarketTicker;
import com.shyblack.cryptosignals.repository.SignalRepository;
import com.shyblack.cryptosignals.service.strategy.StrategyResolver;
import com.shyblack.cryptosignals.signal.EntryCalculator;
import com.shyblack.cryptosignals.signal.FuturesSignalEngine;
import com.shyblack.cryptosignals.signal.ScoreCard;
import com.shyblack.cryptosignals.signal.SignalConstants;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FuturesSignalScheduler {

    private static final Logger log = LoggerFactory.getLogger(FuturesSignalScheduler.class);

    private final FuturesSignalEngine engine;
    private final SignalRepository signalRepository;
    private final MarketBook marketBook;
    private final SignalNotificationService signalNotificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final StrategyResolver strategyResolver;

    @Scheduled(cron = SignalConstants.SIGNAL_CRON)
    @Transactional
    public void runSignalCycle() {
        log.info("[FutSignalCycle] Starting Futures signal analysis cycle");

        Optional<TradingStrategy> strategyOpt = strategyResolver.resolveActive(TradingMode.FUTURES);
        if (strategyOpt.isEmpty()) {
            log.info("[FutSignalCycle] No active FUTURES strategy — skipping signal generation");
            return;
        }
        TradingStrategy strategy = strategyOpt.get();
        log.info("[FutSignalCycle] Using strategy: {} v{}", strategy.getName(), strategy.getVersion());

        int generated = 0;
        try {
            MarketRegime regime = engine.detectMarketRegime();
            log.info("[FutSignalCycle] Market regime: {}", regime);

            List<MarketTicker> tickers = marketBook.futuresTickers().snapshot();
            log.info("[FutSignalCycle] Analyzing {} Futures symbols", tickers.size());

            for (MarketTicker ticker : tickers) {
                String symbol = ticker.symbol();
                if (ticker.volume24h().doubleValue() < SignalConstants.MIN_VOLUME_USDT) continue;

                List<FuturesSignalEngine.FuturesSignalCandidate> candidates = engine.analyze(symbol, regime);
                for (FuturesSignalEngine.FuturesSignalCandidate candidate : candidates) {
                    if (!candidate.valid()) continue;
                    if (recentSignalExists(symbol, candidate.side())) {
                        log.debug("[FutSignalCycle] {} {} already has recent signal", symbol, candidate.side());
                        continue;
                    }
                    Signal signal = buildSignal(symbol, candidate, ticker, strategy);
                    signalRepository.save(signal);
                    try {
                        if (signal.getSignalGrade() == SignalGrade.STRONG_BUY || signal.getSignalGrade() == SignalGrade.BUY) {
                            eventPublisher.publishEvent(new SignalGeneratedEvent(signal.getId()));
                        }
                    } catch (Exception ex) {
                        log.warn("[FutSignalCycle] Event publish failed for {}: {}", symbol, ex.getMessage());
                    }
                    generated++;
                    log.info("[FutSignalCycle] Saved {} {} signal: score={} grade={}",
                            signal.getStatus(), symbol, candidate.scoreCard().total(), candidate.scoreCard().grade());
                }
            }
        } catch (Exception ex) {
            log.error("[FutSignalCycle] Unexpected error: {}", ex.getMessage(), ex);
        }
        log.info("[FutSignalCycle] Cycle complete. Generated {} new futures signals.", generated);
    }

    private boolean recentSignalExists(String symbol, PositionSide side) {
        Instant cutoff = Instant.now().minus(SignalConstants.SIGNAL_COOLDOWN_HOURS, ChronoUnit.HOURS);
        List<Signal> existing = signalRepository.findBySymbolAndTradingModeAndStatusIn(
                symbol, TradingMode.FUTURES, List.of(SignalStatus.ACTIVE, SignalStatus.PENDING));
        return existing.stream()
                .filter(s -> s.getSide() == side)
                .anyMatch(s -> s.getCreatedAt() != null && s.getCreatedAt().isAfter(cutoff));
    }

    private Signal buildSignal(String symbol, FuturesSignalEngine.FuturesSignalCandidate candidate,
                                MarketTicker ticker, TradingStrategy strategy) {
        ScoreCard card = candidate.scoreCard();
        EntryCalculator.EntryPlan plan = candidate.entryPlan();

        Signal s = new Signal();
        s.setSymbol(symbol);
        s.setSide(candidate.side());
        s.setTradingMode(TradingMode.FUTURES);
        s.setMarketRegime(candidate.marketRegime());
        s.setScore(card.total());
        s.setSignalGrade(card.grade());
        s.setEntryType(plan.entryType());
        s.setConfidence(card.total());
        s.setEntryPrice(bd(plan.entry(), 8));
        s.setTargetPrice(bd(plan.tp1(), 8));
        s.setTargetPrice2(bd(plan.tp2(), 8));
        s.setTargetPrice3(bd(plan.tp3(), 8));
        s.setStopLoss(bd(plan.stopLoss(), 8));
        s.setRiskReward(bd(plan.riskReward(), 4));
        s.setSuggestedRiskPercent(new BigDecimal("2.00"));
        s.setStrategy(strategy.getName() + " — " + candidate.side().name() + " " + plan.entryType().name());
        s.setStrategyId(strategy.getId());
        s.setStrategyVersion(strategy.getVersion());
        s.setStrategyWinRate(winRate(card.grade()));
        s.setTechnicalSummary(candidate.technicalSummary());
        s.setDisclaimer("This is not financial advice. Always do your own research.");
        s.setStatus(card.grade() == SignalGrade.STRONG_BUY || card.grade() == SignalGrade.BUY
                ? SignalStatus.ACTIVE : SignalStatus.PENDING);
        return s;
    }

    private static BigDecimal bd(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }

    private static BigDecimal winRate(SignalGrade grade) {
        return switch (grade) {
            case STRONG_BUY -> new BigDecimal("70.00");
            case BUY        -> new BigDecimal("62.00");
            case WATCH      -> new BigDecimal("52.00");
            default         -> new BigDecimal("40.00");
        };
    }
}
