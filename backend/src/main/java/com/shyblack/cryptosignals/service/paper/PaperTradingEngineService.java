package com.shyblack.cryptosignals.service.paper;

import com.shyblack.cryptosignals.config.PaperTradingProperties;
import com.shyblack.cryptosignals.entity.Portfolio;
import com.shyblack.cryptosignals.entity.Position;
import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.CloseReason;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.PositionStatus;
import com.shyblack.cryptosignals.market.MarketBook;
import com.shyblack.cryptosignals.market.MarketTicker;
import com.shyblack.cryptosignals.repository.PositionRepository;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the full paper-trading lifecycle:
 *
 *   1. When a new signal is committed, fan out to every user whose trading
 *      preferences match — one paper position per eligible user.
 *   2. Subscribe to the shared {@link MarketBook} price stream (Binance
 *      !ticker@arr) and evaluate SL/TP on every tick for every OPEN position
 *      whose symbol has just updated.
 *   3. On restart, positions in status=OPEN remain in the DB and are re-evaluated
 *      as soon as the first ticks arrive — no per-position timers needed.
 *
 * SL/TP priority: last-trade prices are our only granularity. When a single
 * tick crosses both SL and TP we CONSERVATIVELY treat it as SL_HIT (safer for
 * the trader in a real market). Documented in PAPER_TRADING_MODULE_REPORT.md.
 */
@Service
@RequiredArgsConstructor
public class PaperTradingEngineService {

	private static final Logger log = LoggerFactory.getLogger(PaperTradingEngineService.class);

	private final MarketBook marketBook;
	private final PositionRepository positionRepository;
	private final PaperTradingAccountService accountService;
	private final PaperTradingExecutionService executionService;
	private final PaperTradingProperties props;

	@PostConstruct
	void wireMarketListeners() {
		marketBook.spotTickers().addBatchListener(this::onTickBatch);
		marketBook.futuresTickers().addBatchListener(this::onTickBatch);
		log.info("[Paper] engine wired to spot + futures ticker streams");
	}

	/**
	 * Entry point for one specific paper account.
	 *
	 * <p>Since Phase 8 this engine no longer listens for {@code SignalGeneratedEvent}
	 * itself: {@code service.execution.ExecutionRouter} is the single authoritative
	 * routing decision and calls this method for each paper candidate. The engine
	 * therefore performs no fan-out and no mode gating of its own — both are the
	 * router's responsibility — while the actual position-opening behaviour below
	 * is unchanged.
	 *
	 * <p>Public so the router can delegate. It is not a Spring bean method any
	 * other component should call.
	 */
	public void executeForUser(User user, Signal signal) {
		try {
			openForUser(user, signal);
		} catch (Exception ex) {
			log.warn("[Paper] failed to open position for user={} signal={} err={}",
					user.getId(), signal.getId(), ex.getMessage());
		}
	}

	private void openForUser(User user, Signal signal) {
		Portfolio portfolio = accountService.getOrCreate(user);
		long openCount = positionRepository
				.findByPortfolioAndStatus(portfolio, PositionStatus.OPEN)
				.size();
		if (openCount >= props.maxActivePositions()) {
			log.info("[Paper] user={} at max active positions ({}) — rejecting signal={}",
					user.getId(), openCount, signal.getId());
			return;
		}
		Optional<Position> opened = executionService.openFromSignal(portfolio, signal);
		opened.ifPresent(p -> log.info("[Paper] user={} signal={} opened position id={} symbol={} qty={} entry={}",
				user.getId(), signal.getId(), p.getId(), p.getSymbol(), p.getSize(), p.getEntryPrice()));
	}

	/** Called for every batch of Binance ticks. Runs outside a transaction so DB is only touched when we act. */
	void onTickBatch(List<MarketTicker> ticks) {
		if (ticks == null || ticks.isEmpty()) return;
		for (MarketTicker tick : ticks) {
			try {
				evaluateSymbol(tick.symbol(), tick.price());
			} catch (Exception ex) {
				log.warn("[Paper] evaluate failed symbol={} err={}", tick.symbol(), ex.getMessage());
			}
		}
	}

	/** Returns open PAPER positions for the given symbol. Repository provides its own transaction. */
	protected List<Position> openPositionsForSymbol(String symbol) {
		return positionRepository.findByStatusAndPortfolio_AccountType(PositionStatus.OPEN, AccountType.PAPER)
				.stream()
				.filter(p -> symbol.equals(p.getSymbol()))
				.toList();
	}

	private void evaluateSymbol(String symbol, BigDecimal lastPrice) {
		if (symbol == null || lastPrice == null || lastPrice.signum() <= 0) return;
		List<Position> open = openPositionsForSymbol(symbol);
		for (Position p : open) {
			// Legacy single-TP positions keep the original exact behavior.
			if (p.getTakeProfit2() == null && p.getTakeProfit3() == null) {
				CloseReason trigger = evaluateTrigger(p, lastPrice);
				if (trigger != null) {
					executionService.close(p.getId(), lastPrice, trigger);
					log.info("[Paper] {} triggered {} on position={} @ {}", symbol, trigger, p.getId(), lastPrice);
				}
				continue;
			}
			evaluatePartial(p, lastPrice);
		}
	}

	/** NFM partial-exit evaluation (SL first, then TP1/TP2/TP3, each once). */
	private void evaluatePartial(Position p, BigDecimal price) {
		PositionSide side = p.getSide();
		// 1) SL first (conservative on a same-tick SL+TP).
		if (stopTouched(side, price, p.getStopLoss())) {
			executionService.partialClose(p.getId(), price, p.remainingQty(), CloseReason.STOP_LOSS);
			log.info("[Paper] {} partial STOP_LOSS remaining on position={} @ {}", p.getSymbol(), p.getId(), price);
			return;
		}
		BigDecimal original = p.originalQty();
		BigDecimal third = original.divide(BigDecimal.valueOf(3), 8, RoundingMode.DOWN);
		BigDecimal remaining = p.remainingQty();

		if (!p.isTp1Hit() && remaining.signum() > 0 && tpTouched(side, price, p.getTakeProfit1())) {
			BigDecimal q = third.min(remaining);
			executionService.partialClose(p.getId(), price, q, CloseReason.TAKE_PROFIT);
			remaining = remaining.subtract(q);
			log.info("[Paper] {} TP1 partial qty={} position={}", p.getSymbol(), q, p.getId());
		}
		if (!p.isTp2Hit() && remaining.signum() > 0 && tpTouched(side, price, p.getTakeProfit2())) {
			BigDecimal q = third.min(remaining);
			executionService.partialClose(p.getId(), price, q, CloseReason.TAKE_PROFIT);
			remaining = remaining.subtract(q);
			log.info("[Paper] {} TP2 partial qty={} position={}", p.getSymbol(), q, p.getId());
		}
		if (!p.isTp3Hit() && remaining.signum() > 0 && tpTouched(side, price, p.getTakeProfit3())) {
			executionService.partialClose(p.getId(), price, remaining, CloseReason.TAKE_PROFIT);
			log.info("[Paper] {} TP3 close remaining position={}", p.getSymbol(), p.getId());
		}
	}

	private static boolean stopTouched(PositionSide side, BigDecimal price, BigDecimal stop) {
		if (stop == null) return false;
		return side == PositionSide.LONG ? price.compareTo(stop) <= 0 : price.compareTo(stop) >= 0;
	}

	private static boolean tpTouched(PositionSide side, BigDecimal price, BigDecimal tp) {
		if (tp == null) return false;
		return side == PositionSide.LONG ? price.compareTo(tp) >= 0 : price.compareTo(tp) <= 0;
	}

	private CloseReason evaluateTrigger(Position position, BigDecimal price) {
		BigDecimal sl = position.getStopLoss();
		BigDecimal tp = position.getTakeProfit1();
		if (position.getSide() == PositionSide.LONG) {
			// SL wins on tie (conservative). Long: SL is below entry, TP is above.
			if (sl != null && price.compareTo(sl) <= 0) return CloseReason.STOP_LOSS;
			if (tp != null && price.compareTo(tp) >= 0) return CloseReason.TAKE_PROFIT;
		} else {
			if (sl != null && price.compareTo(sl) >= 0) return CloseReason.STOP_LOSS;
			if (tp != null && price.compareTo(tp) <= 0) return CloseReason.TAKE_PROFIT;
		}
		return null;
	}
}
