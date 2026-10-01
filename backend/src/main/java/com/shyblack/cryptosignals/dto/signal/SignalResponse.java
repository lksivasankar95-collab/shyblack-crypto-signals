package com.shyblack.cryptosignals.dto.signal;

import com.shyblack.cryptosignals.entity.enums.EntryType;
import com.shyblack.cryptosignals.entity.enums.MarketRegime;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.SignalGrade;
import com.shyblack.cryptosignals.entity.enums.SignalStatus;
import com.shyblack.cryptosignals.entity.SignalNfmContext;
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
        BigDecimal riskReward,
        // Strategy fields
        UUID strategyId,
        Integer strategyVersion,
        String setupId,
        // NFM (News Flow Momentum) event + derivatives context; null for non-NFM signals
        NfmContextResponse nfmContext
) {
    /** NFM-specific event / derivatives context surfaced in Signal Details. */
    public record NfmContextResponse(
            UUID newsEventId,
            String eventType,
            String eventCategory,
            String eventStage,
            String sourceTier,
            String source,
            Instant eventTime,
            BigDecimal expectedValue,
            BigDecimal actualValue,
            BigDecimal surpriseValue,
            String surpriseDirection,
            BigDecimal priceReactionPct,
            BigDecimal volumeMultiplier,
            BigDecimal openInterestChangePct,
            String fundingState,
            String liquidationState,
            Integer eventConfluenceScore,
            String marketInterpretation,
            String configVersion
    ) {
        public static NfmContextResponse from(SignalNfmContext c) {
            if (c == null) {
                return null;
            }
            return new NfmContextResponse(
                    c.getNewsEventId(), c.getEventType(), c.getEventCategory(), c.getEventStage(),
                    c.getSourceTier(), c.getSource(), c.getEventTime(),
                    c.getExpectedValue(), c.getActualValue(), c.getSurpriseValue(), c.getSurpriseDirection(),
                    c.getPriceReactionPct(), c.getVolumeMultiplier(), c.getOpenInterestChangePct(),
                    c.getFundingState(), c.getLiquidationState(), c.getEventConfluenceScore(),
                    c.getMarketInterpretation(), c.getConfigVersion());
        }
    }
}
