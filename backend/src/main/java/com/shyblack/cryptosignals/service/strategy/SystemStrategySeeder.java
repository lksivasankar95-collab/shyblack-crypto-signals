package com.shyblack.cryptosignals.service.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shyblack.cryptosignals.dto.strategy.StrategyConfigDto;
import com.shyblack.cryptosignals.entity.TradingStrategy;
import com.shyblack.cryptosignals.entity.enums.StrategyStatus;
import com.shyblack.cryptosignals.entity.enums.StrategyType;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.repository.TradingStrategyRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SystemStrategySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SystemStrategySeeder.class);

    private final TradingStrategyRepository strategyRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedIfAbsent("EMA + RSI", "Spot Morning Plan — multi-timeframe EMA+RSI momentum strategy",
                TradingMode.SPOT, StrategyConfigDto.spotDefaults());
        seedIfAbsent("EMA + RSI Futures", "Futures LONG/SHORT strategy using EMA+RSI momentum on USDT-M perpetuals",
                TradingMode.FUTURES, StrategyConfigDto.futuresDefaults());
        log.info("[StrategySeeder] System strategies initialized");
    }

    private void seedIfAbsent(String name, String description, TradingMode mode, StrategyConfigDto config) {
        if (strategyRepository.existsByNameAndTradingModeAndStrategyType(name, mode, StrategyType.SYSTEM)) {
            return;
        }
        try {
            TradingStrategy s = new TradingStrategy();
            s.setName(name);
            s.setDescription(description);
            s.setTradingMode(mode);
            s.setStrategyType(StrategyType.SYSTEM);
            s.setOwnerId(null);
            s.setVersion(1);
            s.setStatus(StrategyStatus.ACTIVE);
            s.setDeletable(false);
            s.setEditable(false);
            s.setConfigJson(objectMapper.writeValueAsString(config));
            strategyRepository.save(s);
            log.info("[StrategySeeder] Seeded SYSTEM strategy: {} ({})", name, mode);
        } catch (Exception ex) {
            log.error("[StrategySeeder] Failed to seed {}: {}", name, ex.getMessage(), ex);
        }
    }
}
