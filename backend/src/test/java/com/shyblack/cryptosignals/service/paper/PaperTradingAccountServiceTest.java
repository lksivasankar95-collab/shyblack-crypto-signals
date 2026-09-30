package com.shyblack.cryptosignals.service.paper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shyblack.cryptosignals.config.PaperTradingProperties;
import com.shyblack.cryptosignals.entity.Portfolio;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.exception.BadRequestException;
import com.shyblack.cryptosignals.repository.PortfolioRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Default capital + the safe initial-capital change rule. Automatic execution
 * correctness (signal fan-out, sizing, dedup, SL/TP) lives in the existing
 * execution/sizing tests and the signal bridge.
 */
class PaperTradingAccountServiceTest {

	private final PortfolioRepository repository = mock(PortfolioRepository.class);

	private PaperTradingAccountService service;
	private User user;

	@BeforeEach
	void setUp() {
		// nulls -> record compact constructor applies the documented defaults.
		PaperTradingProperties props = new PaperTradingProperties(null, null, null, 0, null, null);
		service = new PaperTradingAccountService(repository, props);
		user = new User();
		user.setId(UUID.randomUUID());
		when(repository.save(any(Portfolio.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	private Portfolio portfolioWith(int totalTrades, BigDecimal invested, BigDecimal realizedPnl) {
		Portfolio p = new Portfolio();
		p.setId(UUID.randomUUID());
		p.setAccountType(AccountType.PAPER);
		p.setQuoteCurrency("USDT");
		p.setInitialBalance(new BigDecimal("100"));
		p.setTotalBalance(new BigDecimal("100"));
		p.setAvailableBalance(new BigDecimal("100"));
		p.setInvested(invested);
		p.setRealizedPnl(realizedPnl);
		p.setTotalFees(BigDecimal.ZERO);
		p.setTotalTrades(totalTrades);
		return p;
	}

	@Test
	void defaultInitialCapitalIsOneHundredUsdt() {
		when(repository.findFirstByUserAndAccountType(user, AccountType.PAPER))
				.thenReturn(Optional.empty());

		Portfolio created = service.getOrCreate(user);

		assertThat(created.getAccountType()).isEqualTo(AccountType.PAPER);
		assertThat(created.getQuoteCurrency()).isEqualTo("USDT");
		assertThat(created.getInitialBalance()).isEqualByComparingTo("100");
		assertThat(created.getTotalBalance()).isEqualByComparingTo("100");
		assertThat(created.getAvailableBalance()).isEqualByComparingTo("100");
	}

	@Test
	void existingAccountIsReturnedUnchanged() {
		Portfolio existing = portfolioWith(0, BigDecimal.ZERO, BigDecimal.ZERO);
		existing.setInitialBalance(new BigDecimal("500"));
		existing.setAvailableBalance(new BigDecimal("500"));
		when(repository.findFirstByUserAndAccountType(user, AccountType.PAPER))
				.thenReturn(Optional.of(existing));

		Portfolio returned = service.getOrCreate(user);

		assertThat(returned).isSameAs(existing);
		assertThat(returned.getInitialBalance()).isEqualByComparingTo("500");
	}

	@Test
	void capitalCanBeChangedBeforeAnyTrading() {
		Portfolio p = portfolioWith(0, BigDecimal.ZERO, BigDecimal.ZERO);
		when(repository.findByIdForUpdate(p.getId())).thenReturn(Optional.of(p));

		Portfolio saved = service.updateInitialCapital(p, new BigDecimal("250"));

		assertThat(saved.getInitialBalance()).isEqualByComparingTo("250");
		assertThat(saved.getAvailableBalance()).isEqualByComparingTo("250");
		assertThat(saved.getTotalBalance()).isEqualByComparingTo("250");
	}

	@Test
	void capitalChangeSupportsDecimals() {
		Portfolio p = portfolioWith(0, BigDecimal.ZERO, BigDecimal.ZERO);
		when(repository.findByIdForUpdate(p.getId())).thenReturn(Optional.of(p));

		Portfolio saved = service.updateInitialCapital(p, new BigDecimal("123.45"));

		assertThat(saved.getInitialBalance()).isEqualByComparingTo("123.45");
	}

	@Test
	void capitalChangeRejectedForZero() {
		Portfolio p = portfolioWith(0, BigDecimal.ZERO, BigDecimal.ZERO);
		assertThatThrownBy(() -> service.updateInitialCapital(p, BigDecimal.ZERO))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("greater than zero");
	}

	@Test
	void capitalChangeRejectedForNegative() {
		Portfolio p = portfolioWith(0, BigDecimal.ZERO, BigDecimal.ZERO);
		assertThatThrownBy(() -> service.updateInitialCapital(p, new BigDecimal("-5")))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("greater than zero");
	}

	@Test
	void capitalChangeRejectedForNull() {
		Portfolio p = portfolioWith(0, BigDecimal.ZERO, BigDecimal.ZERO);
		assertThatThrownBy(() -> service.updateInitialCapital(p, null))
				.isInstanceOf(BadRequestException.class);
	}

	@Test
	void capitalChangeRejectedWhenAccountHasTrades() {
		Portfolio p = portfolioWith(3, BigDecimal.ZERO, new BigDecimal("1.5"));
		when(repository.findByIdForUpdate(p.getId())).thenReturn(Optional.of(p));

		assertThatThrownBy(() -> service.updateInitialCapital(p, new BigDecimal("250")))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("reset the account");
		// Historical accounting must be untouched.
		assertThat(p.getInitialBalance()).isEqualByComparingTo("100");
		assertThat(p.getRealizedPnl()).isEqualByComparingTo("1.5");
	}

	@Test
	void capitalChangeRejectedWhenOpenPositionExists() {
		Portfolio p = portfolioWith(0, new BigDecimal("50"), BigDecimal.ZERO);
		when(repository.findByIdForUpdate(p.getId())).thenReturn(Optional.of(p));

		assertThatThrownBy(() -> service.updateInitialCapital(p, new BigDecimal("250")))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("reset the account");
	}
}
