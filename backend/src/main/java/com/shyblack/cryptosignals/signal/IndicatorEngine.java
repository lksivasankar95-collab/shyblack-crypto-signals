package com.shyblack.cryptosignals.signal;

import com.shyblack.cryptosignals.dto.market.KlineResponse;
import java.util.List;

public class IndicatorEngine {

    /** All computed indicators for one candle series. */
    public record Indicators(
            double[] close,
            double[] high,
            double[] low,
            double[] volume,
            double[] ema20,
            double[] ema50,
            double[] ema200,
            double[] rsi,
            double[] macdLine,
            double[] macdSignal,
            double[] macdHist,
            double[] atr,
            double[] adx,
            double[] volumeMa20,
            int size
    ) {
        public double lastClose()     { return close[size - 1]; }
        public double lastEma20()     { return ema20[size - 1]; }
        public double lastEma50()     { return ema50[size - 1]; }
        public double lastEma200()    { return ema200[size - 1]; }
        public double lastRsi()       { return rsi[size - 1]; }
        public double lastMacdLine()  { return macdLine[size - 1]; }
        public double lastMacdSignal(){ return macdSignal[size - 1]; }
        public double lastMacdHist()  { return macdHist[size - 1]; }
        public double lastAtr()       { return atr[size - 1]; }
        public double lastAdx()       { return adx[size - 1]; }
        public double lastVolume()    { return volume[size - 1]; }
        public double lastVolumeMa()  { return volumeMa20[size - 1]; }
        public double prevMacdHist()  { return size >= 2 ? macdHist[size - 2] : 0; }
        public double prevRsi()       { return size >= 2 ? rsi[size - 2] : 0; }
    }

    /** Compute all indicators from a candle list (oldest first). */
    public static Indicators compute(List<KlineResponse> candles) {
        int n = candles.size();
        double[] close  = new double[n];
        double[] high   = new double[n];
        double[] low    = new double[n];
        double[] volume = new double[n];
        for (int i = 0; i < n; i++) {
            KlineResponse c = candles.get(i);
            close[i]  = c.close().doubleValue();
            high[i]   = c.high().doubleValue();
            low[i]    = c.low().doubleValue();
            volume[i] = c.volume().doubleValue();
        }

        double[] ema20  = ema(close, SignalConstants.EMA_FAST);
        double[] ema50  = ema(close, SignalConstants.EMA_MID);
        double[] ema200 = ema(close, SignalConstants.EMA_SLOW);
        double[] rsi    = rsi(close, SignalConstants.RSI_PERIOD);
        double[] atr    = atr(high, low, close, SignalConstants.ATR_PERIOD);
        double[] adx    = adx(high, low, close, atr, SignalConstants.ADX_PERIOD);
        double[] vma20  = sma(volume, SignalConstants.VOLUME_MA_PERIOD);

        // MACD
        double[] ema12 = ema(close, SignalConstants.MACD_FAST);
        double[] ema26 = ema(close, SignalConstants.MACD_SLOW);
        double[] macdLine = new double[n];
        for (int i = 0; i < n; i++) macdLine[i] = ema12[i] - ema26[i];
        double[] macdSignal = ema(macdLine, SignalConstants.MACD_SIGNAL);
        double[] macdHist = new double[n];
        for (int i = 0; i < n; i++) macdHist[i] = macdLine[i] - macdSignal[i];

        return new Indicators(close, high, low, volume, ema20, ema50, ema200,
                rsi, macdLine, macdSignal, macdHist, atr, adx, vma20, n);
    }

    // ── EMA ─────────────────────────────────────────────────────────────────
    static double[] ema(double[] src, int period) {
        int n = src.length;
        double[] result = new double[n];
        if (n == 0) return result;
        double k = 2.0 / (period + 1);
        // Seed with SMA of first `period` values
        int start = Math.min(period - 1, n - 1);
        double sum = 0;
        for (int i = 0; i <= start; i++) sum += src[i];
        result[start] = sum / (start + 1);
        for (int i = start + 1; i < n; i++) {
            result[i] = src[i] * k + result[i - 1] * (1 - k);
        }
        return result;
    }

    // ── SMA ─────────────────────────────────────────────────────────────────
    static double[] sma(double[] src, int period) {
        int n = src.length;
        double[] result = new double[n];
        for (int i = 0; i < n; i++) {
            int from = Math.max(0, i - period + 1);
            double s = 0;
            for (int j = from; j <= i; j++) s += src[j];
            result[i] = s / (i - from + 1);
        }
        return result;
    }

    // ── RSI ─────────────────────────────────────────────────────────────────
    static double[] rsi(double[] close, int period) {
        int n = close.length;
        double[] result = new double[n];
        if (n < period + 1) return result;
        double avgGain = 0, avgLoss = 0;
        for (int i = 1; i <= period; i++) {
            double diff = close[i] - close[i - 1];
            if (diff > 0) avgGain += diff; else avgLoss -= diff;
        }
        avgGain /= period;
        avgLoss /= period;
        result[period] = avgLoss == 0 ? 100 : 100 - 100.0 / (1 + avgGain / avgLoss);
        for (int i = period + 1; i < n; i++) {
            double diff = close[i] - close[i - 1];
            double gain = Math.max(diff, 0);
            double loss = Math.max(-diff, 0);
            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;
            result[i] = avgLoss == 0 ? 100 : 100 - 100.0 / (1 + avgGain / avgLoss);
        }
        return result;
    }

    // ── ATR ─────────────────────────────────────────────────────────────────
    static double[] atr(double[] high, double[] low, double[] close, int period) {
        int n = high.length;
        double[] result = new double[n];
        if (n < 2) return result;
        double[] tr = new double[n];
        tr[0] = high[0] - low[0];
        for (int i = 1; i < n; i++) {
            tr[i] = Math.max(high[i] - low[i],
                    Math.max(Math.abs(high[i] - close[i - 1]),
                             Math.abs(low[i] - close[i - 1])));
        }
        // Seed with SMA
        double sum = 0;
        int seed = Math.min(period, n);
        for (int i = 0; i < seed; i++) sum += tr[i];
        result[seed - 1] = sum / seed;
        for (int i = seed; i < n; i++) {
            result[i] = (result[i - 1] * (period - 1) + tr[i]) / period;
        }
        return result;
    }

    // ── ADX ─────────────────────────────────────────────────────────────────
    static double[] adx(double[] high, double[] low, double[] close, double[] atr, int period) {
        int n = high.length;
        double[] result = new double[n];
        if (n < period * 2) return result;
        double[] plusDM  = new double[n];
        double[] minusDM = new double[n];
        for (int i = 1; i < n; i++) {
            double up   = high[i] - high[i - 1];
            double down = low[i - 1] - low[i];
            plusDM[i]  = (up > down && up > 0) ? up : 0;
            minusDM[i] = (down > up && down > 0) ? down : 0;
        }
        double smoothPlus = 0, smoothMinus = 0, smoothAtr = 0;
        for (int i = 1; i <= period && i < n; i++) {
            smoothPlus  += plusDM[i];
            smoothMinus += minusDM[i];
            smoothAtr   += atr[i];
        }
        double[] dx = new double[n];
        if (smoothAtr > 0) {
            double pDI = 100 * smoothPlus / smoothAtr;
            double mDI = 100 * smoothMinus / smoothAtr;
            dx[period] = (pDI + mDI) > 0 ? 100 * Math.abs(pDI - mDI) / (pDI + mDI) : 0;
        }
        double smoothDx = dx[period];
        result[period] = smoothDx;
        for (int i = period + 1; i < n; i++) {
            smoothPlus  = smoothPlus  - smoothPlus  / period + plusDM[i];
            smoothMinus = smoothMinus - smoothMinus / period + minusDM[i];
            smoothAtr   = smoothAtr   - smoothAtr   / period + atr[i];
            if (smoothAtr > 0) {
                double pDI = 100 * smoothPlus / smoothAtr;
                double mDI = 100 * smoothMinus / smoothAtr;
                dx[i] = (pDI + mDI) > 0 ? 100 * Math.abs(pDI - mDI) / (pDI + mDI) : 0;
            }
            smoothDx = (smoothDx * (period - 1) + dx[i]) / period;
            result[i] = smoothDx;
        }
        return result;
    }
}
