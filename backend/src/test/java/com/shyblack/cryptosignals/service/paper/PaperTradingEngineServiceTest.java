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
import com.shyblack.cryptosignals.repository.SignalRepository;
import com.shyblack.cryptosignals.repository.UserRepository;
import com.shyblack.cryptosignals.service.SignalGeneratedEvent;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Signal → paper fan-out eligibility. Proves that an ACTIVE signal automatically
 * reaches enabled PAPER accounts only (never LIVE or disabled), and that the
 * engine creates exactly one paper execution per eligible account via the
 * signal-id idempotency enforced in the execution service.
 */
class PaperTradingEngineServiceTest {

	private final MarketBook marketBook = mock(MarketBook.class);
	private final SignalRepository signalRepository = mock(SignalRepository.class);
	private final PositionRepository positionRepository = mock(PositionRepository.class);
	private final UserRepository userRepository = mock(UserRepository.class);
	private final PaperTradingAccountService accountService = mock(PaperTradingAccountService.class);
	private final PaperTradingExecutionService executionService = mock(PaperTradingExecutionService.class);

	private PaperTradingProperties props;
	private PaperTradingEngineService engine;

	@BeforeEach
	void setUp() {
		props = new PaperTradingProperties(null, null, new BigDecimal("100"), 10, null, null);
		engine = new PaperTradingEngineService(
				marketBook, signalRepository, positionRepository, userRepository,
				accountService, executionService, props);
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
	void activeSignalOpensPaperTradeForEnabledPaperAccount() {
		Signal signal = activeSignal();
		User paperUser = user(true, AccountType.PAPER);
		Portfolio p = portfolio();
		when(signalRepository.findById(signal.getId())).thenReturn(Optional.of(signal));
		when(userRepository.findAll()).thenReturn(List.of(paperUser));
		when(accountService.getOrCreate(paperUser)).thenReturn(p);
		when(positionRepository.findByPortfolioAndStatus(p, PositionStatus.OPEN)).thenReturn(List.of());
		when(executionService.openFromSignal(p, signal)).thenReturn(Optional.of(new Position()));

		engine.onSignalGenerated(new SignalGeneratedEvent(signal.getId()));

		verify(executionService, times(1)).openFromSignal(p, signal);
	}

	@Test
	void disabledPaperAccountDoesNotReceiveTrade() {
		Signal signal = activeSignal();
		User disabled = user(false, AccountType.PAPER);
		when(signalRepository.findById(signal.getId())).thenReturn(Optional.of(signal));
		when(userRepository.findAll()).thenReturn(List.of(disabled));

		engine.onSignalGenerated(new SignalGeneratedEvent(signal.getId()));

		verify(accountService, never()).getOrCreate(any());
		verify(executionService, never()).openFromSignal(any(), any());
	}

	@Test
	void liveAccountDoesNotReceivePaperTrade() {
		Signal signal = activeSignal();
		User liveUser = user(true, AccountType.LIVE);
		when(signalRepository.findById(signal.getId())).thenReturn(Optional.of(signal));
		when(userRepository.findAll()).thenReturn(List.of(liveUser));

		engine.onSignalGenerated(new SignalGeneratedEvent(signal.getId()));

		verify(accountService, never()).getOrCreate(any());
		verify(executionService, never()).openFromSignal(any(), any());
	}

	@Test
	void nonActiveSignalIsIgnored() {
		Signal signal = activeSignal();
		signal.setStatus(SignalStatus.PENDING);
		when(signalRepository.findById(signal.getId())).thenReturn(Optional.of(signal));

		engine.onSignalGenerated(new SignalGeneratedEvent(signal.getId()));

		verify(userRepository, never()).findAll();
		verify(executionService, never()).openFromSignal(any(), any());
	}

	@Test
	void unknownSignalIsIgnored() {
		UUID id = UUID.randomUUID();
		when(signalRepository.findById(id)).thenReturn(Optional.empty());

		engine.onSignalGenerated(new SignalGeneratedEvent(id));

		verify(userRepository, never()).findAll();
		verify(executionService, never()).openFromSignal(any(), any());
	}

	@Test
	void maxActivePositionsRejectsFurtherTrades() {
		props = new PaperTradingProperties(null, null, new BigDecimal("100"), 1, null, null);
		engine = new PaperTradingEngineService(
				marketBook, signalRepository, positionRepository, userRepository,
				accountService, executionService, props);

		Signal signal = activeSignal();
		User paperUser = user(true, AccountType.PAPER);
		Portfolio p = portfolio();
		when(signalRepository.findById(signal.getId())).thenReturn(Optional.of(signal));
		when(userRepository.findAll()).thenReturn(List.of(paperUser));
		when(accountService.getOrCreate(paperUser)).thenReturn(p);
		when(positionRepository.findByPortfolioAndStatus(p, PositionStatus.OPEN))
				.thenReturn(List.of(new Position()));

		engine.onSignalGenerated(new SignalGeneratedEvent(signal.getId()));

		verify(executionService, never()).openFromSignal(any(), any());
	}

	@Test
	void oneSignalFansOutToEveryEligiblePaperAccount() {
		Signal signal = activeSignal();
		User a = user(true, AccountType.PAPER);
		User b = user(true, AccountType.PAPER);
		User live = user(true, AccountType.LIVE);
		Portfolio pa = portfolio();
		Portfolio pb = portfolio();
		when(signalRepository.findById(signal.getId())).thenReturn(Optional.of(signal));
		when(userRepository.findAll()).thenReturn(List.of(a, b, live));
		when(accountService.getOrCreate(a)).thenReturn(pa);
		when(accountService.getOrCreate(b)).thenReturn(pb);
		when(positionRepository.findByPortfolioAndStatus(any(), any())).thenReturn(List.of());
		when(executionService.openFromSignal(any(), any())).thenReturn(Optional.of(new Position()));

		engine.onSignalGenerated(new SignalGeneratedEvent(signal.getId()));

		verify(executionService, times(1)).openFromSignal(pa, signal);
		verify(executionService, times(1)).openFromSignal(pb, signal);
		verify(accountService, never()).getOrCreate(live);
	}
}
