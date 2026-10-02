package com.shyblack.cryptosignals.exchange.futures;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One account income record as reported by the exchange income endpoint.
 *
 * <p>This is the only authoritative source of exchange realized P&amp;L. A record with
 * {@code incomeType} {@code REALIZED_PNL} carries realized profit or loss; {@code COMMISSION},
 * {@code FUNDING_FEE} and the rest are separate income types and must never be summed into realized
 * P&amp;L.
 *
 * <p>{@code transactionId} is the natural identity used to deduplicate income records, because the
 * income endpoint is time-windowed and paginated and can repeat a boundary record across pages.
 */
public record FuturesIncomeSnapshot(
		String symbol,
		/** Raw exchange income type, e.g. REALIZED_PNL, COMMISSION, FUNDING_FEE, TRANSFER. */
		String incomeType,
		BigDecimal income,
		/** Raw exchange asset, e.g. USDT. Null when the exchange omits it. */
		String asset,
		Long transactionId,
		Instant time) {}
