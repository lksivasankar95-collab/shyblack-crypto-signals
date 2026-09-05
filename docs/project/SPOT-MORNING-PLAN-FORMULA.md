# Spot Morning Plan Signal Engine — Formula Reference

## Overview

The Spot Morning Plan engine generates long-side Spot trading signals using a deterministic 100-point scoring model. All decisions are rule-based, fully traceable, and free of black-box ML. Signals are persisted to the database and delivered to the Flutter app via the existing `/api/v1/signals` endpoint.

---

## 1. Universe Filter (Hard Gates)

Before any indicator computation, a symbol must pass all hard gates or it is discarded:

| Gate | Threshold | Rationale |
|------|-----------|-----------|
| 24h Volume | >= $5,000,000 USDT | Ensures liquidity for realistic execution |
| 4H candle count | >= 210 | Enough history to compute EMA-200 on 4H |
| 1H candle count | >= 60 | Enough for 1H confirmation |
| 15M candle count | >= 50 | Enough for entry setup detection |
| Risk/Reward | >= 1.5 : 1 | Hard minimum; below this the signal is invalid regardless of score |

Signals with 24h change > 30% are additionally penalized heavily in scoring and are typically classified NO_TRADE.

---

## 2. Market Regime Detection (BTC Context)

Market regime is determined from BTC/USDT 4H candles only:

```
BULLISH  : BTC price > EMA-200 AND EMA-50 > EMA-200
BEARISH  : BTC price < EMA-200 AND EMA-50 < EMA-200
NEUTRAL  : everything else
```

Regime influences the scoring model but does not independently veto signals. In BEARISH regime the maximum achievable score is capped by the regime score (0 points), making strong buy signals effectively impossible.

---

## 3. Indicator Engine

All indicators are computed from raw OHLCV candle data. No external library is used; formulas are implemented directly in `IndicatorEngine.java`.

### 3.1 EMA — Exponential Moving Average

```
k = 2 / (period + 1)
EMA[0] = SMA of first `period` candles   (seed)
EMA[i] = close[i] × k + EMA[i-1] × (1 - k)
```

Used periods: EMA-20 (fast), EMA-50 (mid), EMA-200 (slow)

### 3.2 RSI — Relative Strength Index (14-period)

Wilder smoothing method:
```
Initial avgGain = SMA of gains over first 14 periods
Initial avgLoss = SMA of losses over first 14 periods
RS = avgGain / avgLoss
RSI = 100 - (100 / (1 + RS))

Subsequent:
avgGain = (prevAvgGain × 13 + currentGain) / 14
avgLoss = (prevAvgLoss × 13 + currentLoss) / 14
```

Thresholds: Oversold < 30, Neutral = 50, Overbought = 70, Extreme OB = 80

### 3.3 MACD (12-26-9)

```
MACD Line   = EMA-12(close) - EMA-26(close)
Signal Line = EMA-9(MACD Line)
Histogram   = MACD Line - Signal Line
```

Bullish when Histogram > 0 and rising (Hist > prev Hist).

### 3.4 ATR — Average True Range (14-period)

```
TR[i] = max(High[i] - Low[i],
            |High[i] - Close[i-1]|,
            |Low[i]  - Close[i-1]|)

ATR[period-1] = SMA(TR, period)   (seed)
ATR[i] = (ATR[i-1] × 13 + TR[i]) / 14
```

Used for: stop-loss placement (support - 0.5 × ATR buffer).

### 3.5 ADX — Average Directional Index (14-period)

```
+DM[i] = max(High[i] - High[i-1], 0) if up > down else 0
-DM[i] = max(Low[i-1] - Low[i], 0) if down > up else 0

Smooth with Wilder (same as ATR):
+DI = 100 × Smooth+DM / SmoothATR
-DI = 100 × Smooth-DM / SmoothATR

DX[i]  = 100 × |+DI - -DI| / (+DI + -DI)
ADX[i] = Wilder smoothing of DX
```

ADX < 20 = weak trend, ADX >= 25 = strong trend. Used in scoring and summaries.

### 3.6 Volume MA (20-period SMA)

Simple moving average of raw volume over 20 candles. Ratio = currentVolume / VolumeMA20.

---

## 4. Structure Analysis

Performed separately on 4H (for macro context) and 15M (for entry timing).

### Swing Detection

A candle at index `i` is a swing high if:
```
high[i] > high[j]  for all j in [i-5 .. i+5], j != i
```

A candle at index `i` is a swing low if:
```
low[i] < low[j]  for all j in [i-5 .. i+5], j != i
```

### Higher Lows / Higher Highs

Detected by comparing the last two swing lows (highs):
```
higherLows  = swingLows[-1]  > swingLows[-2]
higherHighs = swingHighs[-1] > swingHighs[-2]
```

### Nearest Support / Resistance

```
nearestSupport    = max(swingLows where price < currentPrice)
nearestResistance = min(swingHighs where price > currentPrice)
```

Fallback if no swing exists: support = currentPrice × 0.95, resistance = currentPrice × 1.05.

---

## 5. Entry Calculator

Entry type is determined by current market position:

| Entry Type | Condition |
|------------|-----------|
| BREAKOUT | price > nearestResistance × 0.995 AND volume > volumeMA × 1.2 |
| BREAKOUT_RETEST | EMA20 > EMA50 AND price < nearestResistance × 0.99 |
| PRE_BREAKOUT | All other cases (accumulation) |

### Stop Loss

```
stopLoss = nearestSupport - (ATR × 0.5)
stopLoss = min(stopLoss, entry × 0.995)   // enforce at least 0.5% below entry
```

### Targets

TP1 prefers nearest resistance above entry; falls back to R-multiple:
```
TP1 = nearestResistance (if > entry) else entry + risk × 1.5
TP2 = entry + risk × 2.5
TP3 = entry + risk × 4.0
```

Ordering enforced: TP1 < TP2 < TP3.

### Risk/Reward

```
risk = entry - stopLoss
R:R  = (TP1 - entry) / risk
```

Signal is invalid (NO_TRADE regardless of score) if R:R < 1.5.

---

## 6. Scoring Model (100 Points)

### 6.1 Market Regime Score (max 10)

| Market Regime | Points |
|---------------|--------|
| BULLISH | 10 |
| NEUTRAL | 4 |
| BEARISH | 0 |

### 6.2 4H Trend Score (max 20)

| Condition | Points |
|-----------|--------|
| Price > EMA200 AND EMA50 > EMA200 AND Price > EMA20 | 20 |
| Price > EMA200 AND EMA50 > EMA200 | 15 |
| Price > EMA200 only | 8 |
| Price > EMA50 only | 4 |
| None | 0 |

Pump penalties applied afterward:
- 24h change > 30%: -8 points (minimum 0)
- 24h change > 15%: -4 points (minimum 0)

### 6.3 1H Confirmation Score (max 20)

Checks 4 binary conditions on 1H indicators:
1. EMA20 > EMA50
2. Price > EMA20
3. RSI > 50
4. MACD Histogram > 0

| Conditions Met | Points |
|----------------|--------|
| 4 of 4 | 20 |
| 3 of 4 | 14 |
| 2 of 4 | 8 |
| 1 or 0 | 2 |

Penalty: -5 if RSI > 80 (overbought).

### 6.4 15M Entry Setup Score (max 20)

Checks 4 binary conditions on 15M indicators and structure:
1. Higher lows in 15M structure
2. Volume > volumeMA × 1.2 (expanding)
3. MACD Histogram > 0 AND rising (Hist > prevHist)
4. Price > EMA20

| Conditions Met | Points |
|----------------|--------|
| 4 of 4 | 20 |
| 3 of 4 | 14 |
| 2 of 4 | 8 |
| 1 of 4 | 4 |
| 0 of 4 | 0 |

### 6.5 Volume Score (max 10)

| Volume Ratio | Points |
|--------------|--------|
| >= 1.5× MA | 10 |
| >= 1.2× MA | 6 |
| >= 1.0× MA | 4 |
| < 1.0× MA | 0 |

Penalty: if 24h change > 30% AND base = 10, reduce to 4 (volume spike on extreme pump is less valuable).

### 6.6 Structure Score (max 10)

| Condition | Points |
|-----------|--------|
| Price within 1% of nearest resistance | 1 (immediate cap) |
| Price > 15% above nearest support | 2 (extended, risky) |
| Higher lows AND Higher highs | 10 |
| Higher lows only | 6 |
| Flat/neutral | 3 |

### 6.7 Risk/Reward Score (max 10)

| R:R Ratio | Points |
|-----------|--------|
| >= 3.0 | 10 |
| >= 2.0 | 7 |
| >= 1.5 | 4 |
| < 1.5 | 0 |

---

## 7. Signal Grade Thresholds

| Score | Grade |
|-------|-------|
| >= 85 | STRONG_BUY |
| >= 75 | BUY |
| >= 65 | WATCH |
| < 65 | NO_TRADE |

Only STRONG_BUY, BUY, and WATCH are persisted as live signals (ACTIVE status). NO_TRADE results are discarded unless they have a valid entry plan, in which case they are saved as DRAFT for analysis.

---

## 8. Signal Deduplication

Before generating a new signal for a symbol, the engine checks if an ACTIVE or PENDING signal already exists for that symbol in SPOT mode. If one was created within the last 4 hours, the new analysis is skipped.

---

## 9. Scheduled Execution

The engine runs every 15 minutes via Spring `@Scheduled`:
```
cron = "0 0/15 * * * *"
```

Execution flow per cycle:
1. Detect BTC market regime (single 4H kline fetch)
2. Get snapshot of all Spot symbols from `MarketBook.spotTickers()`
3. Filter by minimum volume ($5M)
4. For each qualifying symbol: fetch 3 timeframe candles, compute indicators, score, calculate entry plan
5. Persist ACTIVE signals (BUY / STRONG_BUY) and WATCH signals
6. Log summary of signals generated per cycle

---

## 10. Database Schema Additions

New columns added to the `signals` table (via `ddl-auto: update`):

| Column | Type | Description |
|--------|------|-------------|
| score | INTEGER | Raw 0-100 score |
| signal_grade | VARCHAR | STRONG_BUY / BUY / WATCH / NO_TRADE |
| entry_type | VARCHAR | PRE_BREAKOUT / BREAKOUT / BREAKOUT_RETEST |
| trading_mode | VARCHAR | SPOT (default) |
| market_regime | VARCHAR | BULLISH / NEUTRAL / BEARISH at time of signal |
| target_price2 | DECIMAL(19,8) | TP2 |
| target_price3 | DECIMAL(19,8) | TP3 |
| risk_reward | DECIMAL(10,4) | Calculated R:R ratio |

---

## 11. API Response

New fields added to `/api/v1/signals` response:

```json
{
  "id": "uuid",
  "symbol": "SOLUSDT",
  "status": "ACTIVE",
  "side": "LONG",
  "score": 87,
  "signalGrade": "STRONG_BUY",
  "entryType": "PRE_BREAKOUT",
  "tradingMode": "SPOT",
  "marketRegime": "BULLISH",
  "entryPrice": 145.20,
  "targetPrice": 158.00,
  "targetPrice2": 168.50,
  "targetPrice3": 185.00,
  "stopLoss": 138.50,
  "riskReward": 1.92,
  "confidence": 87,
  "technicalSummary": "SOLUSDT | Score: 87/100 [STRONG_BUY] | 4H: EMA200=above | 1H RSI: 58.3 | 1H MACD: bullish | Volume: 4h/1h/15m | 24h: 3.2%"
}
```

---

## 12. Constants Reference

All thresholds are defined in `SignalConstants.java`. No magic numbers exist in the codebase.

| Constant | Value | Meaning |
|----------|-------|---------|
| MIN_VOLUME_USDT | 5,000,000 | Minimum 24h volume |
| CANDLES_4H | 210 | Candles fetched for 4H timeframe |
| CANDLES_1H | 210 | Candles fetched for 1H timeframe |
| CANDLES_15M | 100 | Candles fetched for 15M timeframe |
| EMA_FAST | 20 | Fast EMA period |
| EMA_MID | 50 | Mid EMA period |
| EMA_SLOW | 200 | Slow EMA period |
| RSI_PERIOD | 14 | RSI calculation period |
| RSI_OVERSOLD | 30.0 | RSI oversold level |
| RSI_NEUTRAL | 50.0 | RSI midline |
| RSI_OVERBOUGHT | 70.0 | RSI overbought |
| RSI_EXTREME_OB | 80.0 | RSI extreme overbought (penalty trigger) |
| MACD_FAST | 12 | MACD fast EMA |
| MACD_SLOW | 26 | MACD slow EMA |
| MACD_SIGNAL | 9 | MACD signal EMA |
| ATR_PERIOD | 14 | ATR period |
| ATR_SL_BUFFER | 0.5 | ATR multiplier for SL buffer |
| ADX_PERIOD | 14 | ADX period |
| ADX_WEAK | 20.0 | ADX weak trend threshold |
| ADX_STRONG | 25.0 | ADX strong trend threshold |
| VOLUME_MA_PERIOD | 20 | Volume MA period |
| VOLUME_HIGH | 1.5 | High volume multiplier |
| VOLUME_MODERATE | 1.2 | Moderate volume multiplier |
| SCORE_STRONG_BUY | 85 | Strong buy threshold |
| SCORE_BUY | 75 | Buy threshold |
| SCORE_WATCH | 65 | Watch threshold |
| MIN_RR | 1.5 | Minimum acceptable R:R |
| GOOD_RR | 2.0 | Good R:R |
| EXCELLENT_RR | 3.0 | Excellent R:R |
| PUMP_THRESHOLD_PCT | 15.0 | 24h pump penalty trigger |
| EXTREME_PUMP_PCT | 30.0 | 24h extreme pump trigger |
| SWING_LOOKBACK | 5 | Candles each side for swing detection |
| TP1_R_MULTIPLE | 1.5 | TP1 fallback R multiple |
| TP2_R_MULTIPLE | 2.5 | TP2 R multiple |
| TP3_R_MULTIPLE | 4.0 | TP3 R multiple |
| SIGNAL_COOLDOWN_HOURS | 4 | Hours before re-analyzing same symbol |
| SIGNAL_CRON | 0 0/15 * * * * | Scheduler cron expression |
