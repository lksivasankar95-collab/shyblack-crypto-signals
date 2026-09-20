package com.shyblack.cryptosignals.entity.enums;

/**
 * When a MARKET order fills relative to the candle in which its signal was generated.
 *
 * NEXT_CANDLE_OPEN — default; prevents same-candle look-ahead. Entry fills at
 *                    candle T+1 open + slippage after a signal at candle T close.
 * SAME_CANDLE_CLOSE — research only; documented as biased.
 */
public enum BacktestExecutionModel {
	NEXT_CANDLE_OPEN,
	SAME_CANDLE_CLOSE
}
