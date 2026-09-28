package com.shyblack.cryptosignals.dto.settings;

import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.UserSettings;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.RiskProfile;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Static mapping helpers shared by the settings services/controllers.
 */
public final class SettingsDtos {

	private SettingsDtos() {
	}

	public static final class SettingsMappers {

		private SettingsMappers() {
		}

		/**
		 * Effective multi-select trading modes: the stored selection when set,
		 * otherwise the legacy singular {@link User#getTradingMode()} as a
		 * single-element list (backward compatibility for pre-existing users).
		 */
		public static List<TradingMode> effectiveSelectedModes(User user, UserSettings settings) {
			Set<TradingMode> stored = settings.getSelectedTradingModes();
			if (stored != null && !stored.isEmpty()) {
				return List.copyOf(new TreeSet<>(stored));
			}
			TradingMode legacy = user.getTradingMode();
			return legacy == null ? List.of(TradingMode.SPOT) : List.of(legacy);
		}

		public static SettingsResponse toSettingsResponse(
				User user,
				UserSettings settings,
				List<ExchangeCredentialView> exchanges
		) {
			RiskProfile override = settings.getRiskProfileOverride();
			RiskProfile effective = override != null ? override : user.getRiskProfile();
			List<ExchangeCredentialView> safeExchanges = exchanges == null ? List.of() : exchanges;
			boolean hasVerified = safeExchanges.stream()
					.anyMatch(view -> view.status() == ExchangeConnectionStatus.CONNECTED);
			return new SettingsResponse(
					user.getId(),
					user.getEmail(),
					user.getFullName(),
					user.getAccountType(),
					user.getTradingMode(),
					effectiveSelectedModes(user, settings),
					effective,
					override,
					settings.getQuoteCurrency(),
					settings.getPositionSizingMode(),
					settings.getDefaultLeverageView(),
					settings.getThemeName(),
					settings.getLanguageCode(),
					settings.isLiveTradingAllowed(),
					hasVerified,
					safeExchanges,
					settings.getCreatedAt(),
					settings.getUpdatedAt()
			);
		}
	}
}