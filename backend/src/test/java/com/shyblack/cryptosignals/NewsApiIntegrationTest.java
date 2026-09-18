package com.shyblack.cryptosignals;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shyblack.cryptosignals.dto.news.NewsSyncResponse;
import com.shyblack.cryptosignals.entity.NewsArticle;
import com.shyblack.cryptosignals.entity.NewsAsset;
import com.shyblack.cryptosignals.entity.enums.NewsAssetRelationshipType;
import com.shyblack.cryptosignals.entity.enums.NewsCategory;
import com.shyblack.cryptosignals.entity.enums.NewsImpact;
import com.shyblack.cryptosignals.entity.enums.NewsProcessingStatus;
import com.shyblack.cryptosignals.entity.enums.NewsSentiment;
import com.shyblack.cryptosignals.repository.NewsArticleRepository;
import com.shyblack.cryptosignals.service.news.NewsIngestionService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NewsApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private NewsArticleRepository newsArticleRepository;

	@MockitoBean
	private NewsIngestionService newsIngestionService;

	private Instant now;

	@BeforeEach
	void seed() {
		newsArticleRepository.deleteAll();
		now = Instant.parse("2026-09-17T12:00:00Z");

		NewsArticle btcEtf = article("Bitcoin ETF approved, record inflow", "BTC",
				+8.0, 0.9, NewsImpact.HIGH, NewsCategory.ETF, now.minusSeconds(1800));
		NewsArticle ethHack = article("Ethereum bridge drained in exploit", "ETH",
				-7.5, -0.9, NewsImpact.HIGH, NewsCategory.EXPLOIT, now.minusSeconds(3600));
		NewsArticle btcMacro = article("Fed rate cut boosts risk assets", "BTC",
				+3.0, 0.3, NewsImpact.MEDIUM, NewsCategory.MACRO, now.minusSeconds(7200));
		newsArticleRepository.saveAll(List.of(btcEtf, ethHack, btcMacro));
	}

	@Test
	void listRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/news"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void listReturnsPagedArticlesForAuthenticatedUser() throws Exception {
		mockMvc.perform(get("/api/v1/news").with(user("trader").roles("USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.items[0].title").value("Bitcoin ETF approved, record inflow"))
				.andExpect(jsonPath("$.items[0].assets[0].symbol").value("BTC"));
	}

	@Test
	void searchFiltersByCategoryAndQuery() throws Exception {
		mockMvc.perform(get("/api/v1/news")
						.with(user("trader").roles("USER"))
						.param("category", "ETF"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.items[0].category").value("ETF"));

		mockMvc.perform(get("/api/v1/news")
						.with(user("trader").roles("USER"))
						.param("q", "rate cut"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.items[0].title").value("Fed rate cut boosts risk assets"));
	}

	@Test
	void invalidSortIsBadRequest() throws Exception {
		mockMvc.perform(get("/api/v1/news")
						.with(user("trader").roles("USER"))
						.param("sort", "bogus"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void detailReturnsArticleAndNotFoundForUnknown() throws Exception {
		NewsArticle seeded = newsArticleRepository.findAll().get(0);

		mockMvc.perform(get("/api/v1/news/" + seeded.getId()).with(user("trader").roles("USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(seeded.getId().toString()))
				.andExpect(jsonPath("$.processingStatus").value("PROCESSED"));

		mockMvc.perform(get("/api/v1/news/" + UUID.randomUUID()).with(user("trader").roles("USER")))
				.andExpect(status().isNotFound());
	}

	@Test
	void assetNewsReturnsOnlyMatchingSymbol() throws Exception {
		mockMvc.perform(get("/api/v1/news/asset/BTC").with(user("trader").roles("USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2));
	}

	@Test
	void assetContextAggregatesProcessedArticles() throws Exception {
		mockMvc.perform(get("/api/v1/news/asset/BTC/context")
						.with(user("trader").roles("USER"))
						.param("windowHours", "48"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.symbol").value("BTC"))
				.andExpect(jsonPath("$.articleCount").value(2))
				.andExpect(jsonPath("$.sentiment").value("POSITIVE"))
				.andExpect(jsonPath("$.latestHighImpactNews.title").value("Bitcoin ETF approved, record inflow"));
	}

	@Test
	void adminSyncForbiddenForUserAndAllowedForAdmin() throws Exception {
		when(newsIngestionService.syncAll()).thenReturn(List.of(
				new NewsSyncResponse("rss", 3, 2, 1, 0, 0, now, now.plusSeconds(2), 2000L)));

		mockMvc.perform(post("/api/v1/admin/news/sync").with(user("trader").roles("USER")))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/v1/admin/news/sync").with(user("admin").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].inserted").value(2))
				.andExpect(jsonPath("$[0].duplicates").value(1));
	}

	private static NewsArticle article(String title, String symbol, double newsScore,
			double sentimentScore, NewsImpact impact, NewsCategory category, Instant publishedAt) {
		NewsArticle article = new NewsArticle();
		article.setExternalNewsId("ext-" + UUID.randomUUID());
		article.setSourceName("CoinDesk");
		article.setSourceUrl("https://coindesk.com/" + UUID.randomUUID());
		article.setTitle(title);
		article.setSummary("Summary for " + title);
		article.setPublishedAt(publishedAt);
		article.setFetchedAt(publishedAt.plusSeconds(60));
		article.setCategory(category);
		article.setSentiment(sentimentScore > 0 ? NewsSentiment.POSITIVE : NewsSentiment.NEGATIVE);
		article.setSentimentScore(sentimentScore);
		article.setImpactLevel(impact);
		article.setImpactScore(NewsImpact.HIGH.equals(impact) ? 0.75 : 0.5);
		article.setNewsScore(newsScore);
		article.setConfidenceScore(0.8);
		article.setProcessingStatus(NewsProcessingStatus.PROCESSED);
		article.setCanonicalHash("hash-" + UUID.randomUUID());

		NewsAsset asset = new NewsAsset();
		asset.setSymbol(symbol);
		asset.setName(symbol);
		asset.setRelevanceScore(1.0);
		asset.setRelationshipType(NewsAssetRelationshipType.PRIMARY);
		article.addAsset(asset);
		return article;
	}
}