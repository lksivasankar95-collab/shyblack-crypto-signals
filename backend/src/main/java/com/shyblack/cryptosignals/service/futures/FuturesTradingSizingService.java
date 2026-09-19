package com.shyblack.cryptosignals.service.futures;

import com.shyblack.cryptosignals.entity.FuturesTradingAccount;
import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.exchange.SymbolRules;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/**
 * Futures position sizing — RISK-based, NOT balance/entry.
 *
 *   qty = (availableBalance × riskPct / 100) / |entry − stop|
 *
 * Leverage does NOT expand the risk budget. It only changes how much margin
 * the exchange requires; the trader's loss on stop is unchanged.
 *
 * After the risk math, qty is normalised to the exchange step / min qty /
 * min notional. If the account cannot post enough initial margin
 * ({@code notional / leverage > availableBalance}) the trade is rejected.
 */
@Service
public class FuturesTradingSizingService {

	public record Sizing(BigDecimal quantity, BigDecimal notional, BigDecimal riskAmount,
			BigDecimal initialMargin, int leverage, String reason) {
		public boolean ok() { return reason == null; }
	}

	public Sizing size(FuturesTradingAccount account, Signal signal, SymbolRules rules,
			BigDecimal availableBalance, BigDecimal referencePrice, int leverage) {
		if (referencePrice == null || referencePrice.signum() <= 0) return fail("INVALID_PRICE");
		if (signal == null || signal.getStopLoss() == null) return fail("INVALID_STOP_LOSS");
		if (availableBalance == null || availableBalance.signum() <= 0) return fail("INSUFFICIENT_MARGIN");
		if (rules == null) return fail("INVALID_SYMBOL_RULES");
		if (leverage <= 0) return fail("LEVERAGE_EXCEEDED");

		BigDecimal riskPct = signal.getSuggestedRiskPercent() != null
				? signal.getSuggestedRiskPercent() : new BigDecimal("1.00");
		BigDecimal riskAmount = availableBalance.multiply(riskPct)
				.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);

		BigDecimal stopDistance = referencePrice.subtract(signal.getStopLoss()).abs();
		if (stopDistance.signum() <= 0) return fail("INVALID_STOP_LOSS");

		BigDecimal rawQty = riskAmount.divide(stopDistance, 8, RoundingMode.DOWN);
		BigDecimal qty = rules.normalizeQuantity(rawQty);
		if (qty.signum() <= 0 || !rules.meetsMinQty(qty)) return fail("POSITION_SIZE_INVALID");
		if (!rules.meetsMinNotional(qty, referencePrice)) return fail("POSITION_SIZE_INVALID");

		BigDecimal notional = qty.multiply(referencePrice);

		// Cap by configured max notional per trade.
		BigDecimal cap = account.getMaxNotionalPerTrade();
		if (cap != null && cap.signum() > 0 && notional.compareTo(cap) > 0) {
			BigDecimal scaledQty = rules.normalizeQuantity(cap.divide(referencePrice, 8, RoundingMode.DOWN));
			if (scaledQty.signum() <= 0 || !rules.meetsMinNotional(scaledQty, referencePrice)) {
				return fail("MAX_NOTIONAL_EXCEEDED");
			}
			qty = scaledQty;
			notional = qty.multiply(referencePrice);
		}

		// Initial margin required = notional / leverage. Must fit inside available balance.
		BigDecimal initialMargin = notional.divide(BigDecimal.valueOf(leverage), 8, RoundingMode.HALF_UP);
		if (initialMargin.compareTo(availableBalance) > 0) return fail("MARGIN_INSUFFICIENT");

		return new Sizing(qty, notional, riskAmount, initialMargin, leverage, null);
	}

	// Suppress unused-side lint — kept for symmetry with SPOT signature.
	@SuppressWarnings("unused")
	private static PositionSide reserved() { return PositionSide.LONG; }

	private static Sizing fail(String reason) {
		return new Sizing(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, reason);
	}
}
