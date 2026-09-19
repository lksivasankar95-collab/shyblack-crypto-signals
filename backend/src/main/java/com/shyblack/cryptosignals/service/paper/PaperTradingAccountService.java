package com.shyblack.cryptosignals.service.paper;

import com.shyblack.cryptosignals.config.PaperTradingProperties;
import com.shyblack.cryptosignals.entity.Portfolio;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.repository.PortfolioRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages a user's simulated paper-trading account. The account is a
 * {@link Portfolio} with {@link AccountType#PAPER}. If none exists we lazily
 * create one seeded with the configured initial balance.
 *
 * This service NEVER touches a LIVE portfolio and NEVER places real orders —
 * paper balances are completely isolated from any exchange integration.
 */
@Service
@RequiredArgsConstructor
public class PaperTradingAccountService {

	private final PortfolioRepository portfolioRepository;
	private final PaperTradingProperties props;

	@Transactional
	public Portfolio getOrCreate(User user) {
		return portfolioRepository.findFirstByUserAndAccountType(user, AccountType.PAPER)
				.orElseGet(() -> createFresh(user));
	}

	@Transactional
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
}
