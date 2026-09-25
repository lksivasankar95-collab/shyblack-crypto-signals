package com.shyblack.cryptosignals.dto.strategy;

import com.shyblack.cryptosignals.entity.enums.StrategyStatus;
import com.shyblack.cryptosignals.entity.enums.StrategyType;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import java.time.Instant;
import java.util.UUID;

public record TradingStrategyResponse(
        UUID id,
        String name,
        String description,
        TradingMode tradingMode,
        StrategyType strategyType,
        int version,
        StrategyStatus status,
        boolean deletable,
        boolean editable,
        StrategyConfigDto config,
        Instant createdAt,
        Instant updatedAt
) {}
