package com.shyblack.cryptosignals.news;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.enums.NewsCategory;
import org.junit.jupiter.api.Test;

class CategoryClassifierTest {

	private final CategoryClassifier classifier = new CategoryClassifier();

	@Test
	void trustsProviderCategoryWhenPresent() {
		assertThat(classifier.classify("Markets", "", ""))
				.isEqualTo(NewsCategory.MARKET);
		assertThat(classifier.classify("Technology", "", ""))
				.isEqualTo(NewsCategory.TECHNOLOGY);
	}

	@Test
	void keywordClassifiesEtfArticle() {
		assertThat(classifier.classify(null, "Spot Ethereum ETF approved by SEC", ""))
				.isEqualTo(NewsCategory.ETF);
	}

	@Test
	void keywordClassifiesHackArticle() {
		assertThat(classifier.classify(null, "Crypto exchange hacked, millions drained", ""))
				.isEqualTo(NewsCategory.HACK);
	}

	@Test
	void keywordClassifiesRegulationArticle() {
		assertThat(classifier.classify(null, "US Congress considers new crypto bill", ""))
				.isEqualTo(NewsCategory.REGULATION);
	}

	@Test
	void noEvidenceFallsBackToOther() {
		assertThat(classifier.classify(null, "A quiet day on the beach", ""))
				.isEqualTo(NewsCategory.OTHER);
	}
}