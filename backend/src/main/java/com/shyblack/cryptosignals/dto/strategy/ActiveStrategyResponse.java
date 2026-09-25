package com.shyblack.cryptosignals.dto.strategy;

import com.shyblack.cryptosignals.entity.enums.TradingMode;
import java.util.UUID;

public record ActiveStrategyResponse(
        TradingMode tradingMode,
        UUID strategyId,
        String strategyName,
        int strategyVersion,
        boolean hasActiveStrategy
) {}
