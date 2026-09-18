package com.shyblack.cryptosignals.news.provider;

import com.shyblack.cryptosignals.config.NewsProperties;
import com.shyblack.cryptosignals.exception.NewsProviderException;
import com.shyblack.cryptosignals.news.TextSanitizer;
import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Keyless RSS/Atom news provider.
 * <p>Fetches configured feeds, parses RSS 2.0 and Atom into {@link RawNewsArticle}s,
 * strips HTML from descriptions, and never trusts provider metadata. Each feed is
 * isolated so a single failing feed never aborts the whole sync. Transient failures
 * (429, 5xx, timeouts, network) are retried a bounded number of times.</p>
 */
@Component
public class RssNewsProvider implements NewsProvider {

	private static final Logger log = LoggerFactory.getLogger(RssNewsProvider.class);
	private static final int MAX_RETRIES = 2;
	private static final long RETRY_BACKOFF_MS = 1500;

	private final NewsProperties properties;
	private final List<FeedClient> feedClients;

	public RssNewsProvider(NewsProperties properties) {
		this.properties = properties;
		this.feedClients = properties.activeFeeds().stream()
				.map(feed -> new FeedClient(feed, createRestClient(properties.requestTimeoutSeconds())))
				.toList();
	}

	@Override
	public String providerName() {
		return "rss";
	}

	@Override
	public String displayName() {
		return "RSS/Atom feeds";
	}

	@Override
	public boolean enabled() {
		return properties.hasFeeds();
	}

	@Override
	public List<RawNewsArticle> fetchLatest() {
		if (feedClients.isEmpty()) {
			log.warn("[News] No RSS feeds configured; RSS provider is disabled");
			return List.of();
		}
		List<RawNewsArticle> articles = new ArrayList<>();
		int failures = 0;
		for (FeedClient feed : feedClients) {
			try {
				List<RawNewsArticle> feedArticles = feed.fetchWithRetry();
				articles.addAll(feedArticles);
				log.info("[News] Fetched {} articles from feed {}", feedArticles.size(), feed.url());
			} catch (NewsProviderException ex) {
				failures++;
				log.error("[News] Feed fetch failed for {}: {}", feed.url(), ex.getMessage());
			} catch (Exception ex) {
				failures++;
				log.error("[News] Unexpected error fetching feed {}: {}", feed.url(), ex.getMessage(), ex);
			}
		}
		if (articles.isEmpty() && failures > 0 && failures == feedClients.size()) {
			throw new NewsProviderException("All RSS feeds failed (" + failures + " of " + feedClients.size() + ")");
		}
		return articles;
	}

	private RestClient createRestClient(long timeoutSeconds) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
		factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
		return RestClient.builder()
				.requestFactory(factory)
				.build();
	}

	/**
	 * Extracts a title text node. Falls back to first child whose name contains the
	 * node name (handles namespace prefixes).
	 */
	private static String firstElementText(Element parent, String localName) {
		NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node node = children.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				String nodeName = node.getNodeName();
				if (nodeName.equals(localName)
						|| nodeName.toLowerCase(Locale.ROOT).endsWith(":" + localName.toLowerCase(Locale.ROOT))) {
					return textOf((Element) node);
				}
			}
		}
		return null;
	}

	private static String textOf(Element element) {
		return element.getTextContent() == null ? null : element.getTextContent().trim();
	}

	/**
	 * Finds the first child element whose local name matches, including namespaced ones.
	 */
	private static Element firstElement(Element parent, String localName) {
		NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node node = children.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				Element element = (Element) node;
				String nodeName = element.getNodeName();
				if (nodeName.equals(localName)
						|| nodeName.toLowerCase(Locale.ROOT).endsWith(":" + localName.toLowerCase(Locale.ROOT))) {
					return element;
				}
			}
		}
		return null;
	}

	private static String attr(Element element, String name) {
		if (element == null) {
			return null;
		}
		String raw = element.getAttribute(name);
		return raw.isBlank() ? null : raw.trim();
	}

	private static String textOrNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static final class FeedClient {
		private final String url;
		private final RestClient rest;

		FeedClient(String url, RestClient rest) {
			this.url = url;
			this.rest = rest;
		}

		String url() {
			return url;
		}

		List<RawNewsArticle> fetchWithRetry() {
			int attempt = 0;
			while (true) {
				try {
					return fetchOnce();
				} catch (NewsProviderException ex) {
					attempt++;
					if (attempt > MAX_RETRIES || !isTransient(ex)) {
						throw ex;
					}
					log.warn("[News] Retrying feed {} (attempt {}/{}) after: {}", url, attempt, MAX_RETRIES, ex.getMessage());
					sleep(RETRY_BACKOFF_MS * attempt);
				}
			}
		}

		boolean isTransient(NewsProviderException ex) {
			return ex.getCause() instanceof java.net.SocketException
					|| ex.getCause() instanceof java.net.ConnectException
					|| ex.getCause() instanceof java.net.UnknownHostException
					|| ex.getMessage() != null && (ex.getMessage().contains("read timed out")
							|| ex.getMessage().contains("429")
							|| ex.getMessage().contains("5"));
		}

		List<RawNewsArticle> fetchOnce() {
			String body;
			try {
				body = rest.get()
						.uri(url)
						.accept(MediaType.APPLICATION_XML, MediaType.TEXT_XML, MediaType.TEXT_HTML)
						.retrieve()
						.body(String.class);
			} catch (RestClientResponseException ex) {
				throw new NewsProviderException("HTTP " + ex.getStatusCode().value() + " from feed " + url);
			} catch (RestClientException ex) {
				throw new NewsProviderException("Network error fetching feed " + url + ": " + ex.getMessage(), ex);
			}
			if (body == null || body.isBlank()) {
				throw new NewsProviderException("Empty response from feed " + url);
			}
			List<RawNewsArticle> parsed = parse(body, url);
			log.debug("[News] Parsed {} articles from {}", parsed.size(), url);
			return parsed;
		}

		static void sleep(long millis) {
			try {
				Thread.sleep(millis);
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
			}
		}
	}

	/**
	 * Package-private for direct unit testing with fixture XML.
	 */
	static List<RawNewsArticle> parse(String xml, String feedUrl) {
		Document document = parseXml(xml);
		Element root = document.getDocumentElement();
		String rootName = root.getNodeName();
		List<RawNewsArticle> articles = new ArrayList<>();
		if (rootName.equals("rss") || rootName.endsWith(":rss")) {
			collectRss(root, feedUrl, articles);
		} else if (rootName.equals("feed") || rootName.endsWith(":feed")) {
			collectAtom(root, feedUrl, articles);
		} else {
			log.warn("[News] Unsupported feed root element '{}' at {}", rootName, feedUrl);
		}
		return articles;
	}

	private static void collectRss(Element root, String feedUrl, List<RawNewsArticle> out) {
		NodeList channelNodes = root.getChildNodes();
		for (int i = 0; i < channelNodes.getLength(); i++) {
			Node node = channelNodes.item(i);
			if (node.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			Element channel = (Element) node;
			if (!channel.getNodeName().equals("channel")) {
				continue;
			}
			String channelTitle = firstElementText(channel, "title");
			NodeList items = channel.getElementsByTagName("item");
			for (int j = 0; j < items.getLength(); j++) {
				Element item = (Element) items.item(j);
				String title = firstElementText(item, "title");
				String link = firstElementText(item, "link");
				if (title == null || link == null) {
					continue;
				}
				String description = firstElementText(item, "description");
				String content = contentOf(item);
				String image = imageOf(item);
				String author = firstElementText(item, "author");
				String guid = firstElementText(item, "guid");
				if (author == null) {
					author = firstElementText(item, "creator");
				}
				Instant publishedAt = parseDateOrNull(firstElementText(item, "pubDate"));
				List<String> categories = categoryTags(item);
				out.add(new RawNewsArticle(
						guid, link, title, stripHtml(description), content, image, author,
						textOrNull(channelTitle) != null ? channelTitle : feedUrl, publishedAt, "EN", categories));
			}
		}
	}

	private static void collectAtom(Element root, String feedUrl, List<RawNewsArticle> out) {
		String feedTitle = textOrNull(firstElementText(root, "title"));
		NodeList entries = root.getElementsByTagName("entry");
		for (int i = 0; i < entries.getLength(); i++) {
			Element entry = (Element) entries.item(i);
			String title = textOrNull(firstElementText(entry, "title"));
			if (title == null) {
				continue;
			}
			String link = atomLink(entry);
			String id = textOrNull(firstElementText(entry, "id"));
			String summary = textOrNull(firstElementText(entry, "summary"));
			String content = textOrNull(firstElementText(entry, "content"));
			String description = summary != null ? summary : content;
			String author = atomAuthor(entry);
			String image = atomImage(entry);
			Instant publishedAt = parseDateOrNull(firstElementText(entry, "published"));
			if (publishedAt == null) {
				publishedAt = parseDateOrNull(firstElementText(entry, "updated"));
			}
			out.add(new RawNewsArticle(
					id, link, title, stripHtml(description), content, image, author,
					feedTitle != null ? feedTitle : feedUrl, publishedAt, "EN", List.of()));
		}
	}

	private static String atomLink(Element entry) {
		NodeList links = entry.getElementsByTagName("link");
		for (int i = 0; i < links.getLength(); i++) {
			Element link = (Element) links.item(i);
			String rel = attr(link, "rel");
			if (rel == null || rel.isBlank() || rel.equals("alternate")) {
				String href = attr(link, "href");
				if (href != null) {
					return href;
				}
			}
		}
		return null;
	}

	private static String atomAuthor(Element entry) {
		Element author = firstElement(entry, "author");
		if (author == null) {
			return null;
		}
		return textOrNull(firstElementText(author, "name"));
	}

	private static String atomImage(Element entry) {
		Element mediaContent = firstElement(entry, "content");
		if (mediaContent != null) {
			String mediaUrl = attr(mediaContent, "url");
			if (mediaUrl != null) {
				return mediaUrl;
			}
		}
		Element thumbnail = firstElement(entry, "thumbnail");
		if (thumbnail != null) {
			return attr(thumbnail, "url");
		}
		return null;
	}

	private static String contentOf(Element item) {
		String contentEncoded = firstElementText(item, "encoded");
		return contentEncoded != null ? contentEncoded : null;
	}

	private static String imageOf(Element item) {
		Element enclosure = firstElement(item, "enclosure");
		if (enclosure != null) {
			String url = attr(enclosure, "url");
			if (url != null) {
				return url;
			}
		}
		Element mediaContent = firstElement(item, "content");
		if (mediaContent != null) {
			String url = attr(mediaContent, "url");
			if (url != null) {
				return url;
			}
		}
		return null;
	}

	private static List<String> categoryTags(Element item) {
		List<String> categories = new ArrayList<>();
		NodeList children = item.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node node = children.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals("category")) {
				NodeList childrenText = children.item(i).getChildNodes();
				StringBuilder value = new StringBuilder();
				for (int j = 0; j < childrenText.getLength(); j++) {
					value.append(childrenText.item(j).getTextContent());
				}
				String text = value.toString().trim();
				if (!text.isBlank()) {
					categories.add(text);
				}
			}
		}
		return categories;
	}

	private static Document parseXml(String xml) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			DocumentBuilder builder = factory.newDocumentBuilder();
			return builder.parse(new InputSource(new StringReader(xml)));
		} catch (Exception ex) {
			throw new NewsProviderException("Malformed feed XML: " + ex.getMessage(), ex);
		}
	}

	static Instant parseDateOrNull(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String value = raw.trim();
		List<DateTimeFormatter> formatters = List.of(
				DateTimeFormatter.RFC_1123_DATE_TIME,
				DateTimeFormatter.ISO_OFFSET_DATE_TIME,
				DateTimeFormatter.ISO_INSTANT,
				DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
				DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH));
		for (DateTimeFormatter formatter : formatters) {
			try {
				ZonedDateTime zoned = ZonedDateTime.parse(value, formatter);
				return zoned.toInstant().truncatedTo(ChronoUnit.SECONDS);
			} catch (DateTimeParseException ignored) {
				// try next formatter
			}
		}
		log.debug("[News] Could not parse date '{}'", raw);
		return null;
	}

	/**
	 * Removes HTML tags from provider descriptions, keeping text (and newlines
	 * for paragraph breaks) safe for storage and display.
	 */
	static String stripHtml(String input) {
		return TextSanitizer.stripHtml(input);
	}
}