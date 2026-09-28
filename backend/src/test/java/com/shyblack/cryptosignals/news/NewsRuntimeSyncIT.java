package com.shyblack.cryptosignals.news;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.dto.news.NewsResponse;
import com.shyblack.cryptosignals.dto.news.NewsSyncResponse;
import com.shyblack.cryptosignals.dto.news.PageResponse;
import com.shyblack.cryptosignals.news.provider.RawNewsArticle;
import com.shyblack.cryptosignals.news.provider.RssNewsProvider;
import com.shyblack.cryptosignals.repository.NewsArticleRepository;
import com.shyblack.cryptosignals.service.news.NewsIngestionService;
import com.shyblack.cryptosignals.service.news.NewsQueryService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * REAL-RUNTIME verification, disabled unless NEWS_RUNTIME=true so the normal
 * suite stays offline. Uses the production RSS provider/config against real
 * publisher feeds (no fake data) and the real ingestion + query services.
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "NEWS_RUNTIME", matches = "true")
class NewsRuntimeSyncIT {

	@Autowired
	private RssNewsProvider rssProvider;

	@Autowired
	private NewsIngestionService ingestionService;

	@Autowired
	private NewsQueryService queryService;

	@Autowired
	private NewsArticleRepository repository;

	@Test
	void syncsRealFeedsAndExposesLatestFirst() throws Exception {
		repository.deleteAll();

		assertThat(rssProvider.enabled()).as("RSS provider must be enabled").isTrue();

		// Per-feed evidence from the production provider.
		List<RawNewsArticle> fetched = rssProvider.fetchLatest();
		Map<String, List<RawNewsArticle>> bySource = fetched.stream()
				.collect(Collectors.groupingBy(RawNewsArticle::sourceName));
		StringBuilder sb = new StringBuilder("NEWS RUNTIME SYNC\n");
		sb.append("totalRawArticles=").append(fetched.size()).append('\n');
		bySource.entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.forEach(e -> {
					Instant newest = e.getValue().stream()
							.map(RawNewsArticle::publishedAt)
							.filter(java.util.Objects::nonNull)
							.max(Comparator.naturalOrder())
							.orElse(null);
					sb.append(String.format("feed=%-24s entries=%d newest=%s%n",
							e.getKey(), e.getValue().size(), newest));
				});

		// Persist through the real ingestion path.
		List<NewsSyncResponse> results = ingestionService.syncAll();
		for (NewsSyncResponse r : results) {
			sb.append(String.format(
					"provider=%s fetched=%d inserted=%d duplicates=%d rejected=%d failed=%d ms=%d%n",
					r.provider(), r.articlesFetched(), r.inserted(), r.duplicates(),
					r.rejected(), r.failed(), r.durationMs()));
		}

		long persisted = repository.count();
		sb.append("persistedTotal=").append(persisted).append('\n');
		assertThat(persisted).as("real feeds must persist at least one article").isGreaterThan(0);

		// Latest-first API contract.
		PageResponse<NewsResponse> page = queryService.search(
				null, null, null, null, null, null, null, 0, 10, null, null);
		assertThat(page.items()).isNotEmpty();
		Instant prev = null;
		for (NewsResponse item : page.items()) {
			if (prev != null && item.publishedAt() != null) {
				assertThat(item.publishedAt()).isBeforeOrEqualTo(prev);
			}
			if (item.publishedAt() != null) prev = item.publishedAt();
		}
		sb.append("apiTop10 (newest first):\n");
		page.items().forEach(i -> sb.append(String.format("  %s | %s | %s%n",
				i.publishedAt(), i.sourceName(), i.title())));

		Path out = Path.of("build", "news-runtime", "report.txt");
		Files.createDirectories(out.getParent());
		Files.writeString(out, sb.toString());
		System.out.println(sb);
	}
}
