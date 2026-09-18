package com.shyblack.cryptosignals.entity;

import com.shyblack.cryptosignals.entity.enums.NewsAssetRelationshipType;
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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
		name = "news_article_assets",
		indexes = {
				@Index(name = "idx_news_asset_symbol", columnList = "symbol"),
				@Index(name = "idx_news_asset_article", columnList = "news_article_id")
		})
public class NewsAsset extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "news_article_id", nullable = false)
	private NewsArticle article;

	/** Base coin symbol, e.g. BTC, ETH. Mirrors the app's canonical Binance base asset naming. */
	@Column(nullable = false, length = 20)
	private String symbol;

	@Column(length = 120)
	private String name;

	@Column
	private Double relevanceScore;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private NewsAssetRelationshipType relationshipType = NewsAssetRelationshipType.MENTIONED;
}