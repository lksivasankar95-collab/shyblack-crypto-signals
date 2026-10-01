package com.shyblack.cryptosignals.service.nfm;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.enums.NewsCategory;
import com.shyblack.cryptosignals.entity.enums.NewsEventStage;
import com.shyblack.cryptosignals.entity.enums.NewsEventType;
import org.junit.jupiter.api.Test;

class NewsEventTypeClassifierTest {

	private final NewsEventTypeClassifier classifier = new NewsEventTypeClassifier();

	@Test
	void etfClassification_distinguishesBtcEthAndFlow() {
		assertThat(classifier.classify(NewsCategory.ETF, "Bitcoin ETF approved", null))
				.isEqualTo(NewsEventType.BTC_ETF);
		assertThat(classifier.classify(NewsCategory.ETF, "Ethereum ETF sees inflows", null))
				.isEqualTo(NewsEventType.ETH_ETF);
		assertThat(classifier.classify(NewsCategory.ETF, "Spot ETF flows turn positive", null))
				.isEqualTo(NewsEventType.ETF_FLOW);
	}

	@Test
	void securityClassification_byKeyword() {
		assertThat(classifier.classify(NewsCategory.HACK, "Major exchange hacked for $50m", null))
				.isEqualTo(NewsEventType.EXCHANGE_HACK);
		assertThat(classifier.classify(NewsCategory.HACK, "Cross-chain bridge exploit", null))
				.isEqualTo(NewsEventType.BRIDGE_HACK);
		assertThat(classifier.classify(NewsCategory.EXPLOIT, "Protocol drained via bug", null))
				.isEqualTo(NewsEventType.PROTOCOL_HACK);
	}

	@Test
	void regulationClassification() {
		assertThat(classifier.classify(NewsCategory.REGULATION, "SEC files suit", null))
				.isEqualTo(NewsEventType.SEC);
		assertThat(classifier.classify(NewsCategory.REGULATION, "CFTC issues guidance", null))
				.isEqualTo(NewsEventType.CFTC);
	}

	@Test
	void macroClassification() {
		assertThat(classifier.classify(NewsCategory.MACRO, "CPI comes in hotter than expected", null))
				.isEqualTo(NewsEventType.CPI);
		assertThat(classifier.classify(NewsCategory.MACRO, "FOMC holds rates", null))
				.isEqualTo(NewsEventType.FOMC);
		assertThat(classifier.classify(NewsCategory.MACRO, "Non-farm payrolls beat", null))
				.isEqualTo(NewsEventType.NFP);
	}

	@Test
	void marketShockClassification() {
		assertThat(classifier.classify(NewsCategory.MARKET, "Cascade of liquidations hits market", null))
				.isEqualTo(NewsEventType.LIQUIDATION_CASCADE);
	}

	@Test
	void stageInference() {
		assertThat(classifier.inferStage("SEC approves the filing", null))
				.isEqualTo(NewsEventStage.APPROVAL);
		assertThat(classifier.inferStage("ETF launches next week", null))
				.isEqualTo(NewsEventStage.LAUNCH);
		assertThat(classifier.inferStage("Unconfirmed rumor about a listing", null))
				.isEqualTo(NewsEventStage.RUMOR);
		assertThat(classifier.inferStage("Bitcoin trades sideways", null))
				.isEqualTo(NewsEventStage.REPORT);
	}
}
