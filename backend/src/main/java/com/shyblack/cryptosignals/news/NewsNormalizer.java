package com.shyblack.cryptosignals.news;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Normalizes raw provider content into stable, dedupable canonical identities.
 * <p>Provider URLs are untrusted: tracking parameters are stripped, scheme/host are
 * lowercased, fragments removed, and only http/https survives.</p>
 */
public final class NewsNormalizer {

	private static final Set<String> TRACKING_PARAMETERS = Set.of(
			"utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
			"fbclid", "gclid", "mc_cid", "mc_eid", "igshid", "ref", "ref_src",
			"ref_type", "campaign", "consent");

	/** Scheme/host/tracking stripped into a canonical URL string, or null if unusable. */
	public static String canonicalUrl(String rawUrl) {
		if (rawUrl == null || rawUrl.isBlank()) {
			return null;
		}
		try {
			java.net.URI uri = java.net.URI.create(rawUrl.trim());
			String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
			if (!scheme.equals("http") && !scheme.equals("https")) {
				return null;
			}
			String host = uri.getHost();
			if (host == null) {
				return null;
			}
			host = host.toLowerCase(Locale.ROOT);
			int defaultPort = scheme.equals("https") ? 443 : 80;
			int port = uri.getPort();
			String hostPort = (port != -1 && port != defaultPort) ? host + ":" + port : host;
			String path = uri.getRawPath();
			while (path != null && path.length() > 1 && path.endsWith("/")) {
				path = path.substring(0, path.length() - 1);
			}
			String query = canonicalQuery(uri.getRawQuery());
			StringBuilder sb = new StringBuilder(scheme).append("://").append(hostPort);
			if (path != null && !path.isBlank()) {
				sb.append(path);
			}
			if (query != null) {
				sb.append('?').append(query);
			}
			return sb.toString();
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private static String canonicalQuery(String rawQuery) {
		if (rawQuery == null || rawQuery.isBlank()) {
			return null;
		}
		Map<String, List<String>> clean = new LinkedHashMap<>();
		for (String pair : rawQuery.split("&")) {
			if (pair.isBlank()) {
				continue;
			}
			int eq = pair.indexOf('=');
			String key = eq == -1 ? pair : pair.substring(0, eq);
			String value = eq == -1 ? "" : pair.substring(eq + 1);
			if (TRACKING_PARAMETERS.contains(key.toLowerCase(Locale.ROOT))) {
				continue;
			}
			clean.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
		}
		return clean.entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.flatMap(entry -> entry.getValue().stream()
						.map(value -> entry.getKey() + (value.isBlank() ? "" : "=" + value)))
				.reduce((a, b) -> a + "&" + b)
				.orElse(null);
	}

	/** Lowercased, whitespace-collapsed title used for fuzzy dedup. */
	public static String canonicalTitle(String title) {
		if (title == null) {
			return null;
		}
		String cleaned = TextSanitizer.collapseWhitespace(title);
		if (cleaned == null) {
			return null;
		}
		return cleaned.toLowerCase(Locale.ROOT)
				.replaceAll("[^a-zA-Z0-9]+", " ")
				.trim();
	}

	/**
	 * SHA-256 canonical identity hash.
	 * <p>Prefer the canonical URL when present; otherwise fall back to the canonical
	 * title plus source name so syndicated copies of the same article collide.</p>
	 */
	public static String canonicalHash(String canonicalUrl, String canonicalTitle, String sourceName) {
		String identity = canonicalUrl != null
				? "url:" + canonicalUrl
				: "title:" + canonicalTitle + "|" + staleSafeSource(sourceName);
		return sha256(identity);
	}

	private static String staleSafeSource(String sourceName) {
		return sourceName == null ? "" : sourceName.toLowerCase(Locale.ROOT).trim();
	}

	private static String sha256(String input) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (Exception ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}

	/** Maximum length for stored fields. */
	public static final int MAX_TITLE = 512;
	public static final int MAX_SUMMARY = 4000;

	public static String cleanSummary(String rawDescription) {
		return Optional.ofNullable(TextSanitizer.stripHtml(rawDescription))
				.map(text -> TextSanitizer.truncate(TextSanitizer.collapseWhitespace(text), MAX_SUMMARY))
				.orElse(null);
	}

	/** Sorted, deduplicated distinct lowercase tokens for keyword scanners. */
	public static List<String> tokens(String text) {
		if (text == null) {
			return List.of();
		}
		return Arrays.stream(text.toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9]", " ")
				.split("\\s+"))
				.filter(token -> !token.isBlank())
				.sorted()
				.distinct()
				.toList();
	}
}