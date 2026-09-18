package com.shyblack.cryptosignals.service.news;

import com.shyblack.cryptosignals.config.NewsProperties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic news ingestion. A non-overlapping guard ensures a slow feed run never
 * starts a second sync concurrently.
 */
@Component
public class NewsIngestionScheduler {

	private static final Logger log = LoggerFactory.getLogger(NewsIngestionScheduler.class);

	private final NewsProperties properties;
	private final NewsIngestionService ingestionService;
	private final AtomicBoolean running = new AtomicBoolean(false);

	public NewsIngestionScheduler(NewsProperties properties, NewsIngestionService ingestionService) {
		this.properties = properties;
		this.ingestionService = ingestionService;
	}

	@Scheduled(cron = "${app.news.sync-cron:0 */15 * * * *}")
	public void syncNews() {
		if (!properties.enabled()) {
			return;
		}
		if (!running.compareAndSet(false, true)) {
			log.info("[News] Scheduled sync skipped: previous run still in progress");
			return;
		}
		try {
			ingestionService.syncAll();
		} catch (Exception ex) {
			log.error("[News] Scheduled sync failed: {}", ex.getMessage(), ex);
		} finally {
			running.set(false);
		}
	}
}