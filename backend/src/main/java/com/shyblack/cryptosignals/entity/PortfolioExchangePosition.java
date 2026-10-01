package com.shyblack.cryptosignals.entity;

import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.FuturesMarginMode;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
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
 * One futures position as last reported by the exchange position-risk endpoint.
 *
 * <p>This is the exchange's own view of an open position. It is intentionally independent of
 * {@code FuturesPosition}, which is the locally recorded shadow the execution engine creates from
 * our own orders, and it is never derived from local signals or local orders.
 *
 * <p>Only positions the exchange reports as open are stored: a flat position is a real closed state
 * and is removed from this table rather than stored with a zero amount. Every money field is
 * nullable so a value the exchange omitted stays unknown instead of becoming zero.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
		name = "portfolio_exchange_positions",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_portfolio_exchange_positions_scope",
				columnNames = {"user_id", "exchange", "symbol", "position_side"}))
public class PortfolioExchangePosition extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(name = "exchange", nullable = false, length = 20)
	private ExchangeName exchange;

	@Column(name = "symbol", nullable = false, length = 20)
	private String symbol;

	@Enumerated(EnumType.STRING)
	@Column(name = "position_side", nullable = false, length = 10)
	private PositionSide positionSide;

	/** Signed exchange amount: positive is LONG, negative is SHORT. */
	@Column(name = "position_amount", precision = 30, scale = 12)
	private BigDecimal positionAmount;

	@Column(name = "entry_price", precision = 30, scale = 12)
	private BigDecimal entryPrice;

	@Column(name = "mark_price", precision = 30, scale = 12)
	private BigDecimal markPrice;

	/** Null when the exchange reports 0, which it uses to mean "not applicable". */
	@Column(name = "liquidation_price", precision = 30, scale = 12)
	private BigDecimal liquidationPrice;

	@Column(name = "leverage")
	private Integer leverage;

	@Enumerated(EnumType.STRING)
	@Column(name = "margin_mode", length = 20)
	private FuturesMarginMode marginMode;

	@Column(name = "isolated_margin", precision = 30, scale = 12)
	private BigDecimal isolatedMargin;

	@Column(name = "notional", precision = 30, scale = 12)
	private BigDecimal notional;

	@Column(name = "unrealized_profit", precision = 30, scale = 12)
	private BigDecimal unrealizedProfit;

	@Column(name = "fetched_at", nullable = false)
	private Instant fetchedAt;

	@Version
	@Column(nullable = false)
	private Long version = 0L;

	/** Absolute size; the sign of {@link #positionAmount} carries the direction. */
	public BigDecimal quantity() {
		return positionAmount == null ? null : positionAmount.abs();
	}
}