package com.shyblack.cryptosignals.entity;

import com.shyblack.cryptosignals.entity.enums.MarketRegime;
import com.shyblack.cryptosignals.entity.enums.NewsEventCategory;
import com.shyblack.cryptosignals.entity.enums.NewsEventStage;
import com.shyblack.cryptosignals.entity.enums.NewsEventType;
import com.shyblack.cryptosignals.entity.enums.NewsImpact;
import com.shyblack.cryptosignals.entity.enums.NewsSourceTier;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Normalized NFM event (spec §8). Created from a {@link NewsArticle} by
 * {@code NewsEventResolver}, or ingested directly for scheduled macro releases
 * where an expected/actual pair is known.
 *
 * <p>The event timestamp is authoritative. Anything computed at signal time is
 * derived downstream by the NFM engine using only data available at that time.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
		name = "news_events",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_news_events_article", columnNames = "article_id")
		},
		indexes = {
				@Index(name = "idx_news_events_event_time", columnList = "event_time"),
				@Index(name = "idx_news_events_type", columnList = "event_type"),
				@Index(name = "idx_news_events_tradeable", columnList = "tradeable")
		})
public class NewsEvent extends BaseEntity {

	/** Source article, if this event was derived from RSS. Null for manually ingested macro events. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "article_id")
	private NewsArticle article;

	/** Optional link to a parent event (e.g. ETF launch follows ETF approval). */
	@Column(name = "parent_event_id")
	private UUID parentEventId;

	/** Authoritative event time (provider publication time or scheduled release time). */
	@Column(name = "event_time", nullable = false)
	private Instant eventTime;

	@Column(length = 200)
	private String source;

	@Enumerated(EnumType.STRING)
	@Column(name = "source_tier", length = 16)
	private NewsSourceTier sourceTier = NewsSourceTier.TIER_4;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_category", length = 32)
	private NewsEventCategory eventCategory = NewsEventCategory.OTHER;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", length = 48)
	private NewsEventType eventType = NewsEventType.OTHER;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_stage", length = 32)
	private NewsEventStage eventStage = NewsEventStage.REPORT;

	@Column(length = 512)
	private String headline;

	@Column(length = 2000)
	private String summary;

	// ── Expected vs actual (macro releases) ─────────────────────────────────
	@Column(precision = 24, scale = 8)
	private BigDecimal expectedValue;

	@Column(precision = 24, scale = 8)
	private BigDecimal actualValue;

	@Column(precision = 24, scale = 8)
	private BigDecimal surpriseValue;

	@Column(precision = 24, scale = 8)
	private BigDecimal initialValue;

	@Column(precision = 24, scale = 8)
	private BigDecimal revisedValue;

	private Instant releaseTimestamp;
	private Instant revisionTimestamp;

	@Column(length = 24)
	private String surpriseDirection;

	@Column(length = 500)
	private String marketInterpretation;

	// ── Pre-event pricing (§10) ─────────────────────────────────────────────
	@Column(precision = 12, scale = 6) private BigDecimal preEventReturn1h;
	@Column(precision = 12, scale = 6) private BigDecimal preEventReturn4h;
	@Column(precision = 12, scale = 6) private BigDecimal preEventReturn24h;
	@Column(precision = 12, scale = 6) private BigDecimal preEventReturn3d;
	@Column(precision = 12, scale = 6) private BigDecimal preEventReturn7d;
	@Column(precision = 12, scale = 6) private BigDecimal preEventMomentum;
	@Column(precision = 12, scale = 6) private BigDecimal preEventVolatility;
	@Column(precision = 12, scale = 6) private BigDecimal preEventExtension;
	@Column(precision = 12, scale = 6) private BigDecimal pricedInRisk;

	// ── Post-event reaction (§11) ───────────────────────────────────────────
	@Column(precision = 12, scale = 6) private BigDecimal postEventReturn1m;
	@Column(precision = 12, scale = 6) private BigDecimal postEventReturn5m;
	@Column(precision = 12, scale = 6) private BigDecimal postEventReturn15m;
	@Column(precision = 12, scale = 6) private BigDecimal postEventReturn30m;
	@Column(precision = 12, scale = 6) private BigDecimal postEventReturn1h;
	@Column(precision = 12, scale = 6) private BigDecimal postEventReturn4h;
	@Column(precision = 12, scale = 6) private BigDecimal postEventReturn24h;

	@Column(precision = 12, scale = 6) private BigDecimal volumeChange;
	@Column(precision = 12, scale = 6) private BigDecimal openInterestChange;
	@Column(precision = 12, scale = 6) private BigDecimal fundingChange;
	@Column(precision = 12, scale = 6) private BigDecimal liquidationChange;

	@Enumerated(EnumType.STRING)
	@Column(length = 16)
	private MarketRegime marketRegime;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_impact", length = 16)
	private NewsImpact eventImpact = NewsImpact.LOW;

	private Integer eventConfidence;

	/**
	 * Tradeable events may feed NFM signals. Tier-4 / low-confidence / watch-only
	 * events are persisted with {@code tradeable=false} and never trade.
	 */
	@Column(nullable = false)
	private boolean tradeable = false;

	@OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<NewsEventAsset> assets = new ArrayList<>();

	public void addAsset(NewsEventAsset asset) {
		asset.setEvent(this);
		assets.add(asset);
	}
}
