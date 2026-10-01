package com.shyblack.cryptosignals.dto.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class NfmFuturesConfigTest {

	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	void defaultsAreSane() {
		NfmFuturesConfig cfg = NfmFuturesConfig.defaults();
		assertThat(NfmFuturesConfig.ENGINE_KEY).isEqualTo("NFM_FUTURES");
		assertThat(cfg.getMinimumScore()).isEqualTo(65);
		assertThat(cfg.getWeightEventQuality() + cfg.getWeightSurprise() + cfg.getWeightAssetRelevance()
				+ cfg.getWeightPriceConfirmation() + cfg.getWeightVolume() + cfg.getWeightOpenInterest()
				+ cfg.getWeightFunding() + cfg.getWeightLiquidation() + cfg.getWeightMarketRegime()
				+ cfg.getWeightCrossAsset()).isEqualTo(100);
	}

	@Test
	void fromJson_readsNestedBlockAndFillsDefaults() {
		String json = "{\"nfmFutures\":{\"minimumScore\":80,\"allowShort\":false}}";
		NfmFuturesConfig cfg = NfmFuturesConfig.fromJson(mapper, json);
		assertThat(cfg.getMinimumScore()).isEqualTo(80);
		assertThat(cfg.isAllowShort()).isFalse();
		assertThat(cfg.getReactionTimeframe()).isEqualTo("5M");
	}

	@Test
	void fromJson_blankReturnsDefaults() {
		assertThat(NfmFuturesConfig.fromJson(mapper, "").getMinimumScore()).isEqualTo(65);
		assertThat(NfmFuturesConfig.fromJson(mapper, null).getMinimumScore()).isEqualTo(65);
	}
}
