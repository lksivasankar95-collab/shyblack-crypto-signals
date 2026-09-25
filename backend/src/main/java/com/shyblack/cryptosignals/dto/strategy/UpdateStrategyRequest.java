package com.shyblack.cryptosignals.dto.strategy;

import com.shyblack.cryptosignals.entity.enums.StrategyStatus;

public record UpdateStrategyRequest(
        String name,
        String description,
        StrategyStatus status,
        StrategyConfigDto config
) {}
