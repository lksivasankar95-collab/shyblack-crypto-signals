package com.shyblack.cryptosignals.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-candle equity snapshot. Enables equity curve + drawdown rendering
 * directly from the DB without recalculating.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "backtest_equity_points",
		indexes = @Index(name = "ix_bt_equity_run_time", columnList = "run_id,time"))
public class BacktestEquityPoint extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "run_id", nullable = false)
	private BacktestRun run;

	@Column(nullable = false)
	private Instant time;

	@Column(nullable = false, precision = 19, scale = 8) private BigDecimal equity;
	@Column(nullable = false, precision = 19, scale = 8) private BigDecimal availableBalance;
	@Column(nullable = false, precision = 19, scale = 8) private BigDecimal unrealizedPnl;
	@Column(nullable = false, precision = 19, scale = 8) private BigDecimal realizedPnl;
	@Column(nullable = false, precision = 19, scale = 8) private BigDecimal peakEquity;
	@Column(nullable = false, precision = 19, scale = 8) private BigDecimal drawdown;
	@Column(precision = 7, scale = 4) private BigDecimal drawdownPct;
}
