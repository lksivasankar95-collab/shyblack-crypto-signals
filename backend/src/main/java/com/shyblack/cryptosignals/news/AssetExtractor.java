package com.shyblack.cryptosignals.news;

import com.shyblack.cryptosignals.entity.enums.NewsAssetRelationshipType;
import com.shyblack.cryptosignals.market.MarketBook;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Maps a news article to the crypto assets it affects.
 * <p>Reuses the live Binance USDT symbol directory when available and a curated
 * well-known token map so major assets always resolve even before the catalog loads.</p>
 */
@Component
public class AssetExtractor {

	private final MarketBook marketBook;
	private final Map<String, Pattern> phrasePatternCache = new ConcurrentHashMap<>();

	/** Curated symbol -> common display name. */
	private static final Map<String, String> CURATED = curated();

	public record ExtractedAsset(String symbol, String name, double relevanceScore, NewsAssetRelationshipType type) {
	}

	public AssetExtractor(MarketBook marketBook) {
		this.marketBook = marketBook;
	}

	/**
	 * Extracts affected assets from the aggregate article text.
	 */
	public List<ExtractedAsset> extract(String title, String summary) {
		Map<String, String> assets = allAssets();
		String titleLower = lower(title);
		String summaryLower = lower(summary);

		Map<String, WordHit> hits = new LinkedHashMap<>();
		for (String text : List.of(titleLower, summaryLower)) {
			for (String token : tokenize(text)) {
				String symbol = assets.get(token);
				if (symbol != null) {
					WordHit hit = hits.computeIfAbsent(symbol, s -> new WordHit());
					hit.hits++;
					if (text == titleLower) {
						hit.titleHits++;
					}
				}
			}
		}
		for (Map.Entry<String, String> entry : assets.entrySet()) {
			String name = entry.getValue();
			if (name == null || !name.contains(" ")) {
				continue;
			}
			String nameLower = name.toLowerCase(Locale.ROOT);
			if (containsWord(titleLower, nameLower)) {
				WordHit hit = hits.computeIfAbsent(entry.getKey(), s -> new WordHit());
				hit.hits++;
				hit.titleHits++;
			} else if (containsWord(summaryLower, nameLower)) {
				WordHit hit = hits.computeIfAbsent(entry.getKey(), s -> new WordHit());
				hit.hits++;
			}
		}

		List<ExtractedAsset> results = new ArrayList<>();
		for (Map.Entry<String, WordHit> entry : hits.entrySet()) {
			WordHit hit = entry.getValue();
			double titleScore = Math.min(1.0, hit.titleHits);
			double summaryScore = Math.min(1.0, (hit.hits - hit.titleHits) * 0.4);
			double relevance = Math.min(1.0, titleScore + summaryScore);
			NewsAssetRelationshipType type = hit.titleHits > 0
					? NewsAssetRelationshipType.PRIMARY
					: NewsAssetRelationshipType.MENTIONED;
			String symbol = entry.getKey();
			results.add(new ExtractedAsset(symbol, lookupName(symbol), round2(relevance), type));
		}
		return results;
	}

	private String lookupName(String symbol) {
		String curatedName = CURATED.get(symbol);
		if (curatedName != null) {
			return curatedName;
		}
		for (String tradable : marketBook.spotSymbols().snapshot()) {
			if (tradable.equals(symbol + "USDT")) {
				return marketBook.spotSymbols().name(tradable);
			}
		}
		return symbol;
	}

	private Map<String, String> allAssets() {
		Map<String, String> assets = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : CURATED.entrySet()) {
			assets.put(lower(entry.getKey()), entry.getKey());
			assets.put(lower(entry.getValue()), entry.getKey());
		}
		for (String tradable : marketBook.spotSymbols().snapshot()) {
			String base = baseSymbol(tradable);
			if (base != null && base.length() >= 2 && base.length() <= 10) {
				assets.putIfAbsent(lower(base), base);
			}
		}
		return assets;
	}

	/** Binance-style "BTCUSDT" -> "BTC". */
	static String baseSymbol(String symbol) {
		String upper = symbol.toUpperCase(Locale.ROOT).trim();
		if (upper.endsWith("USDT") && upper.length() > 4) {
			return upper.substring(0, upper.length() - 4);
		}
		return null;
	}

	private static List<String> tokenize(String text) {
		if (text == null || text.isBlank()) {
			return List.of();
		}
		List<String> tokens = new ArrayList<>();
		java.util.regex.Matcher matcher = WORD.matcher(text);
		while (matcher.find()) {
			tokens.add(matcher.group());
		}
		return tokens;
	}

	private static final Pattern WORD = Pattern.compile("[a-z0-9]+");

	private boolean containsWord(String text, String word) {
		if (text == null || word == null || word.isBlank()) {
			return false;
		}
		Pattern pattern = phrasePatternCache.computeIfAbsent(
				word, w -> Pattern.compile("(?<![a-z0-9])" + Pattern.quote(w) + "(?![a-z0-9])"));
		return pattern.matcher(text).find();
	}

	private static String lower(String text) {
		return text == null ? "" : text.toLowerCase(Locale.ROOT);
	}

	private static double round2(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	private static final class WordHit {
		int hits;
		int titleHits;
	}

	private static Map<String, String> curated() {
		Map<String, String> map = new LinkedHashMap<>();
		map.put("BTC", "Bitcoin");
		map.put("ETH", "Ethereum");
		map.put("BNB", "Binance Coin");
		map.put("SOL", "Solana");
		map.put("XRP", "XRP");
		map.put("ADA", "Cardano");
		map.put("DOGE", "Dogecoin");
		map.put("DOT", "Polkadot");
		map.put("AVAX", "Avalanche");
		map.put("LTC", "Litecoin");
		map.put("LINK", "Chainlink");
		map.put("SHIB", "Shiba Inu");
		map.put("UNI", "Uniswap");
		map.put("MATIC", "Polygon");
		map.put("POL", "Polygon");
		map.put("ATOM", "Cosmos");
		map.put("TRX", "Tron");
		map.put("TON", "Toncoin");
		map.put("NEAR", "Near Protocol");
		map.put("APT", "Aptos");
		map.put("ARB", "Arbitrum");
		map.put("OP", "Optimism");
		map.put("SUI", "Sui");
		map.put("SEI", "Sei");
		map.put("INJ", "Injective");
		map.put("XLM", "Stellar");
		map.put("ALGO", "Algorand");
		map.put("FIL", "Filecoin");
		map.put("ICP", "Internet Computer");
		map.put("ETC", "Ethereum Classic");
		map.put("VET", "VeChain");
		map.put("HBAR", "Hedera");
		map.put("MKR", "Maker");
		map.put("DAI", "Dai");
		map.put("AAVE", "Aave");
		map.put("SNX", "Synthetix");
		map.put("COMP", "Compound");
		map.put("CRV", "Curve DAO");
		map.put("GRT", "The Graph");
		map.put("SAND", "The Sandbox");
		map.put("MANA", "Decentraland");
		map.put("AXS", "Axie Infinity");
		map.put("ENJ", "Enjin Coin");
		map.put("CHZ", "Chiliz");
		map.put("KSM", "Kusama");
		map.put("ZEC", "Zcash");
		map.put("XMR", "Monero");
		map.put("DASH", "Dash");
		map.put("EOS", "EOS");
		map.put("XTZ", "Tezos");
		map.put("THETA", "Theta Network");
		map.put("RUNE", "THORChain");
		map.put("FTM", "Fantom");
		map.put("S", "Sonic");
		map.put("TAO", "Bittensor");
		map.put("PEPE", "Pepe");
		map.put("WIF", "Dogwifhat");
		map.put("BONK", "Bonk");
		map.put("FLOKI", "Floki");
		map.put("USDC", "USD Coin");
		map.put("USDT", "Tether");
		map.put("WBTC", "Wrapped Bitcoin");
		map.put("PENDLE", "Pendle");
		map.put("JUP", "Jupiter");
		map.put("PYTH", "Pyth Network");
		return map;
	}
}