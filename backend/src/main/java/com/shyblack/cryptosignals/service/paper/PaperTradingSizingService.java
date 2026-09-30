package com.shyblack.cryptosignals.service.paper;

import com.shyblack.cryptosignals.config.PaperTradingProperties;
import com.shyblack.cryptosignals.entity.Portfolio;
import com.shyblack.cryptosignals.entity.Signal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Risk-based position sizing.
 *
 * riskAmount = availableBalance * riskPct / 100
 * qty        = riskAmount / |entry - stopLoss|
 *
 * Then constrained by:
 *   - notional + entry fee <= availableBalance (a paper trader cannot deploy more
 *     capital than they have, and the debit includes the entry commission)
 *   - qty > 0, finite, non-NaN
 *
 * If the resulting notional exceeds what the free balance can afford we scale
 * the position down to fit (leaves risk% unchanged but caps size). The cap
 * reserves room for the entry fee so the subsequent debit cannot be rejected
 * as INSUFFICIENT_BALANCE. Returns null when a valid non-zero size cannot be
 * computed.
 */
@Service
@RequiredArgsConstructor
public class PaperTradingSizingService {

	static final int QTY_SCALE = 8;
	static final int MONEY_SCALE = 8;

	private final PaperTradingProperties props;

	public record Sizing(BigDecimal quantity, BigDecimal notional, BigDecimal riskAmount) {}

	public Sizing size(Portfolio portfolio, Signal signal, BigDecimal entryPrice) {
		if (portfolio == null || signal == null || entryPrice == null) return null;
		if (entryPrice.signum() <= 0) return null;
		if (signal.getStopLoss() == null) return null;

		BigDecimal available = portfolio.getAvailableBalance();
		if (available == null || available.signum() <= 0) return null;

		BigDecimal riskPct = signal.getSuggestedRiskPercent() != null
				? signal.getSuggestedRiskPercent()
				: props.riskPerTradePct();
		if (riskPct.signum() <= 0) return null;

		BigDecimal riskAmount = available
				.multiply(riskPct)
				.divide(BigDecimal.valueOf(100), MONEY_SCALE, RoundingMode.HALF_UP);

		BigDecimal stopDistance = entryPrice.subtract(signal.getStopLoss()).abs();
		if (stopDistance.signum() <= 0) return null;

		BigDecimal qty = riskAmount.divide(stopDistance, QTY_SCALE, RoundingMode.DOWN);
		if (qty.signum() <= 0) return null;

		// Largest notional whose debit (notional + fee) still fits the free balance.
		BigDecimal feeRate = props.feeRatePct() == null ? BigDecimal.ZERO : props.feeRatePct();
		BigDecimal feeMultiplier = BigDecimal.ONE.add(
				feeRate.divide(BigDecimal.valueOf(100), MONEY_SCALE, RoundingMode.HALF_UP));
		BigDecimal maxNotional = available.divide(feeMultiplier, MONEY_SCALE, RoundingMode.DOWN);

		BigDecimal notional = qty.multiply(entryPrice).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
		if (notional.compareTo(maxNotional) > 0) {
			// Scale qty down so the fee-inclusive debit fits the free balance.
			qty = maxNotional.divide(entryPrice, QTY_SCALE, RoundingMode.DOWN);
			if (qty.signum() <= 0) return null;
			notional = qty.multiply(entryPrice).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
		}

		return new Sizing(qty, notional, riskAmount);
	}
}
