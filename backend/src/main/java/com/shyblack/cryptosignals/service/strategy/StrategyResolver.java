package com.shyblack.cryptosignals.service.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shyblack.cryptosignals.dto.strategy.StrategyConfigDto;
import com.shyblack.cryptosignals.entity.TradingStrategy;
import com.shyblack.cryptosignals.entity.enums.StrategyStatus;
import com.shyblack.cryptosignals.entity.enums.StrategyType;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.repository.TradingStrategyRepository;
import com.shyblack.cryptosignals.repository.UserStrategySelectionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StrategyResolver {

    private static final Logger log = LoggerFactory.getLogger(StrategyResolver.class);

    private final UserStrategySelectionRepository selectionRepository;
    private final TradingStrategyRepository strategyRepository;
    private final ObjectMapper objectMapper;

    /**
     * Resolves the globally active strategy for signal generation.
     * Checks the most recently updated UserStrategySelection for the mode;
     * falls back to the system default strategy if none set.
     */
    public Optional<TradingStrategy> resolveActive(TradingMode mode) {
        // Most recently updated user selection for this mode
        var selections = selectionRepository.findByTradingModeOrderByUpdatedAtDesc(mode);
        if (!selections.isEmpty()) {
            TradingStrategy strategy = selections.get(0).getStrategy();
            if (strategy.getStatus() == StrategyStatus.ACTIVE) {
                return Optional.of(strategy);
            }
            log.info("[StrategyResolver] Selected strategy {} is INACTIVE for mode {}", strategy.getName(), mode);
            return Optional.empty();
        }
        // Fall back to system default
        var systemStrategies = strategyRepository.findByTradingModeAndStrategyType(mode, StrategyType.SYSTEM);
        return systemStrategies.stream()
                .filter(s -> s.getStatus() == StrategyStatus.ACTIVE)
                .findFirst();
    }

    public StrategyConfigDto parseConfig(TradingStrategy strategy) {
        if (strategy.getConfigJson() == null) {
            return strategy.getTradingMode() == TradingMode.FUTURES
                    ? StrategyConfigDto.futuresDefaults()
                    : StrategyConfigDto.spotDefaults();
        }
        try {
            return objectMapper.readValue(strategy.getConfigJson(), StrategyConfigDto.class);
        } catch (Exception ex) {
            log.warn("[StrategyResolver] Failed to parse config for strategy {}, using defaults: {}",
                    strategy.getId(), ex.getMessage());
            return strategy.getTradingMode() == TradingMode.FUTURES
                    ? StrategyConfigDto.futuresDefaults()
                    : StrategyConfigDto.spotDefaults();
        }
    }
}
