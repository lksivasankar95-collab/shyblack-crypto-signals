package com.shyblack.cryptosignals.entity.research;

import com.shyblack.cryptosignals.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-window (WALK_FORWARD) or per-variant (SENSITIVITY) detail row for a
 * validation run. Windows/variants are never collapsed into the parent row so
 * each remains individually auditable. Descriptive only — no ranking.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
		name = "research_nfm_validation_detail",
		indexes = {
				@Index(name = "idx_nfm_validation_detail_run", columnList = "run_id, kind, ordinal")
		})
public class NfmValidationDetail extends BaseEntity {

	@Column(name = "run_id", nullable = false)
	private java.util.UUID runId;

	/** WINDOW | VARIANT */
	@Column(nullable = false, length = 20)
	private String kind;

	@Column(name = "ordinal", nullable = false)
	private int ordinal;

	@Column(length = 150)
	private String label;

	@Column(name = "variant_id", length = 150)
	private String variantId;

	@Column(name = "params_json", length = 2000)
	private String paramsJson;

	@Column(name = "train_start")
	private Instant trainStart;

	@Column(name = "train_end")
	private Instant trainEnd;

	@Column(name = "test_start")
	private Instant testStart;

	@Column(name = "test_end")
	private Instant testEnd;

	@Column(name = "configuration_hash", length = 128)
	private String configurationHash;

	@Column(name = "data_quality", length = 40)
	private String dataQuality;

	@Column(length = 40)
	private String status;

	private Integer trades;
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
	@Column(name = "max_drawdown_pct")
	private BigDecimal maxDrawdownPct;
	@Column(name = "return_pct")
	private BigDecimal returnPct;
}
