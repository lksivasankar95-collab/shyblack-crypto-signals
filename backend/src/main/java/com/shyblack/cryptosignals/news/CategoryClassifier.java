package com.shyblack.cryptosignals.news;

import com.shyblack.cryptosignals.entity.enums.NewsCategory;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Normalizes raw provider categories plus keyword evidence into the controlled
 * {@link NewsCategory} vocabulary. Providers are never trusted verbatim.
 */
@Component
public class CategoryClassifier {

	/** Exact manual mapping for provider category strings. */
	private static final Map<String, NewsCategory> PROVIDER_CATEGORIES = Map.ofEntries(
			Map.entry("regulation", NewsCategory.REGULATION),
			Map.entry("regulatory", NewsCategory.REGULATION),
			Map.entry("regulations", NewsCategory.REGULATION),
			Map.entry("policy", NewsCategory.REGULATION),
			Map.entry("legislation", NewsCategory.REGULATION),
			Map.entry("censorship", NewsCategory.REGULATION),
			Map.entry("law", NewsCategory.REGULATION),
			Map.entry("sec", NewsCategory.REGULATION),
			Map.entry("us regulation", NewsCategory.REGULATION),
			Map.entry("european regulation", NewsCategory.REGULATION),
			Map.entry("uk regulation", NewsCategory.REGULATION),
			Map.entry("etf", NewsCategory.ETF),
			Map.entry("etfs", NewsCategory.ETF),
			Map.entry("exchange-traded funds", NewsCategory.ETF),
			Map.entry("exchange", NewsCategory.EXCHANGE),
			Map.entry("exchanges", NewsCategory.EXCHANGE),
			Map.entry("companies", NewsCategory.EXCHANGE),
			Map.entry("industry", NewsCategory.EXCHANGE),
			Map.entry("enterprise", NewsCategory.EXCHANGE),
			Map.entry("listing", NewsCategory.LISTING),
			Map.entry("delisting", NewsCategory.DELISTING),
			Map.entry("partnerships", NewsCategory.PARTNERSHIP),
			Map.entry("partnership", NewsCategory.PARTNERSHIP),
			Map.entry("collaborations", NewsCategory.PARTNERSHIP),
			Map.entry("collaboration", NewsCategory.PARTNERSHIP),
			Map.entry("business", NewsCategory.PARTNERSHIP),
			Map.entry("adoption", NewsCategory.ADOPTION),
			Map.entry("wealth", NewsCategory.ADOPTION),
			Map.entry("fundraising", NewsCategory.FUNDING),
			Map.entry("funding", NewsCategory.FUNDING),
			Map.entry("venture capital", NewsCategory.FUNDING),
			Map.entry("markets", NewsCategory.MARKET),
			Map.entry("market analysis", NewsCategory.MARKET),
			Map.entry("market updates", NewsCategory.MARKET),
			Map.entry("market report", NewsCategory.MARKET),
			Map.entry("macro", NewsCategory.MACRO),
			Map.entry("macro and econ", NewsCategory.MACRO),
			Map.entry("economy", NewsCategory.MACRO),
			Map.entry("mining", NewsCategory.MINING),
			Map.entry("bitcoin mining", NewsCategory.MINING),
			Map.entry("technology", NewsCategory.TECHNOLOGY),
			Map.entry("technical analysis", NewsCategory.TECHNOLOGY),
			Map.entry("tech", NewsCategory.TECHNOLOGY),
			Map.entry("protocol", NewsCategory.PROTOCOL_UPDATE),
			Map.entry("defi", NewsCategory.DEFI),
			Map.entry("decentralized finance", NewsCategory.DEFI),
			Map.entry("nft", NewsCategory.NFT),
			Map.entry("nfts", NewsCategory.NFT),
			Map.entry("governance", NewsCategory.GOVERNANCE),
			Map.entry("legal", NewsCategory.LEGAL),
			Map.entry("security", NewsCategory.SECURITY),
			Map.entry("cybersecurity", NewsCategory.SECURITY),
			Map.entry("privacy", NewsCategory.SECURITY),
			Map.entry("network", NewsCategory.NETWORK),
			Map.entry("infrastructure", NewsCategory.NETWORK),
			Map.entry("investment", NewsCategory.INVESTMENT),
			Map.entry("token unlocks", NewsCategory.TOKEN_UNLOCK),
			Map.entry("gaming", NewsCategory.TECHNOLOGY));

	/** Keyword evidence applied when no provider category matches. */
	private static final List<CategoryRule> KEYWORDS = List.of(
			new CategoryRule(NewsCategory.HACK, "hack", "hacked", "hacker", "stolen", "theft", "drained", "breach"),
			new CategoryRule(NewsCategory.EXPLOIT, "exploit", "vulnerability", "flash loan", "bug", "smart contract"),
			new CategoryRule(NewsCategory.ETF, "etf", "exchange-traded fund", "spot etf", "etfs"),
			new CategoryRule(NewsCategory.REGULATION, "sec ", "sec's", "cf", "fincen", "ban", "banned", "regulatory",
					"regulation", "regulator", "legislation", "congress", "senate", "bill"),
			new CategoryRule(NewsCategory.LEGAL, "lawsuit", "sues", "sued", "court", "indict", "indicted",
					"charges", "prosecutor", "subpoena", "settlement", "convicted"),
			new CategoryRule(NewsCategory.DELISTING, "delist", "delisted", "delisting", "removed from the exchange"),
			new CategoryRule(NewsCategory.LISTING, "listing", "listed on binance", "coinbase listing"),
			new CategoryRule(NewsCategory.FUNDING, "raise", "raised", "funding", "fundraiser", "venture"),
			new CategoryRule(NewsCategory.TOKEN_UNLOCK, "token unlock", "unlocked", "vesting schedule", "cliff vesting"),
			new CategoryRule(NewsCategory.TOKEN_BURN, "burn", "burned", "token burn", "buyback and burn"),
			new CategoryRule(NewsCategory.MACRO, "federal reserve", "fed", "inflation", "cpi", "interest rates",
					"rate hike", "rate cut", "treasury", "dollar", "recession", "imf", "gdp", "economy"),
			new CategoryRule(NewsCategory.SECURITY, "security", "hack", "stolen", "kyc", "identity theft"),
			new CategoryRule(NewsCategory.EXCHANGE, "binance", "coinbase", "kraken", "bybit", "okx", "upbit",
					"exchange", "okx listing"),
			new CategoryRule(NewsCategory.ADOPTION, "adoption", "merchant", "payment", "payment rails", "mainstream",
					"institutional", "kiosk", "atm"),
			new CategoryRule(NewsCategory.TECHNOLOGY, "technology", "scaling", "layer 2", "zkp", "zero-knowledge",
					"research", "upgrade", "software", "developers"),
			new CategoryRule(NewsCategory.MARKET, "price", "market", "trading", "surge", "plunge", "rally",
					"correction", "volatility", "liquidations", "futures"),
			new CategoryRule(NewsCategory.GOVERNANCE, "governance", "proposal", "dao vote", "referendum"),
			new CategoryRule(NewsCategory.PROTOCOL_UPDATE, "upgrade", "hard fork", "mainnet", "testnet", "shapella",
					"merge", "v2", "protocol"),
			new CategoryRule(NewsCategory.DEFI, "defi", "decentralized finance", "lending protocol", "staking yield",
					"liquidity pool", "liquid staking"),
			new CategoryRule(NewsCategory.NFT, "nft", "nfts", "collectibles"),
			new CategoryRule(NewsCategory.MINING, "mining", "miners", "hashrate", "halving", "bitmain", "asic"),
			new CategoryRule(NewsCategory.INVESTMENT, "investment", "invest", "institutional money", "acquisition",
					"merger", "portfolio"));

	public NewsCategory classify(String rawCategories, String title, String summary) {
		String text = lower(title) + " " + lower(summary);
		for (String raw : splitCategories(rawCategories)) {
			NewsCategory direct = PROVIDER_CATEGORIES.get(raw);
			if (direct != null && direct != NewsCategory.OTHER) {
				return direct;
			}
		}
		NewsCategory best = NewsCategory.OTHER;
		int bestHits = 0;
		for (CategoryRule rule : KEYWORDS) {
			int hits = rule.countIn(text);
			if (hits > bestHits) {
				bestHits = hits;
				best = rule.category();
			} else if (hits == bestHits && hits > 0 && priority(best) > priority(rule.category())) {
				best = rule.category();
			}
		}
		return best;
	}

	private static List<String> splitCategories(String rawCategories) {
		if (rawCategories == null || rawCategories.isBlank()) {
			return List.of();
		}
		return java.util.Arrays.stream(rawCategories.split(","))
				.map(String::trim)
				.map(s -> s.toLowerCase(Locale.ROOT))
				.filter(s -> !s.isBlank())
				.toList();
	}

	private static String lower(String text) {
		return text == null ? "" : text.toLowerCase(Locale.ROOT);
	}

	/** Market-relevant categories win ties. */
	private static int priority(NewsCategory category) {
		return switch (category) {
			case ETF, REGULATION, HACK, EXPLOIT, DELISTING, MACRO, LEGAL -> 0;
			case SECURITY, TOKEN_UNLOCK, TOKEN_BURN, EXCHANGE, LISTING, PROTOCOL_UPDATE -> 1;
			case FUNDING, INVESTMENT, ADOPTION, NETWORK, MARKET, DEFI -> 2;
			case PARTNERSHIP, GOVERNANCE, TECHNOLOGY, NFT, MINING -> 3;
			case OTHER -> 99;
		};
	}

	private record CategoryRule(NewsCategory category, String... keywords) {

		int countIn(String text) {
			int hits = 0;
			for (String keyword : keywords) {
				if (text.contains(keyword)) {
					hits++;
				}
			}
			return hits;
		}
	}
}