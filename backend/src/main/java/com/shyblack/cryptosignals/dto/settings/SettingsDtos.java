package com.shyblack.cryptosignals.dto.settings;

import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.UserSettings;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.RiskProfile;
import java.util.List;

/**
 * Static mapping helpers shared by the settings services/controllers.
 */
public final class SettingsDtos {

	private SettingsDtos() {
	}

	public static final class SettingsMappers {

		private SettingsMappers() {
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