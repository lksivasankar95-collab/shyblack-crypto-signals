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
import com.shyblack.cryptosignals.entity.enums.SignalStatus;
import com.shyblack.cryptosignals.market.MarketBook;
import com.shyblack.cryptosignals.market.MarketTicker;
import com.shyblack.cryptosignals.repository.PositionRepository;
import com.shyblack.cryptosignals.repository.SignalRepository;
import com.shyblack.cryptosignals.repository.UserRepository;
import com.shyblack.cryptosignals.service.SignalGeneratedEvent;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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
	private final SignalRepository signalRepository;
	private final PositionRepository positionRepository;
	private final UserRepository userRepository;
	private final PaperTradingAccountService accountService;
	private final PaperTradingExecutionService executionService;
	private final PaperTradingProperties props;

	@PostConstruct
	void wireMarketListeners() {
		marketBook.spotTickers().addBatchListener(this::onTickBatch);
		marketBook.futuresTickers().addBatchListener(this::onTickBatch);
		log.info("[Paper] engine wired to spot + futures ticker streams");
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onSignalGenerated(SignalGeneratedEvent event) {
		Optional<Signal> maybeSignal = signalRepository.findById(event.getSignalId());
		if (maybeSignal.isEmpty()) {
			log.warn("[Paper] SignalGeneratedEvent id={} not found in DB", event.getSignalId());
			return;
		}
		Signal signal = maybeSignal.get();
		if (signal.getStatus() != SignalStatus.ACTIVE) {
			log.debug("[Paper] skipping non-ACTIVE signal {} status={}", signal.getId(), signal.getStatus());
			return;
		}
		fanOutForSignal(signal);
	}

	private void fanOutForSignal(Signal signal) {
		// Every user with an active PAPER account whose tradingMode matches the signal gets a trade.
		List<User> users = userRepository.findAll();
		for (User user : users) {
			if (!user.isEnabled()) continue;
			if (user.getAccountType() != AccountType.PAPER) continue;
			if (user.getTradingMode() != signal.getTradingMode()) continue;
			try {
				openForUser(user, signal);
			} catch (Exception ex) {
				log.warn("[Paper] failed to open position for user={} signal={} err={}",
						user.getId(), signal.getId(), ex.getMessage());
			}
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
			CloseReason trigger = evaluateTrigger(p, lastPrice);
			if (trigger != null) {
				executionService.close(p.getId(), lastPrice, trigger);
				log.info("[Paper] {} triggered {} on position={} @ {}", symbol, trigger, p.getId(), lastPrice);
			}
		}
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
