package com.shyblack.cryptosignals.entity;

import com.shyblack.cryptosignals.entity.enums.AccountAvailability;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Synchronization and status record for one account scope
 * ({@link AccountMode} x {@link AccountCategory}).
 *
 * <p>Deliberately holds <em>no</em> credential reference. {@link ExchangeCredential} remains the
 * single Binance credential seam, unique per {@code (user, exchange)} and resolvable on demand via
 * {@code ExchangeCredentialRepository.findByUser_IdAndExchange}; duplicating that FK here would let
 * the credential and the connection row disagree. This entity only persists the status the
 * credential does not already carry.
 *
 * <p>One row per scope per user, enforced by
 * {@code uk_portfolio_account_connections_scope (user_id, account_mode, account_category)}. The key
 * deliberately excludes {@code exchange}: it is nullable (a PAPER scope has no exchange) and
 * PostgreSQL treats NULLs as distinct inside a unique constraint, which would permit duplicates.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
		name = "portfolio_account_connections",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_portfolio_account_connections_scope",
				columnNames = {"user_id", "account_mode", "account_category"}),
		indexes = @Index(
				name = "ix_portfolio_account_connections_user_mode",
				columnList = "user_id,account_mode"))
public class PortfolioAccountConnection extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(name = "account_mode", nullable = false, length = 20)
	private AccountMode accountMode;

	@Enumerated(EnumType.STRING)
	@Column(name = "account_category", nullable = false, length = 20)
	private AccountCategory accountCategory;

	/** Null for a PAPER scope, which has no exchange. Never defaulted to a fabricated exchange. */
	@Enumerated(EnumType.STRING)
	@Column(name = "exchange", length = 20)
	private ExchangeName exchange;

	@Enumerated(EnumType.STRING)
	@Column(name = "connection_status", nullable = false, length = 20)
	private ExchangeConnectionStatus connectionStatus = ExchangeConnectionStatus.NOT_CONNECTED;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AccountAvailability availability = AccountAvailability.NOT_CONNECTED;

	/** Null until the first successful synchronization. Null is not a zero timestamp. */
	@Column(name = "last_synced_at")
	private Instant lastSyncedAt;

	/**
	 * Event time of the last applied user-data WebSocket event for this scope, used as the ordering
	 * high-water mark that rejects out-of-order events and reveals gaps. Kept separate from
	 * {@code lastSyncedAt}, which records the last REST snapshot: a stream event must never overwrite
	 * the snapshot timestamp, and vice versa.
	 */
	@Column(name = "last_event_at")
	private Instant lastEventAt;

	/** Diagnostic text for the most recent synchronization outcome, never fabricated. */
	@Column(name = "last_sync_message", length = 200)
	private String lastSyncMessage;

	@Version
	@Column(nullable = false)
	private Long version = 0L;
}