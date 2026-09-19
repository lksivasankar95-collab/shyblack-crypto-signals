package com.shyblack.cryptosignals.entity;

import com.shyblack.cryptosignals.entity.enums.PositionSizingMode;
import com.shyblack.cryptosignals.entity.enums.QuoteCurrency;
import com.shyblack.cryptosignals.entity.enums.RiskProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-user preferences that are NOT already stored on {@link User}. Fields that
 * live on {@code User} (email, phone, country, timezone, tradingMode,
 * riskProfile, accountType) are intentionally not duplicated here; see
 * {@code riskProfileOverride} for the documented exception.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_settings")
public class UserSettings extends BaseEntity {

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private QuoteCurrency quoteCurrency = QuoteCurrency.USDT;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PositionSizingMode positionSizingMode = PositionSizingMode.FIXED_PERCENT;

	/**
	 * Null means "use {@link User#getRiskProfile()}".
	 */
	@Enumerated(EnumType.STRING)
	private RiskProfile riskProfileOverride;

	@Column(nullable = false)
	private String defaultLeverageView = "1x";

	@Column(nullable = false)
	private String themeName = "dark";

	@Column(nullable = false)
	private String languageCode = "en";

	/**
	 * Conservative default: never auto-enabled. Only a verified, connected
	 * exchange permits live trading, and the settings API rejects attempts to
	 * flip this to true directly.
	 */
	@Column(nullable = false)
	private boolean liveTradingAllowed = false;
}