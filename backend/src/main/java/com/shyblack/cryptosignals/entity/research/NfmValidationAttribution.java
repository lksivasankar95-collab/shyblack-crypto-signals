package com.shyblack.cryptosignals.entity.research;

import com.shyblack.cryptosignals.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Immutable EVENT → SIGNAL → TRADE → OUTCOME attribution for one trade of one
 * validation run. Unavailable values are NULL (never zero). Backed by the real
 * engine-captured decision context; nothing is fabricated. Unique per
 * {@code (run_id, signal_id)} so re-persistence cannot duplicate history.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
		name = "research_nfm_validation_attribution",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_nfm_attr_run_signal", columnNames = {"run_id", "signal_id"})
		},
		indexes = {
				@Index(name = "idx_nfm_attr_run", columnList = "run_id"),
				@Index(name = "idx_nfm_attr_event_type", columnList = "event_type")
		})
public class NfmValidationAttribution extends BaseEntity {

	@Column(name = "run_id", nullable = false)
	private UUID runId;

	@Column(name = "signal_id", nullable = false)
	private UUID signalId;

	@Column(name = "event_ids", length = 2000)
	private String eventIds;

	@Column(name = "event_type", length = 40)
	private String eventType;
	@Column(name = "event_stage", length = 40)
	private String eventStage;
	@Column(name = "source_tier", length = 20)
	private String sourceTier;
	@Column(name = "event_timestamp")
	private Instant eventTimestamp;

	@Column(length = 20)
	private String symbol;

	@Column(name = "signal_direction", length = 10)
	private String signalDirection;
	@Column(name = "signal_score")
	private Integer signalScore;
	@Column(name = "signal_grade", length = 4)
	private String signalGrade;
	@Column(name = "signal_status", length = 20)
	private String signalStatus;

	private BigDecimal expected;
	private BigDecimal actual;
	private BigDecimal surprise;

	@Column(name = "price_reaction")
	private BigDecimal priceReaction;
	@Column(name = "volume_ratio")
	private BigDecimal volumeRatio;
	@Column(name = "oi_change")
	private BigDecimal oiChange;
	private BigDecimal funding;
	private BigDecimal liquidation;

	@Column(name = "market_regime", length = 20)
	private String marketRegime;

	@Column(name = "tradeability_state", length = 30)
	private String tradeabilityState;
	@Column(name = "reject_reason", length = 500)
	private String rejectReason;
	@Column(name = "event_age_seconds")
	private Long eventAgeSeconds;

	@Column(name = "entry_price")
	private BigDecimal entryPrice;
	@Column(name = "entry_time")
	private Instant entryTime;

	@Column(name = "stop_loss")
	private BigDecimal stopLoss;
	private BigDecimal tp1;
	private BigDecimal tp2;
	private BigDecimal tp3;

	@Column(name = "tp1_hit")
	private Boolean tp1Hit;
	@Column(name = "tp2_hit")
	private Boolean tp2Hit;
	@Column(name = "tp3_hit")
	private Boolean tp3Hit;
	@Column(name = "sl_hit")
	private Boolean slHit;

	@Column(name = "gross_pnl")
	private BigDecimal grossPnl;
	private BigDecimal fees;
	private BigDecimal slippage;
	@Column(name = "net_pnl")
	private BigDecimal netPnl;

	@Column(name = "trade_outcome", length = 12)
	private String tradeOutcome;
	@Column(name = "attribution_status", length = 30)
	private String attributionStatus;
}
