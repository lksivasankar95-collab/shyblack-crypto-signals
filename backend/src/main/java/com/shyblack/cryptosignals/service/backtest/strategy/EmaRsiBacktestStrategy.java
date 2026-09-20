package com.shyblack.cryptosignals.service.backtest.strategy;

import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalCandle;
import com.shyblack.cryptosignals.signal.IndicatorEngine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * A single-timeframe EMA/RSI strategy that reuses the SAME
 * {@link IndicatorEngine} the live signal pipeline uses — one canonical
 * implementation of EMA/RSI/MACD/ATR/ADX/VMA, no duplicates.
 *
 * LONG: ema20 > ema50 (trend up) AND close crosses above ema20 AND rsi > 50 AND rsi < 70.
 * SHORT: ema20 < ema50 (trend down) AND close crosses below ema20 AND rsi < 50 AND rsi > 30.
 *
 * SL = 1.5 × ATR from entry; TP = 3 × ATR from entry (R:R ≈ 2).
 *
 * This is a deliberately simple v1 that ships with the module. The live spot
 * signal engine is multi-timeframe (4H+1H+15M) and reusing it verbatim would
 * require historical data at three timeframes simultaneously — a documented
 * next-drop enhancement.
 */
@Component
public class EmaRsiBacktestStrategy implements BacktestStrategy {

	public static final String ID = "ema-rsi";
	public static final String VERSION = "v1";

	@Override public String id() { return ID; }
	@Override public String version() { return VERSION; }
	@Override public int warmup() { return 200; }

	@Override
	public Optional<Signal> evaluate(List<HistoricalCandle> history, int currentIndex) {
		if (currentIndex + 1 < warmup()) return Optional.empty();

		// IndicatorEngine expects List<KlineResponse>. We slice up to currentIndex INCLUSIVE.
		List<com.shyblack.cryptosignals.dto.market.KlineResponse> klines = new ArrayList<>(currentIndex + 1);
		for (int i = 0; i <= currentIndex; i++) klines.add(history.get(i).toKline());
		IndicatorEngine.Indicators ind = IndicatorEngine.compute(klines);

		if (ind.size() < 3) return Optional.empty();

		double prevClose = ind.close()[ind.size() - 2];
		double close = ind.lastClose();
		double prevEma20 = ind.ema20()[ind.size() - 2];
		double ema20 = ind.lastEma20();
		double ema50 = ind.lastEma50();
		double rsi = ind.lastRsi();
		double atr = ind.lastAtr();
		if (atr <= 0) return Optional.empty();

		boolean crossUp = prevClose <= prevEma20 && close > ema20;
		boolean crossDown = prevClose >= prevEma20 && close < ema20;

		BigDecimal ref = BigDecimal.valueOf(close);
		BigDecimal atrBd = BigDecimal.valueOf(atr);

		if (ema20 > ema50 && crossUp && rsi > 50 && rsi < 70) {
			BigDecimal stop = ref.subtract(atrBd.multiply(BigDecimal.valueOf(1.5)))
					.setScale(8, RoundingMode.HALF_UP);
			BigDecimal tp = ref.add(atrBd.multiply(BigDecimal.valueOf(3)))
					.setScale(8, RoundingMode.HALF_UP);
			return Optional.of(new Signal(PositionSide.LONG, ref, stop, tp,
					"emaRsi crossUp rsi=" + String.format("%.1f", rsi)));
		}
		if (ema20 < ema50 && crossDown && rsi < 50 && rsi > 30) {
			BigDecimal stop = ref.add(atrBd.multiply(BigDecimal.valueOf(1.5)))
					.setScale(8, RoundingMode.HALF_UP);
			BigDecimal tp = ref.subtract(atrBd.multiply(BigDecimal.valueOf(3)))
					.setScale(8, RoundingMode.HALF_UP);
			return Optional.of(new Signal(PositionSide.SHORT, ref, stop, tp,
					"emaRsi crossDown rsi=" + String.format("%.1f", rsi)));
		}
		return Optional.empty();
	}
}
