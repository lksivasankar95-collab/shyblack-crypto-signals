package com.shyblack.cryptosignals.service.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shyblack.cryptosignals.config.NewsProperties;
import com.shyblack.cryptosignals.dto.news.NewsSyncResponse;
import com.shyblack.cryptosignals.entity.NewsArticle;
import com.shyblack.cryptosignals.market.MarketBook;
import com.shyblack.cryptosignals.news.AssetExtractor;
import com.shyblack.cryptosignals.news.CategoryClassifier;
import com.shyblack.cryptosignals.news.DuplicateDetector;
import com.shyblack.cryptosignals.news.ImpactClassifier;
import com.shyblack.cryptosignals.news.NewsScorer;
import com.shyblack.cryptosignals.news.SentimentAnalyzer;
import com.shyblack.cryptosignals.news.provider.NewsProvider;
import com.shyblack.cryptosignals.news.provider.NewsProviderRegistry;
import com.shyblack.cryptosignals.news.provider.RawNewsArticle;
import com.shyblack.cryptosignals.repository.NewsArticleRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class NewsIngestionServiceTest {

	private NewsArticleRepository repository;
	private NewsIngestionService service;
	private NewsProvider provider;

	@BeforeEach
	void setUp() {
		repository = mock(NewsArticleRepository.class);
		when(repository.save(any(NewsArticle.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(repository.existsByCanonicalHash(anyString())).thenReturn(false);
		when(repository.existsByExternalNewsIdAndSourceName(anyString(), anyString())).thenReturn(false);
		when(repository.existsBySourceUrl(anyString()))
				.thenAnswer(invocation -> invocation.getArgument(0).equals("https://coindesk.com/watch"));

		NewsProperties properties = new NewsProperties(
				true, "0 */15 * * * *", 10, 20, 60, 48, "", List.of("https://feed.example"));
		provider = mock(NewsProvider.class);
		when(provider.providerName()).thenReturn("rss");
		when(provider.enabled()).thenReturn(true);
		NewsProviderRegistry registry = new NewsProviderRegistry(List.of(provider));

		service = new NewsIngestionService(
				registry,
				properties,
				new DuplicateDetector(repository),
				new AssetExtractor(new MarketBook()),
				new CategoryClassifier(),
				new SentimentAnalyzer(),
				new ImpactClassifier(),
				new NewsScorer(),
				repository,
				stubTransactionManager());
	}

	@Test
	void insertsClassifiesAndDedupes() throws Exception {
		RawNewsArticle fresh = new RawNewsArticle(
				"ext-1", "https://coindesk.com/btc-etf", "Bitcoin ETF approved, record inflows",
				"Institutional investors keep buying.", null, null, "Jane Doe", "CoinDesk",
				Instant.parse("2026-09-17T09:00:00Z"), "EN", List.of("Markets"));
		RawNewsArticle duplicate = new RawNewsArticle(
				"ext-2", "https://coindesk.com/watch", "Duplicate item", "Same story again.",
				null, null, null, "CoinDesk", Instant.parse("2026-09-17T08:00:00Z"), "EN", List.of());
		RawNewsArticle noUrl = new RawNewsArticle(
				"ext-3", "ftp://nonsense.example/x", "No hostname", "Nothing here.",
				null, null, null, "CoinDesk", Instant.parse("2026-09-17T07:00:00Z"), "EN", List.of());

		when(provider.fetchLatest()).thenReturn(List.of(fresh, duplicate, noUrl));

		List<NewsSyncResponse> responses = service.syncAll();

		assertThat(responses).hasSize(1);
		NewsSyncResponse response = responses.get(0);
		assertThat(response.articlesFetched()).isEqualTo(3);
		assertThat(response.inserted()).isEqualTo(1);
		assertThat(response.duplicates()).isEqualTo(1);
		assertThat(response.rejected()).isEqualTo(1);
		assertThat(response.failed()).isEqualTo(0);

		verify(repository, times(1)).save(any(NewsArticle.class));
	}

	@Test
	void providerFailureReportsFailedSync() throws Exception {
		when(provider.fetchLatest()).thenThrow(new RuntimeException("upstream down"));

		List<NewsSyncResponse> responses = service.syncAll();

		assertThat(responses).hasSize(1);
		assertThat(responses.get(0).failed()).isEqualTo(1);
		assertThat(responses.get(0).inserted()).isEqualTo(0);
		verify(repository, times(0)).save(any(NewsArticle.class));
	}

	private static PlatformTransactionManager stubTransactionManager() {
		return new PlatformTransactionManager() {
			@Override
			public org.springframework.transaction.TransactionStatus getTransaction(org.springframework.transaction.TransactionDefinition definition) {
				return new SimpleTransactionStatus();
			}

			@Override
			public void commit(org.springframework.transaction.TransactionStatus status) {
			}

			@Override
			public void rollback(org.springframework.transaction.TransactionStatus status) {
			}
		};
	}
}