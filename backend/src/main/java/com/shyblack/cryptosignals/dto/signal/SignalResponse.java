package com.shyblack.cryptosignals.dto.signal;

import com.shyblack.cryptosignals.entity.enums.EntryType;
import com.shyblack.cryptosignals.entity.enums.MarketRegime;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.SignalGrade;
import com.shyblack.cryptosignals.entity.enums.SignalStatus;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SignalResponse(
        UUID id,
        String symbol,
        SignalStatus status,
        PositionSide side,
        Integer confidence,
        BigDecimal entryPrice,
        BigDecimal targetPrice,
        BigDecimal stopLoss,
        String strategy,
        BigDecimal strategyWinRate,
        BigDecimal suggestedRiskPercent,
        Instant closedAt,
        String technicalSummary,
        String disclaimer,
        Instant createdAt,
        // Spot Morning Plan fields
        Integer score,
        SignalGrade signalGrade,
        EntryType entryType,
        TradingMode tradingMode,
        MarketRegime marketRegime,
        BigDecimal targetPrice2,
        BigDecimal targetPrice3,
        BigDecimal riskReward
) {
}
