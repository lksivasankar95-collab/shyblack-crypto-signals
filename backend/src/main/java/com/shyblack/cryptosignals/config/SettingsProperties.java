package com.shyblack.cryptosignals.config;

import com.shyblack.cryptosignals.entity.enums.PositionSizingMode;
import com.shyblack.cryptosignals.entity.enums.QuoteCurrency;
import com.shyblack.cryptosignals.entity.enums.RiskProfile;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bounded configuration for the settings module: trading ceilings, defaults and
 * the AES-GCM encryption seed. Every getter is crash-safe and falls back to a
 * conservative default when a value is unset (unset incoming doubles/ints/integers
 * arrive as 0 and nulls for the enum/string components).
 */
@ConfigurationProperties(prefix = "app.settings")
public record SettingsProperties(
		double riskPerTradeCeilingPct,
		double maxPositionSizePct,
		int maxOpenPositions,
		String encryptionSecretKey,
		RiskProfile defaultRiskProfile,
		QuoteCurrency defaultQuoteCurrency,
		PositionSizingMode defaultPositionSizingMode
) {

	public static final double DEFAULT_RISK_PER_TRADE_CEILING_PCT = 2.0;
	public static final double DEFAULT_MAX_POSITION_SIZE_PCT = 20.0;
	public static final int DEFAULT_MAX_OPEN_POSITIONS = 4;
	public static final String DEFAULT_ENCRYPTION_SECRET_KEY = "dev-only-settings-encryption-key-seed-change-me";

	@Override
	public double riskPerTradeCeilingPct() {
		return riskPerTradeCeilingPct > 0 ? riskPerTradeCeilingPct : DEFAULT_RISK_PER_TRADE_CEILING_PCT;
	}

	@Override
	public double maxPositionSizePct() {
		return maxPositionSizePct > 0 ? maxPositionSizePct : DEFAULT_MAX_POSITION_SIZE_PCT;
	}

	@Override
	public int maxOpenPositions() {
		return maxOpenPositions > 0 ? maxOpenPositions : DEFAULT_MAX_OPEN_POSITIONS;
	}

	@Override
	public String encryptionSecretKey() {
		return encryptionSecretKey != null && !encryptionSecretKey.isBlank()
				? encryptionSecretKey
				: DEFAULT_ENCRYPTION_SECRET_KEY;
	}

	@Override
	public RiskProfile defaultRiskProfile() {
		return defaultRiskProfile != null ? defaultRiskProfile : RiskProfile.CONSERVATIVE;
	}

	@Override
	public QuoteCurrency defaultQuoteCurrency() {
		return defaultQuoteCurrency != null ? defaultQuoteCurrency : QuoteCurrency.USDT;
	}

	@Override
	public PositionSizingMode defaultPositionSizingMode() {
		return defaultPositionSizingMode != null ? defaultPositionSizingMode : PositionSizingMode.FIXED_PERCENT;
	}
}