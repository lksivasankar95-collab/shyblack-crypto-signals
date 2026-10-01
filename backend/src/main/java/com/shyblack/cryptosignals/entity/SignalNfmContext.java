package com.shyblack.cryptosignals.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * NFM-specific context attached 1:1 to a persisted {@link Signal} (spec §41).
 * Kept as a child entity so the shared {@link Signal} table stays lean and the
 * existing signal contracts are untouched.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
		name = "signal_nfm_context",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_signal_nfm_context_signal", columnNames = "signal_id")
		},
		indexes = {
				@Index(name = "idx_signal_nfm_context_signal", columnList = "signal_id"),
				@Index(name = "idx_signal_nfm_context_event", columnList = "news_event_id")
		})
public class SignalNfmContext extends BaseEntity {

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "signal_id", nullable = false)
	private Signal signal;

	@Column(name = "news_event_id")
	private UUID newsEventId;

	@Column(length = 48) private String eventType;
	@Column(length = 32) private String eventCategory;
	@Column(length = 32) private String eventStage;
	@Column(length = 16) private String sourceTier;
	@Column(length = 200) private String source;
	private Instant eventTime;

	@Column(precision = 24, scale = 8) private BigDecimal expectedValue;
	@Column(precision = 24, scale = 8) private BigDecimal actualValue;
	@Column(precision = 24, scale = 8) private BigDecimal surpriseValue;
	@Column(length = 24) private String surpriseDirection;

	@Column(precision = 12, scale = 6) private BigDecimal priceReactionPct;
	@Column(precision = 12, scale = 6) private BigDecimal volumeMultiplier;
	@Column(precision = 12, scale = 6) private BigDecimal openInterestChangePct;
	@Column(length = 24) private String fundingState;
	@Column(length = 24) private String liquidationState;

	private Integer eventConfluenceScore;
	@Column(length = 500) private String marketInterpretation;
	@Column(length = 48) private String configVersion;
}
