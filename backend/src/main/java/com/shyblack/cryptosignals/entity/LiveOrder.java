package com.shyblack.cryptosignals.entity;

import com.shyblack.cryptosignals.entity.enums.LiveOrderPurpose;
import com.shyblack.cryptosignals.entity.enums.LiveOrderStatus;
import com.shyblack.cryptosignals.entity.enums.LiveOrderType;
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
 * A local shadow of an exchange order. Local state records our intent;
 * `executedQuantity`, `avgFillPrice`, `cumulativeQuoteQty` and `fees` reflect
 * the exchange execution reports we've observed so far.
 *
 * Idempotency: unique(account, clientOrderId). The clientOrderId is derived
 * deterministically from (userId, signalId, symbol, side, purpose) — so
 * duplicate signal fan-outs collapse into a single row.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
		name = "live_orders",
		uniqueConstraints = {
				@UniqueConstraint(
						name = "uk_live_orders_account_client",
						columnNames = {"account_id", "client_order_id"}
				)
		},
		indexes = {
				@Index(name = "ix_live_orders_account_status", columnList = "account_id,status"),
				@Index(name = "ix_live_orders_signal", columnList = "signal_id"),
				@Index(name = "ix_live_orders_exchange_order", columnList = "exchange_order_id"),
				@Index(name = "ix_live_orders_symbol_status", columnList = "symbol,status")
		}
)
public class LiveOrder extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "account_id", nullable = false)
	private LiveTradingAccount account;

	@Column(name = "signal_id")
	private UUID signalId;

	/** Optional back-reference for protective children (SL/TP). */
	@Column(name = "parent_order_id")
	private UUID parentOrderId;

	@Column(name = "client_order_id", nullable = false, length = 40)
	private String clientOrderId;

	@Column(name = "exchange_order_id", length = 80)
	private String exchangeOrderId;

	@Column(nullable = false)
	private String symbol;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PositionSide side;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private LiveOrderType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private LiveOrderPurpose purpose;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private LiveOrderStatus status = LiveOrderStatus.CREATED;

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

	@Column
	private Instant submittedAt;

	@Column
	private Instant lastFillAt;

	@Column
	private Instant completedAt;

	@Version
	@Column(nullable = false)
	private Long version = 0L;

	public BigDecimal remainingQuantity() {
		BigDecimal exec = executedQuantity == null ? BigDecimal.ZERO : executedQuantity;
		return requestedQuantity.subtract(exec).max(BigDecimal.ZERO);
	}
}
