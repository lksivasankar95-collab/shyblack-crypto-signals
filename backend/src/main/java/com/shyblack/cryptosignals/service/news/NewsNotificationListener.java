package com.shyblack.cryptosignals.service.news;

import com.google.gson.JsonObject;
import com.shyblack.cryptosignals.config.NewsNotificationProperties;
import com.shyblack.cryptosignals.entity.DeviceToken;
import com.shyblack.cryptosignals.entity.NewsArticle;
import com.shyblack.cryptosignals.entity.Notification;
import com.shyblack.cryptosignals.entity.enums.NewsImpact;
import com.shyblack.cryptosignals.entity.enums.NotificationCategory;
import com.shyblack.cryptosignals.market.AlertsWebSocketHandler;
import com.shyblack.cryptosignals.repository.DeviceTokenRepository;
import com.shyblack.cryptosignals.repository.NewsArticleRepository;
import com.shyblack.cryptosignals.repository.NotificationRepository;
import com.shyblack.cryptosignals.service.FcmSenderService;
import com.shyblack.cryptosignals.service.NotificationPreferenceService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Post-commit fan-out for a newly persisted news article.
 *
 * Runs strictly AFTER the article transaction commits, so a failing FCM send or
 * WebSocket broadcast can never roll back the article. Each subscriber (device
 * token) is isolated; one failure does not affect the others.
 */
@Service
@RequiredArgsConstructor
public class NewsNotificationListener {

	private static final Logger log = LoggerFactory.getLogger(NewsNotificationListener.class);

	private final NewsArticleRepository newsArticleRepository;
	private final DeviceTokenRepository deviceTokenRepository;
	private final NotificationRepository notificationRepository;
	private final NotificationPreferenceService preferenceService;
	private final FcmSenderService fcmSenderService;
	private final AlertsWebSocketHandler websocketHandler;
	private final NewsNotificationProperties properties;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onNewsCreated(NewsCreatedEvent event) {
		try {
			newsArticleRepository.findById(event.newsArticleId()).ifPresent(this::process);
		} catch (Exception ex) {
			log.warn("[NEWS_NOTIFY] post-commit processing failed for article {}: {}",
					event.newsArticleId(), ex.getMessage());
		}
	}

	void process(NewsArticle article) {
		UUID newsId = article.getId();

		// Real-time leg fires for every new article, independent of notification policy.
		broadcast(article);

		if (!properties.enabled()) {
			log.debug("[NEWS_NOTIFY] disabled — article {} persisted without notification", newsId);
			return;
		}
		if (!isEligible(article)) {
			log.info("[NEWS_NOTIFY] skip newsId={} source={} impact={} score={} (below policy)",
					newsId, article.getSourceName(), article.getImpactLevel(), article.getNewsScore());
			return;
		}

		String title = "📰 " + safe(article.getSourceName());
		String body = safe(article.getTitle());
		JsonObject data = new JsonObject();
		data.addProperty("type", "news");
		data.addProperty("newsId", newsId.toString());
		data.addProperty("source", safe(article.getSourceName()));
		if (article.getPublishedAt() != null) {
			data.addProperty("publishedAt", article.getPublishedAt().toString());
		}
		if (article.getSourceUrl() != null) {
			data.addProperty("url", article.getSourceUrl());
		}

		List<DeviceToken> tokens = deviceTokenRepository.findByActiveTrue();
		int sent = 0;
		int skipped = 0;
		int failed = 0;
		for (DeviceToken dt : tokens) {
			try {
				if (!preferenceService.newsEnabledFor(dt.getUser())) {
					skipped++;
					continue;
				}
				if (notificationRepository.existsByUserAndNewsId(dt.getUser(), newsId)) {
					skipped++;
					continue;
				}
				boolean ok = fcmSenderService.sendToToken(dt.getToken(), title, body, data);
				if (ok) {
					sent++;
					saveHistory(dt, title, body, newsId);
				} else {
					failed++;
				}
			} catch (Exception ex) {
				failed++;
				log.warn("[NEWS_NOTIFY] token id={} newsId={} failed: {}",
						dt.getId(), newsId, ex.getMessage());
			}
		}
		log.info("[NEWS_NOTIFY] newsId={} source={} eligible=true tokens={} sent={} skipped={} failed={}",
				newsId, article.getSourceName(), tokens.size(), sent, skipped, failed);
	}

	private void saveHistory(DeviceToken dt, String title, String body, UUID newsId) {
		try {
			Notification n = new Notification();
			n.setUser(dt.getUser());
			n.setCategory(NotificationCategory.NEWS);
			n.setTitle(title);
			n.setBody(body);
			n.setRead(false);
			n.setNewsId(newsId);
			notificationRepository.save(n);
		} catch (Exception ex) {
			// unique(user_id, news_id) race or transient DB issue — never fatal
			log.debug("[NEWS_NOTIFY] history save skipped for newsId={}: {}", newsId, ex.getMessage());
		}
	}

	/** Deterministic eligibility: importance at/above the floor, or |score| at/above the score floor. */
	boolean isEligible(NewsArticle article) {
		NewsImpact impact = article.getImpactLevel();
		NewsImpact minImpact = properties.minImpact();
		boolean impactOk = impact != null && minImpact != null
				&& impact.ordinal() >= minImpact.ordinal();
		double score = article.getNewsScore() == null ? 0.0 : Math.abs(article.getNewsScore());
		boolean scoreOk = properties.minScore() > 0 && score >= properties.minScore();
		return impactOk || scoreOk;
	}

	private void broadcast(NewsArticle article) {
		try {
			JsonObject payload = new JsonObject();
			payload.addProperty("type", "news");
			payload.addProperty("newsId", article.getId().toString());
			payload.addProperty("title", safe(article.getTitle()));
			payload.addProperty("source", safe(article.getSourceName()));
			if (article.getPublishedAt() != null) {
				payload.addProperty("publishedAt", article.getPublishedAt().toString());
			}
			if (article.getSourceUrl() != null) {
				payload.addProperty("url", article.getSourceUrl());
			}
			websocketHandler.broadcastAlert(payload);
			log.info("[NEWS_WS] broadcast newsId={} source={}", article.getId(), article.getSourceName());
		} catch (Exception ex) {
			log.warn("[NEWS_WS] broadcast failed for newsId={}: {}", article.getId(), ex.getMessage());
		}
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}
}
