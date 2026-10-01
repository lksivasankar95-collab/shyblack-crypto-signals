package com.shyblack.cryptosignals.service.portfolio;

import com.shyblack.cryptosignals.entity.PortfolioExchangeBalance;
import com.shyblack.cryptosignals.entity.PortfolioExchangePosition;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.repository.PortfolioExchangeBalanceRepository;
import com.shyblack.cryptosignals.repository.PortfolioExchangePositionRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * REST reconciliation for the LIVE scopes.
 *
 * <p>REST is the authoritative baseline; the WebSocket only supplies increments. Whenever the
 * stream drops, an event cannot be applied incrementally, or a snapshot is missing, this service
 * re-reads the exchange and repairs the local read snapshot before the stream is allowed to resume.
 * It never assumes a reconnect missed nothing.
 *
 * <p>Reconciles LIVE SPOT and LIVE FUTURES only. PAPER, OPTIONS and trading orders are never
 * touched.
 */
@Service
public class LivePortfolioReconcileService {

	/** What a reconciliation changed, so callers can log or assert on the outcome. */
	public record ReconciliationReport(
			AccountMode accountMode,
			AccountCategory accountCategory,
			boolean succeeded,
			int recordsBefore,
			int recordsAfter,
			boolean repaired,
			String message) {

		public boolean changed() {
			return repaired;
		}
	}

	private final LivePortfolioSyncService syncService;
	private final PortfolioExchangeBalanceRepository balanceRepository;
	private final PortfolioExchangePositionRepository positionRepository;

	public LivePortfolioReconcileService(
			LivePortfolioSyncService syncService,
			PortfolioExchangeBalanceRepository balanceRepository,
			PortfolioExchangePositionRepository positionRepository) {
		this.syncService = syncService;
		this.balanceRepository = balanceRepository;
		this.positionRepository = positionRepository;
	}

	/** Re-reads the spot account and repairs the local balance snapshot. */
	@Transactional
	public ReconciliationReport reconcileSpot(User user) {
		int before = balanceRepository.findByUserAndExchange(user, ExchangeName.BINANCE).size();
		var outcome = syncService.syncSpot(user);
		int after = balanceRepository.findByUserAndExchange(user, ExchangeName.BINANCE).size();
		return new ReconciliationReport(
				AccountMode.LIVE,
				AccountCategory.SPOT,
				outcome.isSuccessful(),
				before,
				after,
				outcome.isSuccessful(),
				outcome.message());
	}

	/** Re-reads the futures account and open positions and repairs the local snapshot. */
	@Transactional
	public ReconciliationReport reconcileFutures(User user) {
		int before = positionRepository.findByUserAndExchangeOrderBySymbolAsc(
				user, ExchangeName.BINANCE).size();
		var outcome = syncService.syncFutures(user);
		List<PortfolioExchangePosition> after = positionRepository.findByUserAndExchangeOrderBySymbolAsc(
				user, ExchangeName.BINANCE);
		return new ReconciliationReport(
				AccountMode.LIVE,
				AccountCategory.FUTURES,
				outcome.isSuccessful(),
				before,
				after.size(),
				outcome.isSuccessful(),
				outcome.message());
	}

	/** Reconciles both stream-capable LIVE scopes. */
	public List<ReconciliationReport> reconcileAll(User user) {
		return List.of(reconcileSpot(user), reconcileFutures(user));
	}

	/** Convenience for tests and diagnostics: how many exchange rows currently exist for a scope. */
	public int localSnapshotSize(User user, AccountCategory category) {
		return category == AccountCategory.SPOT
				? balanceRepository.findByUserAndExchange(user, ExchangeName.BINANCE).size()
				: positionRepository.findByUserAndExchangeOrderBySymbolAsc(user, ExchangeName.BINANCE).size();
	}
}