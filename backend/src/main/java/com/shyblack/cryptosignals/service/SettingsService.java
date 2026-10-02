package com.shyblack.cryptosignals.service;

import com.shyblack.cryptosignals.config.SettingsProperties;
import com.shyblack.cryptosignals.dto.settings.ExchangeCredentialView;
import com.shyblack.cryptosignals.dto.settings.SettingsDtos.SettingsMappers;
import com.shyblack.cryptosignals.dto.settings.SettingsMetaResponse;
import com.shyblack.cryptosignals.dto.settings.SettingsResponse;
import com.shyblack.cryptosignals.dto.settings.SettingsUpdateRequest;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.UserSettings;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.PositionSizingMode;
import com.shyblack.cryptosignals.entity.enums.QuoteCurrency;
import com.shyblack.cryptosignals.entity.enums.RiskProfile;
import com.shyblack.cryptosignals.exception.BadRequestException;
import com.shyblack.cryptosignals.exception.ResourceNotFoundException;
import com.shyblack.cryptosignals.repository.UserRepository;
import com.shyblack.cryptosignals.repository.UserSettingsRepository;
import com.shyblack.cryptosignals.security.UserPrincipal;
import com.shyblack.cryptosignals.service.portfolio.PortfolioSyncLifecycleCoordinator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User settings get-or-create and patch updates. Only the authenticated
 * principal's id is used (never a client-supplied user id), so lookups are
 * IDOR-safe. Conservative defaults: live trading is never auto-enabled and any
 * request that tries to turn it on is rejected.
 */
@Service
public class SettingsService {

	private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

	/**
	 * Implied per-trade risk percentage for each risk profile, used to enforce
	 * the configured {@code riskPerTradeCeilingPct} ceiling.
	 */
	private static final Map<RiskProfile, Double> IMPLIED_RISK_PCT = Map.of(
			RiskProfile.CONSERVATIVE, 1.0,
			RiskProfile.MODERATE, 2.0,
			RiskProfile.AGGRESSIVE, 3.0);

	private final UserSettingsRepository userSettingsRepository;
	private final UserRepository userRepository;
	private final SettingsProperties properties;
	private final ExchangeCredentialService exchangeCredentialService;

	/**
	 * Informed when an account-mode change must start or stop Portfolio synchronization.
	 *
	 * <p>Resolved leniently so this service still wires without it, in which case synchronization
	 * simply does not react to a mode change.
	 */
	private final PortfolioSyncLifecycleCoordinator syncCoordinator;

	@Autowired
	public SettingsService(
			UserSettingsRepository userSettingsRepository,
			UserRepository userRepository,
			SettingsProperties properties,
			ExchangeCredentialService exchangeCredentialService,
			ObjectProvider<PortfolioSyncLifecycleCoordinator> syncCoordinatorProvider) {
		this.userSettingsRepository = userSettingsRepository;
		this.userRepository = userRepository;
		this.properties = properties;
		this.exchangeCredentialService = exchangeCredentialService;
		this.syncCoordinator = syncCoordinatorProvider == null
				? null
				: syncCoordinatorProvider.getIfAvailable();
	}

	/**
	 * Constructor without the synchronization coordinator.
	 *
	 * <p>Retained so settings can be exercised on their own. An account-mode change then simply
	 * does not drive Portfolio synchronization, which is the correct degradation.
	 */
	public SettingsService(
			UserSettingsRepository userSettingsRepository,
			UserRepository userRepository,
			SettingsProperties properties,
			ExchangeCredentialService exchangeCredentialService) {
		this(userSettingsRepository, userRepository, properties, exchangeCredentialService, null);
	}

	@Transactional
	public SettingsResponse get(UserPrincipal principal) {
		User user = currentUser(principal);
		UserSettings settings = getOrCreate(user);
		List<ExchangeCredentialView> exchanges = exchangeCredentialService.list(principal);
		return SettingsMappers.toSettingsResponse(user, settings, exchanges);
	}

	@Transactional
	public SettingsResponse update(UserPrincipal principal, SettingsUpdateRequest request) {
		User user = currentUser(principal);
		UserSettings settings = getOrCreate(user);
		boolean changed = false;

		if (request.quoteCurrency() != null) {
			settings.setQuoteCurrency(request.quoteCurrency());
			changed = true;
		}
		if (request.positionSizingMode() != null) {
			settings.setPositionSizingMode(request.positionSizingMode());
			changed = true;
		}
		if (request.riskProfileOverride() != null) {
			validateRiskCeiling(request.riskProfileOverride());
			settings.setRiskProfileOverride(request.riskProfileOverride());
			changed = true;
		}
		if (request.defaultLeverageView() != null) {
			settings.setDefaultLeverageView(requireNonBlank("defaultLeverageView", request.defaultLeverageView()));
			changed = true;
		}
		if (request.themeName() != null) {
			settings.setThemeName(requireNonBlank("themeName", request.themeName()));
			changed = true;
		}
		if (request.languageCode() != null) {
			settings.setLanguageCode(requireNonBlank("languageCode", request.languageCode()));
			changed = true;
		}
		if (Boolean.TRUE.equals(request.liveTradingAllowed())) {
			throw new BadRequestException("Live trading requires a verified exchange connection; email support");
		}
		if (Boolean.FALSE.equals(request.liveTradingAllowed()) && settings.isLiveTradingAllowed()) {
			settings.setLiveTradingAllowed(false);
			changed = true;
		}

		boolean userChanged = false;
		if (request.accountType() != null && request.accountType() != user.getAccountType()) {
			AccountType previous = user.getAccountType();
			user.setAccountType(request.accountType());
			userRepository.save(user);

			/*
			 * Switching to live begins Portfolio synchronization; switching back to paper ends it,
			 * so no Binance user-data stream is kept open for an account the user has left in
			 * simulation. This is the only place an account's mode changes after signup.
			 *
			 * Runs after the save so the coordinator reads a persisted state, and is wrapped so a
			 * lifecycle problem can never turn a successful settings update into a failed one — the
			 * preference itself is already correct either way.
			 */
			notifyAccountModeChanged(user, previous, request.accountType());
		}
		if (changed) {
			userSettingsRepository.save(settings);
		}

		return SettingsMappers.toSettingsResponse(
				user, settings, exchangeCredentialService.list(principal));
	}

	/**
	 * Informs the Portfolio synchronization lifecycle of an account-mode change.
	 *
	 * <p>Best-effort by design. The setting is already saved, so a failure here degrades the read
	 * lifecycle rather than the request.
	 */
	private void notifyAccountModeChanged(User user, AccountType previous, AccountType current) {
		if (syncCoordinator == null) {
			return;
		}
		try {
			syncCoordinator.onAccountModeChanged(user, previous, current);
		} catch (RuntimeException ex) {
			log.warn("Could not apply Portfolio synchronization for account-mode change user={} from={} to={}: {}",
					user.getId(), previous, current, ex.getMessage());
		}
	}

	@Transactional(readOnly = true)
	public SettingsMetaResponse meta() {
		return new SettingsMetaResponse(
				List.of(RiskProfile.values()),
				List.of(PositionSizingMode.values()),
				List.of(QuoteCurrency.values()),
				List.of(ExchangeName.values()),
				List.of(ExchangeConnectionStatus.values()),
				properties.riskPerTradeCeilingPct(),
				properties.maxPositionSizePct(),
				properties.maxOpenPositions());
	}

	/**
	 * Returns the user's settings, creating them with conservative defaults on
	 * first access. {@code riskProfileOverride} stays null so the user's own
	 * risk profile is used unless explicitly overridden. Live trading is never
	 * auto-enabled.
	 */
	public UserSettings getOrCreate(User user) {
		return userSettingsRepository.findByUser_Id(user.getId()).orElseGet(() -> {
			UserSettings settings = new UserSettings();
			settings.setUser(user);
			settings.setQuoteCurrency(properties.defaultQuoteCurrency());
			settings.setPositionSizingMode(properties.defaultPositionSizingMode());
			settings.setRiskProfileOverride(null);
			settings.setDefaultLeverageView("1x");
			settings.setThemeName("dark");
			settings.setLanguageCode("en");
			settings.setLiveTradingAllowed(false);
			return userSettingsRepository.save(settings);
		});
	}

	private void validateRiskCeiling(RiskProfile profile) {
		double implied = IMPLIED_RISK_PCT.getOrDefault(profile, IMPLIED_RISK_PCT.get(RiskProfile.MODERATE));
		double ceiling = properties.riskPerTradeCeilingPct();
		if (implied > ceiling) {
			throw new BadRequestException("Risk profile " + profile.name()
					+ " implies a per-trade risk of " + implied + "% which exceeds the configured ceiling of "
					+ ceiling + "%");
		}
	}

	private String requireNonBlank(String field, String value) {
		if (value.isBlank()) {
			throw new BadRequestException(field + " must not be blank");
		}
		return value.trim();
	}

	private User currentUser(UserPrincipal principal) {
		if (principal == null) {
			throw new BadRequestException("Authentication required");
		}
		return userRepository.findById(principal.getId())
				.orElseThrow(() -> new ResourceNotFoundException("User not found"));
	}
}