package com.shyblack.cryptosignals.service.futures;

import com.shyblack.cryptosignals.config.FuturesTradingProperties;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Conservative pre-trade liquidation safety.
 *
 * We don't try to reproduce Binance's exact maintenance-margin formula; the
 * live account has that value and Binance will refuse if it fails. Our job
 * pre-trade is to make sure the SL sits comfortably above the estimated
 * liquidation price using an initial-margin approximation:
 *
 *   LONG  liq ≈ entry × (1 − 1/leverage)
 *   SHORT liq ≈ entry × (1 + 1/leverage)
 *
 * We then require |SL − entry| < |liq − entry| × (1 − buffer%). If the SL
 * is too close to (or below) the estimated liquidation price we reject.
 */
@Service
@RequiredArgsConstructor
public class FuturesLiquidationService {

	private final FuturesTradingProperties props;

	public record Assessment(BigDecimal liquidationPrice, boolean safe, String reason) {}

	public Assessment assess(PositionSide side, BigDecimal entry, BigDecimal stop, int leverage) {
		if (entry == null || entry.signum() <= 0) return new Assessment(null, false, "INVALID_ENTRY");
		if (stop == null || stop.signum() <= 0) return new Assessment(null, false, "INVALID_STOP");
		if (leverage <= 0) return new Assessment(null, false, "INVALID_LEVERAGE");

		BigDecimal inverseLev = BigDecimal.ONE.divide(BigDecimal.valueOf(leverage), 8, RoundingMode.HALF_UP);
		BigDecimal liq = side == PositionSide.LONG
				? entry.multiply(BigDecimal.ONE.subtract(inverseLev))
				: entry.multiply(BigDecimal.ONE.add(inverseLev));

		BigDecimal buffer = props.liquidationBufferPct().movePointLeft(2); // 15% -> 0.15
		BigDecimal safeDistance = entry.subtract(liq).abs()
				.multiply(BigDecimal.ONE.subtract(buffer))
				.abs();
		BigDecimal stopDistance = entry.subtract(stop).abs();

		// Minimum stop distance sanity gate: SL must be at least minStopDistancePct away from entry.
		BigDecimal minSlDistance = entry.multiply(props.minStopDistancePct()).movePointLeft(2);
		if (stopDistance.compareTo(minSlDistance) < 0) {
			return new Assessment(liq, false, "STOP_DISTANCE_INVALID");
		}
		if (stopDistance.compareTo(safeDistance) >= 0) {
			return new Assessment(liq, false, "LIQUIDATION_RISK");
		}
		return new Assessment(liq, true, null);
	}
}
