package com.shyblack.cryptosignals.service.paper;

import com.shyblack.cryptosignals.entity.Portfolio;
import com.shyblack.cryptosignals.entity.Position;
import com.shyblack.cryptosignals.entity.PositionLifecycleEvent;
import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.enums.CloseReason;
import com.shyblack.cryptosignals.entity.enums.PositionLifecycleEventType;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.PositionStatus;
import com.shyblack.cryptosignals.repository.PortfolioRepository;
import com.shyblack.cryptosignals.repository.PositionLifecycleEventRepository;
import com.shyblack.cryptosignals.repository.PositionRepository;
import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one and only path through which paper positions are opened and closed.
 *
 * All balance mutations, fee bookings, lifecycle-event writes, and portfolio
 * statistics live here so callers (signal engine, price monitor, controller
 * manual-close, admin reset) get identical semantics.
 *
 * Concurrency:
 *   - Position uniqueness on (portfolio_id, signal_id) prevents duplicate opens.
 *   - Portfolio row is locked PESSIMISTIC_WRITE inside each mutation so two
 *     simultaneous SL/TP evaluations cannot double-count P&L.
 *   - Close is idempotent: if the position is already CLOSED we return the
 *     already-closed row without further mutation.
 */
@Service
@RequiredArgsConstructor
public class PaperTradingExecutionService {

	private static final Logger log = LoggerFactory.getLogger(PaperTradingExecutionService.class);

	private final PortfolioRepository portfolioRepository;
	private final PositionRepository positionRepository;
	private final PositionLifecycleEventRepository lifecycleRepository;
	private final PaperTradingPnLService pnl;
	private final PaperTradingSizingService sizing;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Optional<Position> openFromSignal(Portfolio portfolioRef, Signal signal) {
		Portfolio portfolio = portfolioRepository.findByIdForUpdate(portfolioRef.getId())
				.orElseThrow(() -> new IllegalStateException("Portfolio missing: " + portfolioRef.getId()));

		Optional<Position> existing = positionRepository.findByPortfolioAndSignalId(portfolio, signal.getId());
		if (existing.isPresent()) {
			return existing; // idempotent — same signal already produced a trade for this user
		}

		BigDecimal fillPrice = pnl.applySlippage(signal.getEntryPrice(), signal.getSide(), true);
		PaperTradingSizingService.Sizing s = sizing.size(portfolio, signal, fillPrice);
		if (s == null) {
			log.info("[Paper] rejected signal={} portfolio={} reason=INVALID_SIZE",
					signal.getId(), portfolio.getId());
			return Optional.empty();
		}

		BigDecimal notional = s.notional();
		BigDecimal entryFee = pnl.fee(notional);
		BigDecimal totalDebit = notional.add(entryFee);

		if (portfolio.getAvailableBalance().compareTo(totalDebit) < 0) {
			log.info("[Paper] rejected signal={} portfolio={} reason=INSUFFICIENT_BALANCE",
					signal.getId(), portfolio.getId());
			return Optional.empty();
		}

		Instant now = Instant.now();

		Position p = new Position();
		p.setPortfolio(portfolio);
		p.setSignalId(signal.getId());
		p.setSymbol(signal.getSymbol());
		p.setSide(signal.getSide());
		p.setSize(s.quantity());
		p.setNotional(notional);
		p.setEntryPrice(fillPrice);
		p.setCurrentPrice(fillPrice);
		p.setStopLoss(signal.getStopLoss());
		p.setTakeProfit1(signal.getTargetPrice());
		p.setTakeProfit2(signal.getTargetPrice2());
		p.setTakeProfit3(signal.getTargetPrice3());
		p.setEntryFee(entryFee);
		p.setStatus(PositionStatus.OPEN);
		p.setOpenedAt(now);
		p.setRealizedPnl(BigDecimal.ZERO);
		p.setUnrealizedPnl(BigDecimal.ZERO);

		try {
			p = positionRepository.saveAndFlush(p);
		} catch (DataIntegrityViolationException dup) {
			// unique(portfolio_id, signal_id) — someone else opened it just now
			return positionRepository.findByPortfolioAndSignalId(portfolio, signal.getId());
		}

		portfolio.setAvailableBalance(portfolio.getAvailableBalance().subtract(totalDebit));
		portfolio.setInvested(portfolio.getInvested().add(notional));
		portfolio.setTotalFees(portfolio.getTotalFees().add(entryFee));
		portfolioRepository.save(portfolio);

		writeEvent(p, PositionLifecycleEventType.CREATED, fillPrice, null, "opened from signal " + signal.getId());
		writeEvent(p, PositionLifecycleEventType.OPENED, fillPrice, null,
				"qty=" + s.quantity() + " notional=" + notional + " entryFee=" + entryFee);

		return Optional.of(p);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Optional<Position> close(UUID positionId, BigDecimal referencePrice, CloseReason reason) {
		Position locked;
		try {
			locked = positionRepository.findByIdForUpdate(positionId).orElse(null);
		} catch (ObjectOptimisticLockingFailureException | OptimisticLockException ex) {
			return positionRepository.findById(positionId);
		}
		if (locked == null) return Optional.empty();
		if (locked.getStatus() == PositionStatus.CLOSED) {
			return Optional.of(locked); // idempotent
		}

		BigDecimal exitPrice = pnl.applySlippage(referencePrice, locked.getSide(), false);
		BigDecimal exitNotional = pnl.notional(exitPrice, locked.getSize());
		BigDecimal exitFee = pnl.fee(exitNotional);
		BigDecimal gross = pnl.grossPnl(locked.getSide(), locked.getEntryPrice(), exitPrice, locked.getSize());
		BigDecimal net = pnl.netPnl(gross, safe(locked.getEntryFee()), exitFee);

		locked.setExitPrice(exitPrice);
		locked.setExitFee(exitFee);
		locked.setRealizedPnl(net);
		locked.setUnrealizedPnl(BigDecimal.ZERO);
		locked.setCurrentPrice(exitPrice);
		locked.setStatus(PositionStatus.CLOSED);
		locked.setClosedAt(Instant.now());
		locked.setCloseReason(reason);

		Position saved = positionRepository.save(locked);

		Portfolio portfolio = portfolioRepository.findByIdForUpdate(locked.getPortfolio().getId())
				.orElseThrow();
		BigDecimal entryNotional = safe(locked.getNotional());
		// Release margin + realize net P&L; fees were already booked at open/close time.
		portfolio.setInvested(portfolio.getInvested().subtract(entryNotional).max(BigDecimal.ZERO));
		portfolio.setAvailableBalance(portfolio.getAvailableBalance()
				.add(entryNotional)
				.add(gross)
				.subtract(exitFee));
		portfolio.setRealizedPnl(portfolio.getRealizedPnl().add(net));
		portfolio.setTotalFees(portfolio.getTotalFees().add(exitFee));
		portfolio.setTotalTrades(portfolio.getTotalTrades() + 1);
		if (net.signum() > 0) portfolio.setWinningTrades(portfolio.getWinningTrades() + 1);
		else if (net.signum() < 0) portfolio.setLosingTrades(portfolio.getLosingTrades() + 1);
		// totalBalance = availableBalance + invested (mark-to-close, so invested is 0 for closed)
		portfolio.setTotalBalance(portfolio.getAvailableBalance().add(portfolio.getInvested()));
		portfolioRepository.save(portfolio);

		PositionLifecycleEventType type = switch (reason) {
			case STOP_LOSS -> PositionLifecycleEventType.SL_HIT;
			case TAKE_PROFIT -> PositionLifecycleEventType.TP_HIT;
			case MANUAL -> PositionLifecycleEventType.MANUAL_CLOSE;
			default -> PositionLifecycleEventType.CLOSED;
		};
		writeEvent(saved, type, exitPrice, net,
				"reason=" + reason + " exitFee=" + exitFee + " gross=" + gross);
		writeEvent(saved, PositionLifecycleEventType.CLOSED, exitPrice, net, "closed reason=" + reason);

		return Optional.of(saved);
	}

	private void writeEvent(Position position, PositionLifecycleEventType type,
			BigDecimal price, BigDecimal pnlValue, String message) {
		PositionLifecycleEvent event = new PositionLifecycleEvent();
		event.setPosition(position);
		event.setEventType(type);
		event.setPrice(price);
		event.setPnl(pnlValue);
		event.setMessage(message);
		lifecycleRepository.save(event);
	}

	private static BigDecimal safe(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}
}
