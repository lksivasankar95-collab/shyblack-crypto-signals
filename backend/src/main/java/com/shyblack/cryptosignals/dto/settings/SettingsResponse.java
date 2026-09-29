package com.shyblack.cryptosignals.dto.settings;

import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.PositionSizingMode;
import com.shyblack.cryptosignals.entity.enums.QuoteCurrency;
import com.shyblack.cryptosignals.entity.enums.RiskProfile;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Flat view of the authenticated user's settings. The profile fields
 * (accountType and the effective riskProfile) are sourced from the
 * {@code User} entity, while preferences owned by {@code UserSettings} plus the
 * masked exchange summary are merged in.
 *
 * <p>Trading mode is no longer a user-level preference; it is controlled by
 * the application's active strategy per mode (see the signal schedulers).</p>
 */
public record SettingsResponse(
		UUID userId,
		String email,
		String fullName,
		AccountType accountType,
		RiskProfile riskProfile,
		RiskProfile riskProfileOverride,
		QuoteCurrency quoteCurrency,
		PositionSizingMode positionSizingMode,
		String defaultLeverageView,
		String themeName,
		String languageCode,
		boolean liveTradingAllowed,
		boolean hasVerifiedExchange,
		List<ExchangeCredentialView> exchanges,
		Instant createdAt,
		Instant updatedAt
) {
}