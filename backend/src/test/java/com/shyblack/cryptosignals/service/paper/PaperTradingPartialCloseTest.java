package com.shyblack.cryptosignals.service.paper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shyblack.cryptosignals.config.PaperTradingProperties;
import com.shyblack.cryptosignals.entity.Portfolio;
import com.shyblack.cryptosignals.entity.Position;
import com.shyblack.cryptosignals.entity.enums.CloseReason;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.PositionStatus;
import com.shyblack.cryptosignals.repository.PortfolioRepository;
import com.shyblack.cryptosignals.repository.PositionLifecycleEventRepository;
import com.shyblack.cryptosignals.repository.PositionRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** FIXTURE TEST — paper partial-close accounting + idempotency (mocked persistence). */
class PaperTradingPartialCloseTest {

	private final PortfolioRepository portfolioRepository = mock(PortfolioRepository.class);
	private final PositionRepository positionRepository = mock(PositionRepository.class);
	private final PositionLifecycleEventRepository lifecycleRepository = mock(PositionLifecycleEventRepository.class);
	private final PaperTradingSizingService sizing = mock(PaperTradingSizingService.class);

	private PaperTradingExecutionService service;
	private Portfolio portfolio;
	private Position position;

	@BeforeEach
	void setUp() {
		PaperTradingProperties props = new PaperTradingProperties(
				new BigDecimal("0.10"), new BigDecimal("0.05"), new BigDecimal("100"), 10,
				new BigDecimal("2.00"), "USDT");
		PaperTradingPnLService pnl = new PaperTradingPnLService(props);
		service = new PaperTradingExecutionService(portfolioRepository, positionRepository,
				lifecycleRepository, pnl, sizing);

		portfolio = new Portfolio();
		portfolio.setAvailableBalance(new BigDecimal("1000"));
		portfolio.setInvested(new BigDecimal("300"));
		portfolio.setRealizedPnl(BigDecimal.ZERO);
		portfolio.setTotalFees(new BigDecimal("0.30"));
		portfolio.setTotalTrades(0);
		portfolio.setWinningTrades(0);
		portfolio.setLosingTrades(0);
		portfolio.setTotalBalance(new BigDecimal("1000"));

		position = new Position();
		position.setId(UUID.randomUUID());
		position.setPortfolio(portfolio);
		position.setSymbol("BTCUSDT");
		position.setSide(PositionSide.LONG);
		position.setSize(new BigDecimal("3"));
		position.setEntryPrice(new BigDecimal("100"));
		position.setStopLoss(new BigDecimal("99"));
		position.setTakeProfit1(new BigDecimal("101.5"));
		position.setTakeProfit2(new BigDecimal("102.5"));
		position.setTakeProfit3(new BigDecimal("104"));
		position.setEntryFee(new BigDecimal("0.30"));
		position.setRealizedPnl(BigDecimal.ZERO);
		position.setUnrealizedPnl(BigDecimal.ZERO);
		position.setStatus(PositionStatus.OPEN);

		when(positionRepository.findByIdForUpdate(position.getId())).thenReturn(Optional.of(position));
		when(positionRepository.save(any(Position.class))).thenAnswer(inv -> inv.getArgument(0));
		when(portfolioRepository.findByIdForUpdate(any())).thenReturn(Optional.of(portfolio));
		when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(inv -> inv.getArgument(0));
		when(lifecycleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
	}

	@Test
	void long_tp1_tp2_tp3_conservesQuantity_andReconciles() {
		service.partialClose(position.getId(), new BigDecimal("101.5"), new BigDecimal("1"), CloseReason.TAKE_PROFIT);
		assertThat(position.isTp1Hit()).isTrue();
		assertThat(position.remainingQty()).isEqualByComparingTo("2");
		assertThat(position.getStatus()).isEqualTo(PositionStatus.OPEN);

		service.partialClose(position.getId(), new BigDecimal("102.5"), new BigDecimal("1"), CloseReason.TAKE_PROFIT);
		assertThat(position.isTp2Hit()).isTrue();
		assertThat(position.remainingQty()).isEqualByComparingTo("1");

		service.partialClose(position.getId(), new BigDecimal("104"), new BigDecimal("1"), CloseReason.TAKE_PROFIT);
		assertThat(position.isTp3Hit()).isTrue();
		assertThat(position.getStatus()).isEqualTo(PositionStatus.CLOSED);
		assertThat(position.getCloseReason()).isEqualTo(CloseReason.TAKE_PROFIT);
		assertThat(position.getRemainingSize()).isEqualByComparingTo("0");
		assertThat(position.originalQty()).isEqualByComparingTo("3");
		// trade counted exactly once, on final slice
		assertThat(portfolio.getTotalTrades()).isEqualTo(1);
		// realized PnL reconciles: gross - entryFee - exitFees (all positive here)
		assertThat(position.getRealizedPnl()).isGreaterThan(BigDecimal.ZERO);
	}

	@Test
	void slAfterTp1_closesOnlyRemaining() {
		service.partialClose(position.getId(), new BigDecimal("101.5"), new BigDecimal("1"), CloseReason.TAKE_PROFIT);
		service.partialClose(position.getId(), new BigDecimal("99"), position.remainingQty(), CloseReason.STOP_LOSS);
		assertThat(position.getStatus()).isEqualTo(PositionStatus.CLOSED);
		assertThat(position.getCloseReason()).isEqualTo(CloseReason.STOP_LOSS);
		assertThat(position.getRemainingSize()).isEqualByComparingTo("0");
		assertThat(portfolio.getTotalTrades()).isEqualTo(1);
	}

	@Test
	void closedPosition_isIdempotent_noFurtherChange() {
		service.partialClose(position.getId(), new BigDecimal("104"), new BigDecimal("3"), CloseReason.TAKE_PROFIT);
		assertThat(position.getStatus()).isEqualTo(PositionStatus.CLOSED);
		BigDecimal pnlAfterClose = position.getRealizedPnl();
		int trades = portfolio.getTotalTrades();

		service.partialClose(position.getId(), new BigDecimal("104"), new BigDecimal("3"), CloseReason.TAKE_PROFIT);
		assertThat(position.getRealizedPnl()).isEqualByComparingTo(pnlAfterClose);
		assertThat(portfolio.getTotalTrades()).isEqualTo(trades);
	}

	@Test
	void quantityIsClampedToRemaining_neverNegative() {
		service.partialClose(position.getId(), new BigDecimal("104"), new BigDecimal("999"), CloseReason.TAKE_PROFIT);
		assertThat(position.getRemainingSize()).isEqualByComparingTo("0");
		assertThat(position.getStatus()).isEqualTo(PositionStatus.CLOSED);
		assertThat(position.remainingQty()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
		assertThat(position.remainingQty()).isLessThanOrEqualTo(position.originalQty());
	}

	@Test
	void zeroQuantity_isIgnored() {
		service.partialClose(position.getId(), new BigDecimal("101.5"), BigDecimal.ZERO, CloseReason.TAKE_PROFIT);
		assertThat(position.getStatus()).isEqualTo(PositionStatus.OPEN);
		assertThat(position.isTp1Hit()).isFalse();
		assertThat(position.remainingQty()).isEqualByComparingTo("3");
	}
}
