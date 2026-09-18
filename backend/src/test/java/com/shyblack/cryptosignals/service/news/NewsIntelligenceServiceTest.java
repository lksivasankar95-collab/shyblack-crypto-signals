package com.shyblack.cryptosignals.service.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shyblack.cryptosignals.config.NewsProperties;
import com.shyblack.cryptosignals.dto.news.NewsContextResponse;
import com.shyblack.cryptosignals.entity.NewsArticle;
import com.shyblack.cryptosignals.entity.enums.NewsCategory;
import com.shyblack.cryptosignals.entity.enums.NewsImpact;
import com.shyblack.cryptosignals.entity.enums.NewsProcessingStatus;
import com.shyblack.cryptosignals.entity.enums.NewsSentiment;
import com.shyblack.cryptosignals.exception.InvalidNewsFilterException;
import com.shyblack.cryptosignals.repository.NewsArticleRepository;
import com.shyblack.cryptosignals.repository.NewsAssetRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NewsIntelligenceServiceTest {

	private final NewsArticleRepository repository = mock(NewsArticleRepository.class);
	private final NewsAssetRepository assetRepository = mock(NewsAssetRepository.class);

	private final NewsIntelligenceService service = new NewsIntelligenceService(
			repository, assetRepository, new NewsResponseMapper(), properties());

	@Test
	void aggregatesWeightedNewsContextForAsset() {
		Instant now = Instant.now();
		NewsArticle positive = article("BTC", +7.0, 0.9, 0.8, NewsImpact.HIGH, now.minusSeconds(3600));
		NewsArticle negative = article("BTC", -5.0, -0.7, 0.6, NewsImpact.MEDIUM, now.minusSeconds(10L * 3600));
		when(repository.findProcessedForAssetSince(eq("BTC"), any(Instant.class),
				eq(NewsProcessingStatus.PROCESSED))).thenReturn(List.of(positive, negative));
		when(assetRepository.findByArticleIdIn(any())).thenReturn(List.of());

		NewsContextResponse context = service.context("btc", 48);

		assertThat(context.symbol()).isEqualTo("BTC");
		assertThat(context.articleCount()).isEqualTo(2);
		assertThat(context.sentiment()).isEqualTo(NewsSentiment.POSITIVE);
		assertThat(context.impactLevel()).isEqualTo(NewsImpact.HIGH);
		assertThat(context.newsScore()).isPositive();
		assertThat(context.sentimentScore()).isGreaterThan(0);
		assertThat(context.confidenceScore()).isBetween(0.0, 1.0);
		assertThat(context.latestHighImpactNews()).isNotNull();
		assertThat(context.latestHighImpactNews().title()).isEqualTo("BTC positive story");
		assertThat(context.calculatedAt()).isNotNull();
	}

	@Test
	void emptyWindowReturnsNeutralContext() {
		when(repository.findProcessedForAssetSince(eq("BTC"), any(Instant.class),
				eq(NewsProcessingStatus.PROCESSED))).thenReturn(List.of());

		NewsContextResponse context = service.context("BTC", 48);

		assertThat(context.articleCount()).isZero();
		assertThat(context.sentiment()).isEqualTo(NewsSentiment.NEUTRAL);
		assertThat(context.impactLevel()).isEqualTo(NewsImpact.LOW);
		assertThat(context.newsScore()).isZero();
		assertThat(context.latestHighImpactNews()).isNull();
	}

	@Test
	void rejectsInvalidSymbol() {
		try {
			service.context("B@D SYMBOL", 48);
			assertThat(true).as("should have thrown").isFalse();
		} catch (InvalidNewsFilterException ex) {
			assertThat(ex.getMessage()).contains("Invalid asset symbol");
		}
	}

	private static NewsArticle article(String symbol, double newsScore, double sentimentScore,
			double confidence, NewsImpact impact, Instant publishedAt) {
		NewsArticle article = new NewsArticle();
		article.setId(UUID.randomUUID());
		article.setSourceName("CoinDesk");
		article.setSourceUrl("https://coindesk.com/item");
		article.setTitle(symbol + " positive story");
		article.setSummary("A test summary.");
		article.setPublishedAt(publishedAt);
		article.setFetchedAt(publishedAt);
		article.setCategory(NewsCategory.MARKET);
		article.setSentiment(sentimentScore >= 0 ? NewsSentiment.POSITIVE : NewsSentiment.NEGATIVE);
		article.setSentimentScore(sentimentScore);
		article.setImpactLevel(impact);
		article.setImpactScore(NewsImpact.HIGH.equals(impact) ? 0.75 : 0.5);
		article.setNewsScore(newsScore);
		article.setConfidenceScore(confidence);
		article.setProcessingStatus(NewsProcessingStatus.PROCESSED);
		article.setCanonicalHash("hash-" + UUID.randomUUID());
		return article;
	}

	private static NewsProperties properties() {
		return new NewsProperties(true, "0 */15 * * * *", 10, 20, 60, 48, "", List.of("https://feed.example"));
	}
}