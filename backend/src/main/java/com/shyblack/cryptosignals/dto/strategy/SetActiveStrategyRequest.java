package com.shyblack.cryptosignals.dto.strategy;

import com.shyblack.cryptosignals.entity.enums.TradingMode;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SetActiveStrategyRequest(
        @NotNull TradingMode tradingMode,
        @NotNull UUID strategyId
) {}
