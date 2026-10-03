package com.shyblack.cryptosignals.dto.paper;

import com.shyblack.cryptosignals.entity.enums.PaperCapitalEventType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One audited capital movement on a paper account. */
public record PaperCapitalEventResponse(
		UUID id,
		PaperCapitalEventType type,
		BigDecimal amount,
		BigDecimal previousBalance,
		BigDecimal newBalance,
		String reason,
		Instant createdAt
) {}
