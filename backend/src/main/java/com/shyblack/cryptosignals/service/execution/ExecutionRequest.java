package com.shyblack.cryptosignals.service.execution;

import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * The normalized, engine-agnostic description of one intended execution.
 *
 * <p>Deliberately <b>not</b> an exchange request model. It is the router's own
 * vocabulary: the existing {@code PlaceOrderRequest} and
 * {@code PlaceFuturesOrderRequest} records remain the sole models that reach an
 * adapter, and each engine maps this record into its own.
 *
 * <p>Contains no credential of any kind — no API key, no API secret, no listen
 * key. The exchange credential is resolved by the engine from the owning account
 * at submission time and never passes through the router.
 *
 * @param signalId signal this execution derives from
 * @param userId owning account holder
 * @param accountMode PAPER or LIVE
 * @param accountCategory SPOT or FUTURES; never MAIN or OPTIONS
 * @param tradingMode market mode carried by the signal
 * @param symbol market symbol
 * @param side tradable direction
 * @param quantity intended size; null when sizing has not run
 * @param orderType market/limit/stop type in engine-neutral form
 * @param referencePrice price used for sizing and reference only
 * @param stopLoss stop price, null when the signal has none
 * @param takeProfit primary target, null when the signal has none
 * @param strategyId strategy identifier from the signal, for audit only
 * @param idempotencyKey deterministic identity of this execution
 */
public record ExecutionRequest(
		UUID signalId,
		UUID userId,
		AccountMode accountMode,
		AccountCategory accountCategory,
		TradingMode tradingMode,
		String symbol,
		String side,
		BigDecimal quantity,
		OrderType orderType,
		BigDecimal referencePrice,
		BigDecimal stopLoss,
		BigDecimal takeProfit,
		String strategyId,
		String idempotencyKey) {

	/**
	 * Engine-neutral order type.
	 *
	 * <p>Each execution engine maps this onto its own exchange enum. Keeping it
	 * separate is what stops a spot {@code STOP_LOSS_LIMIT} from leaking into the
	 * futures path, where the equivalent is {@code STOP_MARKET}.
	 */
	public enum OrderType {
		MARKET,
		LIMIT,
		STOP_LOSS_LIMIT
	}

	/** The category is one an engine actually exists for. */
	public boolean isExecutable() {
		return accountCategory == AccountCategory.SPOT
				|| accountCategory == AccountCategory.FUTURES;
	}

	/**
	 * A copy with sizing results filled in.
	 *
	 * <p>The router produces the request before sizing has necessarily run, so a
	 * DRY_RUN or an audit record may hold a request whose quantity is null. That
	 * is honest: the router did not invent a size.
	 */
	public ExecutionRequest withSizing(BigDecimal sizedQuantity, BigDecimal sizedReferencePrice) {
		return new ExecutionRequest(
				signalId, userId, accountMode, accountCategory, tradingMode, symbol, side,
				sizedQuantity, orderType,
				sizedReferencePrice == null ? referencePrice : sizedReferencePrice,
				stopLoss, takeProfit, strategyId, idempotencyKey);
	}
}