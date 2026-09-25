package com.shyblack.cryptosignals.service.paper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shyblack.cryptosignals.config.PaperTradingProperties;
import com.shyblack.cryptosignals.entity.Portfolio;
import com.shyblack.cryptosignals.entity.Position;
import com.shyblack.cryptosignals.entity.PositionLifecycleEvent;
import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.CloseReason;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.PositionStatus;
import com.shyblack.cryptosignals.repository.PortfolioRepository;
import com.shyblack.cryptosignals.repository.PositionLifecycleEventRepository;
import com.shyblack.cryptosignals.repository.PositionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaperTradingExecutionServiceTest {

	private PortfolioRepository portfolios;
	private PositionRepository positions;
	private PositionLifecycleEventRepository lifecycle;
	private PaperTradingPnLService pnl;
	private PaperTradingSizingService sizing;
	private PaperTradingExecutionService svc;

	private Portfolio portfolio;

	@BeforeEach
	void setUp() {
		PaperTradingProperties props = new PaperTradingProperties(
				new BigDecimal("0.10"),
				new BigDecimal("0.05"),
				new BigDecimal("10000.00"),
				10,
				new BigDecimal("2.00"),
				"USDT");

		portfolios = mock(PortfolioRepository.class);
		positions = mock(PositionRepository.class);
		lifecycle = mock(PositionLifecycleEventRepository.class);
		pnl = new PaperTradingPnLService(props);
		sizing = new PaperTradingSizingService(props);

		svc = new PaperTradingExecutionService(portfolios, positions, lifecycle, pnl, sizing);

		portfolio = new Portfolio();
		portfolio.setId(UUID.randomUUID());
		portfolio.setAccountType(AccountType.PAPER);
		portfolio.setQuoteCurrency("USDT");
		portfolio.setInitialBalance(new BigDecimal("10000"));
		portfolio.setTotalBalance(new BigDecimal("10000"));
		portfolio.setAvailableBalance(new BigDecimal("10000"));
		portfolio.setInvested(BigDecimal.ZERO);
		portfolio.setRealizedPnl(BigDecimal.ZERO);
		portfolio.setTotalFees(BigDecimal.ZERO);

		when(portfolios.findByIdForUpdate(portfolio.getId())).thenReturn(Optional.of(portfolio));
	}

	@Test
	void openFromSignal_isIdempotent_whenPositionAlreadyExists() {
		Signal signal = buildSignal(new BigDecimal("100"), new BigDecimal("95"), new BigDecimal("110"));
		Position existing = new Position();
		existing.setId(UUID.randomUUID());
		existing.setPortfolio(portfolio);
		existing.setSignalId(signal.getId());
		existing.setStatus(PositionStatus.OPEN);

		when(positions.findByPortfolioAndSignalId(portfolio, signal.getId()))
				.thenReturn(Optional.of(existing));

		Optional<Position> result = svc.openFromSignal(portfolio, signal);

		assertThat(result).contains(existing);
		verify(positions, never()).saveAndFlush(any());
		verify(portfolios, never()).save(any());
	}

	@Test
	void openFromSignal_debitsBalance_bookEntryFee_writesLifecycleEvents() {
		Signal signal = buildSignal(new BigDecimal("100"), new BigDecimal("95"), new BigDecimal("110"));
		when(positions.findByPortfolioAndSignalId(portfolio, signal.getId())).thenReturn(Optional.empty());
		when(positions.saveAndFlush(any(Position.class))).thenAnswer(inv -> inv.getArgument(0));
		when(portfolios.save(any(Portfolio.class))).thenAnswer(inv -> inv.getArgument(0));

		Optional<Position> opened = svc.openFromSignal(portfolio, signal);

		assertThat(opened).isPresent();
		Position p = opened.get();
		assertThat(p.getStatus()).isEqualTo(PositionStatus.OPEN);
		assertThat(p.getSignalId()).isEqualTo(signal.getId());
		assertThat(p.getSymbol()).isEqualTo("BTCUSDT");
		assertThat(p.getSide()).isEqualTo(PositionSide.LONG);
		assertThat(p.getSize().signum()).isPositive();
		assertThat(p.getNotional().signum()).isPositive();
		assertThat(p.getEntryFee().signum()).isPositive();
		assertThat(p.getStopLoss()).isEqualByComparingTo("95");
		assertThat(p.getTakeProfit1()).isEqualByComparingTo("110");

		// Balance was debited by notional + fee
		BigDecimal expectedDebit = p.getNotional().add(p.getEntryFee());
		assertThat(portfolio.getAvailableBalance())
				.isEqualByComparingTo(new BigDecimal("10000").subtract(expectedDebit));
		assertThat(portfolio.getInvested()).isEqualByComparingTo(p.getNotional());
		assertThat(portfolio.getTotalFees()).isEqualByComparingTo(p.getEntryFee());

		// Two lifecycle events written: CREATED + OPENED
		verify(lifecycle, times(2)).save(any(PositionLifecycleEvent.class));
	}

	@Test
	void openFromSignal_rejects_whenSizingCannotProduceQty() {
		// Available balance = 0 -> sizing returns null
		portfolio.setAvailableBalance(BigDecimal.ZERO);
		Signal signal = buildSignal(new BigDecimal("100"), new BigDecimal("95"), new BigDecimal("110"));
		when(positions.findByPortfolioAndSignalId(portfolio, signal.getId())).thenReturn(Optional.empty());

		Optional<Position> result = svc.openFromSignal(portfolio, signal);

		assertThat(result).isEmpty();
		verify(positions, never()).saveAndFlush(any());
	}

	@Test
	void close_isIdempotent_whenAlreadyClosed() {
		Position closed = new Position();
		closed.setId(UUID.randomUUID());
		closed.setStatus(PositionStatus.CLOSED);
		closed.setPortfolio(portfolio);

		when(positions.findByIdForUpdate(closed.getId())).thenReturn(Optional.of(closed));

		Optional<Position> result = svc.close(closed.getId(), new BigDecimal("100"), CloseReason.STOP_LOSS);

		assertThat(result).contains(closed);
		verify(positions, never()).save(any());
		verify(portfolios, never()).save(any());
	}

	@Test
	void close_atTP_creditsProfit_incrementsWinCount() {
		Position open = openPosition(new BigDecimal("100"), new BigDecimal("95"), new BigDecimal("110"), new BigDecimal("40"));
		portfolio.setAvailableBalance(new BigDecimal("6000"));
		portfolio.setInvested(new BigDecimal("4000"));
		portfolio.setTotalFees(new BigDecimal("4")); // entry fee already booked

		when(positions.findByIdForUpdate(open.getId())).thenReturn(Optional.of(open));
		when(positions.save(any(Position.class))).thenAnswer(inv -> inv.getArgument(0));
		when(portfolios.save(any(Portfolio.class))).thenAnswer(inv -> inv.getArgument(0));

		Optional<Position> result = svc.close(open.getId(), new BigDecimal("110"), CloseReason.TAKE_PROFIT);

		assertThat(result).isPresent();
		Position closed = result.get();
		assertThat(closed.getStatus()).isEqualTo(PositionStatus.CLOSED);
		assertThat(closed.getCloseReason()).isEqualTo(CloseReason.TAKE_PROFIT);
		assertThat(closed.getRealizedPnl().signum()).isPositive();

		// Winning trade
		assertThat(portfolio.getWinningTrades()).isEqualTo(1);
		assertThat(portfolio.getLosingTrades()).isZero();
		assertThat(portfolio.getTotalTrades()).isEqualTo(1);
		// Invested returns to free balance + P&L
		assertThat(portfolio.getInvested()).isEqualByComparingTo("0");
	}

	@Test
	void close_atSL_debitsLoss_incrementsLossCount() {
		Position open = openPosition(new BigDecimal("100"), new BigDecimal("95"), new BigDecimal("110"), new BigDecimal("40"));
		portfolio.setAvailableBalance(new BigDecimal("6000"));
		portfolio.setInvested(new BigDecimal("4000"));
		portfolio.setTotalFees(new BigDecimal("4"));

		when(positions.findByIdForUpdate(open.getId())).thenReturn(Optional.of(open));
		when(positions.save(any(Position.class))).thenAnswer(inv -> inv.getArgument(0));
		when(portfolios.save(any(Portfolio.class))).thenAnswer(inv -> inv.getArgument(0));

		Optional<Position> result = svc.close(open.getId(), new BigDecimal("95"), CloseReason.STOP_LOSS);

		assertThat(result).isPresent();
		Position closed = result.get();
		assertThat(closed.getRealizedPnl().signum()).isNegative();
		assertThat(portfolio.getLosingTrades()).isEqualTo(1);
		assertThat(portfolio.getWinningTrades()).isZero();
	}

	@Test
	void close_missingPosition_returnsEmpty() {
		UUID id = UUID.randomUUID();
		when(positions.findByIdForUpdate(id)).thenReturn(Optional.empty());

		Optional<Position> result = svc.close(id, new BigDecimal("100"), CloseReason.MANUAL);

		assertThat(result).isEmpty();
	}

	@Test
	void openFromSignal_copiesStrategyIdAndVersion() {
		UUID strategyId = UUID.randomUUID();
		Signal signal = buildSignal(new BigDecimal("100"), new BigDecimal("95"), new BigDecimal("110"));
		signal.setStrategyId(strategyId);
		signal.setStrategyVersion(3);
		when(positions.findByPortfolioAndSignalId(portfolio, signal.getId())).thenReturn(Optional.empty());
		when(positions.saveAndFlush(any(Position.class))).thenAnswer(inv -> inv.getArgument(0));
		when(portfolios.save(any(Portfolio.class))).thenAnswer(inv -> inv.getArgument(0));

		Optional<Position> opened = svc.openFromSignal(portfolio, signal);

		assertThat(opened).isPresent();
		assertThat(opened.get().getStrategyId()).isEqualTo(strategyId);
		assertThat(opened.get().getStrategyVersion()).isEqualTo(3);
	}

	@Test
	void openFromSignal_short_setsCorrectSide() {
		Signal signal = buildShortSignal(new BigDecimal("100"), new BigDecimal("105"), new BigDecimal("90"));
		when(positions.findByPortfolioAndSignalId(portfolio, signal.getId())).thenReturn(Optional.empty());
		when(positions.saveAndFlush(any(Position.class))).thenAnswer(inv -> inv.getArgument(0));
		when(portfolios.save(any(Portfolio.class))).thenAnswer(inv -> inv.getArgument(0));

		Optional<Position> opened = svc.openFromSignal(portfolio, signal);

		assertThat(opened).isPresent();
		assertThat(opened.get().getSide()).isEqualTo(PositionSide.SHORT);
		assertThat(opened.get().getStopLoss()).isEqualByComparingTo("105");
		assertThat(opened.get().getTakeProfit1()).isEqualByComparingTo("90");
	}

	@Test
	void close_short_atTP_profits_whenPriceDropsBelowTP() {
		Position open = openShortPosition(new BigDecimal("100"), new BigDecimal("105"), new BigDecimal("90"), new BigDecimal("40"));
		portfolio.setAvailableBalance(new BigDecimal("6000"));
		portfolio.setInvested(new BigDecimal("4000"));
		portfolio.setTotalFees(new BigDecimal("4"));

		when(positions.findByIdForUpdate(open.getId())).thenReturn(Optional.of(open));
		when(positions.save(any(Position.class))).thenAnswer(inv -> inv.getArgument(0));
		when(portfolios.save(any(Portfolio.class))).thenAnswer(inv -> inv.getArgument(0));

		Optional<Position> result = svc.close(open.getId(), new BigDecimal("90"), CloseReason.TAKE_PROFIT);

		assertThat(result).isPresent();
		assertThat(result.get().getRealizedPnl().signum()).isPositive(); // (100-90)*40 net > 0
		assertThat(portfolio.getWinningTrades()).isEqualTo(1);
	}

	private Signal buildSignal(BigDecimal entry, BigDecimal stop, BigDecimal tp) {
		Signal s = new Signal();
		s.setId(UUID.randomUUID());
		s.setSymbol("BTCUSDT");
		s.setSide(PositionSide.LONG);
		s.setEntryPrice(entry);
		s.setStopLoss(stop);
		s.setTargetPrice(tp);
		s.setSuggestedRiskPercent(new BigDecimal("2.00"));
		return s;
	}

	private Signal buildShortSignal(BigDecimal entry, BigDecimal stop, BigDecimal tp) {
		Signal s = new Signal();
		s.setId(UUID.randomUUID());
		s.setSymbol("BTCUSDT");
		s.setSide(PositionSide.SHORT);
		s.setEntryPrice(entry);
		s.setStopLoss(stop);
		s.setTargetPrice(tp);
		s.setSuggestedRiskPercent(new BigDecimal("2.00"));
		return s;
	}

	private Position openShortPosition(BigDecimal entry, BigDecimal stop, BigDecimal tp, BigDecimal qty) {
		Position p = new Position();
		p.setId(UUID.randomUUID());
		p.setPortfolio(portfolio);
		p.setSymbol("BTCUSDT");
		p.setSide(PositionSide.SHORT);
		p.setEntryPrice(entry);
		p.setStopLoss(stop);
		p.setTakeProfit1(tp);
		p.setSize(qty);
		p.setNotional(entry.multiply(qty));
		p.setEntryFee(new BigDecimal("4"));
		p.setStatus(PositionStatus.OPEN);
		p.setOpenedAt(Instant.now());
		return p;
	}

	private Position openPosition(BigDecimal entry, BigDecimal stop, BigDecimal tp, BigDecimal qty) {
		Position p = new Position();
		p.setId(UUID.randomUUID());
		p.setPortfolio(portfolio);
		p.setSymbol("BTCUSDT");
		p.setSide(PositionSide.LONG);
		p.setEntryPrice(entry);
		p.setStopLoss(stop);
		p.setTakeProfit1(tp);
		p.setSize(qty);
		p.setNotional(entry.multiply(qty));
		p.setEntryFee(new BigDecimal("4"));
		p.setStatus(PositionStatus.OPEN);
		p.setOpenedAt(Instant.now());
		return p;
	}
}
