package com.shyblack.cryptosignals.service.paper;

import com.shyblack.cryptosignals.config.PaperTradingProperties;
import com.shyblack.cryptosignals.entity.Portfolio;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.exception.BadRequestException;
import com.shyblack.cryptosignals.repository.PortfolioRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
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

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Portfolio getOrCreate(User user) {
		return portfolioRepository.findFirstByUserAndAccountType(user, AccountType.PAPER)
				.orElseGet(() -> createFresh(user));
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Portfolio reset(Portfolio portfolio) {
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
		return portfolioRepository.save(portfolio);
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
		return portfolioRepository.save(p);
	}

	private static BigDecimal safe(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}
}
