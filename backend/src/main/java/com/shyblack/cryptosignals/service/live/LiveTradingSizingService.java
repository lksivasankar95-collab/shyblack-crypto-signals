package com.shyblack.cryptosignals.service.live;

import com.shyblack.cryptosignals.entity.LiveTradingAccount;
import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.exchange.SymbolRules;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/**
 * Live position sizing. Deliberately NOT shared with paper trading — a
 * miscalculation here spends real money, and we want the code path auditable
 * in a single file.
 *
 *   qty = (availableEquity * riskPct / 100) / |entry - stop|
 *
 * After math, the qty is normalized to the exchange's step size and validated
 * against {@code minQty}, {@code minNotional}, and {@code maxNotionalPerTrade}
 * from the account. If precision rounding pushes notional or risk above the
 * configured limits, the trade is rejected — never silently exceeded.
 */
@Service
public class LiveTradingSizingService {

	public record Sizing(BigDecimal quantity, BigDecimal notional, BigDecimal riskAmount, String reason) {
		public boolean ok() { return reason == null; }
	}

	public Sizing size(LiveTradingAccount account, Signal signal, SymbolRules rules,
			BigDecimal availableBalance, BigDecimal referencePrice) {
		if (referencePrice == null || referencePrice.signum() <= 0) return fail("INVALID_PRICE");
		if (signal == null || signal.getStopLoss() == null) return fail("INVALID_STOP_LOSS");
		if (availableBalance == null || availableBalance.signum() <= 0) return fail("INSUFFICIENT_BALANCE");
		if (rules == null) return fail("INVALID_SYMBOL_RULES");

		BigDecimal riskPct = signal.getSuggestedRiskPercent() != null
				? signal.getSuggestedRiskPercent()
				: new BigDecimal("1.00");
		BigDecimal riskAmount = availableBalance
				.multiply(riskPct)
				.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);

		BigDecimal stopDistance = referencePrice.subtract(signal.getStopLoss()).abs();
		if (stopDistance.signum() <= 0) return fail("INVALID_STOP_LOSS");

		BigDecimal rawQty = riskAmount.divide(stopDistance, 8, RoundingMode.DOWN);
		BigDecimal qty = rules.normalizeQuantity(rawQty);
		if (qty.signum() <= 0) return fail("INVALID_QUANTITY");
		if (!rules.meetsMinQty(qty)) return fail("INVALID_QUANTITY");
		if (!rules.meetsMinNotional(qty, referencePrice)) return fail("INVALID_QUANTITY");

		BigDecimal notional = qty.multiply(referencePrice);

		// Never deploy more than the configured guardrail.
		BigDecimal cap = account.getMaxNotionalPerTrade();
		if (cap != null && cap.signum() > 0 && notional.compareTo(cap) > 0) {
			BigDecimal scaledQty = rules.normalizeQuantity(cap.divide(referencePrice, 8, RoundingMode.DOWN));
			if (scaledQty.signum() <= 0 || !rules.meetsMinNotional(scaledQty, referencePrice)) {
				return fail("MAX_NOTIONAL_EXCEEDED");
			}
			qty = scaledQty;
			notional = qty.multiply(referencePrice);
		}

		if (notional.compareTo(availableBalance) > 0) return fail("INSUFFICIENT_BALANCE");
		return new Sizing(qty, notional, riskAmount, null);
	}

	private static Sizing fail(String reason) {
		return new Sizing(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, reason);
	}
}
