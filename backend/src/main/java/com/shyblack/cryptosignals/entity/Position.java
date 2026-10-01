package com.shyblack.cryptosignals.entity;

import com.shyblack.cryptosignals.entity.enums.CloseReason;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.PositionStatus;
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
 * A simulated trading position. This entity backs both the paper-trading engine
 * and any future live-trading integration. For paper positions, signalId ties
 * the row 1:1 to the Signal that produced it (idempotency).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
		name = "positions",
		uniqueConstraints = {
				@UniqueConstraint(
						name = "uk_positions_portfolio_signal",
						columnNames = {"portfolio_id", "signal_id"}
				)
		},
		indexes = {
				@Index(name = "ix_positions_status", columnList = "status"),
				@Index(name = "ix_positions_symbol_status", columnList = "symbol,status"),
				@Index(name = "ix_positions_portfolio_status", columnList = "portfolio_id,status")
		}
)
public class Position extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "portfolio_id", nullable = false)
	private Portfolio portfolio;

	/** Signal that produced this position; used as idempotency key. Nullable for manually-opened positions. */
	@Column(name = "signal_id")
	private UUID signalId;

	@Column(nullable = false)
	private String symbol;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PositionSide side;

	/** Base-asset quantity (units of the coin). */
	@Column(nullable = false, precision = 19, scale = 8)
	private BigDecimal size;

	/** Notional value at open in quote currency (size * entryPrice). */
	@Column(precision = 19, scale = 8)
	private BigDecimal notional;

	@Column(nullable = false, precision = 19, scale = 8)
	private BigDecimal entryPrice;

	@Column(precision = 19, scale = 8)
	private BigDecimal currentPrice;

	@Column(precision = 19, scale = 8)
	private BigDecimal exitPrice;

	@Column(precision = 19, scale = 8)
	private BigDecimal stopLoss;

	@Column(precision = 19, scale = 8)
	private BigDecimal takeProfit1;

	@Column(precision = 19, scale = 8)
	private BigDecimal takeProfit2;

	@Column(precision = 19, scale = 8)
	private BigDecimal takeProfit3;

	@Column(precision = 19, scale = 8)
	private BigDecimal margin;

	@Column(precision = 19, scale = 8)
	private BigDecimal liquidationPrice;

	@Column(precision = 19, scale = 8)
	private BigDecimal entryFee;

	@Column(precision = 19, scale = 8)
	private BigDecimal exitFee;

	@Column(precision = 19, scale = 8)
	private BigDecimal realizedPnl;

	@Column(precision = 19, scale = 8)
	private BigDecimal unrealizedPnl;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PositionStatus status = PositionStatus.OPEN;

	@Enumerated(EnumType.STRING)
	@Column
	private CloseReason closeReason;

	@Column
	private Instant openedAt;

	@Column
	private Instant closedAt;

	/** Strategy that produced the originating signal — preserved for trade history auditability. */
	@Column
	private UUID strategyId;

	@Column
	private Integer strategyVersion;

	// ── Partial-exit state (NFM). Nullable/defaulted so legacy single-TP rows
	//    (originalSize/remainingSize null) behave exactly as before. ──
	@Column(name = "original_size", precision = 19, scale = 8)
	private BigDecimal originalSize;

	@Column(name = "remaining_size", precision = 19, scale = 8)
	private BigDecimal remainingSize;

	@Column(name = "average_exit_price", precision = 19, scale = 8)
	private BigDecimal averageExitPrice;

	// Nullable columns: rows created before these columns existed hold SQL NULL.
	// They MUST be boxed (Boolean) so Hibernate can hydrate NULL — a primitive
	// boolean cannot hold SQL NULL and hydration fails. Read via the null-safe
	// isX() accessors below; NULL is treated as "not hit".
	@Column(name = "tp1_hit")
	private Boolean tp1Hit;

	@Column(name = "tp2_hit")
	private Boolean tp2Hit;

	@Column(name = "tp3_hit")
	private Boolean tp3Hit;

	public boolean isTp1Hit() {
		return Boolean.TRUE.equals(tp1Hit);
	}

	public boolean isTp2Hit() {
		return Boolean.TRUE.equals(tp2Hit);
	}

	public boolean isTp3Hit() {
		return Boolean.TRUE.equals(tp3Hit);
	}

	/** Legacy-safe original quantity (falls back to {@link #size}). */
	public BigDecimal originalQty() {
		return originalSize != null ? originalSize : size;
	}

	/** Legacy-safe remaining quantity (falls back to {@link #size}). */
	public BigDecimal remainingQty() {
		return remainingSize != null ? remainingSize : size;
	}

	@Version
	@Column(nullable = false)
	private Long version = 0L;
}
