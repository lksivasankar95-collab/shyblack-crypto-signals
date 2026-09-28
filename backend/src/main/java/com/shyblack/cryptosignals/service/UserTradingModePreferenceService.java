package com.shyblack.cryptosignals.service;

import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.UserSettings;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.repository.UserSettingsRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Resolves the trading modes a user has selected, for user-scoped notification
 * filtering. Global signal generation is unchanged — this only decides whether
 * a given signal's mode is eligible for a specific recipient.
 *
 * <p>Backward compatible: users with no stored multi-select list fall back to
 * the legacy singular {@link User#getTradingMode()} as a single-element set.</p>
 */
@Service
@RequiredArgsConstructor
public class UserTradingModePreferenceService {

	private final UserSettingsRepository userSettingsRepository;

	/** Effective selected modes: stored list when present, else the legacy mode. */
	public Set<TradingMode> effectiveSelectedModes(User user) {
		if (user == null) {
			return Set.of(TradingMode.SPOT);
		}
		return userSettingsRepository.findByUser_Id(user.getId())
				.map(UserSettings::getSelectedTradingModes)
				.filter(modes -> modes != null && !modes.isEmpty())
				.orElseGet(() -> {
					TradingMode legacy = user.getTradingMode();
					return legacy != null ? Set.of(legacy) : Set.of(TradingMode.SPOT);
				});
	}

	/**
	 * True when {@code mode} is among the user's selected modes. A null mode is
	 * treated as eligible so an unknown/legacy signal is never silently dropped.
	 */
	public boolean isModeSelected(User user, TradingMode mode) {
		if (mode == null) {
			return true;
		}
		return effectiveSelectedModes(user).contains(mode);
	}
}
