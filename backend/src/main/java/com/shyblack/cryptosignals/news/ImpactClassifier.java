package com.shyblack.cryptosignals.news;

import com.shyblack.cryptosignals.entity.enums.NewsCategory;
import com.shyblack.cryptosignals.entity.enums.NewsImpact;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Assigns a market impact level (LOW..CRITICAL) via a transparent point rubric:
 * category weight + signal keyword boosts + sentiment magnitude + breadth.
 */
@Component
public class ImpactClassifier {

	public NewsImpact classify(NewsCategory category, double sentimentScore, int assetCount, String title) {
		double points = 1.0;
		points += categoryWeight(category) + intensity(sentimentScore) + breadth(assetCount);
		points += keywordBoost(title);

		if (points >= NewsScoringConstants.IMPACT_CRITICAL) {
			return NewsImpact.CRITICAL;
		}
		if (points >= NewsScoringConstants.IMPACT_HIGH) {
			return NewsImpact.HIGH;
		}
		if (points >= NewsScoringConstants.IMPACT_MEDIUM) {
			return NewsImpact.MEDIUM;
		}
		return NewsImpact.LOW;
	}

	private static double categoryWeight(NewsCategory category) {
		return switch (category) {
			case HACK, EXPLOIT -> 4;
			case REGULATION, ETF, DELISTING, LEGAL, MACRO -> 3;
			case SECURITY, LISTING, TOKEN_UNLOCK, EXCHANGE, FUNDING, INVESTMENT, ADOPTION,
					PROTOCOL_UPDATE, DEFI, GOVERNANCE -> 2;
			case PARTNERSHIP, TECHNOLOGY, NETWORK, NFT, MINING, TOKEN_BURN, MARKET -> 1;
			case OTHER -> 0;
		};
	}

	private static double intensity(double sentimentScore) {
		double magnitude = Math.abs(sentimentScore);
		if (magnitude >= 0.7) {
			return 1.0;
		}
		if (magnitude >= 0.4) {
			return 0.5;
		}
		return 0.0;
	}

	private static double breadth(int assetCount) {
		if (assetCount >= 3) {
			return 1.0;
		}
		return 0.0;
	}

	private static final String[][] BOOST_KEYWORDS = {
			{"sec ", "0.5"}, {"etf", "0.5"}, {"federal reserve", "0.5"}, {"central bank", "0.5"},
			{"congress", "0.5"}, {"legislation", "0.5"}, {"ban", "0.5"}, {"delist", "0.5"},
			{"hack", "0.5"}, {"exploit", "0.5"}, {"collapse", "0.5"}, {"lawsuit", "0.5"},
			{"indicted", "0.5"}, {"bankruptcy", "0.5"}, {"halving", "0.5"}, {"all-time high", "0.5"},
			{"all-time low", "0.5"}, {"milestone", "0.5"},
	};

	private static double keywordBoost(String title) {
		if (title == null) {
			return 0.0;
		}
		String text = " " + title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ") + " ";
		double bonus = 0.0;
		for (String[] kw : BOOST_KEYWORDS) {
			if (text.contains(" " + kw[0] + " ")) {
				bonus += Double.parseDouble(kw[1]);
			}
		}
		return Math.min(bonus, 2.0);
	}
}