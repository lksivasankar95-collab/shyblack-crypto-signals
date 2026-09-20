package com.shyblack.cryptosignals.service.backtest.engine;

import com.shyblack.cryptosignals.entity.enums.BacktestExecutionModel;
import com.shyblack.cryptosignals.entity.enums.BacktestSameCandlePolicy;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalCandle;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Simulates order execution against historical candles.
 *
 * Entry model:
 *   NEXT_CANDLE_OPEN (default) — no same-candle look-ahead. Entry price =
 *   next candle's open, adjusted by adverse slippage.
 *
 * SL/TP evaluation:
 *   On every subsequent candle, check whether HIGH or LOW touched TP or SL.
 *   For a LONG:  SL at LOW ≤ stopLoss ; TP at HIGH ≥ takeProfit.
 *   For a SHORT: SL at HIGH ≥ stopLoss ; TP at LOW ≤ takeProfit.
 *
 * Same-candle policy (SL and TP both touched in one candle):
 *   Default SL_FIRST — we never know intrabar order from OHLC, so we assume
 *   the stop hit first (safer for the trader). Configurable via
 *   {@link BacktestSameCandlePolicy}.
 *
 * Liquidation (Futures only):
 *   If the candle also crosses the pre-computed liquidation price, that
 *   fires BEFORE any TP — you cannot take profit on a position the exchange
 *   has already liquidated.
 */
public final class BacktestExecutionSimulator {

	private BacktestExecutionSimulator() {}

	/** Result of evaluating a candle against an open position. */
	public sealed interface Trigger permits TpTrigger, SlTrigger, LiquidationTrigger, NoTrigger {}
	public record TpTrigger(BigDecimal exitPrice) implements Trigger {}
	public record SlTrigger(BigDecimal exitPrice) implements Trigger {}
	public record LiquidationTrigger(BigDecimal exitPrice) implements Trigger {}
	public record NoTrigger() implements Trigger {}

	public static final NoTrigger NONE = new NoTrigger();

	/**
	 * Compute the entry price given execution model + slippage. Returns null
	 * when the model is NEXT_CANDLE_OPEN and no next candle exists.
	 */
	public static BigDecimal computeEntryPrice(BacktestExecutionModel model, PositionSide side,
			HistoricalCandle signalCandle, HistoricalCandle nextCandle, BigDecimal slippagePct) {
		BigDecimal ref = switch (model) {
			case NEXT_CANDLE_OPEN -> nextCandle == null ? null : nextCandle.open();
			case SAME_CANDLE_CLOSE -> signalCandle.close();
		};
		if (ref == null) return null;
		return applySlippage(ref, side, true, slippagePct);
	}

	/**
	 * Evaluate one candle for an open position. Returns the trigger (SL, TP,
	 * LIQ) or NoTrigger. Liquidation checked first; then the same-candle
	 * policy resolves SL/TP collisions.
	 */
	public static Trigger evaluate(HistoricalCandle candle, PositionSide side,
			BigDecimal stopLoss, BigDecimal takeProfit, BigDecimal liquidationPrice,
			BacktestSameCandlePolicy policy, TradingMode mode, BigDecimal slippagePct) {

		boolean liqTouched = mode == TradingMode.FUTURES && liquidationPrice != null
				&& liquidationPrice.signum() > 0 && touched(candle, side, liquidationPrice, true);
		boolean slTouched = stopLoss != null && stopLoss.signum() > 0
				&& touched(candle, side, stopLoss, true);
		boolean tpTouched = takeProfit != null && takeProfit.signum() > 0
				&& touched(candle, side, takeProfit, false);

		if (liqTouched) {
			// Liquidation exit: no protective slippage — the exchange liquidates at market.
			return new LiquidationTrigger(applySlippage(liquidationPrice, side, false, slippagePct));
		}

		if (slTouched && tpTouched) {
			return resolveCollision(policy, side, stopLoss, takeProfit, slippagePct);
		}
		if (slTouched) return new SlTrigger(applySlippage(stopLoss, side, false, slippagePct));
		if (tpTouched) return new TpTrigger(applySlippage(takeProfit, side, false, slippagePct));
		return NONE;
	}

	private static boolean touched(HistoricalCandle candle, PositionSide side,
			BigDecimal level, boolean isStopOrLiq) {
		if (side == PositionSide.LONG) {
			// LONG: stop/liq below entry (low touches), TP above (high touches).
			return isStopOrLiq
					? candle.low().compareTo(level) <= 0
					: candle.high().compareTo(level) >= 0;
		} else {
			// SHORT: stop/liq above entry (high touches), TP below (low touches).
			return isStopOrLiq
					? candle.high().compareTo(level) >= 0
					: candle.low().compareTo(level) <= 0;
		}
	}

	private static Trigger resolveCollision(BacktestSameCandlePolicy policy, PositionSide side,
			BigDecimal stopLoss, BigDecimal takeProfit, BigDecimal slippagePct) {
		return switch (policy) {
			// CONSERVATIVE == SL_FIRST — deliberately never automatically choose the profitable outcome.
			case CONSERVATIVE, SL_FIRST, REQUIRE_LOWER_TIMEFRAME ->
					new SlTrigger(applySlippage(stopLoss, side, false, slippagePct));
			case TP_FIRST -> new TpTrigger(applySlippage(takeProfit, side, false, slippagePct));
		};
	}

	/**
	 * Adverse slippage in %.
	 *   entering=true and LONG:  price ↑
	 *   entering=true and SHORT: price ↓
	 *   entering=false (exit) and LONG:  price ↓
	 *   entering=false (exit) and SHORT: price ↑
	 */
	public static BigDecimal applySlippage(BigDecimal ref, PositionSide side,
			boolean entering, BigDecimal slippagePct) {
		if (slippagePct == null || slippagePct.signum() == 0) return ref;
		BigDecimal delta = ref.multiply(slippagePct)
				.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
		boolean adverseUp = (side == PositionSide.LONG) == entering;
		BigDecimal out = adverseUp ? ref.add(delta) : ref.subtract(delta);
		return out.max(BigDecimal.ZERO).setScale(8, RoundingMode.HALF_UP);
	}

	/**
	 * Approximate liquidation price for a Futures position with ISOLATED margin.
	 *   LONG:  liq = entry × (1 − 1/leverage)
	 *   SHORT: liq = entry × (1 + 1/leverage)
	 * Returns null when the trading mode is SPOT or leverage ≤ 1.
	 */
	public static BigDecimal estimateLiquidation(TradingMode mode, PositionSide side,
			BigDecimal entry, int leverage) {
		if (mode != TradingMode.FUTURES || leverage <= 1 || entry == null) return null;
		BigDecimal inv = BigDecimal.ONE.divide(BigDecimal.valueOf(leverage), 8, RoundingMode.HALF_UP);
		return side == PositionSide.LONG
				? entry.multiply(BigDecimal.ONE.subtract(inv))
				: entry.multiply(BigDecimal.ONE.add(inv));
	}

	/**
	 * Risk-based sizing. Leverage does NOT scale the risk budget — it only
	 * reduces the required margin. Verified by
	 * {@code BacktestExecutionSimulatorTest#leverage_doesNotChangeRiskAmount}.
	 */
	public static BigDecimal riskBasedQuantity(BigDecimal availableBalance,
			BigDecimal riskPct, BigDecimal entryPrice, BigDecimal stopLoss) {
		if (availableBalance == null || availableBalance.signum() <= 0) return BigDecimal.ZERO;
		if (entryPrice == null || entryPrice.signum() <= 0) return BigDecimal.ZERO;
		if (stopLoss == null || stopLoss.signum() <= 0) return BigDecimal.ZERO;
		BigDecimal distance = entryPrice.subtract(stopLoss).abs();
		if (distance.signum() <= 0) return BigDecimal.ZERO;
		BigDecimal riskAmount = availableBalance.multiply(riskPct)
				.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
		return riskAmount.divide(distance, 8, RoundingMode.DOWN);
	}
}
