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
			BigDecimal u = pnl.grossPnl(p.getSide(), p.getEntryPrice(), current, p.remainingQty());
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

	/**
	 * The live price of this position, resolved within the market the position trades on.
	 *
	 * <p>Deliberately <b>not</b> a symbol-only lookup. The previous implementation probed spot first
	 * and fell back to futures, which is correct only while nothing can hold both markets under one
	 * symbol — but that is exactly the situation: a spot position and a futures position can both
	 * be open on {@code BTCUSDT} at the same time, and the probe would price both of them off the
	 * spot quote. A spot-first fallback is not a neutral default, it is a silent rule that futures
	 * positions get marked to spot.
	 *
	 * <p>When the market has no live quote yet, the price captured at open time is used, which is a
	 * real recorded value rather than a substituted one.
	 */
	public BigDecimal currentPrice(Position position) {
		return marketBook.ticker(position.effectiveTradingMode(), position.getSymbol())
				.map(t -> t.price())
				.orElseGet(position::getCurrentPrice);
	}

	public BigDecimal unrealized(Position position) {
		BigDecimal cur = currentPrice(position);
		if (cur == null) return BigDecimal.ZERO;
		return pnl.grossPnl(position.getSide(), position.getEntryPrice(), cur, position.remainingQty());
	}

	public BigDecimal unrealizedPct(Position position) {
		BigDecimal u = unrealized(position);
		return pnl.pctReturn(u, position.getNotional());
	}
}
