package com.shyblack.cryptosignals.news;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NewsNormalizerTest {

	@Test
	void canonicalUrlStripsTrackingAndFragmentAndNormalizes() {
		String normalized = NewsNormalizer.canonicalUrl(
				"HTTPS://www.CoinDesk.com/markets/BTC?utm_source=rss&ref=x&a=1#section");
		assertThat(normalized).isEqualTo("https://www.coindesk.com/markets/BTC?a=1");
	}

	@Test
	void canonicalUrlStripsTrailingSlashAndDefaultPort() {
		assertThat(NewsNormalizer.canonicalUrl("https://decrypt.co:443/123/")).isEqualTo("https://decrypt.co/123");
	}

	@Test
	void canonicalUrlRejectsNonHttpSchemesAndBlanks() {
		assertThat(NewsNormalizer.canonicalUrl("ftp://example.com/x")).isNull();
		assertThat(NewsNormalizer.canonicalUrl("javascript:alert(1)")).isNull();
		assertThat(NewsNormalizer.canonicalUrl("   ")).isNull();
		assertThat(NewsNormalizer.canonicalUrl(null)).isNull();
	}

	@Test
	void canonicalTitleLowercasesAndCollapses() {
		assertThat(NewsNormalizer.canonicalTitle("  Bitcoin   ETF   Approved!  "))
				.isEqualTo("bitcoin etf approved");
		assertThat(NewsNormalizer.canonicalTitle(null)).isNull();
	}

	@Test
	void canonicalHashIsStableAndDifferentForDifferentIdentity() {
		String url = "https://coindesk.com/a";
		String hashA1 = NewsNormalizer.canonicalHash(url, "bitcoin etf", "CoinDesk");
		String hashA2 = NewsNormalizer.canonicalHash(url, "bitcoin etf", "CoinDesk");
		String hashB = NewsNormalizer.canonicalHash("https://coindesk.com/b", "bitcoin etf", "CoinDesk");
		assertThat(hashA1).isEqualTo(hashA2);
		assertThat(hashA1).isNotEqualTo(hashB);
		assertThat(hashA1).hasSize(64);
	}

	@Test
	void cleanSummaryStripsTagsAndTruncates() {
		assertThat(NewsNormalizer.cleanSummary("<p>A short summary.</p>")).isEqualTo("A short summary.");
		assertThat(NewsNormalizer.cleanSummary(null)).isNull();
	}
}