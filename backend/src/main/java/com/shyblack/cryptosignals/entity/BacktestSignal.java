package com.shyblack.cryptosignals.entity;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Signal emitted DURING the backtest at a specific historical candle. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "backtest_signals",
		indexes = @Index(name = "ix_bt_signals_run", columnList = "run_id"))
public class BacktestSignal extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "run_id", nullable = false)
	private BacktestRun run;

	@Column(nullable = false)
	private String symbol;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PositionSide side;

	@Column(nullable = false)
	private Instant candleTime;

	@Column(nullable = false, precision = 19, scale = 8) private BigDecimal referencePrice;
	@Column(precision = 19, scale = 8) private BigDecimal entryPrice;
	@Column(precision = 19, scale = 8) private BigDecimal stopLoss;
	@Column(precision = 19, scale = 8) private BigDecimal takeProfit;

	@Column(length = 100) private String strategyId;
	@Column(length = 60) private String strategyVersion;
	@Column(length = 500) private String notes;
}
