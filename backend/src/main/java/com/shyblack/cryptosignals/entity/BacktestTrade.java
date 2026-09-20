package com.shyblack.cryptosignals.entity;

import com.shyblack.cryptosignals.entity.enums.BacktestExitReason;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "backtest_trades",
		indexes = {
				@Index(name = "ix_bt_trades_run", columnList = "run_id"),
				@Index(name = "ix_bt_trades_signal", columnList = "signal_id")
		})
public class BacktestTrade extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "run_id", nullable = false)
	private BacktestRun run;

	@Column(name = "signal_id")
	private UUID signalId;

	@Column(nullable = false)
	private String symbol;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PositionSide side;

	@Column(nullable = false, precision = 19, scale = 8) private BigDecimal quantity;
	@Column(nullable = false, precision = 19, scale = 8) private BigDecimal entryPrice;
	@Column(nullable = false, precision = 19, scale = 8) private BigDecimal exitPrice;
	@Column(nullable = false, precision = 19, scale = 8) private BigDecimal notional;
	@Column(precision = 19, scale = 8) private BigDecimal stopLoss;
	@Column(precision = 19, scale = 8) private BigDecimal takeProfit;
	@Column(nullable = false, precision = 19, scale = 8) private BigDecimal entryFee;
	@Column(nullable = false, precision = 19, scale = 8) private BigDecimal exitFee;
	@Column(nullable = false, precision = 19, scale = 8) private BigDecimal grossPnl;
	@Column(nullable = false, precision = 19, scale = 8) private BigDecimal netPnl;
	@Column(precision = 10, scale = 4) private BigDecimal rMultiple;
	@Column(nullable = false) private int leverage = 1;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private BacktestExitReason exitReason;

	@Column(nullable = false) private Instant entryTime;
	@Column(nullable = false) private Instant exitTime;
	@Column private long holdingSeconds;
}
