package com.shyblack.cryptosignals.signal;

import com.shyblack.cryptosignals.dto.market.KlineResponse;
import com.shyblack.cryptosignals.entity.enums.MarketRegime;
import com.shyblack.cryptosignals.entity.enums.SignalGrade;
import com.shyblack.cryptosignals.market.BinanceRestClient;
import com.shyblack.cryptosignals.market.MarketBook;
import com.shyblack.cryptosignals.market.MarketTicker;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SpotSignalEngine {

    private static final Logger log = LoggerFactory.getLogger(SpotSignalEngine.class);

    private final BinanceRestClient restClient;
    private final MarketBook marketBook;

    public SpotSignalEngine(BinanceRestClient restClient, MarketBook marketBook) {
        this.restClient = restClient;
        this.marketBook = marketBook;
    }

    public record SignalCandidate(
            String symbol,
            ScoreCard scoreCard,
            EntryCalculator.EntryPlan entryPlan,
            MarketRegime marketRegime,
            String technicalSummary,
            boolean valid
    ) {}

    /** Determine market regime from BTC 4H. */
    public MarketRegime detectMarketRegime() {
        try {
            List<KlineResponse> btc4h = restClient.klines("BTCUSDT", "4h", SignalConstants.CANDLES_4H);
            if (btc4h.size() < 210) return MarketRegime.NEUTRAL;
            IndicatorEngine.Indicators ind = IndicatorEngine.compute(btc4h);
            double price = ind.lastClose();
            if (price > ind.lastEma200() && ind.lastEma50() > ind.lastEma200()) return MarketRegime.BULLISH;
            if (price < ind.lastEma200() && ind.lastEma50() < ind.lastEma200()) return MarketRegime.BEARISH;
            return MarketRegime.NEUTRAL;
        } catch (Exception ex) {
            log.warn("[Signal] Cannot determine market regime: {}", ex.getMessage());
            return MarketRegime.NEUTRAL;
        }
    }

    /** Analyze one Spot symbol. Returns empty if data insufficient. */
    public Optional<SignalCandidate> analyze(String symbol, MarketRegime regime) {
        try {
            Optional<MarketTicker> tickerOpt = marketBook.spotTickers().get(symbol);
            if (tickerOpt.isEmpty()) return Optional.empty();
            MarketTicker ticker = tickerOpt.get();

            double volume24h    = ticker.volume24h().doubleValue();
            double livePrice    = ticker.price().doubleValue();
            double change24hPct = ticker.changePercent24h().doubleValue();

            if (volume24h < SignalConstants.MIN_VOLUME_USDT) {
                log.debug("[Signal] {} skipped: low liquidity ${}", symbol, (long) volume24h);
                return Optional.empty();
            }

            List<KlineResponse> candles4h  = restClient.klines(symbol, "4h",  SignalConstants.CANDLES_4H);
            List<KlineResponse> candles1h  = restClient.klines(symbol, "1h",  SignalConstants.CANDLES_1H);
            List<KlineResponse> candles15m = restClient.klines(symbol, "15m", SignalConstants.CANDLES_15M);

            if (candles4h.size() < 210 || candles1h.size() < 60 || candles15m.size() < 50) {
                log.debug("[Signal] {} insufficient candles", symbol);
                return Optional.empty();
            }

            IndicatorEngine.Indicators ind4h  = IndicatorEngine.compute(candles4h);
            IndicatorEngine.Indicators ind1h  = IndicatorEngine.compute(candles1h);
            IndicatorEngine.Indicators ind15m = IndicatorEngine.compute(candles15m);

            double[] high4h  = extractHigh(candles4h);
            double[] low4h   = extractLow(candles4h);
            double[] high15m = extractHigh(candles15m);
            double[] low15m  = extractLow(candles15m);

            StructureAnalyzer.StructureSummary structure4h  =
                    StructureAnalyzer.analyze(high4h, low4h, ind4h.close(), 20);
            StructureAnalyzer.StructureSummary structure15m =
                    StructureAnalyzer.analyze(high15m, low15m, ind15m.close(), 10);

            int regimeScore = SpotScorer.scoreMarketRegime(regime);
            int trend4h     = SpotScorer.scoreTrend4h(ind4h, change24hPct);
            int conf1h      = SpotScorer.scoreConfirmation1h(ind1h);
            int entry15m    = SpotScorer.scoreEntrySetup15m(ind15m, structure15m);
            int volumeScore = SpotScorer.scoreVolume(ind15m, change24hPct);
            int structScore = SpotScorer.scoreStructure(structure4h, livePrice);

            EntryCalculator.EntryPlan plan = EntryCalculator.calculate(ind15m, structure15m, livePrice);
            int rrScore = SpotScorer.scoreRiskReward(plan.riskReward());

            ScoreCard card = new ScoreCard(regimeScore, trend4h, conf1h, entry15m, volumeScore, structScore, rrScore);

            String summary = buildSummary(symbol, card, ind4h, ind1h, change24hPct);

            // Hard filter: insufficient R:R
            if (!plan.valid()) {
                log.debug("[Signal] {} R:R too low: {}", symbol, plan.riskReward());
                return Optional.of(new SignalCandidate(symbol, card, plan, regime, summary, false));
            }

            // Hard filter: extreme pump + no-trade grade
            if (change24hPct > SignalConstants.EXTREME_PUMP_PCT && card.grade() == SignalGrade.NO_TRADE) {
                return Optional.of(new SignalCandidate(symbol, card, plan, regime, summary, false));
            }

            boolean valid = card.grade() != SignalGrade.NO_TRADE;
            return Optional.of(new SignalCandidate(symbol, card, plan, regime, summary, valid));

        } catch (Exception ex) {
            log.warn("[Signal] Error analyzing {}: {}", symbol, ex.getMessage());
            return Optional.empty();
        }
    }

    private String buildSummary(String symbol, ScoreCard card,
            IndicatorEngine.Indicators ind4h, IndicatorEngine.Indicators ind1h, double change24hPct) {
        return String.format(
                "%s | Score: %d/100 [%s] | 4H: EMA200=%s | 1H RSI: %.1f | " +
                "1H MACD: %s | Volume: 4h/1h/15m | 24h: %.1f%%",
                symbol, card.total(), card.grade(),
                ind4h.lastClose() > ind4h.lastEma200() ? "above" : "below",
                ind1h.lastRsi(),
                ind1h.lastMacdHist() > 0 ? "bullish" : "bearish",
                change24hPct
        );
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
