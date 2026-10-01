package com.shyblack.cryptosignals.entity;

import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One applied Binance user-data event, recorded purely so a replay can be recognised.
 *
 * <p>{@link #eventIdentity} is composed from the exchange's own fields (event type, transaction or
 * update id, order id, status) rather than a generated UUID. That distinction matters: a replayed
 * event is byte-for-byte the same message, so it yields the <em>same</em> natural identity and is
 * therefore detected as a duplicate, whereas a synthetic UUID would differ on every delivery and
 * never dedupe anything.
 *
 * <p>The unique constraint on
 * {@code (user_id, account_category, event_type, event_identity)} is what makes suppression
 * restart-safe and race-safe: a concurrent duplicate hits the database rather than a heap set.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
		name = "portfolio_exchange_events",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_portfolio_exchange_events_identity",
				columnNames = {"user_id", "account_category", "event_type", "event_identity"}))
public class PortfolioExchangeEventApplied extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(name = "account_category", nullable = false, length = 20)
	private AccountCategory accountCategory;

	@Enumerated(EnumType.STRING)
	@Column(name = "exchange", nullable = false, length = 20)
	private ExchangeName exchange;

	@Column(name = "event_type", nullable = false, length = 40)
	private String eventType;

	/** Exchange-provided event time. Null when the payload carried none. */
	@Column(name = "event_time")
	private Instant eventTime;

	/** Natural identity derived from exchange fields; see the class javadoc. */
	@Column(name = "event_identity", nullable = false, length = 160)
	private String eventIdentity;

	@Column(name = "applied_at", nullable = false)
	private Instant appliedAt;
}