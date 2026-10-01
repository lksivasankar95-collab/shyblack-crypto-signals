package com.shyblack.cryptosignals.config;

import com.shyblack.cryptosignals.entity.enums.NewsSourceTier;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Platform-level News Flow Momentum (NFM) settings (spec §7, §47, §50).
 *
 * <p>Per-strategy tunables (weights, thresholds, SL/TP) live in
 * {@code NfmFuturesConfig} on the strategy row; this record only holds
 * ingestion/source-quality policy.</p>
 */
@ConfigurationProperties(prefix = "app.nfm")
public record NfmProperties(
		boolean enabled,
		int eventMaxAgeMinutes,
		List<String> tier1Sources,
		List<String> tier2Sources,
		List<String> tier3Sources
) {

	private static final List<String> DEFAULT_TIER1 = List.of(
			"federalreserve", "federal reserve", "sec.gov", "cftc", "treasury", "bls.gov",
			"ecb", "european central bank", "bank of england", "bankofengland", "bank of japan",
			"boj", "official");
	private static final List<String> DEFAULT_TIER2 = List.of(
			"reuters", "bloomberg", "financial times", "ft.com", "wall street journal", "wsj");
	private static final List<String> DEFAULT_TIER3 = List.of(
			"cointelegraph", "decrypt", "bitcoinmagazine", "theblock", "cryptoslate",
			"cryptobriefing", "ambcrypto", "u.today");

	public NfmProperties {
		if (eventMaxAgeMinutes <= 0) {
			eventMaxAgeMinutes = 180;
		}
		tier1Sources = normalize(tier1Sources, DEFAULT_TIER1);
		tier2Sources = normalize(tier2Sources, DEFAULT_TIER2);
		tier3Sources = normalize(tier3Sources, DEFAULT_TIER3);
	}

	/** Classifies a source name into a quality tier by substring match. */
	public NewsSourceTier tierFor(String source) {
		if (source == null || source.isBlank()) {
			return NewsSourceTier.TIER_4;
		}
		String s = source.toLowerCase(Locale.ROOT);
		if (containsAny(s, tier1Sources)) return NewsSourceTier.TIER_1;
		if (containsAny(s, tier2Sources)) return NewsSourceTier.TIER_2;
		if (containsAny(s, tier3Sources)) return NewsSourceTier.TIER_3;
		return NewsSourceTier.TIER_4;
	}

	private static boolean containsAny(String haystack, List<String> needles) {
		for (String needle : needles) {
			if (haystack.contains(needle)) {
				return true;
			}
		}
		return false;
	}

	private static List<String> normalize(List<String> provided, List<String> fallback) {
		List<String> source = (provided == null || provided.isEmpty()) ? fallback : provided;
		return source.stream()
				.filter(s -> s != null && !s.isBlank())
				.map(s -> s.toLowerCase(Locale.ROOT).trim())
				.toList();
	}
}
