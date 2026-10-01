package com.shyblack.cryptosignals.service.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shyblack.cryptosignals.dto.strategy.EMATrendFollowingConfig;
import com.shyblack.cryptosignals.dto.strategy.StrategyConfigDto;
import com.shyblack.cryptosignals.entity.TradingStrategy;
import org.junit.jupiter.api.Test;

class EmaTrendFollowingConfigRoundTripTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static EMATrendFollowingConfig perturbed() {
        EMATrendFollowingConfig c = EMATrendFollowingConfig.defaults();
        c.setHtfTimeframe("4H");
        c.setEntryTimeframe("5M");
        c.setHtfFastEma(34);
        c.setHtfSlowEma(144);
        c.setTrendSlopeLookback(3);
        c.setEntryFastEma(9);
        c.setEntrySlowEma(21);
        c.setMinimumEmaSeparationPct(0.25);
        c.setRsiFilterEnabled(false);
        c.setRsiPeriod(9);
        c.setMinimumRsiForLong(45);
        c.setMaximumRsiForLong(65);
        c.setVolumeFilterEnabled(false);
        c.setVolumePeriod(30);
        c.setMinimumVolumeRatio(1.7);
        c.setAtrFilterEnabled(false);
        c.setAtrPeriod(21);
        c.setMinimumAtrPct(0.5);
        c.setSlAtrBuffer(2.0);
        c.setMinRR(2.0);
        c.setTp1R(2.0);
        c.setTp2R(3.0);
        c.setTp3R(5.0);
        c.setCooldownCandles(6);
        c.setMinimumScore(75);
        c.setWeightTrendAlignment(40);
        c.setWeightEmaTransition(15);
        c.setWeightPriceConfirmation(15);
        c.setWeightMomentum(10);
        c.setWeightVolume(10);
        c.setWeightVolatility(10);
        return c;
    }

    @Test
    void fullStrategyConfig_roundTripsEveryEmaField() throws Exception {
        EMATrendFollowingConfig original = perturbed();
        StrategyConfigDto dto = new StrategyConfigDto(
                null, null, null, null, null, null, original, null);

        TradingStrategy strategy = new TradingStrategy();
        strategy.setConfigJson(MAPPER.writeValueAsString(dto));

        EMATrendFollowingConfig resolved = new StrategyResolver(null, null, MAPPER)
                .parseEmaTrendFollowingConfig(strategy);

        assertThat(MAPPER.writeValueAsString(resolved))
                .isEqualTo(MAPPER.writeValueAsString(original));
        assertThat(resolved.getHtfFastEma()).isEqualTo(34);
        assertThat(resolved.isRsiFilterEnabled()).isFalse();
        assertThat(resolved.getMinimumScore()).isEqualTo(75);
        assertThat(resolved.getCooldownCandles()).isEqualTo(6);
    }

    @Test
    void defaults_surviveRoundTrip() throws Exception {
        EMATrendFollowingConfig original = EMATrendFollowingConfig.defaults();
        String json = MAPPER.writeValueAsString(StrategyConfigDto.emaTrendFollowingDefaults());
        TradingStrategy strategy = new TradingStrategy();
        strategy.setConfigJson(json);
        EMATrendFollowingConfig resolved = new StrategyResolver(null, null, MAPPER)
                .parseEmaTrendFollowingConfig(strategy);
        assertThat(MAPPER.writeValueAsString(resolved))
                .isEqualTo(MAPPER.writeValueAsString(original));
    }

    @Test
    void bareObjectAndNested_bothParse() {
        assertThat(EMATrendFollowingConfig.fromJson(MAPPER, "{\"minimumScore\":80}").getMinimumScore())
                .isEqualTo(80);
        assertThat(EMATrendFollowingConfig.fromJson(MAPPER, "{\"emaTrendFollowing\":{\"minRR\":3.0}}").getMinRR())
                .isEqualTo(3.0);
    }
}
