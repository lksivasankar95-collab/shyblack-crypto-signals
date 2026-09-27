package com.shyblack.cryptosignals.baseline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shyblack.cryptosignals.config.MarketProperties;
import com.shyblack.cryptosignals.dto.strategy.TrendPullbackConfig;
import com.shyblack.cryptosignals.entity.BacktestRun;
import com.shyblack.cryptosignals.entity.BacktestTrade;
import com.shyblack.cryptosignals.entity.enums.BacktestExecutionModel;
import com.shyblack.cryptosignals.entity.enums.BacktestExitReason;
import com.shyblack.cryptosignals.entity.enums.BacktestSameCandlePolicy;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.market.BinanceRestClient;
import com.shyblack.cryptosignals.service.backtest.engine.BacktestConfig;
import com.shyblack.cryptosignals.service.backtest.engine.BacktestEngine;
import com.shyblack.cryptosignals.service.backtest.engine.BacktestMetricsCalculator;
import com.shyblack.cryptosignals.service.backtest.historical.BinanceHistoricalDataProvider;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalCandle;
import com.shyblack.cryptosignals.service.backtest.strategy.BacktestStrategy;
import com.shyblack.cryptosignals.service.backtest.strategy.TrendPullbackBacktestStrategy;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * BASELINE ONLY — disabled unless TP_BASELINE=true.
 *
 * 12-month Trend Pullback baseline over the default config, using the
 * PRODUCTION paginated data provider ({@link BinanceHistoricalDataProvider})
 * and the existing {@link BacktestEngine}. No parameters are tuned. Writes
 * build/tp-baseline/report-12m.txt incrementally.
 */
@EnabledIfEnvironmentVariable(named = "TP_BASELINE", matches = "true")
class TrendPullbackBaselineBacktestIT {

    private static final String[] SYMBOLS = {
            "BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT", "XRPUSDT",
            "ADAUSDT", "DOGEUSDT", "AVAXUSDT", "LINKUSDT", "SUIUSDT"
    };
    private static final BigDecimal CAPITAL = bd(10_000);
    private static final BigDecimal RISK_PCT = bd(2.0);   // platform paper default
    private static final BigDecimal FEE_PCT = bd(0.10);   // platform paper default
    private static final BigDecimal SLIP_PCT = bd(0.05);  // platform paper default
    private static final long STEP = 900_000L;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path OUT = Path.of("build", "tp-baseline", "report-12m.txt");

    @Test
    void runTwelveMonthBaseline() throws IOException {
        MarketProperties props = new MarketProperties(false, false,
                "https://api.binance.com", "wss://stream.binance.com:9443/ws",
                "https://fapi.binance.com", "wss://fstream.binance.com/ws", "USDT", 3_600_000);
        // PRODUCTION provider (paginated).
        BinanceHistoricalDataProvider provider =
                new BinanceHistoricalDataProvider(new BinanceRestClient(props));
        TrendPullbackBacktestStrategy prototype = new TrendPullbackBacktestStrategy(MAPPER);
        TrendPullbackConfig def = TrendPullbackConfig.defaults();

        long nowBucket = (System.currentTimeMillis() / STEP) * STEP;
        Instant end = Instant.ofEpochMilli(nowBucket);          // last CLOSED candle boundary
        Instant start = end.minus(Duration.ofDays(365));        // 12 months

        Files.createDirectories(OUT.getParent());
        Files.writeString(OUT, header(def, start, end));

        List<Row> rows = new ArrayList<>();
        for (String symbol : SYMBOLS) {
            List<HistoricalCandle> candles = provider.load(symbol, "15m", start, end);
            Row row;
            if (candles.isEmpty()) {
                row = new Row(symbol, 0,
                        new BacktestEngine.Result(List.of(), List.of(), List.of(), 0, null),
                        BacktestMetricsCalculator.compute(List.of(), List.of(), CAPITAL));
            } else {
                BacktestConfig cfg = new BacktestConfig("TREND_PULLBACK", symbol, "15m",
                        TradingMode.SPOT, candles.get(0).openTime(),
                        candles.get(candles.size() - 1).closeTime(), CAPITAL, RISK_PCT, FEE_PCT,
                        SLIP_PCT, 1, BacktestExecutionModel.NEXT_CANDLE_OPEN,
                        BacktestSameCandlePolicy.SL_FIRST, null);
                BacktestStrategy strategy = prototype.create(null); // default config, fresh instance
                BacktestEngine.Result result = BacktestEngine.run(new BacktestRun(), cfg, strategy,
                        candles, null);
                row = new Row(symbol, candles.size(), result, BacktestMetricsCalculator.compute(
                        result.trades(), result.equity(), CAPITAL));
            }
            rows.add(row);
            Files.writeString(OUT, row.line() + "\n", StandardOpenOption.APPEND);
            System.out.println(row.line());
        }
        String agg = aggregate(rows);
        Files.writeString(OUT, "\n" + agg + "\n", StandardOpenOption.APPEND);
        System.out.println(agg);
    }

    private static String header(TrendPullbackConfig def, Instant start, Instant end) {
        return "TREND_PULLBACK 12-MONTH BASELINE (defaults, production paginated provider)\n"
                + "window=[" + start + ", " + end + ") capital=" + CAPITAL
                + " risk%=" + RISK_PCT + " fee%=" + FEE_PCT + " slip%=" + SLIP_PCT + "\n"
                + "htf=" + def.getHtf() + " entry=" + def.getEntryTimeframe()
                + " cooldownCandles=" + def.getCooldownCandles()
                + " minRR=" + def.getMinRR() + " minScore=" + def.getMinimumScore()
                + " emaFastHtf=" + def.getEmaFastHtf() + " emaSlowHtf=" + def.getEmaSlowHtf()
                + " pullbackEma=" + def.getPullbackEma() + " entryEma=" + def.getEntryEma() + "\n\n"
                + "symbol | candles | trades | W | L | winRate% | grossPnl | netPnl | PF | expectancy | avgR | maxDD | maxDD% | maxConsecLoss | fees | slipEst | TP | SL | EOT\n";
    }

    private static String aggregate(List<Row> rows) {
        int trades = 0, wins = 0, losses = 0, maxConsec = 0;
        BigDecimal net = BigDecimal.ZERO, fees = BigDecimal.ZERO, slip = BigDecimal.ZERO,
                grossWin = BigDecimal.ZERO, grossLossAbs = BigDecimal.ZERO,
                maxDD = BigDecimal.ZERO, maxDDPct = BigDecimal.ZERO;
        double rSum = 0;
        int rN = 0;
        for (Row r : rows) {
            trades += r.m.totalTrades();
            wins += r.m.winningTrades();
            losses += r.m.losingTrades();
            net = net.add(nz(r.m.totalNetPnl()));
            fees = fees.add(nz(r.m.totalFees()));
            slip = slip.add(r.slippageEst);
            grossWin = grossWin.add(nz(r.m.grossProfit()));
            grossLossAbs = grossLossAbs.add(nz(r.m.grossLoss()).abs());
            if (nz(r.m.maxDrawdown()).compareTo(maxDD) > 0) {
                maxDD = nz(r.m.maxDrawdown());
                maxDDPct = nz(r.m.maxDrawdownPct());
            }
            maxConsec = Math.max(maxConsec, r.maxConsecLosses);
            for (BacktestTrade t : r.trades) {
                if (t.getRMultiple() != null) { rSum += t.getRMultiple().doubleValue(); rN++; }
            }
        }
        BigDecimal gross = net.add(fees);
        BigDecimal wr = trades == 0 ? null : BigDecimal.valueOf(wins * 100.0)
                .divide(BigDecimal.valueOf(trades), 2, RoundingMode.HALF_UP);
        BigDecimal pf = grossLossAbs.signum() == 0 ? null
                : grossWin.divide(grossLossAbs, 4, RoundingMode.HALF_UP);
        BigDecimal exp = trades == 0 ? null
                : net.divide(BigDecimal.valueOf(trades), 4, RoundingMode.HALF_UP);
        String avgR = rN == 0 ? "n/a" : String.format(Locale.ROOT, "%.4f", rSum / rN);
        return String.format(Locale.ROOT,
                "AGGREGATE: trades=%d wins=%d losses=%d winRate=%s%% grossPnl=%s netPnl=%s PF=%s "
                        + "expectancy=%s avgR=%s maxDD=%s maxDD%%=%s maxConsecLoss=%d fees=%s slipEst=%s",
                trades, wins, losses, s(wr), s(gross), s(net), s(pf), s(exp), avgR,
                s(maxDD), s(maxDDPct), maxConsec, s(fees), s(slip));
    }

    private static final class Row {
        final String symbol;
        final int candles;
        final BacktestMetricsCalculator.Metrics m;
        final List<BacktestTrade> trades;
        final BigDecimal slippageEst;
        final int maxConsecLosses;

        Row(String symbol, int candles, BacktestEngine.Result result,
                BacktestMetricsCalculator.Metrics m) {
            this.symbol = symbol;
            this.candles = candles;
            this.m = m;
            this.trades = result.trades();
            this.slippageEst = estimateSlippage(trades);
            this.maxConsecLosses = maxConsecutiveLosses(trades);
        }

        String line() {
            double rSum = 0;
            int rN = 0;
            for (BacktestTrade t : trades) {
                if (t.getRMultiple() != null) { rSum += t.getRMultiple().doubleValue(); rN++; }
            }
            String avgR = rN == 0 ? "n/a" : String.format(Locale.ROOT, "%.4f", rSum / rN);
            BigDecimal grossPnl = nz(m.totalNetPnl()).add(nz(m.totalFees()));
            return String.format(Locale.ROOT,
                    "%s | %d | %d | %d | %d | %s | %s | %s | %s | %s | %s | %s | %s | %d | %s | %s | %d | %d | %d",
                    symbol, candles, m.totalTrades(), m.winningTrades(), m.losingTrades(),
                    s(m.winRatePct()), s(grossPnl), s(m.totalNetPnl()), s(m.profitFactor()),
                    s(m.expectancy()), avgR, s(m.maxDrawdown()), s(m.maxDrawdownPct()),
                    maxConsecLosses, s(m.totalFees()), s(slippageEst),
                    count(BacktestExitReason.TAKE_PROFIT), count(BacktestExitReason.STOP_LOSS),
                    count(BacktestExitReason.END_OF_TEST));
        }

        private int count(BacktestExitReason reason) {
            return (int) trades.stream().filter(t -> t.getExitReason() == reason).count();
        }
    }

    private static BigDecimal estimateSlippage(List<BacktestTrade> trades) {
        BigDecimal perSide = SLIP_PCT.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        BigDecimal total = BigDecimal.ZERO;
        for (BacktestTrade t : trades) {
            total = total.add(t.getEntryPrice().multiply(t.getQuantity()))
                    .add(t.getExitPrice().multiply(t.getQuantity()));
        }
        return total.multiply(perSide).setScale(4, RoundingMode.HALF_UP);
    }

    private static int maxConsecutiveLosses(List<BacktestTrade> trades) {
        List<BacktestTrade> sorted = new ArrayList<>(trades);
        sorted.sort(Comparator.comparing(BacktestTrade::getExitTime));
        int best = 0, cur = 0;
        for (BacktestTrade t : sorted) {
            if (nz(t.getNetPnl()).signum() < 0) { cur++; best = Math.max(best, cur); }
            else cur = 0;
        }
        return best;
    }

    private static String s(BigDecimal v) {
        return v == null ? "n/a" : v.stripTrailingZeros().toPlainString();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }
}
