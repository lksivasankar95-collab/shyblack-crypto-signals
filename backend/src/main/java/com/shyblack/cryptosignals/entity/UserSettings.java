package com.shyblack.cryptosignals.entity;

import com.shyblack.cryptosignals.entity.enums.PositionSizingMode;
import com.shyblack.cryptosignals.entity.enums.QuoteCurrency;
import com.shyblack.cryptosignals.entity.enums.RiskProfile;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
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

	/**
	 * Multi-select trading modes, stored as a comma-joined enum-name list
	 * (e.g. {@code "SPOT,FUTURES"}). Null/blank means "not set" — callers fall
	 * back to the legacy singular {@link User#getTradingMode()} for backward
	 * compatibility.
	 */
	@Column(length = 200)
	private String selectedTradingModesCsv;

	@jakarta.persistence.Transient
	public java.util.Set<TradingMode> getSelectedTradingModes() {
		if (selectedTradingModesCsv == null || selectedTradingModesCsv.isBlank()) {
			return java.util.Set.of();
		}
		java.util.LinkedHashSet<TradingMode> modes = new java.util.LinkedHashSet<>();
		for (String part : selectedTradingModesCsv.split(",")) {
			String trimmed = part.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			try {
				modes.add(TradingMode.valueOf(trimmed));
			} catch (IllegalArgumentException ignored) {
				// Skip unknown values rather than failing the whole settings load.
			}
		}
		return modes;
	}

	@jakarta.persistence.Transient
	public void setSelectedTradingModes(java.util.Collection<TradingMode> modes) {
		if (modes == null || modes.isEmpty()) {
			this.selectedTradingModesCsv = null;
			return;
		}
		this.selectedTradingModesCsv = modes.stream()
				.map(Enum::name)
				.collect(java.util.stream.Collectors.joining(","));
	}
}