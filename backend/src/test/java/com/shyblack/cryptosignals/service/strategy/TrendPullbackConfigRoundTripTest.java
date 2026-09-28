package com.shyblack.cryptosignals.service.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shyblack.cryptosignals.dto.strategy.StrategyConfigDto;
import com.shyblack.cryptosignals.dto.strategy.TrendPullbackConfig;
import com.shyblack.cryptosignals.entity.TradingStrategy;
import org.junit.jupiter.api.Test;

/**
 * DEFECT 1 — proves the TrendPullbackConfig round-trips through the existing
 * strategy configuration mechanism (DTO → JSON → StrategyResolver) with every
 * field preserved.
 */
class TrendPullbackConfigRoundTripTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static TrendPullbackConfig perturbed() {
        TrendPullbackConfig c = TrendPullbackConfig.defaults();
        c.setHtf("4H");
        c.setEntryTimeframe("5M");
        c.setEmaFastHtf(30);
        c.setEmaSlowHtf(240);
        c.setAdxPeriod(20);
        c.setMinAdx(25.5);
        c.setRequirePositiveSlope(false);
        c.setSlopeLookback(3);
        c.setPullbackEma(21);
        c.setEntryEma(55);
        c.setZoneMode("EMA20");
        c.setMaxPullbackDistanceAtr(2.25);
        c.setRsiPeriod(9);
        c.setRsiMin(35);
        c.setRsiMax(65);
        c.setRequireRecovery(false);
        c.setVolumeFilterEnabled(false);
        c.setVolumeSmaPeriod(30);
        c.setMinVolumeMultiplier(1.7);
        c.setAtrPeriod(21);
        c.setSlAtrBuffer(0.35);
        c.setMaxSlAtr(3.0);
        c.setMinRR(2.0);
        c.setTp1R(1.5);
        c.setTp2R(2.5);
        c.setTp3R(4.5);
        c.setMaxSetupCandles(20);
        c.setCooldownCandles(7);
        c.setCandleConfirmationEnabled(false);
        c.setMinLowerWickRatio(0.4);
        c.setSwingLookback(4);
        c.setInvalidationBodyAtr(1.3);
        c.setInvalidationVolumeMult(1.9);
        c.setMinimumScore(80);
        c.setWeightTrend(25);
        c.setWeightAdx(5);
        c.setWeightPullback(30);
        c.setWeightRsi(5);
        c.setWeightVolume(5);
        c.setWeightStructure(20);
        c.setWeightRiskReward(10);
        c.setHtfCandleCount(333);
        c.setEntryCandleCount(222);
        return c;
    }

    @Test
    void fullStrategyConfig_roundTripsEveryPullbackField() throws Exception {
        TrendPullbackConfig original = perturbed();

        StrategyConfigDto dto = new StrategyConfigDto(
                StrategyConfigDto.IndicatorConfig.defaults(),
                StrategyConfigDto.ScoringConfig.defaults(),
                StrategyConfigDto.FilterConfig.defaults(),
                StrategyConfigDto.EntryConfig.defaults(),
                null,
                original,
                null);

        TradingStrategy strategy = new TradingStrategy();
        strategy.setConfigJson(MAPPER.writeValueAsString(dto));

        StrategyResolver resolver = new StrategyResolver(null, null, MAPPER);
        TrendPullbackConfig resolved = resolver.parseTrendPullbackConfig(strategy);

        // Structural equality over every field (same serialized form).
        assertThat(MAPPER.writeValueAsString(resolved))
                .isEqualTo(MAPPER.writeValueAsString(original));

        // Spot-checks across each config section.
        assertThat(resolved.getHtf()).isEqualTo("4H");
        assertThat(resolved.getZoneMode()).isEqualTo("EMA20");
        assertThat(resolved.getMinAdx()).isEqualTo(25.5);
        assertThat(resolved.isRequireRecovery()).isFalse();
        assertThat(resolved.isVolumeFilterEnabled()).isFalse();
        assertThat(resolved.getTp3R()).isEqualTo(4.5);
        assertThat(resolved.getWeightStructure()).isEqualTo(20);
        assertThat(resolved.getMinimumScore()).isEqualTo(80);
        assertThat(resolved.getEntryCandleCount()).isEqualTo(222);
    }

    @Test
    void defaults_surviveRoundTrip() throws Exception {
        TrendPullbackConfig original = TrendPullbackConfig.defaults();
        String json = MAPPER.writeValueAsString(
                new StrategyConfigDto(null, null, null, null, null, original, null));

        TradingStrategy strategy = new TradingStrategy();
        strategy.setConfigJson(json);
        TrendPullbackConfig resolved = new StrategyResolver(null, null, MAPPER)
                .parseTrendPullbackConfig(strategy);

        assertThat(MAPPER.writeValueAsString(resolved))
                .isEqualTo(MAPPER.writeValueAsString(original));
    }

    @Test
    void bareObjectOrFullConfig_bothParse() {
        TrendPullbackConfig bare = TrendPullbackConfig.fromJson(MAPPER, "{\"minRR\":2.75}");
        assertThat(bare.getMinRR()).isEqualTo(2.75);
        assertThat(bare.getHtf()).isEqualTo("1H"); // default filled

        TrendPullbackConfig nested = TrendPullbackConfig.fromJson(
                MAPPER, "{\"pullback\":{\"minRR\":3.25}}");
        assertThat(nested.getMinRR()).isEqualTo(3.25);
    }
}
