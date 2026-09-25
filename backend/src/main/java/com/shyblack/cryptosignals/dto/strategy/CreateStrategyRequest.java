package com.shyblack.cryptosignals.dto.strategy;

import com.shyblack.cryptosignals.entity.enums.TradingMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateStrategyRequest(
        @NotBlank String name,
        String description,
        @NotNull TradingMode tradingMode,
        StrategyConfigDto config
) {}
