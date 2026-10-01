package com.shyblack.cryptosignals.entity;

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
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One asset balance as last reported by the exchange, stored by the Portfolio read model.
 *
 * <p>This is a read-model snapshot owned by {@code service.portfolio}; it is deliberately separate
 * from {@code LiveTradingAccount.cachedAvailableBalance}, which the trading engine narrows to a
 * single quote asset. It stores every asset the exchange reported so a caller can tell an explicit
 * zero apart from an asset the exchange did not mention.
 *
 * <p>{@code free} and {@code locked} are nullable because the exchange may report an unusable value.
 * A zero here is only ever a zero the exchange actually sent.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
		name = "portfolio_exchange_balances",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_portfolio_exchange_balances_scope",
				columnNames = {"user_id", "exchange", "asset"}))
public class PortfolioExchangeBalance extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(name = "exchange", nullable = false, length = 20)
	private ExchangeName exchange;

	@Column(name = "asset", nullable = false, length = 20)
	private String asset;

	@Column(name = "free_balance", precision = 30, scale = 12)
	private BigDecimal free;

	@Column(name = "locked_balance", precision = 30, scale = 12)
	private BigDecimal locked;

	@Column(name = "fetched_at", nullable = false)
	private Instant fetchedAt;

	@Version
	@Column(nullable = false)
	private Long version = 0L;

	/** Free + locked, or null when either component is unknown. */
	public BigDecimal total() {
		if (free == null || locked == null) {
			return null;
		}
		return free.add(locked);
	}
}