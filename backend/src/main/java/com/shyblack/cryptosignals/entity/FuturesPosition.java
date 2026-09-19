package com.shyblack.cryptosignals.entity;

import com.shyblack.cryptosignals.entity.enums.FuturesMarginMode;
import com.shyblack.cryptosignals.entity.enums.FuturesPositionStatus;
import com.shyblack.cryptosignals.entity.enums.FuturesProtectionStatus;
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
 * A live USDT-M FUTURES position — LONG or SHORT. One position per
 * (account, symbol, positionSide) at any time in ONE_WAY mode.
 *
 * Aggregates the actual executed state derived from Binance execution
 * reports (never from signal.entryPrice). Realized P&L, funding, and fees
 * are separate columns per Phase 24.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
		name = "futures_positions",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_futures_positions_open",
				// Enforced only when status=OPEN via app-level check.
				columnNames = {"account_id", "symbol", "position_side"}),
		indexes = {
				@Index(name = "ix_futures_positions_account_status", columnList = "account_id,status"),
				@Index(name = "ix_futures_positions_signal", columnList = "signal_id")
		})
public class FuturesPosition extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "account_id", nullable = false)
	private FuturesTradingAccount account;

	@Column(name = "signal_id")
	private UUID signalId;

	@Column(name = "entry_order_id")
	private UUID entryOrderId;

	@Column(name = "stop_order_id")
	private UUID stopOrderId;

	@Column(nullable = false)
	private String symbol;

	@Enumerated(EnumType.STRING)
	@Column(name = "position_side", nullable = false)
	private PositionSide positionSide;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private FuturesMarginMode marginMode = FuturesMarginMode.ISOLATED;

	@Column(nullable = false)
	private int leverage = 1;

	@Column(precision = 19, scale = 8, nullable = false)
	private BigDecimal quantity = BigDecimal.ZERO;

	@Column(precision = 19, scale = 8)
	private BigDecimal entryPrice;

	@Column(precision = 19, scale = 8)
	private BigDecimal exitPrice;

	@Column(precision = 19, scale = 8)
	private BigDecimal stopLoss;

	@Column(precision = 19, scale = 8)
	private BigDecimal takeProfit;

	@Column(precision = 19, scale = 8)
	private BigDecimal initialMargin;

	@Column(precision = 19, scale = 8)
	private BigDecimal liquidationPrice;

	@Column(precision = 19, scale = 8, nullable = false)
	private BigDecimal realizedPnl = BigDecimal.ZERO;

	@Column(precision = 19, scale = 8, nullable = false)
	private BigDecimal unrealizedPnl = BigDecimal.ZERO;

	@Column(precision = 19, scale = 8, nullable = false)
	private BigDecimal tradingFees = BigDecimal.ZERO;

	@Column(precision = 19, scale = 8, nullable = false)
	private BigDecimal fundingFees = BigDecimal.ZERO;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private FuturesPositionStatus status = FuturesPositionStatus.OPEN;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private FuturesProtectionStatus protectionStatus = FuturesProtectionStatus.PENDING;

	@Column private Instant openedAt;
	@Column private Instant closedAt;

	@Version
	@Column(nullable = false)
	private Long version = 0L;
}
