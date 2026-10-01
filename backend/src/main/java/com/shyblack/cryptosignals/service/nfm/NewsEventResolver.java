package com.shyblack.cryptosignals.service.nfm;

import com.shyblack.cryptosignals.config.NfmProperties;
import com.shyblack.cryptosignals.entity.NewsArticle;
import com.shyblack.cryptosignals.entity.NewsAsset;
import com.shyblack.cryptosignals.entity.NewsEvent;
import com.shyblack.cryptosignals.entity.NewsEventAsset;
import com.shyblack.cryptosignals.entity.enums.NewsEventType;
import com.shyblack.cryptosignals.entity.enums.NewsImpact;
import com.shyblack.cryptosignals.entity.enums.NewsSourceTier;
import com.shyblack.cryptosignals.repository.NewsArticleRepository;
import com.shyblack.cryptosignals.repository.NewsEventRepository;
import com.shyblack.cryptosignals.service.news.NewsCreatedEvent;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns a persisted {@link NewsArticle} into a normalized {@link NewsEvent}
 * (spec §5–§8). Runs AFTER the article commits so event resolution can never
 * roll back ingestion. Idempotent per article.
 *
 * <p>Expected/actual values are only known for scheduled macro releases; RSS
 * events are stored with those fields null and are classified by type/tier.</p>
 */
@Service
public class NewsEventResolver {

	private static final Logger log = LoggerFactory.getLogger(NewsEventResolver.class);

	private final NewsArticleRepository articleRepository;
	private final NewsEventRepository eventRepository;
	private final NewsEventTypeClassifier typeClassifier;
	private final NfmProperties properties;

	public NewsEventResolver(NewsArticleRepository articleRepository, NewsEventRepository eventRepository,
			NewsEventTypeClassifier typeClassifier, NfmProperties properties) {
		this.articleRepository = articleRepository;
		this.eventRepository = eventRepository;
		this.typeClassifier = typeClassifier;
		this.properties = properties;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onNewsCreated(NewsCreatedEvent event) {
		if (!properties.enabled() || event == null) {
			return;
		}
		try {
			resolve(event.newsArticleId());
		} catch (Exception ex) {
			log.warn("[NFM] event resolution failed for article={} err={}", event.newsArticleId(), ex.getMessage());
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Optional<NewsEvent> resolve(UUID articleId) {
		if (articleId == null || eventRepository.existsByArticleId(articleId)) {
			return eventRepository.findByArticleId(articleId);
		}
		Optional<NewsArticle> maybe = articleRepository.findById(articleId);
		if (maybe.isEmpty()) {
			return Optional.empty();
		}
		NewsArticle article = maybe.get();
		NewsSourceTier tier = properties.tierFor(article.getSourceName());
		NewsEventType type = typeClassifier.classify(
				article.getCategory(), article.getTitle(), article.getSummary());
		NewsImpact impact = article.getImpactLevel() == null ? NewsImpact.LOW : article.getImpactLevel();

		NewsEvent event = new NewsEvent();
		event.setArticle(article);
		event.setEventTime(article.getPublishedAt() != null ? article.getPublishedAt() : article.getFetchedAt());
		event.setSource(article.getSourceName());
		event.setSourceTier(tier);
		event.setEventCategory(type.category());
		event.setEventType(type);
		event.setEventStage(typeClassifier.inferStage(article.getTitle(), article.getSummary()));
		event.setHeadline(article.getTitle());
		event.setSummary(article.getSummary());
		event.setEventImpact(impact);
		event.setEventConfidence(article.getConfidenceScore() == null
				? null : (int) Math.round(article.getConfidenceScore() * 100));
		event.setMarketInterpretation(article.getSentiment() == null ? null : article.getSentiment().name());
		// Tier 4 never trades; low-impact events are persisted as watch-only.
		event.setTradeable(tier.tradeable() && impact.ordinal() >= NewsImpact.MEDIUM.ordinal());

		for (NewsAsset asset : article.getAssets()) {
			NewsEventAsset eventAsset = new NewsEventAsset();
			eventAsset.setSymbol(asset.getSymbol());
			eventAsset.setName(asset.getName());
			eventAsset.setRelevanceScore(asset.getRelevanceScore());
			eventAsset.setRelevanceLevel(relevanceFor(asset, impact));
			event.addAsset(eventAsset);
		}

		NewsEvent saved = eventRepository.save(event);
		log.info("[NFM] event resolved id={} type={} tier={} stage={} tradeable={} article={}",
				saved.getId(), saved.getEventType(), saved.getSourceTier(), saved.getEventStage(),
				saved.isTradeable(), articleId);
		return Optional.of(saved);
	}

	private NewsImpact relevanceFor(NewsAsset asset, NewsImpact impact) {
		if (asset.getRelationshipType() == com.shyblack.cryptosignals.entity.enums.NewsAssetRelationshipType.PRIMARY) {
			return impact.ordinal() >= NewsImpact.HIGH.ordinal() ? impact : NewsImpact.HIGH;
		}
		return NewsImpact.MEDIUM;
	}
}
