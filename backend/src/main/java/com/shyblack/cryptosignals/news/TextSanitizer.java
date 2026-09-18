package com.shyblack.cryptosignals.news;

/**
 * Shared text cleaning utilities for untrusted provider content.
 */
public final class TextSanitizer {

	private TextSanitizer() {
	}

	/**
	 * Removes HTML tags from provider content, keeping text safe for storage/display.
	 */
	public static String stripHtml(String input) {
		if (input == null || input.isBlank()) {
			return null;
		}
		String withoutTags = input.replaceAll("(?i)<br\\s*/?>", "\n")
				.replaceAll("(?i)</p>", "\n")
				.replaceAll("(?s)<[^>]+>", " ")
				.replaceAll("&nbsp;", " ")
				.replaceAll("&amp;", "&")
				.replaceAll("&lt;", "<")
				.replaceAll("&gt;", ">")
				.replaceAll("&quot;", "\"")
				.replaceAll("&#39;|&apos;", "'")
				.replaceAll("[ \\t]+", " ")
				.replaceAll("\\n{3,}", "\n\n")
				.trim();
		return withoutTags.isBlank() ? null : withoutTags;
	}

	public static String collapseWhitespace(String input) {
		if (input == null) {
			return null;
		}
		String collapsed = input.replaceAll("\\s+", " ").trim();
		return collapsed.isBlank() ? null : collapsed;
	}

	public static String truncate(String input, int maxChars) {
		if (input == null) {
			return null;
		}
		if (input.length() <= maxChars) {
			return input;
		}
		return input.substring(0, maxChars);
	}
}