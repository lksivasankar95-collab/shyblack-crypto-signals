package com.shyblack.cryptosignals.entity;

import com.shyblack.cryptosignals.entity.enums.NewsCategory;
import com.shyblack.cryptosignals.entity.enums.NewsImpact;
import com.shyblack.cryptosignals.entity.enums.NewsProcessingStatus;
import com.shyblack.cryptosignals.entity.enums.NewsSentiment;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
		name = "news_articles",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_news_articles_canonical_hash", columnNames = "canonical_hash"),
				@UniqueConstraint(name = "uk_news_articles_source_url", columnNames = "source_url")
		},
		indexes = {
				@Index(name = "idx_news_articles_published_at", columnList = "published_at"),
				@Index(name = "idx_news_articles_category", columnList = "category"),
				@Index(name = "idx_news_articles_sentiment", columnList = "sentiment"),
				@Index(name = "idx_news_articles_impact", columnList = "impact_level"),
				@Index(name = "idx_news_articles_source", columnList = "source_name"),
				@Index(name = "idx_news_articles_news_score", columnList = "news_score")
		})
public class NewsArticle extends BaseEntity {

	/** Provider-specific article id when available (e.g. RSS guid). */
	@Column(length = 255)
	private String externalNewsId;

	@Column(nullable = false, length = 120)
	private String sourceName;

	@Column(nullable = false, length = 1000)
	private String sourceUrl;

	@Column(nullable = false, length = 512)
	private String title;

	@Column(length = 4000)
	private String summary;

	@Column(length = 20000)
	private String content;

	@Column(length = 1000)
	private String imageUrl;

	@Column(length = 255)
	private String author;

	/** Original publication time supplied by the provider, never touched by freshness math. */
	private Instant publishedAt;

	@Column(nullable = false)
	private Instant fetchedAt;

	@Column(nullable = false, length = 8)
	private String language = "EN";

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private NewsCategory category = NewsCategory.OTHER;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private NewsSentiment sentiment = NewsSentiment.NEUTRAL;

	/** Normalized sentiment in [-1.0, 1.0]; informational, never a trading signal. */
	@Column
	private Double sentimentScore;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private NewsImpact impactLevel = NewsImpact.LOW;

	@Column
	private Double impactScore;

	/** Normalized news score in [-10.0, 10.0]; combined signal input, not a trade trigger. */
	@Column
	private Double newsScore;

	@Column
	private Double confidenceScore;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private NewsProcessingStatus processingStatus = NewsProcessingStatus.NEW;

	/** SHA-256 of the normalized canonical identity (URL or title+source signal). */
	@Column(nullable = false, length = 64)
	private String canonicalHash;

	@OneToMany(mappedBy = "article", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<NewsAsset> assets = new ArrayList<>();

	public void addAsset(NewsAsset asset) {
		asset.setArticle(this);
		assets.add(asset);
	}
}