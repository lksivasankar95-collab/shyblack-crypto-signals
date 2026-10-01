package com.shyblack.cryptosignals.service.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shyblack.cryptosignals.dto.strategy.CreateStrategyRequest;
import com.shyblack.cryptosignals.dto.strategy.StrategyConfigDto;
import com.shyblack.cryptosignals.dto.strategy.TradingStrategyResponse;
import com.shyblack.cryptosignals.entity.TradingStrategy;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.exception.BadRequestException;
import com.shyblack.cryptosignals.repository.TradingStrategyRepository;
import com.shyblack.cryptosignals.repository.UserStrategySelectionRepository;
import com.shyblack.cryptosignals.security.UserPrincipal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * DEFECT 1 — a USER strategy can carry engineKey=TREND_PULLBACK plus its
 * config, while legacy strategies stay engineKey=null and invalid engines are
 * rejected.
 */
class TradingStrategyServiceTest {

    private TradingStrategyRepository strategyRepository;
    private UserStrategySelectionRepository selectionRepository;
    private StrategyResolver resolver;
    private TradingStrategyService service;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        strategyRepository = mock(TradingStrategyRepository.class);
        selectionRepository = mock(UserStrategySelectionRepository.class);
        resolver = mock(StrategyResolver.class);
        service = new TradingStrategyService(strategyRepository, selectionRepository, resolver,
                new ObjectMapper());
        principal = mock(UserPrincipal.class);
        when(principal.getId()).thenReturn(UUID.randomUUID());
        lenient().when(strategyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private CreateStrategyRequest request(TradingMode mode, String engineKey,
            StrategyConfigDto config) {
        return new CreateStrategyRequest("Trend Pullback", "desc", mode, config, engineKey);
    }

    @Test
    void create_persistsEngineKeyAndPullbackConfig() {
        CreateStrategyRequest req = request(TradingMode.SPOT, "TREND_PULLBACK",
                StrategyConfigDto.trendPullbackDefaults());

        TradingStrategyResponse resp = service.create(principal, req);

        ArgumentCaptor<TradingStrategy> captor = ArgumentCaptor.forClass(TradingStrategy.class);
        verify(strategyRepository).save(captor.capture());
        assertThat(captor.getValue().getEngineKey()).isEqualTo("TREND_PULLBACK");
        assertThat(captor.getValue().getConfigJson()).contains("pullback");
        assertThat(captor.getValue().getStrategyType().name()).isEqualTo("USER");
        assertThat(captor.getValue().isEditable()).isTrue();
        assertThat(resp.engineKey()).isEqualTo("TREND_PULLBACK");
    }

    @Test
    void create_legacyStrategy_keepsNullEngineKey() {
        service.create(principal, request(TradingMode.SPOT, null,
                StrategyConfigDto.spotDefaults()));
        ArgumentCaptor<TradingStrategy> captor = ArgumentCaptor.forClass(TradingStrategy.class);
        verify(strategyRepository).save(captor.capture());
        assertThat(captor.getValue().getEngineKey()).isNull();
    }

    @Test
    void create_rejectsUnknownEngineKey() {
        assertThatThrownBy(() -> service.create(principal,
                request(TradingMode.SPOT, "SOME_RANDOM_ENGINE", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unknown strategy engine");
    }

    @Test
    void create_rejectsTrendPullbackForFutures() {
        assertThatThrownBy(() -> service.create(principal,
                request(TradingMode.FUTURES, "TREND_PULLBACK", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("SPOT");
    }

    @Test
    void create_persistsNfmFuturesEngineKeyAndConfig() {
        CreateStrategyRequest req = new CreateStrategyRequest("NFM", "desc", TradingMode.FUTURES,
                StrategyConfigDto.nfmFuturesDefaults(), "NFM_FUTURES");

        TradingStrategyResponse resp = service.create(principal, req);

        ArgumentCaptor<TradingStrategy> captor = ArgumentCaptor.forClass(TradingStrategy.class);
        verify(strategyRepository).save(captor.capture());
        assertThat(captor.getValue().getEngineKey()).isEqualTo("NFM_FUTURES");
        assertThat(captor.getValue().getTradingMode()).isEqualTo(TradingMode.FUTURES);
        // The NFM config block is stored under "nfmFutures".
        assertThat(captor.getValue().getConfigJson()).contains("nfmFutures");
        assertThat(resp.engineKey()).isEqualTo("NFM_FUTURES");
    }

    @Test
    void create_rejectsNfmFuturesForSpot() {
        assertThatThrownBy(() -> service.create(principal,
                new CreateStrategyRequest("NFM", "desc", TradingMode.SPOT,
                        StrategyConfigDto.spotDefaults(), "NFM_FUTURES")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("FUTURES");
    }
}
