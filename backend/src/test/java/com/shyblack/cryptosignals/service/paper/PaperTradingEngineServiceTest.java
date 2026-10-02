package com.shyblack.cryptosignals.service.paper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shyblack.cryptosignals.config.PaperTradingProperties;
import com.shyblack.cryptosignals.entity.Portfolio;
import com.shyblack.cryptosignals.entity.Position;
import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.PositionStatus;
import com.shyblack.cryptosignals.entity.enums.SignalStatus;
import com.shyblack.cryptosignals.market.MarketBook;
import com.shyblack.cryptosignals.repository.PositionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Per-account paper execution, exercised through the engine's delegation entry
 * point {@link PaperTradingEngineService#executeForUser}.
 *
 * <p><b>Why this test changed shape in Phase 8.</b> The engine no longer listens
 * for {@code SignalGeneratedEvent} and no longer performs fan-out or
 * eligibility filtering — the execution router owns both. These tests therefore
 * drive the account-specific path directly, and the fan-out behaviour they used
 * to assert is now covered by {@code ExecutionRouterTest}. What is asserted here
 * is unchanged paper behaviour: one position per eligible account, and the
 * max-active-positions guard.
 */
class PaperTradingEngineServiceTest {

	private final MarketBook marketBook = mock(MarketBook.class);
	private final PositionRepository positionRepository = mock(PositionRepository.class);
	private final PaperTradingAccountService accountService = mock(PaperTradingAccountService.class);
	private final PaperTradingExecutionService executionService =
			mock(PaperTradingExecutionService.class);

	private PaperTradingProperties props;
	private PaperTradingEngineService engine;

	@BeforeEach
	void setUp() {
		props = new PaperTradingProperties(null, null, new BigDecimal("100"), 10, null, null);
		engine = new PaperTradingEngineService(
				marketBook, positionRepository, accountService, executionService, props);
	}

	private Signal activeSignal() {
		Signal s = new Signal();
		s.setId(UUID.randomUUID());
		s.setSymbol("BTCUSDT");
		s.setStatus(SignalStatus.ACTIVE);
		s.setEntryPrice(new BigDecimal("50000"));
		s.setStopLoss(new BigDecimal("49000"));
		return s;
	}

	private User user(boolean enabled, AccountType type) {
		User u = new User();
		u.setId(UUID.randomUUID());
		u.setEnabled(enabled);
		u.setAccountType(type);
		return u;
	}

	private Portfolio portfolio() {
		Portfolio p = new Portfolio();
		p.setId(UUID.randomUUID());
		p.setAccountType(AccountType.PAPER);
		p.setAvailableBalance(new BigDecimal("100"));
		return p;
	}

	@Test
	void activeSignalOpensPaperTradeForTheDelegatedAccount() {
		Signal signal = activeSignal();
		User paperUser = user(true, AccountType.PAPER);
		Portfolio p = portfolio();
		when(accountService.getOrCreate(paperUser)).thenReturn(p);
		when(positionRepository.findByPortfolioAndStatus(p, PositionStatus.OPEN)).thenReturn(List.of());
		when(executionService.openFromSignal(p, signal)).thenReturn(Optional.of(new Position()));

		engine.executeForUser(paperUser, signal);

		verify(executionService, times(1)).openFromSignal(p, signal);
	}

	@Test
	void maxActivePositionsRejectsFurtherTrades() {
		props = new PaperTradingProperties(null, null, new BigDecimal("100"), 1, null, null);
		engine = new PaperTradingEngineService(
				marketBook, positionRepository, accountService, executionService, props);

		Signal signal = activeSignal();
		User paperUser = user(true, AccountType.PAPER);
		Portfolio p = portfolio();
		when(accountService.getOrCreate(paperUser)).thenReturn(p);
		when(positionRepository.findByPortfolioAndStatus(p, PositionStatus.OPEN))
				.thenReturn(List.of(new Position()));

		engine.executeForUser(paperUser, signal);

		verify(executionService, never()).openFromSignal(any(), any());
	}

	@Test
	void eachAccountGetsItsOwnPortfolioAndPosition() {
		Signal signal = activeSignal();
		User a = user(true, AccountType.PAPER);
		User b = user(true, AccountType.PAPER);
		Portfolio pa = portfolio();
		Portfolio pb = portfolio();
		when(accountService.getOrCreate(a)).thenReturn(pa);
		when(accountService.getOrCreate(b)).thenReturn(pb);
		when(positionRepository.findByPortfolioAndStatus(any(), any())).thenReturn(List.of());
		when(executionService.openFromSignal(any(), any())).thenReturn(Optional.of(new Position()));

		engine.executeForUser(a, signal);
		engine.executeForUser(b, signal);

		verify(executionService, times(1)).openFromSignal(pa, signal);
		verify(executionService, times(1)).openFromSignal(pb, signal);
	}

	@Test
	void aFailingAccountDoesNotPreventTheNextAccountFromExecuting() {
		Signal signal = activeSignal();
		User failing = user(true, AccountType.PAPER);
		User healthy = user(true, AccountType.PAPER);
		Portfolio healthyPortfolio = portfolio();
		when(accountService.getOrCreate(failing)).thenThrow(new IllegalStateException("boom"));
		when(accountService.getOrCreate(healthy)).thenReturn(healthyPortfolio);
		when(positionRepository.findByPortfolioAndStatus(any(), any())).thenReturn(List.of());
		when(executionService.openFromSignal(any(), any())).thenReturn(Optional.of(new Position()));

		engine.executeForUser(failing, signal);
		engine.executeForUser(healthy, signal);

		verify(executionService, times(1)).openFromSignal(healthyPortfolio, signal);
	}

	@Test
	void aDelegationFailureIsContainedAndDoesNotEscape() {
		// The router calls this entry point inside a fan-out, so one account's failure
		// must not propagate and abort the remaining accounts.
		Signal signal = activeSignal();
		User paperUser = user(true, AccountType.PAPER);
		Portfolio p = portfolio();
		when(accountService.getOrCreate(paperUser)).thenThrow(new IllegalStateException("boom"));

		org.assertj.core.api.Assertions.assertThatCode(
				() -> engine.executeForUser(paperUser, signal))
				.doesNotThrowAnyException();

		verify(executionService, never()).openFromSignal(any(), any());
	}
}