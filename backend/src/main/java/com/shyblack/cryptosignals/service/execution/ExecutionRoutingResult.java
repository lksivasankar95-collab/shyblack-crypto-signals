package com.shyblack.cryptosignals.service.execution;

import com.shyblack.cryptosignals.entity.ExecutionDecisionRecord;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.service.execution.ExecutionOutcome;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.experimental.Accessors;
import lombok.Value;

/**
 * The complete, immutable outcome of one routing attempt.
 *
 * <p>Produced for every gate outcome, including successes. A caller never has to
 * infer why nothing happened: when {@code decision} is not
 * {@link ExecutionDecision#EXECUTE}, {@code rejectionReason} or {@code detail}
 * says so.
 *
 * <p>Carries no credential. {@code detail} is written by the router from its own
 * values and the existing risk enums, never from a raw exchange response or an
 * authorization header.
 */
@Value
@Builder
@Accessors(fluent = true)
public class ExecutionRoutingResult {

	/** The identity this attempt was made under. Always present. */
	ExecutionIdentity identity;

	/** The single authoritative decision. */
	ExecutionDecision decision;

	/** Set only when {@code decision} is {@code REJECT}. */
	ExecutionRejectionReason rejectionReason;

	/** Human-readable explanation. Never a credential or a raw exchange body. */
	String detail;

	/** Name of the engine selected, for audit. */
	String targetEngine;

	/**
	 * The request that was produced.
	 *
	 * <p>Non-null for {@code EXECUTE} and for {@code DRY_RUN}; null when no request
	 * could be built.
	 */
	ExecutionRequest request;

	/** True when routing completed but nothing was sent to the exchange. */
	boolean dryRun;

	/** Exchange outcome, only known once a submission has been attempted. */
	ExecutionOutcome outcome;

	/** Exchange order id, when one exists. */
	String exchangeOrderId;

	/** Exchange status string, exactly as reported. */
	String exchangeStatus;

	/** Id of the persisted decision record, when one was written. */
	UUID recordId;

	Instant decidedAt;

	/** True only for {@link ExecutionDecision#EXECUTE}. */
	public boolean executed() {
		return decision == ExecutionDecision.EXECUTE;
	}

	/** True when the attempt produced a complete request but sent nothing. */
	public boolean dryRunComplete() {
		return decision == ExecutionDecision.DRY_RUN && request != null;
	}

	/** True when nothing was sent to any exchange. */
	public boolean noOrderPlaced() {
		return decision != ExecutionDecision.EXECUTE;
	}

	public AccountMode accountMode() {
		return identity == null ? null : identity.accountMode();
	}

	public AccountCategory accountCategory() {
		return identity == null ? null : identity.accountCategory();
	}

	public TradingMode tradingMode() {
		return identity == null ? null : identity.tradingMode();
	}

	/**
	 * Converts to a persistable audit row.
	 *
	 * <p>Copies only fields that carry no credential, and stores the rejection
	 * reason as its enum name so a query can filter on it.
	 */
	public ExecutionDecisionRecord toRecord() {
		ExecutionDecisionRecord row = new ExecutionDecisionRecord();
		row.setAccountMode(identity.accountMode());
		row.setAccountCategory(identity.accountCategory());
		row.setTradingMode(identity.tradingMode());
		row.setUserId(identity.userId());
		row.setSignalId(identity.signalId());
		row.setIdentityKey(identity.canonical());
		row.setSymbol(request == null ? null : request.symbol());
		row.setSide(request == null ? null : request.side());
		row.setDecision(decision);
		row.setRejectionReason(rejectionReason == null ? null : rejectionReason.name());
		row.setDetail(truncate(detail, 500));
		row.setTargetEngine(targetEngine);
		row.setOutcome(outcome);
		row.setExchangeOrderId(exchangeOrderId);
		row.setExchangeStatus(exchangeStatus);
		row.setDecidedAt(decidedAt == null ? Instant.now() : decidedAt);
		row.setDryRun(dryRun);
		return row;
	}

	/** Matches the existing 500-character diagnostic column convention. */
	private static String truncate(String value, int max) {
		if (value == null) {
			return null;
		}
		return value.length() <= max ? value : value.substring(0, max - 3) + "...";
	}
}