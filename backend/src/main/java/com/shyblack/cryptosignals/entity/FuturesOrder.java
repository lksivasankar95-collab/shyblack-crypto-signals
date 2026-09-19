package com.shyblack.cryptosignals.entity;

import com.shyblack.cryptosignals.entity.enums.FuturesOrderPurpose;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderStatus;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderType;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A local shadow of a Binance USDT-M Futures order.
 *
 *   side          — BUY / SELL from the exchange perspective (which we
 *                   store in the existing PositionSide enum where LONG=BUY,
 *                   SHORT=SELL for readability).
 *   positionSide  — LONG or SHORT — describes which position this order
 *                   opens or reduces. Distinct from {@code side} so a
 *                   reduce-only SELL on a LONG still shows positionSide=LONG.
 *   reduceOnly    — the exchange must not open a reverse position.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
		name = "futures_orders",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_futures_orders_account_client",
				columnNames = {"account_id", "client_order_id"}),
		indexes = {
				@Index(name = "ix_futures_orders_account_status", columnList = "account_id,status"),
				@Index(name = "ix_futures_orders_symbol_status", columnList = "symbol,status"),
				@Index(name = "ix_futures_orders_signal", columnList = "signal_id")
		})
public class FuturesOrder extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "account_id", nullable = false)
	private FuturesTradingAccount account;

	@Column(name = "signal_id")
	private UUID signalId;

	/** For protective / close orders — points at the ENTRY order. */
	@Column(name = "parent_order_id")
	private UUID parentOrderId;

	@Column(name = "client_order_id", nullable = false, length = 40)
	private String clientOrderId;

	@Column(name = "exchange_order_id", length = 80)
	private String exchangeOrderId;

	@Column(nullable = false)
	private String symbol;

	/** Exchange side: LONG => BUY, SHORT => SELL. Kept in PositionSide for reuse. */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PositionSide side;

	/** Which position this order relates to — LONG or SHORT. */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PositionSide positionSide;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private FuturesOrderType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private FuturesOrderPurpose purpose;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private FuturesOrderStatus status = FuturesOrderStatus.CREATED;

	@Column(nullable = false)
	private boolean reduceOnly = false;

	@Column(nullable = false)
	private int leverage = 1;

	@Column(precision = 19, scale = 8, nullable = false)
	private BigDecimal requestedQuantity;

	@Column(precision = 19, scale = 8)
	private BigDecimal price;

	@Column(precision = 19, scale = 8)
	private BigDecimal stopPrice;

	@Column(precision = 19, scale = 8, nullable = false)
	private BigDecimal executedQuantity = BigDecimal.ZERO;

	@Column(precision = 19, scale = 8, nullable = false)
	private BigDecimal cumulativeQuoteQty = BigDecimal.ZERO;

	@Column(precision = 19, scale = 8)
	private BigDecimal avgFillPrice;

	@Column(precision = 19, scale = 8, nullable = false)
	private BigDecimal fees = BigDecimal.ZERO;

	@Column(length = 20)
	private String feeAsset;

	@Column(length = 500)
	private String rejectReason;

	@Column private Instant submittedAt;
	@Column private Instant lastFillAt;
	@Column private Instant completedAt;

	@Version
	@Column(nullable = false)
	private Long version = 0L;
}
