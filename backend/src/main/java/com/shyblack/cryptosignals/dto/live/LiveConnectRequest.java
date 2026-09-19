package com.shyblack.cryptosignals.dto.live;

import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import jakarta.validation.constraints.NotNull;

public record LiveConnectRequest(
		@NotNull ExchangeName exchange
) {}
