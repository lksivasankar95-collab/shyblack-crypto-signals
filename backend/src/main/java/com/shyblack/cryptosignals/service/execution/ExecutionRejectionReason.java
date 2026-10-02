package com.shyblack.cryptosignals.service.execution;

/**
 * The deterministic reason a routing attempt did not execute.
 *
 * <p>Every constant corresponds to exactly one gate. The ordering is the order in
 * which the gates are evaluated, so the first failing gate wins and the reason
 * reported for a given input never changes between runs.
 *
 * <p>There is deliberately no catch-all reason such as {@code ERROR}: an
 * unexplained refusal is a bug, and collapsing every unknown cause into one value
 * would hide it.
 */
public enum ExecutionRejectionReason {

	// ---- capability: no execution path exists at all ----------------------

	/** {@code TradingMode.OPTIONS}. No options engine, no options exchange integration. */
	OPTIONS_NOT_SUPPORTED,

	/** {@code AccountCategory.MAIN}. An aggregation, not a tradable market. */
	MAIN_NOT_EXECUTABLE,

	/** A live category with no corresponding live execution engine. */
	NO_EXECUTION_ENGINE,

	// ---- signal eligibility -----------------------------------------------

	/** The signal id in the event does not resolve to a stored signal. */
	SIGNAL_NOT_FOUND,

	/** {@code Signal.status} is not {@code ACTIVE}. */
	SIGNAL_NOT_ACTIVE,

	/** The signal carries a null or blank symbol. */
	SYMBOL_MISSING,

	/** The signal's {@code tradingMode} is null. */
	TRADING_MODE_MISSING,

	/** The signal's side is null or not a tradable direction. */
	DIRECTION_INVALID,

	// ---- account resolution ------------------------------------------------

	/** No account of the routed category exists for this user. */
	ACCOUNT_NOT_FOUND,

	/** The account exists but {@code enabled} is false. Never auto-activated. */
	ACCOUNT_INACTIVE,

	/** The account's kill switch is active. Blocks new entries. */
	KILL_SWITCH_ACTIVE,

	/** The account's {@code connectionStatus} is not {@code CONNECTED}. */
	ACCOUNT_NOT_CONNECTED,

	/** The futures account has not been acknowledged by the operator. */
	ACCOUNT_NOT_ACKNOWLEDGED,

	/** The account has no credential, or its credential is not usable. */
	CREDENTIALS_UNAVAILABLE,

	/** {@code UserSettings.liveTradingAllowed} is false. */
	LIVE_TRADING_NOT_ALLOWED,

	// ---- execution switch --------------------------------------------------

	/** {@code app.live-trading.auto-execute} / {@code app.futures-trading.auto-execute} is false. */
	AUTO_EXECUTE_DISABLED,

	/** {@code app.execution.dry-run} is true, so routing completes but nothing is sent. */
	DRY_RUN_ACTIVE,

	// ---- market and risk ---------------------------------------------------

	/** The adapter has no trading rules for this symbol. */
	SYMBOL_NOT_SUPPORTED,

	/** The adapter is unavailable for this credential. */
	ADAPTER_UNAVAILABLE,

	/**
	 * An existing risk service refused the signal.
	 *
	 * <p>The underlying {@code LiveTradingRiskReason} or {@code FuturesRiskReason}
	 * is carried in the decision detail; this reason only records that the
	 * delegated gate said no. The router does not reinterpret it.
	 */
	RISK_REJECTED,

	/** The sizing service could not size a position for this signal. */
	SIZING_REJECTED,

	/** Position, notional, margin or liquidation limits refused the trade. */
	LIMITS_EXCEEDED,

	// ---- idempotency ------------------------------------------------------

	/**
	 * An execution decision already exists for this
	 * (user, signal, mode, category) identity.
	 *
	 * <p>The normal outcome of a duplicate signal event, a restart replay, or two
	 * concurrent consumers racing. Never a signal of trouble on its own.
	 */
	DUPLICATE_EXECUTION,

	/**
	 * A previous attempt for this identity is still {@code UNKNOWN}, so its true
	 * exchange outcome is not yet established.
	 *
	 * <p>Re-sending is unsafe until the order state is reconciled, so this is a
	 * distinct reason from {@link #DUPLICATE_EXECUTION}.
	 */
	PREVIOUS_OUTCOME_UNKNOWN
}