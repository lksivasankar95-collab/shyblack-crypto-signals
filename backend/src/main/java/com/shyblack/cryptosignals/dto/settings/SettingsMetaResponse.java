package com.shyblack.cryptosignals.dto.settings;

import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.PositionSizingMode;
import com.shyblack.cryptosignals.entity.enums.QuoteCurrency;
import com.shyblack.cryptosignals.entity.enums.RiskProfile;
import java.util.List;

/**
 * Read-only catalogue of selectable options and the configured trading
 * ceilings that the client must respect.
 */
public record SettingsMetaResponse(
		List<RiskProfile> riskProfiles,
		List<PositionSizingMode> positionSizingModes,
		List<QuoteCurrency> quoteCurrencies,
		List<ExchangeName> exchangeNames,
		List<ExchangeConnectionStatus> exchangeConnectionStatuses,
		double riskPerTradeCeilingPct,
		double maxPositionSizePct,
		int maxOpenPositions
) {
}