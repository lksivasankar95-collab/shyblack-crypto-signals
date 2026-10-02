package com.shyblack.cryptosignals.service.execution;

import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import java.util.UUID;

/**
 * Builds the normalized {@link ExecutionRequest} for one routing attempt.
 *
 * <p>Separate from the router so the mapping from a {@link Signal} to an
 * execution request is testable in isolation, with no database and no exchange.
 *
 * <p>Values are copied from the signal. Nothing is computed here: no size, no
 * price adjustment, no score change, no strategy parameter. Quantity and
 * reference price stay null until the sizing service supplies them, because a
 * router-invented size would be a fabricated order.
 */
public final class ExecutionRequests {

	private ExecutionRequests() {
	}

	/**
	 * Builds the request for one account scope.
	 *
	 * @param category category resolved by {@link ExecutionRouting}; must not be
	 *     MAIN or OPTIONS
	 * @return a request whose {@code quantity} and {@code referencePrice} are null
	 *     until sizing runs
	 */
	public static ExecutionRequest from(
			Signal signal,
			UUID userId,
			AccountMode accountMode,
			AccountCategory category,
			TradingMode tradingMode) {

		ExecutionIdentity identity = ExecutionIdentity.of(
				userId, signal.getId(), accountMode, category, tradingMode);

		return new ExecutionRequest(
				signal.getId(),
				userId,
				accountMode,
				category,
				tradingMode,
				normaliseSymbol(signal.getSymbol()),
				sideName(signal.getSide()),
				/* quantity        */ null,
				ExecutionRequest.OrderType.MARKET,
				/* referencePrice */ null,
				signal.getStopLoss(),
				signal.getTargetPrice(),
				// Signal carries both a strategy name and a strategy UUID; prefer the stable id
				// and fall back to the name for signals created before it existed.
				signal.getStrategyId() != null
						? signal.getStrategyId().toString()
						: signal.getStrategy(),
				identity.canonical());
	}

	/**
	 * The engine-neutral order type for a signal.
	 *
	 * <p>Signals are actionable market entries, so this is always
	 * {@link ExecutionRequest.OrderType#MARKET}. The type is carried in the
	 * request anyway so a future strategy that requires a limit entry does not
	 * need a new request model.
	 */
	public static ExecutionRequest.OrderType orderTypeFor(Signal signal) {
		return ExecutionRequest.OrderType.MARKET;
	}

	/** Upper-cases a symbol, returning null rather than an empty string. */
	private static String normaliseSymbol(String symbol) {
		if (symbol == null) {
			return null;
		}
		String trimmed = symbol.trim();
		return trimmed.isEmpty() ? null : trimmed.toUpperCase();
	}

	private static String sideName(PositionSide side) {
		return side == null ? null : side.name();
	}
}