package com.shyblack.cryptosignals.entity;

import com.shyblack.cryptosignals.entity.enums.BacktestExecutionModel;
import com.shyblack.cryptosignals.entity.enums.BacktestSameCandlePolicy;
import com.shyblack.cryptosignals.entity.enums.BacktestStatus;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single backtest run. The full configuration is snapshotted as JSON so the
 * result stays reproducible even if strategy defaults change later.
 *
 * Fields group into: identity, configuration snapshot, engine identity,
 * live progress counters, and final metrics.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "backtest_runs",
		indexes = {
				@Index(name = "ix_backtest_runs_user", columnList = "user_id"),
				@Index(name = "ix_backtest_runs_status", columnList = "status")
		})
public class BacktestRun extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private BacktestStatus status = BacktestStatus.QUEUED;

	@Column(nullable = false, length = 100)
	private String strategyId;

	@Column(nullable = false, length = 60)
	private String strategyVersion;

	@Column(nullable = false, length = 60)
	private String engineVersion;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TradingMode tradingMode;

	@Column(nullable = false)
	private String symbol;

	@Column(nullable = false, length = 20)
	private String timeframe;

	@Column(nullable = false)
	private Instant startDate;

	@Column(nullable = false)
	private Instant endDate;

	@Column(nullable = false, precision = 19, scale = 8)
	private BigDecimal initialCapital;

	@Column(nullable = false, precision = 7, scale = 4)
	private BigDecimal riskPerTradePct;

	@Column(nullable = false, precision = 7, scale = 4)
	private BigDecimal feePct;

	@Column(nullable = false, precision = 7, scale = 4)
	private BigDecimal slippagePct;

	@Column(nullable = false)
	private int leverage = 1;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private BacktestExecutionModel executionModel = BacktestExecutionModel.NEXT_CANDLE_OPEN;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private BacktestSameCandlePolicy sameCandlePolicy = BacktestSameCandlePolicy.SL_FIRST;

	/** SHA-256 of the canonical configuration string. Same config → same hash → reproducible run. */
	@Column(nullable = false, length = 64)
	private String configurationHash;

	/** Complete JSON snapshot of the request so future replays are unambiguous. */
	@Lob
	@Column(nullable = false)
	private String configurationJson;

	// ── Progress ────────────────────────────────────────────────
	@Column(nullable = false)
	private int processedCandles = 0;

	@Column(nullable = false)
	private int totalCandles = 0;

	@Column(length = 500)
	private String failureReason;

	@Column
	private Instant startedAt;

	@Column
	private Instant completedAt;

	// ── Final metrics (null until COMPLETED) ────────────────────
	@Column(precision = 19, scale = 8) private BigDecimal finalEquity;
	@Column(precision = 19, scale = 8) private BigDecimal totalNetPnl;
	@Column(precision = 7, scale = 4)  private BigDecimal totalReturnPct;
	@Column(precision = 19, scale = 8) private BigDecimal maxDrawdown;
	@Column(precision = 7, scale = 4)  private BigDecimal maxDrawdownPct;
	@Column(precision = 7, scale = 4)  private BigDecimal winRatePct;
	@Column(precision = 10, scale = 4) private BigDecimal profitFactor;
	@Column(precision = 10, scale = 4) private BigDecimal sharpeRatio;
	@Column(precision = 10, scale = 4) private BigDecimal sortinoRatio;
	@Column(precision = 19, scale = 8) private BigDecimal totalFees;
	@Column(precision = 19, scale = 8) private BigDecimal grossProfit;
	@Column(precision = 19, scale = 8) private BigDecimal grossLoss;
	@Column private int totalTrades;
	@Column private int winningTrades;
	@Column private int losingTrades;
	@Column private int liquidations;
	@Column(precision = 19, scale = 8) private BigDecimal averageWin;
	@Column(precision = 19, scale = 8) private BigDecimal averageLoss;
	@Column(precision = 19, scale = 8) private BigDecimal largestWin;
	@Column(precision = 19, scale = 8) private BigDecimal largestLoss;
	@Column(precision = 10, scale = 4) private BigDecimal expectancy;

	@Version
	@Column(nullable = false)
	private Long version = 0L;
}
