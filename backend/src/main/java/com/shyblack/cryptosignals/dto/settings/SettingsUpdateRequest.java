package com.shyblack.cryptosignals.dto.settings;

import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.PositionSizingMode;
import com.shyblack.cryptosignals.entity.enums.QuoteCurrency;
import com.shyblack.cryptosignals.entity.enums.RiskProfile;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import java.util.List;

/**
 * Partial (patch-style) settings update. Only non-null fields are applied.
 * {@code liveTradingAllowed=true} is always rejected by the service unless a
 * verified exchange connection exists.
 *
 * <p>{@code selectedTradingModes} is the multi-select trading-mode list. When
 * present it must be non-empty; unknown enum values are rejected by Jackson
 * before the service runs. The legacy singular {@code tradingMode} remains
 * supported and is interpreted as a single-element selection.</p>
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
		AccountType accountType,
		List<TradingMode> selectedTradingModes
) {

	/** Backwards-compatible constructor without the multi-select field. */
	public SettingsUpdateRequest(
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
		this(quoteCurrency, positionSizingMode, riskProfileOverride, defaultLeverageView,
				themeName, languageCode, liveTradingAllowed, tradingMode, accountType, null);
	}
}