package com.shyblack.cryptosignals.news;

import com.shyblack.cryptosignals.entity.enums.NewsSentiment;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Lexicon-based directional sentiment analysis.
 * <p>No LLM is used (project rule). Scores are informational tone, never a trading
 * decision. Negations and known high-signal phrases are handled explicitly.</p>
 */
@Component
public class SentimentAnalyzer {

	public record SentimentResult(NewsSentiment sentiment, double score, double confidence) {
	}

	private static final Map<String, Double> LEXICON = new LinkedHashMap<>();
	private static final Map<String, Double> PHRASES = new LinkedHashMap<>();
	private static final Set<String> NEGATIONS = Set.of("not", "never", "no", "against", "without", "failed", "fails");

	private static final double NEUTRAL_BAND = NewsScoringConstants.SENTIMENT_NEUTRAL_BAND;

	public SentimentResult analyze(String title, String summary) {
		String text = (title == null ? "" : title) + " " + (summary == null ? "" : summary);
		String haystack = " " + text.toLowerCase(java.util.Locale.ROOT)
				.replaceAll("[^a-z0-9\\s]", " ")
				.replaceAll("\\s+", " ") + " ";

		double phraseScore = 0.0;
		for (Map.Entry<String, Double> phrase : PHRASES.entrySet()) {
			if (haystack.contains(" " + phrase.getKey() + " ")) {
				phraseScore += phrase.getValue();
			}
		}

		double wordScore = 0.0;
		int matchedGroups = 0;
		Set<String> seen = new java.util.HashSet<>();
		String[] toks = haystack.split("\\s+");
		for (int i = 0; i < toks.length; i++) {
			String token = toks[i];
			boolean negated = false;
			for (int back = Math.max(0, i - 3); back < i; back++) {
				if (NEGATIONS.contains(toks[back])) {
					negated = true;
					break;
				}
			}
			if (!LEXICON.containsKey(token)) {
				continue;
			}
			double weight = LEXICON.get(token);
			if (negated) {
				weight = -weight * 0.7;
			}
			wordScore += weight;
			if (seen.add(token)) {
				matchedGroups++;
			}
		}

		double total = phraseScore + wordScore;
		double score = round2(Math.tanh(total * 0.5));

		double confidence;
		if (matchedGroups == 0 && phraseScore == 0.0) {
			confidence = 0.25;
		} else {
			confidence = round2(Math.min(1.0, 0.35 + Math.min(matchedGroups, 6) * 0.12));
		}

		NewsSentiment sentiment = score >= NEUTRAL_BAND ? NewsSentiment.POSITIVE
				: score <= -NEUTRAL_BAND ? NewsSentiment.NEGATIVE
				: NewsSentiment.NEUTRAL;
		return new SentimentResult(sentiment, score, confidence);
	}

	private static double round2(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	private static Map<String, Double> lexicon(String[][] entries) {
		Map<String, Double> map = new LinkedHashMap<>();
		for (String[] entry : entries) {
			map.put(entry[0], Double.parseDouble(entry[1]));
		}
		return map;
	}

	static {
		PHRASES.putAll(phrases(new String[][] {
				{"etf approved", "1.0"},
				{"etf approval", "0.9"},
				{"sec approves", "1.0"},
				{"sec approval", "0.9"},
				{"all-time high", "0.8"},
				{"record high", "0.8"},
				{"etf rejected", "-1.0"},
				{"etf rejection", "-0.9"},
				{"sec rejects", "-1.0"},
				{"sec revokes", "-1.0"},
				{"rug pull", "-0.9"},
				{"all-time low", "-0.8"},
				{"flash crash", "-0.9"},
		}));

		LEXICON.putAll(lexicon(new String[][] {
				{"approve", "0.9"}, {"approval", "0.9"}, {"approved", "0.9"}, {"greenlight", "0.9"},
				{"greenlights", "0.9"}, {"listing", "0.5"}, {"listed", "0.5"}, {"adoption", "0.6"},
				{"partnership", "0.5"}, {"integrated", "0.5"}, {"integration", "0.5"}, {"institutional", "0.3"},
				{"growth", "0.4"}, {"surge", "0.6"}, {"surges", "0.6"}, {"rally", "0.7"}, {"rallies", "0.7"},
				{"gains", "0.5"}, {"soars", "0.7"}, {"skyrockets", "0.7"}, {"outperform", "0.6"},
				{"bullish", "0.7"}, {"bullrun", "0.7"}, {"mainnet", "0.4"}, {"upgrade", "0.4"},
				{"inflow", "0.6"}, {"inflows", "0.6"}, {"accumulate", "0.5"}, {"accumulation", "0.5"},
				{"milestone", "0.3"}, {"rebound", "0.5"}, {"recovery", "0.4"}, {"climbs", "0.4"},
				{"tops", "0.3"}, {"beat", "0.4"}, {"strength", "0.3"}, {"strong", "0.3"},
				{"hack", "-0.9"}, {"hacked", "-0.9"}, {"hackers", "-0.9"}, {"exploit", "-0.9"}, {"exploited", "-0.9"},
				{"breach", "-0.8"}, {"breached", "-0.8"}, {"stolen", "-0.9"}, {"steal", "-0.7"}, {"theft", "-0.8"},
				{"drained", "-0.8"}, {"scam", "-0.8"}, {"fraud", "-0.8"}, {"fraudulent", "-0.8"},
				{"collapse", "-0.9"}, {"collapses", "-0.9"}, {"crash", "-0.9"}, {"crashes", "-0.9"},
				{"plunge", "-0.8"}, {"plunges", "-0.8"}, {"decline", "-0.4"}, {"declines", "-0.4"},
				{"drop", "-0.4"}, {"drops", "-0.4"}, {"dropped", "-0.4"}, {"falls", "-0.4"}, {"fell", "-0.4"},
				{"loss", "-0.5"}, {"losses", "-0.5"}, {"bearish", "-0.8"}, {"outflow", "-0.6"}, {"outflows", "-0.6"},
				{"selling", "-0.3"}, {"liquidations", "-0.6"}, {"liquidation", "-0.6"},
				{"lawsuit", "-0.7"}, {"sues", "-0.7"}, {"sued", "-0.7"}, {"charges", "-0.7"}, {"charged", "-0.7"},
				{"indicted", "-0.8"}, {"indictment", "-0.8"}, {"ban", "-0.9"}, {"banned", "-0.9"}, {"bans", "-0.9"},
				{"delist", "-0.8"}, {"delisted", "-0.8"}, {"delisting", "-0.8"},
				{"reject", "-0.8"}, {"rejected", "-0.8"}, {"rejects", "-0.8"}, {"rejection", "-0.8"},
				{"denied", "-0.8"}, {"denial", "-0.7"}, {"delay", "-0.5"}, {"delays", "-0.5"},
				{"postpone", "-0.5"}, {"halt", "-0.6"}, {"halts", "-0.6"}, {"suspension", "-0.5"},
				{"bankrupt", "-0.9"}, {"bankruptcy", "-0.9"}, {"insolvent", "-0.9"}, {"freeze", "-0.7"}, {"frozen", "-0.7"},
				{"subpoena", "-0.7"}, {"investigation", "-0.6"}, {"investigate", "-0.6"}, {"investigating", "-0.6"},
				{"sanctions", "-0.7"}, {"sanction", "-0.7"}, {"risk", "-0.2"}, {"threat", "-0.3"},
		}));
	}

	private static Map<String, Double> phrases(String[][] entries) {
		return lexicon(entries);
	}
}