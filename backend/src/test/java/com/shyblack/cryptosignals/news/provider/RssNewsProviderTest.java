package com.shyblack.cryptosignals.news.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RssNewsProviderTest {

	@Test
	void parsesRssTwoFeedIntoRawArticles() {
		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<rss version="2.0">
				  <channel>
				    <title>CoinDesk</title>
				    <item>
				      <title>Bitcoin ETF approvals drive record inflow</title>
				      <link>https://www.coindesk.com/markets/btc-etf?utm_source=rss&amp;utm_medium=feed</link>
				      <description><![CDATA[<p>Institutional investors keep buying.</p>]]></description>
				      <guid isPermaLink="false">guid-001</guid>
				      <pubDate>Thu, 17 Sep 2026 09:00:00 GMT</pubDate>
				      <category>Markets</category>
				    </item>
				    <item>
				      <title>Ethereum upgrade ships on mainnet</title>
				      <link>https://www.coindesk.com/tech/eth-upgrade</link>
				      <pubDate>Thu, 17 Sep 2026 08:00:00 +0000</pubDate>
				    </item>
				  </channel>
				</rss>
				""";

		List<RawNewsArticle> articles = RssNewsProvider.parse(xml, "https://feed.example/rss");

		assertThat(articles).hasSize(2);
		RawNewsArticle first = articles.get(0);
		assertThat(first.title()).isEqualTo("Bitcoin ETF approvals drive record inflow");
		assertThat(first.url()).isEqualTo("https://www.coindesk.com/markets/btc-etf?utm_source=rss&utm_medium=feed");
		assertThat(first.description()).isEqualTo("Institutional investors keep buying.");
		assertThat(first.externalId()).isEqualTo("guid-001");
		assertThat(first.sourceName()).isEqualTo("CoinDesk");
		assertThat(first.publishedAt()).isEqualTo(Instant.parse("2026-09-17T09:00:00Z"));
		assertThat(first.categories()).containsExactly("Markets");
		assertThat(articles.get(1).description()).isNull();
	}

	@Test
	void parsesAtomFeed() {
		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<feed xmlns="http://www.w3.org/2005/Atom">
				  <title>Decrypt</title>
				  <entry>
				    <id>urn:decrypt:123</id>
				    <title>Solana outage resolved after exploit attempt</title>
				    <link href="https://decrypt.co/123" rel="alternate"/>
				    <summary type="html">&lt;p&gt;Validators patched the software.&lt;/p&gt;</summary>
				    <author><name>Jim Reporter</name></author>
				    <published>2026-09-17T07:30:00Z</published>
				  </entry>
				</feed>
				""";

		List<RawNewsArticle> articles = RssNewsProvider.parse(xml, "https://feed.example/atom");

		assertThat(articles).hasSize(1);
		RawNewsArticle article = articles.get(0);
		assertThat(article.externalId()).isEqualTo("urn:decrypt:123");
		assertThat(article.title()).isEqualTo("Solana outage resolved after exploit attempt");
		assertThat(article.url()).isEqualTo("https://decrypt.co/123");
		assertThat(article.author()).isEqualTo("Jim Reporter");
		assertThat(article.sourceName()).isEqualTo("Decrypt");
		assertThat(article.publishedAt()).isEqualTo(Instant.parse("2026-09-17T07:30:00Z"));
	}

	@Test
	void rejectsUnsupportedRoot() {
		List<RawNewsArticle> articles = RssNewsProvider.parse(
				"<html><body>plain</body></html>", "https://feed.example/page");
		assertThat(articles).isEmpty();
	}

	@Test
	void stripHtmlRemovesTagsButKeepsText() {
		assertThat(RssNewsProvider.stripHtml("<p>Hello <b>world</b> &amp; beyond</p>")).isEqualTo("Hello world & beyond");
		assertThat(RssNewsProvider.stripHtml("<p>one<br/>two</p>")).contains("two");
		assertThat(RssNewsProvider.stripHtml("   ")).isNull();
	}

	@Test
	void parseDateSamples() {
		assertThat(RssNewsProvider.parseDateOrNull("Thu, 17 Sep 2026 09:00:00 GMT"))
				.isEqualTo(Instant.parse("2026-09-17T09:00:00Z"));
		assertThat(RssNewsProvider.parseDateOrNull("2026-09-17T07:30:00Z"))
				.isEqualTo(Instant.parse("2026-09-17T07:30:00Z"));
		assertThat(RssNewsProvider.parseDateOrNull("not a date")).isNull();
		assertThat(RssNewsProvider.parseDateOrNull(null)).isNull();
	}
}