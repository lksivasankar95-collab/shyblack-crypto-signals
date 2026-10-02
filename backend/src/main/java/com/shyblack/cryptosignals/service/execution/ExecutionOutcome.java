package com.shyblack.cryptosignals.service.execution;

/**
 * Outcome of one routed execution attempt, recorded once an order has actually
 * been handed to the exchange.
 *
 * <p>Distinct from {@link LiveOrderStatus} and {@link FuturesOrderStatus}, which
 * model the <i>order's</i> lifecycle as reported by the exchange. This enum
 * models the <i>submission attempt's</i> outcome, which can fail before any order
 * exists at all.
 *
 * <p>The separation matters for retry safety: a submission whose outcome is
 * {@link #UNKNOWN} may or may not have created an order on the exchange, and
 * must be reconciled by querying exchange state rather than by re-sending.
 */
public enum ExecutionOutcome {

	/** The request was built and accepted for submission. No exchange call yet. */
	ORDER_REQUESTED,

	/** The exchange acknowledged the order. It exists; it is not necessarily filled. */
	ORDER_ACCEPTED,

	/** The exchange filled some but not all of the requested quantity. */
	ORDER_PARTIALLY_FILLED,

	/** The exchange reports the order fully filled. Only the exchange can say this. */
	ORDER_FILLED,

	/** The order was cancelled. */
	ORDER_CANCELED,

	/** The exchange or a gate refused the order. Nothing was created. */
	ORDER_REJECTED,

	/**
	 * The submission attempt failed in a way that leaves the exchange outcome
	 * unestablished: timeout, connection reset, or an unparseable response.
	 *
	 * <p>The order may or may not exist. This state is explicitly not a licence to
	 * retry — the order must be reconciled first.
	 */
	UNKNOWN;

	/**
	 * True when the exchange has given a definite answer.
	 *
	 * <p>Only these states are safe to treat as final. {@link #UNKNOWN} is not.
	 */
	public boolean isDefinitive() {
		return this != UNKNOWN;
	}

	/**
	 * True when this outcome reflects a rejection rather than a transport problem.
	 *
	 * <p>A rejection is a definite answer from the exchange and a retry would be
	 * refused again for the same reason, so a retry policy must distinguish it
	 * from {@link #UNKNOWN}.
	 */
	public boolean isRejection() {
		return this == ORDER_REJECTED;
	}
}