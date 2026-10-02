package com.shyblack.cryptosignals.service.execution;

/**
 * The single authoritative decision the {@link ExecutionRouter} makes about one
 * (account, signal) pair.
 *
 * <p>Exactly one of these is produced per routing attempt, and every branch
 * other than {@link #EXECUTE} means no exchange order was sent.
 */
public enum ExecutionDecision {

	/**
	 * Every gate passed and the order was submitted to the exchange.
	 *
	 * <p>Does <b>not</b> mean filled. Submission produces an exchange order in
	 * some state, and that state is recorded separately; the router never
	 * reports a fill it did not observe.
	 */
	EXECUTE,

	/**
	 * Every gate was evaluated and an execution request was produced, but no
	 * exchange order was sent.
	 *
	 * <p>This is the validation mode: it exercises the full routing and gating
	 * path, produces exactly the request that would have been sent, and proves
	 * the boundary without touching an exchange. Required for safe validation
	 * when live execution is disabled.
	 */
	DRY_RUN,

	/**
	 * A gate refused the execution. Nothing was sent.
	 *
	 * <p>The accompanying {@link ExecutionRejectionReason} is deterministic:
	 * the same inputs always produce the same reason.
	 */
	REJECT,

	/**
	 * The requested capability has no execution engine at all.
	 *
	 * <p>Currently only {@code OPTIONS}. This is deliberately distinct from
	 * {@link #REJECT}: a rejection implies the capability exists and a specific
	 * gate failed, whereas this says no engine was ever built.
	 */
	NOT_SUPPORTED,

	/**
	 * The target is an aggregation rather than a tradable market, so it has no
	 * execution path.
	 *
	 * <p>Currently only {@link com.shyblack.cryptosignals.entity.enums.AccountCategory#MAIN}.
	 * MAIN is a read-model aggregate across SPOT and FUTURES; summing the two
	 * wallets into one executable target would be a category error, so it is
	 * never executable.
	 */
	NOT_EXECUTABLE
}