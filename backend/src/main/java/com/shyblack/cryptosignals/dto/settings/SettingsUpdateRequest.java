package com.shyblack.cryptosignals.dto.settings;

import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.PositionSizingMode;
import com.shyblack.cryptosignals.entity.enums.QuoteCurrency;
import com.shyblack.cryptosignals.entity.enums.RiskProfile;
import com.shyblack.cryptosignals.entity.enums.TradingMode;

/**
 * Partial (patch-style) settings update. Only non-null fields are applied.
 * {@code liveTradingAllowed=true} is always rejected by the service unless a
 * verified exchange connection exists.
 */
public record SettingsUpdateRequest(
		QuoteCurrency quoteCurrency,
		PositionSizingMode positionSizingMode,
		RiskProfile riskProfileOverride,
		String defaultLeverageView,
		String themeName,
		String languageCode,
		Boolean liveTradingAllowed,
		TradingMode tradingMode,
		AccountType accountType
) {
}