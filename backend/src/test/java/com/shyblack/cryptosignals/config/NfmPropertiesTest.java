package com.shyblack.cryptosignals.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.enums.NewsSourceTier;
import org.junit.jupiter.api.Test;

class NfmPropertiesTest {

	@Test
	void tierFor_mapsKnownSources() {
		NfmProperties props = new NfmProperties(true, 180, null, null, null);
		assertThat(props.tierFor("Federal Reserve")).isEqualTo(NewsSourceTier.TIER_1);
		assertThat(props.tierFor("Reuters")).isEqualTo(NewsSourceTier.TIER_2);
		assertThat(props.tierFor("CoinTelegraph")).isEqualTo(NewsSourceTier.TIER_3);
		assertThat(props.tierFor("random-twitter-bot")).isEqualTo(NewsSourceTier.TIER_4);
		assertThat(props.tierFor(null)).isEqualTo(NewsSourceTier.TIER_4);
	}

	@Test
	void defaultsApplyWhenUnset() {
		NfmProperties props = new NfmProperties(true, 0, null, null, null);
		assertThat(props.eventMaxAgeMinutes()).isEqualTo(180);
		assertThat(props.tier1Sources()).isNotEmpty();
	}
}
