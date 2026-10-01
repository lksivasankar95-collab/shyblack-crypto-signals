package com.shyblack.cryptosignals.entity.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Guards the Phase 1 account-scope domain: the {@code accountMode} / {@code accountCategory}
 * discriminators, their mapping onto the pre-existing {@link AccountType} and {@link TradingMode}
 * enums, and the availability vocabulary that keeps missing data from being reported as zero.
 */
class AccountScopeEnumsTest {

	@Test
	void legacyAccountTypeIsNotRenamedAndKeepsItsOriginalValues() {
		assertThat(AccountType.values())
				.as("D1: AccountType keeps its original {LIVE, PAPER} meaning")
				.containsExactly(AccountType.LIVE, AccountType.PAPER);
	}

	@Test
	void accountModeDeclaresPaperAndLiveOnly() {
		assertThat(AccountMode.values()).containsExactly(AccountMode.PAPER, AccountMode.LIVE);
	}

	@Test
	void accountModeConvertsBothLegacyAccountTypeValues() {
		assertThat(AccountMode.fromAccountType(AccountType.PAPER)).isEqualTo(AccountMode.PAPER);
		assertThat(AccountMode.fromAccountType(AccountType.LIVE)).isEqualTo(AccountMode.LIVE);
	}

	@Test
	void accountModeConversionRoundTripsThroughLegacyAccountType() {
		for (AccountMode mode : AccountMode.values()) {
			assertThat(AccountMode.fromAccountType(mode.toAccountType()))
					.as("round trip for %s", mode)
					.isEqualTo(mode);
		}
	}

	@Test
	void accountModeRefusesNullLegacyAccountTypeRatherThanDefaulting() {
		assertThatThrownBy(() -> AccountMode.fromAccountType(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("accountType");
	}

	@Test
	void accountModeFlagsIdentifyTheirOwnSide() {
		assertThat(AccountMode.PAPER.isPaper()).isTrue();
		assertThat(AccountMode.PAPER.isLive()).isFalse();
		assertThat(AccountMode.LIVE.isLive()).isTrue();
		assertThat(AccountMode.LIVE.isPaper()).isFalse();
	}

	@Test
	void accountCategoryDeclaresAllFourBoundaries() {
		assertThat(AccountCategory.values())
				.containsExactly(
						AccountCategory.MAIN,
						AccountCategory.SPOT,
						AccountCategory.FUTURES,
						AccountCategory.OPTIONS);
	}

	@Test
	void marketScopedCategoriesMapOntoTheirTradingMode() {
		assertThat(AccountCategory.SPOT.tradingMode()).isEqualTo(Optional.of(TradingMode.SPOT));
		assertThat(AccountCategory.FUTURES.tradingMode()).isEqualTo(Optional.of(TradingMode.FUTURES));
		assertThat(AccountCategory.OPTIONS.tradingMode()).isEqualTo(Optional.of(TradingMode.OPTIONS));
	}

	@Test
	void mainCategoryHasNoSingleTradingMode() {
		assertThat(AccountCategory.MAIN.tradingMode())
				.as("MAIN aggregates several categories; it must not report a fabricated market mode")
				.isEmpty();
	}

	@Test
	void mainIsTheOnlyAggregatingCategory() {
		assertThat(AccountCategory.MAIN.isAggregating()).isTrue();
		assertThat(AccountCategory.SPOT.isAggregating()).isFalse();
		assertThat(AccountCategory.FUTURES.isAggregating()).isFalse();
		assertThat(AccountCategory.OPTIONS.isAggregating()).isFalse();

		assertThat(AccountCategory.MAIN.isMarketScoped()).isFalse();
		assertThat(AccountCategory.SPOT.isMarketScoped()).isTrue();
		assertThat(AccountCategory.FUTURES.isMarketScoped()).isTrue();
		assertThat(AccountCategory.OPTIONS.isMarketScoped()).isTrue();
	}

	@Test
	void everyMarketScopedCategoryHasExactlyOneTradingMode() {
		for (AccountCategory category : AccountCategory.values()) {
			if (category.isMarketScoped()) {
				assertThat(category.tradingMode())
						.as("%s must resolve to one market mode", category)
						.isPresent();
			}
		}
	}

	@Test
	void availabilityCoversEveryStateTheReadModelMustReport() {
		assertThat(AccountAvailability.values())
				.containsExactlyInAnyOrder(
						AccountAvailability.AVAILABLE,
						AccountAvailability.SYNCING,
						AccountAvailability.STALE,
						AccountAvailability.NOT_CONNECTED,
						AccountAvailability.DISCONNECTED,
						AccountAvailability.UNAVAILABLE,
						AccountAvailability.UNSUPPORTED,
						AccountAvailability.ERROR);
	}
}