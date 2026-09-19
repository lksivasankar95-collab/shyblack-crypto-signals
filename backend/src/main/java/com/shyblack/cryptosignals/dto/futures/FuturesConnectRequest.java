package com.shyblack.cryptosignals.dto.futures;

import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import jakarta.validation.constraints.NotNull;

public record FuturesConnectRequest(@NotNull ExchangeName exchange) {}
