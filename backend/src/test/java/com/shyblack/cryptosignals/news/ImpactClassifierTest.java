package com.shyblack.cryptosignals.news;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.enums.NewsCategory;
import com.shyblack.cryptosignals.entity.enums.NewsImpact;
import org.junit.jupiter.api.Test;

class ImpactClassifierTest {

	private final ImpactClassifier classifier = new ImpactClassifier();

	@Test
	void hackWithStrongSentimentIsCritical() {
		NewsImpact impact = classifier.classify(NewsCategory.HACK, -0.9, 4,
				"Major exchange hacked, millions collapse");
		assertThat(impact).isEqualTo(NewsImpact.CRITICAL);
	}

	@Test
	void majorRegulatoryNewsIsHigh() {
		NewsImpact impact = classifier.classify(NewsCategory.REGULATION, -0.5, 0,
				"Congress weighs drastic crypto ban");
		assertThat(impact).isEqualTo(NewsImpact.HIGH);
	}

	@Test
	void routineTechnologyNewsIsLowOrMedium() {
		NewsImpact impact = classifier.classify(NewsCategory.TECHNOLOGY, 0.1, 0,
				"Protocol releases v2 documentation");
		assertThat(impact).isEqualTo(NewsImpact.LOW);
	}

	@Test
	void neutralPartnershipIsLow() {
		NewsImpact impact = classifier.classify(NewsCategory.PARTNERSHIP, 0.2, 1,
				"Crypto firm announces integration");
		assertThat(impact).isEqualTo(NewsImpact.LOW);
	}

	@Test
	void defaultIsLow() {
		NewsImpact impact = classifier.classify(NewsCategory.OTHER, 0.0, 0, "");
		assertThat(impact).isEqualTo(NewsImpact.LOW);
	}
}