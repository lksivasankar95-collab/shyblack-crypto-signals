package com.shyblack.cryptosignals.service.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shyblack.cryptosignals.dto.strategy.StrategyConfigDto;
import com.shyblack.cryptosignals.dto.strategy.TrendPullbackConfig;
import com.shyblack.cryptosignals.entity.TradingStrategy;
import com.shyblack.cryptosignals.entity.UserStrategySelection;
import com.shyblack.cryptosignals.entity.enums.StrategyStatus;
import com.shyblack.cryptosignals.entity.enums.StrategyType;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.repository.TradingStrategyRepository;
import com.shyblack.cryptosignals.repository.UserStrategySelectionRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Live path item 1 — an ACTIVE USER strategy with engineKey=TREND_PULLBACK is
 * resolved by {@link StrategyResolver#resolveActive} and its persisted config
 * is the one reconstructed for the live service.
 */
class StrategyResolverTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private UserStrategySelectionRepository selectionRepository;
    private TradingStrategyRepository strategyRepository;
    private StrategyResolver resolver;

    @BeforeEach
    void setUp() {
        selectionRepository = mock(UserStrategySelectionRepository.class);
        strategyRepository = mock(TradingStrategyRepository.class);
        resolver = new StrategyResolver(selectionRepository, strategyRepository, MAPPER);
    }

    private TradingStrategy userTrendPullback(StrategyStatus status) {
        TrendPullbackConfig cfg = TrendPullbackConfig.defaults();
        cfg.setMinRR(2.5);
        TradingStrategy s = new TradingStrategy();
        s.setName("My Trend Pullback");
        s.setTradingMode(TradingMode.SPOT);
        s.setStrategyType(StrategyType.USER);
        s.setStatus(status);
        s.setEngineKey("TREND_PULLBACK");
        try {
            s.setConfigJson(MAPPER.writeValueAsString(
                    new StrategyConfigDto(null, null, null, null, null, cfg, null, null)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        return s;
    }

    private UserStrategySelection selection(TradingStrategy strategy) {
        UserStrategySelection sel = new UserStrategySelection();
        sel.setTradingMode(TradingMode.SPOT);
        sel.setStrategy(strategy);
        return sel;
    }

    @Test
    void activeUserSelection_isResolved_andItsConfigIsUsed() {
        TradingStrategy user = userTrendPullback(StrategyStatus.ACTIVE);
        when(selectionRepository.findByTradingModeOrderByUpdatedAtDesc(TradingMode.SPOT))
                .thenReturn(List.of(selection(user)));

        TradingStrategy resolved = resolver.resolveActive(TradingMode.SPOT).orElseThrow();

        assertThat(resolved.getEngineKey()).isEqualTo("TREND_PULLBACK");
        assertThat(resolved.getStrategyType()).isEqualTo(StrategyType.USER);
        // The live service reconstructs the USER's persisted config, not defaults.
        assertThat(resolver.parseTrendPullbackConfig(resolved).getMinRR()).isEqualTo(2.5);
        // A user selection short-circuits the system default lookup.
        verifyNoInteractions(strategyRepository);
    }

    @Test
    void inactiveUserSelection_yieldsNoStrategy() {
        TradingStrategy user = userTrendPullback(StrategyStatus.INACTIVE);
        when(selectionRepository.findByTradingModeOrderByUpdatedAtDesc(TradingMode.SPOT))
                .thenReturn(List.of(selection(user)));

        assertThat(resolver.resolveActive(TradingMode.SPOT)).isEmpty();
    }

    private TradingStrategy userNfmFutures(StrategyStatus status) {
        com.shyblack.cryptosignals.dto.strategy.NfmFuturesConfig cfg =
                com.shyblack.cryptosignals.dto.strategy.NfmFuturesConfig.defaults();
        cfg.setMinimumScore(77);
        TradingStrategy s = new TradingStrategy();
        s.setName("My NFM");
        s.setTradingMode(TradingMode.FUTURES);
        s.setStrategyType(StrategyType.USER);
        s.setStatus(status);
        s.setEngineKey("NFM_FUTURES");
        try {
            s.setConfigJson(MAPPER.writeValueAsString(
                    new StrategyConfigDto(null, null, null, null,
                            StrategyConfigDto.FuturesConfig.defaults(), null, null, cfg)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        return s;
    }

    private UserStrategySelection futuresSelection(TradingStrategy strategy) {
        UserStrategySelection sel = new UserStrategySelection();
        sel.setTradingMode(TradingMode.FUTURES);
        sel.setStrategy(strategy);
        return sel;
    }

    @Test
    void activeNfmUserSelection_isResolvedForFutures_andItsConfigIsUsed() {
        TradingStrategy user = userNfmFutures(StrategyStatus.ACTIVE);
        when(selectionRepository.findByTradingModeOrderByUpdatedAtDesc(TradingMode.FUTURES))
                .thenReturn(List.of(futuresSelection(user)));

        TradingStrategy resolved = resolver.resolveActive(TradingMode.FUTURES).orElseThrow();

        assertThat(resolved.getEngineKey()).isEqualTo("NFM_FUTURES");
        // The live NFM service reconstructs the USER's persisted config.
        assertThat(resolver.parseNfmFuturesConfig(resolved).getMinimumScore()).isEqualTo(77);
        verifyNoInteractions(strategyRepository);
    }

    @Test
    void noUserSelection_fallsBackToActiveSystemStrategy() {
        TradingStrategy inactive = new TradingStrategy();
        inactive.setStatus(StrategyStatus.INACTIVE);
        TradingStrategy active = new TradingStrategy();
        active.setStatus(StrategyStatus.ACTIVE);
        active.setEngineKey(null);
        when(selectionRepository.findByTradingModeOrderByUpdatedAtDesc(TradingMode.SPOT))
                .thenReturn(List.of());
        when(strategyRepository.findByTradingModeAndStrategyType(TradingMode.SPOT, StrategyType.SYSTEM))
                .thenReturn(List.of(inactive, active));

        TradingStrategy resolved = resolver.resolveActive(TradingMode.SPOT).orElseThrow();
        assertThat(resolved).isSameAs(active);
    }
}
