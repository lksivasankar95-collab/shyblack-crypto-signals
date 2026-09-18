package com.shyblack.cryptosignals.news;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.enums.NewsSentiment;
import org.junit.jupiter.api.Test;

class SentimentAnalyzerTest {

	private final SentimentAnalyzer analyzer = new SentimentAnalyzer();

	@Test
	void positiveEtfApprovalScoresPositive() {
		SentimentAnalyzer.SentimentResult result =
				analyzer.analyze("Bitcoin ETF approved, record inflow expected", "");
		assertThat(result.sentiment()).isEqualTo(NewsSentiment.POSITIVE);
		assertThat(result.score()).isGreaterThanOrEqualTo(0.5);
		assertThat(result.confidence()).isGreaterThan(0.3);
	}

	@Test
	void negativeHackScoresNegative() {
		SentimentAnalyzer.SentimentResult result =
				analyzer.analyze("Exchange hacked, user funds stolen", "");
		assertThat(result.sentiment()).isEqualTo(NewsSentiment.NEGATIVE);
		assertThat(result.score()).isLessThanOrEqualTo(-0.5);
	}

	@Test
	void rejectionPhraseIsNegative() {
		SentimentAnalyzer.SentimentResult result =
				analyzer.analyze("SEC rejects Bitcoin ETF application", "");
		assertThat(result.sentiment()).isEqualTo(NewsSentiment.NEGATIVE);
	}

	@Test
	void negationFlipsPositiveWord() {
		SentimentAnalyzer.SentimentResult result =
				analyzer.analyze("Company failed to gain approval for merger", "");
		assertThat(result.sentiment()).isEqualTo(NewsSentiment.NEGATIVE);
	}

	@Test
	void neutralTextIsNeutralWithLowConfidence() {
		SentimentAnalyzer.SentimentResult result =
				analyzer.analyze("Developer writes documentation for protocol", "");
		assertThat(result.sentiment()).isEqualTo(NewsSentiment.NEUTRAL);
		assertThat(result.confidence()).isLessThanOrEqualTo(0.35);
	}

	@Test
	void scoreStaysBoundedInUnitInterval() {
		SentimentAnalyzer.SentimentResult positive =
				analyzer.analyze("approve approve approve approve approve approve", "");
		assertThat(positive.score()).isLessThanOrEqualTo(1.0);
		SentimentAnalyzer.SentimentResult negative =
				analyzer.analyze("hack hack hack hack hack hack", "");
		assertThat(negative.score()).isGreaterThanOrEqualTo(-1.0);
	}
}