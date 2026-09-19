package com.shyblack.cryptosignals.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shyblack.cryptosignals.config.SettingsProperties;
import com.shyblack.cryptosignals.dto.settings.ExchangeCredentialView;
import com.shyblack.cryptosignals.dto.settings.SettingsResponse;
import com.shyblack.cryptosignals.dto.settings.SettingsUpdateRequest;
import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.UserSettings;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.PositionSizingMode;
import com.shyblack.cryptosignals.entity.enums.QuoteCurrency;
import com.shyblack.cryptosignals.entity.enums.RiskProfile;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.exception.BadRequestException;
import com.shyblack.cryptosignals.repository.UserRepository;
import com.shyblack.cryptosignals.repository.UserSettingsRepository;
import com.shyblack.cryptosignals.security.UserPrincipal;
import com.shyblack.cryptosignals.service.ExchangeCredentialService;
import com.shyblack.cryptosignals.service.SettingsService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SettingsServiceTest {

	private final UserSettingsRepository settingsRepository = mock(UserSettingsRepository.class);
	private final UserRepository userRepository = mock(UserRepository.class);
	private final ExchangeCredentialService exchangeCredentialService = mock(ExchangeCredentialService.class);

	private SettingsProperties properties;
	private SettingsService service;
	private User user;
	private UserPrincipal principal;

	@BeforeEach
	void setUp() {
		properties = new SettingsProperties(
				2.0, 20.0, 4, "unit-test-seed", RiskProfile.CONSERVATIVE,
				QuoteCurrency.USDT, PositionSizingMode.FIXED_PERCENT);
		service = new SettingsService(settingsRepository, userRepository, properties, exchangeCredentialService);

		user = new User();
		user.setId(UUID.randomUUID());
		user.setEmail("trader@example.com");
		user.setFullName("Test Trader");
		user.setAccountType(AccountType.PAPER);
		user.setTradingMode(TradingMode.SPOT);
		user.setRiskProfile(RiskProfile.MODERATE);
		principal = new UserPrincipal(user);

		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
		when(settingsRepository.findByUser_Id(user.getId())).thenReturn(Optional.empty());
		when(settingsRepository.save(any(UserSettings.class))).thenAnswer(inv -> inv.getArgument(0));
		when(exchangeCredentialService.list(any(UserPrincipal.class))).thenReturn(List.of());
	}

	@Test
	void getCreatesSettingsWithConservativeDefaultsWhenMissing() {
		SettingsResponse response = service.get(principal);

		assertThat(response.userId()).isEqualTo(user.getId());
		assertThat(response.liveTradingAllowed()).isFalse();
		assertThat(response.quoteCurrency()).isEqualTo(QuoteCurrency.USDT);
		assertThat(response.positionSizingMode()).isEqualTo(PositionSizingMode.FIXED_PERCENT);
		assertThat(response.riskProfileOverride()).isNull();
		assertThat(response.riskProfile()).isEqualTo(RiskProfile.MODERATE);
		assertThat(response.defaultLeverageView()).isEqualTo("1x");
		assertThat(response.themeName()).isEqualTo("dark");
		assertThat(response.languageCode()).isEqualTo("en");
		assertThat(response.hasVerifiedExchange()).isFalse();

		verify(settingsRepository).save(any(UserSettings.class));
	}

	@Test
	void getReturnsExistingSettingsWithoutSavingAgain() {
		UserSettings existing = new UserSettings();
		existing.setUser(user);
		existing.setQuoteCurrency(QuoteCurrency.ETH);
		existing.setRiskProfileOverride(RiskProfile.AGGRESSIVE);
		existing.setThemeName("light");
		when(settingsRepository.findByUser_Id(user.getId())).thenReturn(Optional.of(existing));

		SettingsResponse response = service.get(principal);

		assertThat(response.quoteCurrency()).isEqualTo(QuoteCurrency.ETH);
		assertThat(response.riskProfileOverride()).isEqualTo(RiskProfile.AGGRESSIVE);
		assertThat(response.riskProfile()).isEqualTo(RiskProfile.AGGRESSIVE);
		assertThat(response.themeName()).isEqualTo("light");

		verify(settingsRepository, never()).save(any(UserSettings.class));
	}

	@Test
	void updateAppliesOnlyNonNullFields() {
		when(settingsRepository.findByUser_Id(user.getId()))
				.thenReturn(Optional.of(existingSettings()));

		SettingsUpdateRequest request = new SettingsUpdateRequest(
				null, null, null, "5x", "light", "de", null, null, null);
		SettingsResponse response = service.update(principal, request);

		assertThat(response.defaultLeverageView()).isEqualTo("5x");
		assertThat(response.themeName()).isEqualTo("light");
		assertThat(response.languageCode()).isEqualTo("de");
		// Untouched fields keep their previous values.
		assertThat(response.quoteCurrency()).isEqualTo(QuoteCurrency.USDT);
		assertThat(response.positionSizingMode()).isEqualTo(PositionSizingMode.FIXED_PERCENT);
		verify(settingsRepository).save(any(UserSettings.class));
	}

	@Test
	void updateRejectsAnyRequestThatEnablesLiveTrading() {
		when(settingsRepository.findByUser_Id(user.getId()))
				.thenReturn(Optional.of(existingSettings()));

		SettingsUpdateRequest request = new SettingsUpdateRequest(
				null, null, null, null, null, null, true, null, null);

		assertThatThrownBy(() -> service.update(principal, request))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("Live trading requires a verified exchange connection");
	}

	@Test
	void updateRejectsRiskOverrideAboveConfiguredCeiling() {
		properties = new SettingsProperties(
				1.5, 20.0, 4, "unit-test-seed", RiskProfile.CONSERVATIVE,
				QuoteCurrency.USDT, PositionSizingMode.FIXED_PERCENT);
		service = new SettingsService(settingsRepository, userRepository, properties, exchangeCredentialService);
		SettingsUpdateRequest request = new SettingsUpdateRequest(
				null, null, RiskProfile.AGGRESSIVE, null, null, null, null, null, null);

		assertThatThrownBy(() -> service.update(principal, request))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("configured ceiling");
	}

	@Test
	void updateAllowsRiskOverrideAtOrBelowCeiling() {
		when(settingsRepository.findByUser_Id(user.getId()))
				.thenReturn(Optional.of(existingSettings()));

		SettingsUpdateRequest request = new SettingsUpdateRequest(
				null, null, RiskProfile.CONSERVATIVE, null, null, null, null, null, null);
		SettingsResponse response = service.update(principal, request);

		assertThat(response.riskProfileOverride()).isEqualTo(RiskProfile.CONSERVATIVE);
	}

	@Test
	void metaExposesEnumsAndCeilings() {
		var meta = service.meta();

		assertThat(meta.riskProfiles()).containsExactlyInAnyOrderElementsOf(List.of(RiskProfile.values()));
		assertThat(meta.positionSizingModes())
				.containsExactlyInAnyOrderElementsOf(List.of(PositionSizingMode.values()));
		assertThat(meta.quoteCurrencies()).containsExactlyInAnyOrderElementsOf(List.of(QuoteCurrency.values()));
		assertThat(meta.exchangeNames()).containsExactlyInAnyOrderElementsOf(List.of(ExchangeName.values()));
		assertThat(meta.exchangeConnectionStatuses())
				.containsExactlyInAnyOrderElementsOf(List.of(ExchangeConnectionStatus.values()));
		assertThat(meta.riskPerTradeCeilingPct()).isEqualTo(2.0);
		assertThat(meta.maxPositionSizePct()).isEqualTo(20.0);
		assertThat(meta.maxOpenPositions()).isEqualTo(4);
	}

	@Test
	void settingsResponseMasksSecretsAndNeverExposesRawValues() {
		ExchangeCredential credential = new ExchangeCredential();
		credential.setId(UUID.randomUUID());
		credential.setExchange(ExchangeName.BINANCE);
		credential.setStatus(ExchangeConnectionStatus.CONNECTED);
		credential.setApiKey("ciphertext-key-part");
		credential.setApiSecret("ciphertext-secret-part");

		ExchangeCredentialView masked = new ExchangeCredentialView(
				credential.getId(), credential.getExchange(), credential.getStatus(),
				credential.getLabel(), credential.getDisplayName(),
				credential.maskedApiKey(), credential.maskedSecret(),
				credential.getCreatedAt(), credential.getUpdatedAt());

		assertThat(masked.maskedApiKey()).startsWith("****");
		assertThat(masked.maskedSecret()).startsWith("****");
		assertThat(masked.maskedSecret()).doesNotContain("ciphertext-secret-part");
		assertThat(credential.maskedSecret()).doesNotContain("secret-part");
		assertThat(credential.maskedApiKey()).doesNotContain("ciphertext-key-part");
	}

	private UserSettings existingSettings() {
		UserSettings settings = new UserSettings();
		settings.setUser(user);
		settings.setQuoteCurrency(QuoteCurrency.USDT);
		settings.setPositionSizingMode(PositionSizingMode.FIXED_PERCENT);
		settings.setDefaultLeverageView("1x");
		settings.setThemeName("dark");
		settings.setLanguageCode("en");
		return settings;
	}
}