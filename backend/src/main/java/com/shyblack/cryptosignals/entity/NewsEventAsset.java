package com.shyblack.cryptosignals.entity;

import com.shyblack.cryptosignals.entity.enums.NewsImpact;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Asset relevance for an NFM event (spec §9). Relevance is persisted per event
 * so the engine can gate symbols without hard-coding assumptions.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
		name = "news_event_assets",
		indexes = {
				@Index(name = "idx_news_event_asset_symbol", columnList = "symbol"),
				@Index(name = "idx_news_event_asset_event", columnList = "news_event_id")
		})
public class NewsEventAsset extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "news_event_id", nullable = false)
	private NewsEvent event;

	/** Base coin symbol, e.g. BTC. */
	@Column(nullable = false, length = 20)
	private String symbol;

	@Column(length = 120)
	private String name;

	/** Relevance level reused from the news impact enum (LOW..CRITICAL). */
	@Enumerated(EnumType.STRING)
	@Column(name = "relevance_level", length = 16)
	private NewsImpact relevanceLevel = NewsImpact.LOW;

	@Column
	private Double relevanceScore;
}
