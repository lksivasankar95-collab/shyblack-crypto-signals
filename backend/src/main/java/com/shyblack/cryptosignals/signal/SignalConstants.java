package com.shyblack.cryptosignals.signal;

public final class SignalConstants {

    // Liquidity
    public static final double MIN_VOLUME_USDT = 5_000_000.0;

    // Candle requirements
    public static final int CANDLES_4H = 210;
    public static final int CANDLES_1H = 210;
    public static final int CANDLES_15M = 100;

    // EMA periods
    public static final int EMA_FAST = 20;
    public static final int EMA_MID = 50;
    public static final int EMA_SLOW = 200;

    // RSI
    public static final int RSI_PERIOD = 14;
    public static final double RSI_OVERSOLD = 30.0;
    public static final double RSI_NEUTRAL = 50.0;
    public static final double RSI_OVERBOUGHT = 70.0;
    public static final double RSI_EXTREME_OB = 80.0;

    // MACD (standard 12-26-9)
    public static final int MACD_FAST = 12;
    public static final int MACD_SLOW = 26;
    public static final int MACD_SIGNAL = 9;

    // ATR
    public static final int ATR_PERIOD = 14;
    public static final double ATR_SL_BUFFER = 0.5;

    // ADX
    public static final int ADX_PERIOD = 14;
    public static final double ADX_WEAK = 20.0;
    public static final double ADX_STRONG = 25.0;

    // Volume
    public static final int VOLUME_MA_PERIOD = 20;
    public static final double VOLUME_HIGH = 1.5;
    public static final double VOLUME_MODERATE = 1.2;

    // Score thresholds
    public static final int SCORE_STRONG_BUY = 85;
    public static final int SCORE_BUY = 75;
    public static final int SCORE_WATCH = 65;

    // Risk/Reward
    public static final double MIN_RR = 1.5;
    public static final double GOOD_RR = 2.0;
    public static final double EXCELLENT_RR = 3.0;

    // Pump-chasing protection
    public static final double PUMP_THRESHOLD_PCT = 15.0;
    public static final double EXTREME_PUMP_PCT = 30.0;

    // Swing detection lookback
    public static final int SWING_LOOKBACK = 5;

    // Target calculation - fallback R multiples
    public static final double TP1_R_MULTIPLE = 1.5;
    public static final double TP2_R_MULTIPLE = 2.5;
    public static final double TP3_R_MULTIPLE = 4.0;

    // Signal deduplication
    public static final int SIGNAL_COOLDOWN_HOURS = 4;

    // Scheduled job
    public static final String SIGNAL_CRON = "0 0/15 * * * *";

    private SignalConstants() {
    }
}
