package com.shyblack.cryptosignals.service.paper;

import com.shyblack.cryptosignals.entity.Portfolio;
import com.shyblack.cryptosignals.entity.Position;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.PositionStatus;
import com.shyblack.cryptosignals.market.MarketBook;
import com.shyblack.cryptosignals.repository.PortfolioRepository;
import com.shyblack.cryptosignals.repository.PositionRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side of the paper-trading module. Refreshes unrealized P&L using the
 * latest MarketBook price on every read so the client always sees fresh
 * numbers without additional DB writes.
 */
@Service
@RequiredArgsConstructor
public class PaperTradingQueryService {

	private final PortfolioRepository portfolioRepository;
	private final PositionRepository positionRepository;
	private final PaperTradingAccountService accountService;
	private final PaperTradingPnLService pnl;
	private final MarketBook marketBook;

	public record AccountView(
			Portfolio portfolio,
			BigDecimal equity,
			BigDecimal unrealizedPnl,
			BigDecimal winRate
	) {}

	@Transactional(readOnly = true)
	public AccountView loadAccount(User user) {
		Portfolio portfolio = accountService.getOrCreate(user);
		List<Position> open = positionRepository.findByPortfolioAndStatus(portfolio, PositionStatus.OPEN);
		BigDecimal totalUnrealized = BigDecimal.ZERO;
		for (Position p : open) {
			BigDecimal current = currentPrice(p);
			BigDecimal u = pnl.grossPnl(p.getSide(), p.getEntryPrice(), current, p.getSize());
			totalUnrealized = totalUnrealized.add(u);
		}
		BigDecimal equity = portfolio.getAvailableBalance()
				.add(portfolio.getInvested())
				.add(totalUnrealized);
		BigDecimal winRate = portfolio.getTotalTrades() == 0
				? BigDecimal.ZERO
				: BigDecimal.valueOf(portfolio.getWinningTrades())
						.multiply(BigDecimal.valueOf(100))
						.divide(BigDecimal.valueOf(portfolio.getTotalTrades()),
								2, java.math.RoundingMode.HALF_UP);
		return new AccountView(portfolio, equity, totalUnrealized, winRate);
	}

	@Transactional(readOnly = true)
	public List<Position> listOpen(User user) {
		return positionRepository.findByPortfolio_UserAndPortfolio_AccountTypeAndStatusOrderByCreatedAtDesc(
				user, AccountType.PAPER, PositionStatus.OPEN);
	}

	@Transactional(readOnly = true)
	public List<Position> listHistory(User user) {
		return positionRepository.findByPortfolio_UserAndPortfolio_AccountTypeAndStatusOrderByCreatedAtDesc(
				user, AccountType.PAPER, PositionStatus.CLOSED);
	}

	@Transactional(readOnly = true)
	public List<Position> listAll(User user) {
		return positionRepository.findByPortfolio_UserAndPortfolio_AccountTypeOrderByCreatedAtDesc(
				user, AccountType.PAPER);
	}

	/** Prices are augmented from the live MarketBook so responses always reflect the latest quote. */
	public BigDecimal currentPrice(Position position) {
		return marketBook.spotTickers().get(position.getSymbol())
				.map(t -> t.price())
				.orElseGet(() -> marketBook.futuresTickers().get(position.getSymbol())
						.map(t -> t.price())
						.orElseGet(position::getCurrentPrice));
	}

	public BigDecimal unrealized(Position position) {
		BigDecimal cur = currentPrice(position);
		if (cur == null) return BigDecimal.ZERO;
		return pnl.grossPnl(position.getSide(), position.getEntryPrice(), cur, position.getSize());
	}

	public BigDecimal unrealizedPct(Position position) {
		BigDecimal u = unrealized(position);
		return pnl.pctReturn(u, position.getNotional());
	}
}
