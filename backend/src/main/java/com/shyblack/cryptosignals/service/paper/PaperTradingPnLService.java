package com.shyblack.cryptosignals.service.paper;

import com.shyblack.cryptosignals.config.PaperTradingProperties;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Authoritative P&L math for paper trading. The same primitives are used for
 * open (unrealized) and close (realized) so screens never diverge.
 *
 * gross(LONG)  = (exit - entry) * qty
 * gross(SHORT) = (entry - exit) * qty
 * fee          = notional * feeRatePct / 100     (applied on both legs)
 * net          = gross - entryFee - exitFee
 */
@Service
@RequiredArgsConstructor
public class PaperTradingPnLService {

	static final int PNL_SCALE = 8;
	static final int PRICE_SCALE = 8;
	static final int PCT_SCALE = 4;

	private final PaperTradingProperties props;

	public BigDecimal notional(BigDecimal price, BigDecimal quantity) {
		return safe(price).multiply(safe(quantity)).setScale(PNL_SCALE, RoundingMode.HALF_UP);
	}

	public BigDecimal fee(BigDecimal notional) {
		return safe(notional)
				.multiply(props.feeRatePct())
				.divide(BigDecimal.valueOf(100), PNL_SCALE, RoundingMode.HALF_UP);
	}

	/** Adverse slippage against the trader's direction. */
	public BigDecimal applySlippage(BigDecimal referencePrice, PositionSide side, boolean entering) {
		BigDecimal delta = safe(referencePrice)
				.multiply(props.slippagePct())
				.divide(BigDecimal.valueOf(100), PRICE_SCALE, RoundingMode.HALF_UP);
		boolean adverseUp = (side == PositionSide.LONG) == entering; // long entering = pay up; long exiting = get less
		if (adverseUp) {
			return referencePrice.add(delta).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
		}
		return referencePrice.subtract(delta).max(BigDecimal.ZERO)
				.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
	}

	public BigDecimal grossPnl(PositionSide side, BigDecimal entryPrice, BigDecimal exitPrice, BigDecimal quantity) {
		BigDecimal diff = side == PositionSide.SHORT
				? safe(entryPrice).subtract(safe(exitPrice))
				: safe(exitPrice).subtract(safe(entryPrice));
		return diff.multiply(safe(quantity)).setScale(PNL_SCALE, RoundingMode.HALF_UP);
	}

	public BigDecimal netPnl(BigDecimal grossPnl, BigDecimal entryFee, BigDecimal exitFee) {
		return safe(grossPnl).subtract(safe(entryFee)).subtract(safe(exitFee))
				.setScale(PNL_SCALE, RoundingMode.HALF_UP);
	}

	public BigDecimal pctReturn(BigDecimal netPnl, BigDecimal entryNotional) {
		if (entryNotional == null || entryNotional.signum() == 0) {
			return BigDecimal.ZERO.setScale(PCT_SCALE, RoundingMode.HALF_UP);
		}
		return safe(netPnl)
				.multiply(BigDecimal.valueOf(100))
				.divide(entryNotional, PCT_SCALE, RoundingMode.HALF_UP);
	}

	private static BigDecimal safe(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}
}
