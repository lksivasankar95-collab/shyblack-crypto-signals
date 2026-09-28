package com.shyblack.cryptosignals.news;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.config.NewsProperties;
import com.shyblack.cryptosignals.news.provider.NewsProviderRegistry;
import com.shyblack.cryptosignals.news.provider.RssNewsProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Regression guard for the root cause of "latest news not syncing": the
 * application must ship with a usable feed configuration, otherwise the RSS
 * provider has zero clients and the scheduler has nothing to do.
 */
@SpringBootTest
@ActiveProfiles("test")
class NewsConfigPresenceTest {

	@Autowired
	private NewsProperties newsProperties;

	@Autowired
	private RssNewsProvider rssNewsProvider;

	@Autowired
	private NewsProviderRegistry registry;

	@Test
	void defaultConfigurationDefinesFeeds() {
		assertThat(newsProperties.hasFeeds())
				.as("app.news.feeds must be configured by default")
				.isTrue();
		assertThat(newsProperties.activeFeeds()).hasSizeGreaterThanOrEqualTo(5);
	}

	@Test
	void rssProviderIsEnabledWhenFeedsExist() {
		assertThat(rssNewsProvider.enabled()).isTrue();
		assertThat(registry.activeProviders()).isNotEmpty();
	}
}
