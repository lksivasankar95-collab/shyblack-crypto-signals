package com.shyblack.cryptosignals.service.news;

import com.shyblack.cryptosignals.config.NewsProperties;
import com.shyblack.cryptosignals.dto.news.NewsContextResponse;
import com.shyblack.cryptosignals.dto.news.NewsResponse;
import com.shyblack.cryptosignals.entity.NewsArticle;
import com.shyblack.cryptosignals.entity.NewsAsset;
import com.shyblack.cryptosignals.entity.enums.NewsImpact;
import com.shyblack.cryptosignals.entity.enums.NewsProcessingStatus;
import com.shyblack.cryptosignals.entity.enums.NewsSentiment;
import com.shyblack.cryptosignals.exception.InvalidNewsFilterException;
import com.shyblack.cryptosignals.news.NewsFreshness;
import com.shyblack.cryptosignals.news.NewsScoringConstants;
import com.shyblack.cryptosignals.repository.NewsArticleRepository;
import com.shyblack.cryptosignals.repository.NewsAssetRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregated, asset-scoped news intelligence consumed as one input of the
 * Signal Generation module. Pure aggregation of stored, processed articles -
 * it never generates trading decisions by itself.
 */
@Service
public class NewsIntelligenceService {

	private final NewsArticleRepository repository;
	private final NewsAssetRepository assetRepository;
	private final NewsResponseMapper mapper;
	private final NewsProperties properties;

	public NewsIntelligenceService(
			NewsArticleRepository repository,
			NewsAssetRepository assetRepository,
			NewsResponseMapper mapper,
			NewsProperties properties
	) {
		this.repository = repository;
		this.assetRepository = assetRepository;
		this.mapper = mapper;
		this.properties = properties;
	}

	@Transactional(readOnly = true)
	public NewsContextResponse context(String symbol, Integer windowHours) {
		String normalized = normalizeSymbol(symbol);
		Instant now = Instant.now();
		int window = windowHours != null && windowHours > 0
				? windowHours
				: (properties.freshnessWindowHours() > 0 ? properties.freshnessWindowHours() : 48);
		Instant since = now.minus(Duration.ofHours(window));

		List<NewsArticle> articles = repository.findProcessedForAssetSince(
				normalized, since, NewsProcessingStatus.PROCESSED);

		if (articles.isEmpty()) {
			return new NewsContextResponse(normalized, 0.0, NewsSentiment.NEUTRAL, 0.0,
					NewsImpact.LOW, 0.0, 0, null, now);
		}

		double weightSum = 0.0;
		double newsScoreSum = 0.0;
		double sentimentScoreSum = 0.0;
		double confidenceSum = 0.0;
		for (NewsArticle article : articles) {
			double weight = NewsFreshness.weight(article.getPublishedAt(), now, window);
			double effectiveWeight = weight <= 0 ? 1.0 : weight;
			weightSum += effectiveWeight;
			newsScoreSum += nz(article.getNewsScore()) * effectiveWeight;
			sentimentScoreSum += nz(article.getSentimentScore()) * effectiveWeight;
			confidenceSum += nz(article.getConfidenceScore()) * effectiveWeight;
		}

		double avgNewsScore = round2(clamp(newsScoreSum / Math.max(1e-9, weightSum),
				-NewsScoringConstants.NEWS_SCORE_MAX, NewsScoringConstants.NEWS_SCORE_MAX));
		double avgSentiment = round2(clamp(sentimentScoreSum / Math.max(1e-9, weightSum), -1.0, 1.0));
		double avgConfidence = round2(Math.min(1.0, confidenceSum / Math.max(1e-9, weightSum)));

		NewsSentiment sentiment = avgSentiment >= NewsScoringConstants.SENTIMENT_NEUTRAL_BAND
				? NewsSentiment.POSITIVE
				: avgSentiment <= -NewsScoringConstants.SENTIMENT_NEUTRAL_BAND
				? NewsSentiment.NEGATIVE
				: NewsSentiment.NEUTRAL;

		NewsImpact maxImpact = articles.stream()
				.map(NewsArticle::getImpactLevel)
				.max(Comparator.comparingInt(NewsIntelligenceService::impactOrder))
				.orElse(NewsImpact.LOW);

		NewsArticle top = articles.stream()
				.max(Comparator
						.comparingDouble((NewsArticle a) -> Math.abs(nz(a.getNewsScore())))
						.thenComparing(NewsArticle::getPublishedAt, Comparator.nullsFirst(Comparator.reverseOrder())))
				.orElse(null);
		NewsResponse topResponse = top == null ? null : withAssets(top);

		return new NewsContextResponse(normalized, avgNewsScore, sentiment, avgSentiment,
				maxImpact, avgConfidence, articles.size(), topResponse, now);
	}

	private NewsResponse withAssets(NewsArticle article) {
		List<UUID> ids = List.of(article.getId());
		Map<UUID, List<NewsAsset>> grouped = mapper.groupByArticle(assetRepository.findByArticleIdIn(ids));
		return mapper.toResponse(article, grouped.getOrDefault(article.getId(), List.of()));
	}

	private static int impactOrder(NewsImpact impact) {
		return switch (impact) {
			case CRITICAL -> 3;
			case HIGH -> 2;
			case MEDIUM -> 1;
			case LOW -> 0;
		};
	}

	private static double nz(Double value) {
		return value == null ? 0.0 : value;
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double round2(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	private static String normalizeSymbol(String symbol) {
		if (symbol == null || symbol.isBlank()) {
			throw new InvalidNewsFilterException("Asset symbol is required");
		}
		String normalized = symbol.trim().toUpperCase(Locale.ROOT);
		if (!normalized.matches("[A-Z0-9]{1,20}")) {
			throw new InvalidNewsFilterException("Invalid asset symbol: " + symbol);
		}
		return normalized;
	}
}