package com.shyblack.cryptosignals.signal;

import com.shyblack.cryptosignals.dto.market.KlineResponse;
import com.shyblack.cryptosignals.entity.enums.EntryType;
import com.shyblack.cryptosignals.entity.enums.MarketRegime;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.SignalGrade;
import com.shyblack.cryptosignals.market.BinanceFuturesRestClient;
import com.shyblack.cryptosignals.market.BinanceRestClient;
import com.shyblack.cryptosignals.market.MarketBook;
import com.shyblack.cryptosignals.market.MarketTicker;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Futures signal engine — generates LONG and SHORT candidates from futures market data.
 * Uses the same IndicatorEngine and StructureAnalyzer as the spot engine, but:
 * - Fetches futures klines (BinanceFuturesRestClient)
 * - Generates both LONG (bullish) and SHORT (bearish) candidates
 * - Does not skip BEARISH regime — SHORT signals are valid in bearish markets
 */
@Component
public class FuturesSignalEngine {

    private static final Logger log = LoggerFactory.getLogger(FuturesSignalEngine.class);

    private final BinanceFuturesRestClient futuresRestClient;
    private final BinanceRestClient spotRestClient; // for BTC regime detection
    private final MarketBook marketBook;

    public FuturesSignalEngine(BinanceFuturesRestClient futuresRestClient,
                                BinanceRestClient spotRestClient,
                                MarketBook marketBook) {
        this.futuresRestClient = futuresRestClient;
        this.spotRestClient = spotRestClient;
        this.marketBook = marketBook;
    }

    public record FuturesSignalCandidate(
            String symbol,
            PositionSide side,
            ScoreCard scoreCard,
            EntryCalculator.EntryPlan entryPlan,
            MarketRegime marketRegime,
            String technicalSummary,
            boolean valid
    ) {}

    public MarketRegime detectMarketRegime() {
        try {
            List<KlineResponse> btc4h = spotRestClient.klines("BTCUSDT", "4h", SignalConstants.CANDLES_4H);
            if (btc4h.size() < 210) return MarketRegime.NEUTRAL;
            IndicatorEngine.Indicators ind = IndicatorEngine.compute(btc4h);
            double price = ind.lastClose();
            if (price > ind.lastEma200() && ind.lastEma50() > ind.lastEma200()) return MarketRegime.BULLISH;
            if (price < ind.lastEma200() && ind.lastEma50() < ind.lastEma200()) return MarketRegime.BEARISH;
            return MarketRegime.NEUTRAL;
        } catch (Exception ex) {
            log.warn("[FutSignal] Cannot determine market regime: {}", ex.getMessage());
            return MarketRegime.NEUTRAL;
        }
    }

    public List<FuturesSignalCandidate> analyze(String symbol, MarketRegime regime) {
        List<FuturesSignalCandidate> results = new ArrayList<>();
        try {
            Optional<MarketTicker> tickerOpt = marketBook.futuresTickers().get(symbol);
            if (tickerOpt.isEmpty()) return results;
            MarketTicker ticker = tickerOpt.get();
            double volume24h    = ticker.volume24h().doubleValue();
            double livePrice    = ticker.price().doubleValue();
            double change24hPct = ticker.changePercent24h().doubleValue();

            if (volume24h < SignalConstants.MIN_VOLUME_USDT) return results;

            List<KlineResponse> candles4h  = futuresRestClient.klines(symbol, "4h",  SignalConstants.CANDLES_4H);
            List<KlineResponse> candles1h  = futuresRestClient.klines(symbol, "1h",  SignalConstants.CANDLES_1H);
            List<KlineResponse> candles15m = futuresRestClient.klines(symbol, "15m", SignalConstants.CANDLES_15M);

            if (candles4h.size() < 210 || candles1h.size() < 60 || candles15m.size() < 50) return results;

            IndicatorEngine.Indicators ind4h  = IndicatorEngine.compute(candles4h);
            IndicatorEngine.Indicators ind1h  = IndicatorEngine.compute(candles1h);
            IndicatorEngine.Indicators ind15m = IndicatorEngine.compute(candles15m);

            double[] high4h  = extractHigh(candles4h);
            double[] low4h   = extractLow(candles4h);
            double[] high15m = extractHigh(candles15m);
            double[] low15m  = extractLow(candles15m);

            StructureAnalyzer.StructureSummary structure4h  = StructureAnalyzer.analyze(high4h, low4h, ind4h.close(), 20);
            StructureAnalyzer.StructureSummary structure15m = StructureAnalyzer.analyze(high15m, low15m, ind15m.close(), 10);

            // LONG candidate (bullish setup)
            if (regime == MarketRegime.BULLISH || regime == MarketRegime.NEUTRAL) {
                int regimeScore = SpotScorer.scoreMarketRegime(regime);
                int trend4h     = SpotScorer.scoreTrend4h(ind4h, change24hPct);
                int conf1h      = SpotScorer.scoreConfirmation1h(ind1h);
                int entry15m    = SpotScorer.scoreEntrySetup15m(ind15m, structure15m);
                int volumeScore = SpotScorer.scoreVolume(ind15m, change24hPct);
                int structScore = SpotScorer.scoreStructure(structure4h, livePrice);
                EntryCalculator.EntryPlan plan = EntryCalculator.calculate(ind15m, structure15m, livePrice);
                int rrScore = SpotScorer.scoreRiskReward(plan.riskReward());
                ScoreCard card = new ScoreCard(regimeScore, trend4h, conf1h, entry15m, volumeScore, structScore, rrScore);
                if (plan.valid() && card.grade() != SignalGrade.NO_TRADE) {
                    String summary = buildSummary(symbol, PositionSide.LONG, card, ind4h, ind1h, change24hPct);
                    results.add(new FuturesSignalCandidate(symbol, PositionSide.LONG, card, plan, regime, summary, true));
                }
            }

            // SHORT candidate (bearish setup — inverse scoring)
            if (regime == MarketRegime.BEARISH || regime == MarketRegime.NEUTRAL) {
                int regimeSc = regime == MarketRegime.BEARISH ? 10 : 4;
                int trend4hSc = scoreBearishTrend4h(ind4h, change24hPct);
                int conf1hSc  = scoreBearishConfirmation1h(ind1h);
                int entry15mSc = scoreBearishEntrySetup15m(ind15m, structure15m);
                int volumeSc = SpotScorer.scoreVolume(ind15m, change24hPct);
                int structSc = scoreShortStructure(structure4h, livePrice);
                EntryCalculator.EntryPlan shortPlan = calculateShortPlan(ind15m, structure15m, livePrice);
                int rrSc = SpotScorer.scoreRiskReward(shortPlan.riskReward());
                ScoreCard card = new ScoreCard(regimeSc, trend4hSc, conf1hSc, entry15mSc, volumeSc, structSc, rrSc);
                if (shortPlan.valid() && card.grade() != SignalGrade.NO_TRADE) {
                    String summary = buildSummary(symbol, PositionSide.SHORT, card, ind4h, ind1h, change24hPct);
                    results.add(new FuturesSignalCandidate(symbol, PositionSide.SHORT, card, shortPlan, regime, summary, true));
                }
            }
        } catch (Exception ex) {
            log.warn("[FutSignal] Error analyzing {}: {}", symbol, ex.getMessage());
        }
        return results;
    }

    // ── Short-side scoring (inverse of spot long scoring) ──────────────────────

    private static int scoreBearishTrend4h(IndicatorEngine.Indicators ind, double change24h) {
        double price = ind.lastClose();
        int score = 0;
        if (price < ind.lastEma200() && ind.lastEma50() < ind.lastEma200() && price < ind.lastEma20()) score = 20;
        else if (price < ind.lastEma200() && ind.lastEma50() < ind.lastEma200()) score = 15;
        else if (price < ind.lastEma200()) score = 8;
        else if (price < ind.lastEma50()) score = 4;
        if (change24h < -15.0) score = Math.max(0, score - 4);
        if (change24h < -30.0) score = Math.max(0, score - 8);
        return score;
    }

    private static int scoreBearishConfirmation1h(IndicatorEngine.Indicators ind) {
        int met = 0;
        if (ind.lastEma20() < ind.lastEma50())  met++;
        if (ind.lastClose() < ind.lastEma20())  met++;
        if (ind.lastRsi() < 50)                 met++;
        if (ind.lastMacdHist() < 0)             met++;
        int score = switch (met) { case 4 -> 20; case 3 -> 14; case 2 -> 8; default -> 2; };
        if (ind.lastRsi() < 20) score = Math.max(0, score - 5); // extreme oversold penalty
        return score;
    }

    private static int scoreBearishEntrySetup15m(IndicatorEngine.Indicators ind,
                                                  StructureAnalyzer.StructureSummary structure) {
        int met = 0;
        if (!structure.higherLows() && !structure.higherHighs()) met++; // lower lows = bearish structure
        if (ind.lastVolume() > ind.lastVolumeMa() * 1.2)        met++;
        if (ind.lastMacdHist() < 0 && ind.lastMacdHist() < ind.prevMacdHist()) met++;
        if (ind.lastClose() < ind.lastEma20())                   met++;
        return switch (met) { case 4 -> 20; case 3 -> 14; case 2 -> 8; case 1 -> 4; default -> 0; };
    }

    private static int scoreShortStructure(StructureAnalyzer.StructureSummary structure, double price) {
        if (!structure.higherLows() && !structure.higherHighs()) return 10; // lower highs + lower lows
        if (!structure.higherHighs()) return 6; // only lower highs
        return 3;
    }

    private static EntryCalculator.EntryPlan calculateShortPlan(IndicatorEngine.Indicators ind,
                                                                  StructureAnalyzer.StructureSummary structure,
                                                                  double livePrice) {
        double nearestResistance = structure.nearestResistance(livePrice);
        double nearestSupport    = structure.nearestSupport(livePrice);
        double atr = ind.lastAtr();

        double entry = livePrice;
        double stop  = nearestResistance + (atr * SignalConstants.ATR_SL_BUFFER);
        stop = Math.max(stop, entry * 1.005); // at least 0.5% above entry

        double risk = stop - entry;
        if (risk <= 0) return new EntryCalculator.EntryPlan(entry, stop, 0, 0, 0, 0, EntryType.PRE_BREAKOUT, false);

        double tp1 = nearestSupport < entry ? nearestSupport : entry - risk * SignalConstants.TP1_R_MULTIPLE;
        double tp2 = entry - risk * SignalConstants.TP2_R_MULTIPLE;
        double tp3 = entry - risk * SignalConstants.TP3_R_MULTIPLE;

        // Ordering: tp1 > tp2 > tp3 (all below entry for shorts)
        if (tp1 <= tp2) tp1 = entry - risk * SignalConstants.TP1_R_MULTIPLE;
        tp2 = Math.min(tp1, tp2);
        tp3 = Math.min(tp2, tp3);

        double rr = (entry - tp1) / risk;
        boolean valid = rr >= SignalConstants.MIN_RR;
        return new EntryCalculator.EntryPlan(entry, stop, tp1, tp2, tp3, rr, EntryType.PRE_BREAKOUT, valid);
    }

    private String buildSummary(String symbol, PositionSide side, ScoreCard card,
            IndicatorEngine.Indicators ind4h, IndicatorEngine.Indicators ind1h, double change24hPct) {
        return String.format(
                "%s | %s | Score: %d/100 [%s] | 4H: EMA200=%s | 1H RSI: %.1f | 1H MACD: %s | 24h: %.1f%%",
                symbol, side, card.total(), card.grade(),
                ind4h.lastClose() > ind4h.lastEma200() ? "above" : "below",
                ind1h.lastRsi(),
                ind1h.lastMacdHist() > 0 ? "bullish" : "bearish",
                change24hPct);
    }

    private static double[] extractHigh(List<KlineResponse> candles) {
        double[] arr = new double[candles.size()];
        for (int i = 0; i < candles.size(); i++) arr[i] = candles.get(i).high().doubleValue();
        return arr;
    }
    private static double[] extractLow(List<KlineResponse> candles) {
        double[] arr = new double[candles.size()];
        for (int i = 0; i < candles.size(); i++) arr[i] = candles.get(i).low().doubleValue();
        return arr;
    }
}
