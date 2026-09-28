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
                TradingMode.SPOT, StrategyConfigDto.spotDefaults(), "SPOT_MORNING_PLAN");
        seedIfAbsent("EMA + RSI Futures", "Futures LONG/SHORT strategy using EMA+RSI momentum on USDT-M perpetuals",
                TradingMode.FUTURES, StrategyConfigDto.futuresDefaults(), "FUTURES_MOMENTUM");
        seedIfAbsent("Trend Pullback", "HTF trend + pullback continuation (LONG-only SPOT). "
                        + "Enters on a confirmed lower-timeframe reversal inside the EMA pullback zone.",
                TradingMode.SPOT, StrategyConfigDto.trendPullbackDefaults(),
                com.shyblack.cryptosignals.dto.strategy.TrendPullbackConfig.ENGINE_KEY);
        seedIfAbsent("EMA Trend Following", "HTF EMA trend regime + entry EMA transition (LONG-only SPOT). "
                        + "Buys a confirmed EMA20/EMA50 bullish transition with momentum/volume/volatility confirmation.",
                TradingMode.SPOT, StrategyConfigDto.emaTrendFollowingDefaults(),
                com.shyblack.cryptosignals.dto.strategy.EMATrendFollowingConfig.ENGINE_KEY);
        log.info("[StrategySeeder] System strategies initialized");
    }

    private void seedIfAbsent(String name, String description, TradingMode mode,
            StrategyConfigDto config, String engineKey) {
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
            s.setEngineKey(engineKey);
            s.setConfigJson(objectMapper.writeValueAsString(config));
            strategyRepository.save(s);
            log.info("[StrategySeeder] Seeded SYSTEM strategy: {} ({}) engine={}", name, mode, engineKey);
        } catch (Exception ex) {
            log.error("[StrategySeeder] Failed to seed {}: {}", name, ex.getMessage(), ex);
        }
    }
}
