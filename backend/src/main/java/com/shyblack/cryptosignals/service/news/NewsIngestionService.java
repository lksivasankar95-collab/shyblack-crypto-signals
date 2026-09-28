package com.shyblack.cryptosignals.service.news;

import com.shyblack.cryptosignals.config.NewsProperties;
import com.shyblack.cryptosignals.dto.news.NewsSyncResponse;
import com.shyblack.cryptosignals.entity.NewsArticle;
import com.shyblack.cryptosignals.entity.NewsAsset;
import com.shyblack.cryptosignals.entity.enums.NewsProcessingStatus;
import com.shyblack.cryptosignals.news.AssetExtractor;
import com.shyblack.cryptosignals.news.CategoryClassifier;
import com.shyblack.cryptosignals.news.DuplicateDetector;
import com.shyblack.cryptosignals.news.ImpactClassifier;
import com.shyblack.cryptosignals.news.NewsFreshness;
import com.shyblack.cryptosignals.news.NewsNormalizer;
import com.shyblack.cryptosignals.news.NewsScorer;
import com.shyblack.cryptosignals.news.NewsScoringConstants;
import com.shyblack.cryptosignals.news.SentimentAnalyzer;
import com.shyblack.cryptosignals.news.TextSanitizer;
import com.shyblack.cryptosignals.news.provider.NewsProvider;
import com.shyblack.cryptosignals.news.provider.NewsProviderRegistry;
import com.shyblack.cryptosignals.news.provider.RawNewsArticle;
import com.shyblack.cryptosignals.repository.NewsArticleRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pulls articles from all active providers, normalizes, deduplicates, classifies,
 * scores, and persists them. Each article is committed in its own transaction so a
 * single malformed item can never roll back the rest of the sync.
 */
@Service
public class NewsIngestionService {

	private static final Logger log = LoggerFactory.getLogger(NewsIngestionService.class);

	private static final int MAX_ASSETS_PER_ARTICLE = 8;
	private static final int MAX_TITLE = 512;
	private static final int MAX_SOURCE_NAME = 120;
	private static final int MAX_EXTERNAL_ID = 255;
	private static final int MAX_AUTHOR = 255;
	private static final int MAX_IMAGE_URL = 1000;
	private static final int MAX_CONTENT = 20000;

	private final NewsProviderRegistry registry;
	private final NewsProperties properties;
	private final DuplicateDetector duplicateDetector;
	private final AssetExtractor assetExtractor;
	private final CategoryClassifier categoryClassifier;
	private final SentimentAnalyzer sentimentAnalyzer;
	private final ImpactClassifier impactClassifier;
	private final NewsScorer newsScorer;
	private final NewsArticleRepository repository;
	private final TransactionTemplate transactionTemplate;
	private final ApplicationEventPublisher eventPublisher;

	public NewsIngestionService(
			NewsProviderRegistry registry,
			NewsProperties properties,
			DuplicateDetector duplicateDetector,
			AssetExtractor assetExtractor,
			CategoryClassifier categoryClassifier,
			SentimentAnalyzer sentimentAnalyzer,
			ImpactClassifier impactClassifier,
			NewsScorer newsScorer,
			NewsArticleRepository repository,
			PlatformTransactionManager transactionManager,
			ApplicationEventPublisher eventPublisher
	) {
		this.registry = registry;
		this.properties = properties;
		this.duplicateDetector = duplicateDetector;
		this.assetExtractor = assetExtractor;
		this.categoryClassifier = categoryClassifier;
		this.sentimentAnalyzer = sentimentAnalyzer;
		this.impactClassifier = impactClassifier;
		this.newsScorer = newsScorer;
		this.repository = repository;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.eventPublisher = eventPublisher;
	}

	public List<NewsSyncResponse> syncAll() {
		return registry.activeProviders().stream().map(this::sync).toList();
	}

	public NewsSyncResponse sync(NewsProvider provider) {
		Instant startedAt = Instant.now();
		int fetched = 0;
		int inserted = 0;
		int duplicates = 0;
		int rejected = 0;
		int failed = 0;

		List<RawNewsArticle> rawArticles;
		try {
			rawArticles = provider.fetchLatest();
		} catch (Exception ex) {
			log.error("[News] Provider {} failed: {}", provider.providerName(), ex.getMessage());
			failed = 1;
			NewsSyncResponse empty = new NewsSyncResponse(provider.providerName(), 0, 0, 0, 0, 1,
					startedAt, Instant.now(), java.time.Duration.between(startedAt, Instant.now()).toMillis());
			return empty;
		}
		if (rawArticles == null) {
			rawArticles = List.of();
		}
		// The provider returns articles in feed order, not by recency. When the
		// per-sync cap is exceeded, keep the NEWEST so a verbose early feed can
		// never push newer articles from later feeds out of the sync.
		List<RawNewsArticle> ordered = new ArrayList<>(rawArticles);
		ordered.sort(Comparator.comparing(
				RawNewsArticle::publishedAt, Comparator.nullsLast(Comparator.reverseOrder())));
		int limit = properties.maxArticlesPerSync();
		fetched = ordered.size();
		if (limit > 0 && ordered.size() > limit) {
			ordered = ordered.subList(0, limit);
			fetched = ordered.size();
		}
		rawArticles = ordered;

		Instant now = Instant.now();
		for (RawNewsArticle raw : rawArticles) {
			try {
				switch (persistOne(raw, now)) {
					case INSERTED -> inserted++;
					case DUPLICATE -> duplicates++;
					case REJECTED -> rejected++;
				}
			} catch (Exception ex) {
				failed++;
				log.error("[News] Unexpected error persisting article '{}' from {}: {}",
						raw.title(), raw.sourceName(), ex.getMessage(), ex);
			}
		}

		Instant finishedAt = Instant.now();
		log.info("[News] {} -> fetched={} inserted={} duplicates={} rejected={} failed={} in {} ms",
				provider.providerName(), fetched, inserted, duplicates, rejected, failed,
				java.time.Duration.between(startedAt, finishedAt).toMillis());
		return new NewsSyncResponse(
				provider.providerName(), fetched, inserted, duplicates, rejected, failed,
				startedAt, finishedAt, java.time.Duration.between(startedAt, finishedAt).toMillis());
	}

	private enum Outcome {
		INSERTED,
		DUPLICATE,
		REJECTED
	}

	private Outcome persistOne(RawNewsArticle raw, Instant now) {
		String canonicalUrl = NewsNormalizer.canonicalUrl(raw.url());
		if (canonicalUrl == null) {
			log.debug("[News] Rejected article without a usable URL: {}", raw.title());
			return Outcome.REJECTED;
		}
		String title = TextSanitizer.collapseWhitespace(raw.title());
		if (title == null) {
			log.debug("[News] Rejected article without a title: {}", canonicalUrl);
			return Outcome.REJECTED;
		}
		title = TextSanitizer.truncate(title, MAX_TITLE);

		String canonicalTitle = NewsNormalizer.canonicalTitle(title);
		String canonicalHash = NewsNormalizer.canonicalHash(canonicalUrl, canonicalTitle, raw.sourceName());

		DuplicateDetector.DedupResult dedup = duplicateDetector.check(raw, canonicalUrl, canonicalHash);
		if (dedup.duplicated()) {
			return Outcome.DUPLICATE;
		}

		String summary = NewsNormalizer.cleanSummary(raw.description());
		String analysisText = summary != null ? summary : "";
		var assets = assetExtractor.extract(title, analysisText);

		var sentiment = sentimentAnalyzer.analyze(title, summary);
		var category = categoryClassifier.classify(joinCategories(raw.categories()), title, summary);
		var impact = impactClassifier.classify(category, sentiment.score(), assets.size(), title);
		double freshness = NewsFreshness.weight(raw.publishedAt(), now, properties.freshnessWindowHours());
		var scoring = newsScorer.score(sentiment.score(), sentiment.confidence(), impact, freshness);

		NewsArticle article = new NewsArticle();
		article.setExternalNewsId(TextSanitizer.truncate(raw.externalId(), MAX_EXTERNAL_ID));
		article.setSourceName(TextSanitizer.truncate(raw.sourceName(), MAX_SOURCE_NAME));
		article.setSourceUrl(canonicalUrl);
		article.setTitle(title);
		article.setSummary(summary);
		article.setContent(TextSanitizer.truncate(raw.content(), MAX_CONTENT));
		article.setImageUrl(TextSanitizer.truncate(raw.imageUrl(), MAX_IMAGE_URL));
		article.setAuthor(TextSanitizer.truncate(raw.author(), MAX_AUTHOR));
		article.setPublishedAt(raw.publishedAt());
		article.setFetchedAt(now);
		article.setCategory(category);
		article.setSentiment(sentiment.sentiment());
		article.setSentimentScore(sentiment.score());
		article.setImpactLevel(impact);
		article.setImpactScore(NewsScoringConstants.impactWeight(impact));
		article.setNewsScore(scoring.newsScore());
		article.setConfidenceScore(sentiment.confidence());
		article.setProcessingStatus(assets.isEmpty()
				? NewsProcessingStatus.PARTIALLY_PROCESSED
				: NewsProcessingStatus.PROCESSED);
		article.setCanonicalHash(canonicalHash);

		assets.stream()
				.limit(MAX_ASSETS_PER_ARTICLE)
				.map(extracted -> toAsset(extracted, article))
				.forEach(article::addAsset);

		try {
			transactionTemplate.executeWithoutResult(ts -> {
				repository.save(article);
				// Published INSIDE the tx; @TransactionalEventListener(AFTER_COMMIT)
				// consumers run only after the article is durably committed.
				eventPublisher.publishEvent(new NewsCreatedEvent(article.getId()));
			});
			return Outcome.INSERTED;
		} catch (DataIntegrityViolationException ex) {
			log.debug("[News] Concurrent duplicate for {} ({})", canonicalUrl, dedup.reason());
			return Outcome.DUPLICATE;
		}
	}

	private static NewsAsset toAsset(AssetExtractor.ExtractedAsset extracted, NewsArticle article) {
		NewsAsset asset = new NewsAsset();
		asset.setSymbol(extracted.symbol());
		asset.setName(TextSanitizer.truncate(extracted.name(), 120));
		asset.setRelevanceScore(extracted.relevanceScore());
		asset.setRelationshipType(extracted.type());
		return asset;
	}

	private static String joinCategories(List<String> categories) {
		if (categories == null || categories.isEmpty()) {
			return null;
		}
		return String.join(",", categories);
	}
}