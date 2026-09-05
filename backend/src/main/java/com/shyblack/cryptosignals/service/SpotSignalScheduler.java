package com.shyblack.cryptosignals.service;

import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.enums.MarketRegime;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.SignalGrade;
import com.shyblack.cryptosignals.entity.enums.SignalStatus;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.market.MarketBook;
import com.shyblack.cryptosignals.market.MarketTicker;
import com.shyblack.cryptosignals.repository.SignalRepository;
import com.shyblack.cryptosignals.signal.EntryCalculator;
import com.shyblack.cryptosignals.signal.ScoreCard;
import com.shyblack.cryptosignals.signal.SignalConstants;
import com.shyblack.cryptosignals.signal.SpotSignalEngine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpotSignalScheduler {

    private static final Logger log = LoggerFactory.getLogger(SpotSignalScheduler.class);

    private final SpotSignalEngine engine;
    private final SignalRepository signalRepository;
    private final MarketBook marketBook;

    @Scheduled(cron = SignalConstants.SIGNAL_CRON)
    @Transactional
    public void runSignalCycle() {
        log.info("[SignalCycle] Starting Spot signal analysis cycle");
        int generated = 0;

        try {
            MarketRegime regime = engine.detectMarketRegime();
            log.info("[SignalCycle] Market regime: {}", regime);

            // Skip entire cycle in bearish regime to avoid generating bad signals
            if (regime == MarketRegime.BEARISH) {
                log.info("[SignalCycle] BEARISH regime — skipping spot signal generation");
                return;
            }

            List<MarketTicker> tickers = marketBook.spotTickers().snapshot();
            log.info("[SignalCycle] Analyzing {} Spot symbols", tickers.size());

            for (MarketTicker ticker : tickers) {
                String symbol = ticker.symbol();

                // Volume gate
                if (ticker.volume24h().doubleValue() < SignalConstants.MIN_VOLUME_USDT) {
                    continue;
                }

                // Deduplication: skip if active/pending signal already exists within cooldown
                if (recentSignalExists(symbol)) {
                    log.debug("[SignalCycle] {} already has recent signal, skipping", symbol);
                    continue;
                }

                Optional<SpotSignalEngine.SignalCandidate> candidateOpt = engine.analyze(symbol, regime);
                if (candidateOpt.isEmpty()) continue;

                SpotSignalEngine.SignalCandidate candidate = candidateOpt.get();
                ScoreCard card = candidate.scoreCard();
                EntryCalculator.EntryPlan plan = candidate.entryPlan();
                SignalGrade grade = card.grade();

                // Only persist actionable signals
                if (!candidate.valid()) continue;

                Signal signal = buildSignal(symbol, candidate, ticker);
                signalRepository.save(signal);
                generated++;

                log.info("[SignalCycle] Saved {} signal: {} score={} grade={}",
                        signal.getStatus(), symbol, card.total(), grade);
            }

        } catch (Exception ex) {
            log.error("[SignalCycle] Unexpected error during signal cycle: {}", ex.getMessage(), ex);
        }

        log.info("[SignalCycle] Cycle complete. Generated {} new signals.", generated);
    }

    private boolean recentSignalExists(String symbol) {
        Instant cutoff = Instant.now().minus(SignalConstants.SIGNAL_COOLDOWN_HOURS, ChronoUnit.HOURS);
        List<Signal> existing = signalRepository.findBySymbolAndTradingModeAndStatusIn(
                symbol, TradingMode.SPOT,
                List.of(SignalStatus.ACTIVE, SignalStatus.PENDING));
        return existing.stream().anyMatch(s -> s.getCreatedAt() != null && s.getCreatedAt().isAfter(cutoff));
    }

    private Signal buildSignal(String symbol, SpotSignalEngine.SignalCandidate candidate, MarketTicker ticker) {
        ScoreCard card = candidate.scoreCard();
        EntryCalculator.EntryPlan plan = candidate.entryPlan();
        SignalGrade grade = card.grade();
        double change24hPct = ticker.changePercent24h().doubleValue();

        Signal s = new Signal();
        s.setSymbol(symbol);
        s.setSide(PositionSide.LONG);
        s.setTradingMode(TradingMode.SPOT);
        s.setMarketRegime(candidate.marketRegime());
        s.setScore(card.total());
        s.setSignalGrade(grade);
        s.setEntryType(plan.entryType());
        s.setConfidence(card.total());
        s.setEntryPrice(bd(plan.entry(), 8));
        s.setTargetPrice(bd(plan.tp1(), 8));
        s.setTargetPrice2(bd(plan.tp2(), 8));
        s.setTargetPrice3(bd(plan.tp3(), 8));
        s.setStopLoss(bd(plan.stopLoss(), 8));
        s.setRiskReward(bd(plan.riskReward(), 4));
        s.setSuggestedRiskPercent(new BigDecimal("2.00"));
        s.setStrategy("Spot Morning Plan — " + plan.entryType().name());
        s.setStrategyWinRate(winRate(grade));
        s.setTechnicalSummary(candidate.technicalSummary());
        s.setDisclaimer("This is not financial advice. Always do your own research.");

        s.setStatus(grade == SignalGrade.STRONG_BUY || grade == SignalGrade.BUY
                ? SignalStatus.ACTIVE
                : SignalStatus.PENDING);

        return s;
    }

    private static BigDecimal bd(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
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
