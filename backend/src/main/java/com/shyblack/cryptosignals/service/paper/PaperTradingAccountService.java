package com.shyblack.cryptosignals.service.paper;

import com.shyblack.cryptosignals.config.PaperTradingProperties;
import com.shyblack.cryptosignals.entity.PaperCapitalEvent;
import com.shyblack.cryptosignals.entity.Portfolio;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.PaperCapitalEventType;
import com.shyblack.cryptosignals.exception.BadRequestException;
import com.shyblack.cryptosignals.repository.PaperCapitalEventRepository;
import com.shyblack.cryptosignals.repository.PortfolioRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages a user's simulated paper-trading account. The account is a
 * {@link Portfolio} with {@link AccountType#PAPER}. If none exists we lazily
 * create one seeded with the configured initial balance.
 *
 * This service NEVER touches a LIVE portfolio and NEVER places real orders —
 * paper balances are completely isolated from any exchange integration.
 *
 * <p>All mutations use {@link Propagation#REQUIRES_NEW}: this service is
 * invoked from the {@code SignalGeneratedEvent} AFTER_COMMIT listener, where the
 * original (already committed) transaction is still bound to the thread. With
 * default propagation the freshly created Portfolio would join that completed
 * transaction and never commit, so the subsequent {@code REQUIRES_NEW}
 * execution transaction would fail with "Portfolio missing".</p>
 */
@Service
@RequiredArgsConstructor
public class PaperTradingAccountService {

	private final PortfolioRepository portfolioRepository;
	private final PaperTradingProperties props;
	private final PaperCapitalEventRepository capitalEventRepository;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Portfolio getOrCreate(User user) {
		return portfolioRepository.findFirstByUserAndAccountType(user, AccountType.PAPER)
				.orElseGet(() -> createFresh(user));
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Portfolio reset(Portfolio portfolioRef) {
		// Re-load under a pessimistic lock: closing open positions (also a
		// REQUIRES_NEW tx) bumps the Portfolio @Version, so the reference passed
		// in by the caller is stale and would fail with StaleObjectStateException.
		Portfolio portfolio = portfolioRepository.findByIdForUpdate(portfolioRef.getId())
				.orElseThrow(() -> new BadRequestException("Paper account not found"));
		BigDecimal previousBalance = totalBalance(portfolio);
		BigDecimal initial = props.initialBalance();
		portfolio.setInitialBalance(initial);
		portfolio.setTotalBalance(initial);
		portfolio.setAvailableBalance(initial);
		portfolio.setInvested(BigDecimal.ZERO);
		portfolio.setRealizedPnl(BigDecimal.ZERO);
		portfolio.setTotalFees(BigDecimal.ZERO);
		portfolio.setTotalTrades(0);
		portfolio.setWinningTrades(0);
		portfolio.setLosingTrades(0);
		Portfolio saved = portfolioRepository.save(portfolio);
		record(saved, PaperCapitalEventType.RESET,
				initial.subtract(previousBalance), previousBalance, initial,
				"Paper account reset");
		return saved;
	}

	// ── Capital adjustments (paper only) ─────────────────────────────

	/**
	 * Adds capital to the paper account and audits the movement.
	 *
	 * <p>Only free cash moves; {@code initialBalance} stays the seed value so the
	 * ledger — not a mutated constant — explains the balance. Open positions are
	 * untouched and no LIVE portfolio is reachable from here.</p>
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Portfolio addCapital(Portfolio portfolioRef, BigDecimal amount, String reason) {
		BigDecimal delta = requirePositive(amount);
		Portfolio locked = lockPaper(portfolioRef);
		BigDecimal previous = totalBalance(locked);
		locked.setAvailableBalance(scaled(safe(locked.getAvailableBalance()).add(delta)));
		locked.setTotalBalance(totalBalance(locked));
		Portfolio saved = portfolioRepository.save(locked);
		record(saved, PaperCapitalEventType.ADD, delta, previous, totalBalance(saved), reason);
		return saved;
	}

	/**
	 * Withdraws capital from the paper account and audits the movement.
	 *
	 * <p>Bounded by available cash, not by equity: capital committed to an open
	 * position is not withdrawable and unrealised P&amp;L has not been realised.
	 * A withdrawal beyond free cash is rejected rather than allowed to strand an
	 * open position with no backing.</p>
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Portfolio reduceCapital(Portfolio portfolioRef, BigDecimal amount, String reason) {
		BigDecimal delta = requirePositive(amount);
		Portfolio locked = lockPaper(portfolioRef);
		BigDecimal available = safe(locked.getAvailableBalance());
		if (available.compareTo(delta) < 0) {
			throw new BadRequestException("Cannot reduce capital by " + delta
					+ ": only " + available + " is available"
					+ (safe(locked.getInvested()).signum() > 0
							? " (capital is committed to open positions)" : ""));
		}
		BigDecimal previous = totalBalance(locked);
		locked.setAvailableBalance(scaled(available.subtract(delta)));
		locked.setTotalBalance(totalBalance(locked));
		Portfolio saved = portfolioRepository.save(locked);
		record(saved, PaperCapitalEventType.REDUCE, delta.negate(), previous,
				totalBalance(saved), reason);
		return saved;
	}

	/** Newest-first capital ledger for the caller's paper account. */
	@Transactional(readOnly = true)
	public List<PaperCapitalEvent> capitalHistory(Portfolio portfolio, int limit) {
		int bounded = limit <= 0 ? 50 : Math.min(limit, 200);
		return capitalEventRepository.findByPortfolioOrderByCreatedAtDesc(
				portfolio, PageRequest.of(0, bounded));
	}

	// ── Helpers ──────────────────────────────────────────────────────

	private Portfolio lockPaper(Portfolio portfolioRef) {
		if (portfolioRef == null) throw new BadRequestException("Paper account not found");
		Portfolio locked = portfolioRepository.findByIdForUpdate(portfolioRef.getId())
				.orElseThrow(() -> new BadRequestException("Paper account not found"));
		if (locked.getAccountType() != AccountType.PAPER) {
			// Defence in depth: capital management must never reach a live account.
			throw new BadRequestException(
					"Capital management is only available on paper accounts");
		}
		return locked;
	}

	private void record(Portfolio portfolio, PaperCapitalEventType type, BigDecimal amount,
			BigDecimal previous, BigDecimal next, String reason) {
		PaperCapitalEvent event = new PaperCapitalEvent();
		event.setPortfolio(portfolio);
		event.setEventType(type);
		event.setAmount(scaled(amount));
		event.setPreviousBalance(scaled(previous));
		event.setNewBalance(scaled(next));
		event.setReason(reason == null || reason.isBlank() ? null : reason.trim());
		capitalEventRepository.save(event);
	}

	private static BigDecimal requirePositive(BigDecimal amount) {
		if (amount == null) throw new BadRequestException("amount is required");
		if (amount.signum() <= 0) {
			throw new BadRequestException("amount must be greater than zero");
		}
		return scaled(amount);
	}

	/** Paper invariant: total capital currently held = free cash + capital at work. */
	private static BigDecimal totalBalance(Portfolio portfolio) {
		return scaled(safe(portfolio.getAvailableBalance()).add(safe(portfolio.getInvested())));
	}

	private static BigDecimal scaled(BigDecimal value) {
		return value.setScale(8, RoundingMode.HALF_UP);
	}

	/**
	 * Update the initial capital of a paper account that has no trading history.
	 * Rejects zero/negative values and refuses to rewrite an account that already
	 * has trades or open positions, so historical accounting is never corrupted.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Portfolio updateInitialCapital(Portfolio portfolio, BigDecimal newInitialCapital) {
		if (newInitialCapital == null || newInitialCapital.signum() <= 0) {
			throw new BadRequestException("Initial capital must be greater than zero");
		}
		Portfolio locked = portfolioRepository.findByIdForUpdate(portfolio.getId())
				.orElseThrow(() -> new BadRequestException("Paper account not found"));
		boolean hasHistory = locked.getTotalTrades() > 0
				|| safe(locked.getInvested()).signum() > 0
				|| safe(locked.getRealizedPnl()).signum() != 0;
		if (hasHistory) {
			throw new BadRequestException(
					"Initial capital cannot be changed once the paper account has trades or open positions; reset the account instead");
		}
		BigDecimal scaled = newInitialCapital.setScale(8, RoundingMode.HALF_UP);
		locked.setInitialBalance(scaled);
		locked.setTotalBalance(scaled);
		locked.setAvailableBalance(scaled);
		return portfolioRepository.save(locked);
	}

	private Portfolio createFresh(User user) {
		Portfolio p = new Portfolio();
		p.setUser(user);
		p.setName("Paper Trading");
		p.setAccountType(AccountType.PAPER);
		p.setQuoteCurrency(props.defaultQuoteCurrency());
		BigDecimal initial = props.initialBalance();
		p.setInitialBalance(initial);
		p.setTotalBalance(initial);
		p.setAvailableBalance(initial);
		p.setInvested(BigDecimal.ZERO);
		p.setRealizedPnl(BigDecimal.ZERO);
		p.setTotalFees(BigDecimal.ZERO);
		Portfolio saved = portfolioRepository.save(p);
		record(saved, PaperCapitalEventType.INITIAL, initial, BigDecimal.ZERO, initial,
				"Paper account created");
		return saved;
	}

	private static BigDecimal safe(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}
}
