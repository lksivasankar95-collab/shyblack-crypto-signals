package com.shyblack.cryptosignals.entity.research;

import com.shyblack.cryptosignals.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Immutable, auditable record of one NFM historical validation run. Unavailable
 * metrics are stored as NULL (UNKNOWN), never zero. There is intentionally no
 * winner/ranking column. Once a run id exists it is never silently overwritten
 * (enforced by both a unique constraint and the persistence service).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
		name = "research_nfm_validation_run",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_nfm_validation_run_id", columnNames = {"run_id"})
		},
		indexes = {
				@Index(name = "idx_nfm_validation_strategy", columnList = "strategy_id, strategy_version"),
				@Index(name = "idx_nfm_validation_run_type", columnList = "run_type")
		})
public class NfmValidationRun extends BaseEntity {

	@Column(name = "run_id", nullable = false)
	private java.util.UUID runId;

	@Column(name = "strategy_id", nullable = false, length = 80)
	private String strategyId;

	@Column(name = "strategy_version", nullable = false, length = 80)
	private String strategyVersion;

	@Column(name = "run_type", nullable = false, length = 30)
	private String runType;

	@Column(name = "configuration_hash", nullable = false, length = 128)
	private String configurationHash;

	@Column(name = "configuration_json", length = 4000)
	private String configurationJson;

	@Column(name = "market_dataset_version", length = 150)
	private String marketDatasetVersion;

	@Column(name = "event_dataset_version", length = 150)
	private String eventDatasetVersion;

	@Column(name = "derivatives_dataset_version", length = 150)
	private String derivativesDatasetVersion;

	@Column(length = 500)
	private String symbols;

	@Column(length = 20)
	private String timeframe;

	@Column(name = "start_time")
	private Instant startTime;

	@Column(name = "end_time")
	private Instant endTime;

	@Column(name = "validation_status", nullable = false, length = 40)
	private String validationStatus;

	@Column(name = "data_quality_status", length = 40)
	private String dataQualityStatus;

	@Column(name = "execution_status", nullable = false, length = 40)
	private String executionStatus;

	@Column(name = "event_coverage_status", length = 40)
	private String eventCoverageStatus;

	@Column(name = "trade_count")
	private Integer tradeCount;
	private Integer wins;
	private Integer losses;
	private BigDecimal netPnl;
	private BigDecimal grossProfit;
	private BigDecimal grossLoss;
	private BigDecimal fees;
	private BigDecimal slippage;
	private BigDecimal winRate;
	private BigDecimal expectancy;
	private BigDecimal profitFactor;
	@Column(name = "max_drawdown")
	private BigDecimal maxDrawdown;
	@Column(name = "max_drawdown_pct")
	private BigDecimal maxDrawdownPct;
	@Column(name = "return_pct")
	private BigDecimal returnPct;

	@Column(length = 2000)
	private String notes;
}
