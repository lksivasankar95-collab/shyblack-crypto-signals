package com.shyblack.cryptosignals.entity;

import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.service.execution.ExecutionOutcome;
import com.shyblack.cryptosignals.service.execution.ExecutionDecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * The authoritative record of one routing decision.
 *
 * <p>This is both the audit trail and the idempotency ledger. Its uniqueness
 * constraint on {@code (user_id, signal_id, account_mode, account_category)} is
 * what makes duplicate suppression restart-safe and race-safe: a duplicate signal
 * event, an application restart replaying pending work, or two concurrent
 * consumers all collide in the database rather than relying on a heap check that
 * a restart would erase. This is the same pattern as
 * {@link PortfolioExchangeEventApplied}, and for the same reason.
 *
 * <p><b>No credential is stored here.</b> There is deliberately no API key, no
 * secret, no listen key and no signed request string. The owning account
 * reference is enough to resolve credentials when needed, and the row itself is
 * safe to read and log.
 *
 * <p>The row is written before any exchange call, so an attempt that never
 * completes still leaves evidence. An {@link ExecutionOutcome#UNKNOWN} row is
 * what tells a later attempt that the previous submission may or may not have
 * reached the exchange.
 */
@Entity
@Table(
		name = "execution_decisions",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_execution_decisions_identity",
				columnNames = {"user_id", "signal_id", "account_mode", "account_category"}),
		indexes = {
			@Index(name = "ix_execution_decisions_signal", columnList = "signal_id"),
			@Index(name = "ix_execution_decisions_decision", columnList = "decision"),
			@Index(name = "ix_execution_decisions_outcome", columnList = "outcome"),
			@Index(name = "ix_execution_decisions_decided_at", columnList = "decided_at")
		})
@Getter
@Setter
public class ExecutionDecisionRecord extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(name = "account_mode", nullable = false, length = 16)
	private AccountMode accountMode;

	@Enumerated(EnumType.STRING)
	@Column(name = "account_category", nullable = false, length = 16)
	private AccountCategory accountCategory;

	@Enumerated(EnumType.STRING)
	@Column(name = "trading_mode", length = 16)
	private TradingMode tradingMode;

	@Column(name = "user_id", nullable = false)
	private java.util.UUID userId;

	@Column(name = "signal_id", nullable = false)
	private java.util.UUID signalId;

	/** Stable identity string; mirrors the uniqueness constraint columns. */
	@Column(name = "identity_key", nullable = false, length = 200)
	private String identityKey;

	@Column(name = "symbol", length = 32)
	private String symbol;

	@Column(name = "side", length = 8)
	private String side;

	@Enumerated(EnumType.STRING)
	@Column(name = "decision", nullable = false, length = 24)
	private ExecutionDecision decision;

	/**
	 * Set only when {@link #decision} is {@code REJECT}.
	 *
	 * <p>Null for every other decision, so a rejection always carries a reason and
	 * a non-rejection never carries a misleading one.
	 */
	@Column(name = "rejection_reason", length = 48)
	private String rejectionReason;

	/**
	 * Human-readable detail. Never a credential, never a raw exchange response
	 * body, and never an authorization header.
	 */
	@Column(name = "detail", length = 500)
	private String detail;

	/** The engine that was selected, for audit. Never used to bypass a gate. */
	@Column(name = "target_engine", length = 32)
	private String targetEngine;

	@Enumerated(EnumType.STRING)
	@Column(name = "outcome", length = 32)
	private ExecutionOutcome outcome;

	/** Exchange order id, once the exchange has supplied one. Null before then. */
	@Column(name = "exchange_order_id", length = 80)
	private String exchangeOrderId;

	/**
	 * Exchange-side status string exactly as the exchange reported it.
	 *
	 * <p>Stored verbatim rather than normalised so an unexpected value is visible
	 * instead of being mapped to a convenient default.
	 */
	@Column(name = "exchange_status", length = 32)
	private String exchangeStatus;

	@Column(name = "decided_at", nullable = false)
	private Instant decidedAt;

	@Column(name = "dry_run", nullable = false)
	private boolean dryRun;

	/**
	 * True when a DRY_RUN produced a complete request that would have been sent.
	 *
	 * <p>Lets an operator confirm the router produces the expected order shape
	 * without any exchange involvement.
	 */
	public boolean isRequestProduced() {
		return dryRun && decision == ExecutionDecision.DRY_RUN;
	}
}