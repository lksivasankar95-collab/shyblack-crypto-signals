package com.shyblack.cryptosignals.signal.nfm;

import com.shyblack.cryptosignals.entity.enums.FundingState;
import com.shyblack.cryptosignals.entity.enums.LiquidationState;
import com.shyblack.cryptosignals.entity.enums.NfmAction;
import com.shyblack.cryptosignals.entity.enums.NfmGrade;
import java.math.BigDecimal;

/**
 * NFM engine output (spec §17–§23). {@code actionable} is true only for a
 * confirmed, risk-acceptable directional signal — {@code NO_TRADE} and
 * {@code WAIT_CONFIRMATION} are valid results.
 */
public record NfmAssessment(
		boolean actionable,
		NfmAction action,
		NfmGrade grade,
		int score,
		BigDecimal entry,
		BigDecimal stopLoss,
		BigDecimal tp1,
		BigDecimal tp2,
		BigDecimal tp3,
		BigDecimal riskReward,
		BigDecimal priceReactionPct,
		BigDecimal preEventReturnPct,
		BigDecimal volumeMultiplier,
		BigDecimal oiChangePct,
		FundingState fundingState,
		LiquidationState liquidationState,
		String reason,
		String setupId
) {
}
