package com.shyblack.cryptosignals.service.portfolio;

import com.shyblack.cryptosignals.dto.portfolio.PortfolioHistoryEntry;
import com.shyblack.cryptosignals.dto.portfolio.PortfolioHistoryResponse;
import com.shyblack.cryptosignals.dto.portfolio.PortfolioHoldingsResponse;
import com.shyblack.cryptosignals.dto.portfolio.PortfolioHoldingView;
import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.PortfolioExchangeBalance;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountAvailability;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.exchange.ExchangeAdapterException;
import com.shyblack.cryptosignals.exchange.ExchangeOrderSnapshot;
import com.shyblack.cryptosignals.exchange.ExchangeTradeSnapshot;
import com.shyblack.cryptosignals.exchange.ExchangeTradingAdapter;
import com.shyblack.cryptosignals.exchange.futures.FuturesExchangeAdapter;
import com.shyblack.cryptosignals.exchange.futures.FuturesIncomeSnapshot;
import com.shyblack.cryptosignals.exchange.futures.FuturesOrderSnapshot;
import com.shyblack.cryptosignals.exchange.futures.FuturesTradeSnapshot;
import com.shyblack.cryptosignals.repository.ExchangeCredentialRepository;
import com.shyblack.cryptosignals.repository.PortfolioExchangeBalanceRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only holdings and history for the LIVE scopes.
 *
 * <p>Two deliberate decisions:
 *
 * <ul>
 *   <li><b>Read-through, not persisted.</b> History is inherently a query-time concern over a bounded
 *       window. Persisting it would add a second copy of exchange truth plus an unbounded retention
 *       problem, so history is read from the exchange on demand. Current-state snapshots
 *       (balances, positions) remain persisted exactly as Phase 3 built them.</li>
 *   <li><b>Never reconstructed.</b> Every record here comes from an exchange endpoint. No fill is
 *       derived from a signal, a local order, a position quantity or a price difference, and no
 *       realized P&amp;L is inferred from local state.</li>
 * </ul>
 *
 * <p>PAPER and OPTIONS are handled by the read model instead: simulated history is authoritative
 * local data and is read from the paper module, and Options has no capability to report.
 */
@Service
@RequiredArgsConstructor
public class PortfolioHistoryService {

	private static final Logger log = LoggerFactory.getLogger(PortfolioHistoryService.class);

	/** Raw exchange income type for realized profit or loss. */
	public static final String INCOME_REALIZED_PNL = "REALIZED_PNL";

	static final String SOURCE_EXCHANGE = "EXCHANGE";
	static final String SOURCE_LOCAL_PAPER = "LOCAL_PAPER";

	private final ExchangeCredentialRepository credentialRepository;
	private final PortfolioExchangeBalanceRepository balanceRepository;
	private final ExchangeTradingAdapter spotAdapter;
	private final FuturesExchangeAdapter futuresAdapter;
	private final LiveUserStreamEventProcessor eventProcessor;

	// ---------------------------------------------------------------- holdings

	/**
	 * Per-asset wallet holdings. Only LIVE SPOT has them: Binance Spot has no open-position
	 * concept, so these are holdings and must not be read as positions.
	 */
	@Transactional(readOnly = true)
	public PortfolioHoldingsResponse holdings(User user, AccountMode mode, AccountCategory category) {
		Objects.requireNonNull(user, "user is required");
		Objects.requireNonNull(mode, "accountMode is required");
		Objects.requireNonNull(category, "accountCategory is required");

		if (category == AccountCategory.OPTIONS) {
			return new PortfolioHoldingsResponse(mode, category, AccountAvailability.UNSUPPORTED,
					null, List.of(), "Options is a reserved capability and exposes no holdings.");
		}
		if (mode.isPaper()) {
			return new PortfolioHoldingsResponse(mode, category, AccountAvailability.UNSUPPORTED,
					SOURCE_LOCAL_PAPER, List.of(),
					"A simulated account holds capital, not exchange assets, so it has no per-asset "
							+ "wallet holdings.");
		}
		if (category != AccountCategory.SPOT) {
			return new PortfolioHoldingsResponse(mode, category, AccountAvailability.UNSUPPORTED,
					SOURCE_EXCHANGE, List.of(),
					"Per-asset wallet holdings are only available for the spot wallet.");
		}

		List<PortfolioHoldingView> holdings = balanceRepository
				.findByUserAndExchange(user, ExchangeName.BINANCE)
				.stream()
				.sorted(Comparator.comparing(PortfolioExchangeBalance::getAsset))
				.map(b -> new PortfolioHoldingView(
						b.getAsset(), b.getFree(), b.getLocked(), b.total()))
				.toList();

		if (holdings.isEmpty()) {
			return new PortfolioHoldingsResponse(mode, category, AccountAvailability.UNAVAILABLE,
					SOURCE_EXCHANGE, List.of(),
					"No exchange balances have been synchronised for this account yet.");
		}
		return new PortfolioHoldingsResponse(mode, category, AccountAvailability.AVAILABLE,
				SOURCE_EXCHANGE, holdings, null);
	}

	// ---------------------------------------------------------------- history

	/**
	 * Orders, fills or income for one scope over an explicit window.
	 *
	 * @param entryType ORDER, TRADE or INCOME. INCOME is only meaningful for FUTURES, where the
	 *     exchange publishes income records.
	 */
	@Transactional(readOnly = true)
	public PortfolioHistoryResponse history(
			User user,
			AccountMode mode,
			AccountCategory category,
			String entryType,
			HistoryWindow window,
			String symbol) {

		Objects.requireNonNull(user, "user is required");
		Objects.requireNonNull(mode, "accountMode is required");
		Objects.requireNonNull(category, "accountCategory is required");
		Objects.requireNonNull(window, "window is required");
		String type = entryType == null || entryType.isBlank() ? "ORDER" : entryType.toUpperCase();

		if (category == AccountCategory.OPTIONS) {
			return empty(mode, category, type, AccountAvailability.UNSUPPORTED, null, window,
					"Options is a reserved capability and exposes no history.");
		}
		if (mode.isPaper()) {
			return paperHistory(user, mode, category, type, window);
		}
		if (category == AccountCategory.MAIN) {
			// Serving this would require merging the spot and futures wallets.
			return empty(mode, category, type, AccountAvailability.UNAVAILABLE, null, window,
					"History is reported per market scope; MAIN would mix the spot and futures wallets.");
		}
		return category == AccountCategory.SPOT
				? spotHistory(user, mode, category, type, window, symbol)
				: futuresHistory(user, mode, category, type, window, symbol);
	}

	private PortfolioHistoryResponse spotHistory(
			User user, AccountMode mode, AccountCategory category,
			String type, HistoryWindow window, String symbol) {

		ExchangeCredential credential = credentialRepository
				.findByUser_IdAndExchange(user.getId(), ExchangeName.BINANCE)
				.orElse(null);
		if (credential == null) {
			return empty(mode, category, type, AccountAvailability.NOT_CONNECTED, SOURCE_EXCHANGE, window,
					"No exchange credential is connected for this account.");
		}
		try {
			List<PortfolioHistoryEntry> entries = new ArrayList<>();
			if ("ORDER".equals(type)) {
				for (ExchangeOrderSnapshot order : spotAdapter.getAllOrders(
						credential, symbol, window.from(), window.to(), window.limit())) {
					entries.add(toEntry(mode, category, type, order));
				}
			} else if ("TRADE".equals(type)) {
				for (ExchangeTradeSnapshot trade : spotAdapter.getTrades(
						credential, symbol, window.from(), window.to(), window.limit())) {
					entries.add(new PortfolioHistoryEntry(
							"TRADE", mode, category, SOURCE_EXCHANGE,
							trade.symbol(), trade.orderId(), trade.tradeId(),
							trade.side(), null, trade.price(), trade.quantity(), trade.quoteQuantity(),
							trade.commission(), trade.commissionAsset(), null, trade.tradedAt()));
				}
			} else {
				return empty(mode, category, type, AccountAvailability.UNSUPPORTED, SOURCE_EXCHANGE, window,
						"Income records are published by the futures account only.");
			}
			return finish(mode, category, type, SOURCE_EXCHANGE, window, entries);
		} catch (ExchangeAdapterException ex) {
			log.warn("[PortfolioHistory] spot {} read failed for user={} reason={}",
					type, user.getId(), ex.getMessage());
			return empty(mode, category, type, AccountAvailability.ERROR, SOURCE_EXCHANGE, window,
					LivePortfolioSyncService.describe(ex));
		}
	}

	private PortfolioHistoryResponse futuresHistory(
			User user, AccountMode mode, AccountCategory category,
			String type, HistoryWindow window, String symbol) {

		ExchangeCredential credential = credentialRepository
				.findByUser_IdAndExchange(user.getId(), ExchangeName.BINANCE)
				.orElse(null);
		if (credential == null) {
			return empty(mode, category, type, AccountAvailability.NOT_CONNECTED, SOURCE_EXCHANGE, window,
					"No exchange credential is connected for this account.");
		}
		try {
			List<PortfolioHistoryEntry> entries = new ArrayList<>();
			if ("ORDER".equals(type)) {
				for (FuturesOrderSnapshot order : futuresAdapter.getAllOrders(
						credential, symbol, window.from(), window.to(), window.limit())) {
					entries.add(new PortfolioHistoryEntry(
							"ORDER", mode, category, SOURCE_EXCHANGE,
							order.symbol(), order.orderId(), null, order.side(), order.status(),
							order.price(), order.originalQuantity(), order.cumulativeQuoteQuantity(),
							null, null, null, order.updatedAt() != null ? order.updatedAt() : order.createdAt()));
				}
			} else if ("TRADE".equals(type)) {
				for (FuturesTradeSnapshot trade : futuresAdapter.getTrades(
						credential, symbol, window.from(), window.to(), window.limit())) {
					entries.add(new PortfolioHistoryEntry(
							"TRADE", mode, category, SOURCE_EXCHANGE,
							trade.symbol(), trade.orderId(), trade.tradeId(),
							trade.side(), null, trade.price(), trade.quantity(), trade.quoteQuantity(),
							trade.commission(), trade.commissionAsset(), trade.realizedPnl(), trade.tradedAt()));
				}
			} else if ("INCOME".equals(type)) {
				for (FuturesIncomeSnapshot income : futuresAdapter.getIncome(
						credential, INCOME_REALIZED_PNL, window.from(), window.to(), window.limit())) {
					entries.add(new PortfolioHistoryEntry(
							"INCOME", mode, category, SOURCE_EXCHANGE,
							income.symbol(), null, income.transactionId(),
							null, income.incomeType(), null, null, null,
							null, income.asset(), income.income(), income.time()));
				}
			} else {
				return empty(mode, category, type, AccountAvailability.UNSUPPORTED, SOURCE_EXCHANGE, window,
						"Unknown history type '" + type + "'.");
			}
			return finish(mode, category, type, SOURCE_EXCHANGE, window, entries);
		} catch (ExchangeAdapterException ex) {
			log.warn("[PortfolioHistory] futures {} read failed for user={} reason={}",
					type, user.getId(), ex.getMessage());
			return empty(mode, category, type, AccountAvailability.ERROR, SOURCE_EXCHANGE, window,
					LivePortfolioSyncService.describe(ex));
		}
	}

	/**
	 * Simulated history comes from the paper module's own records, which are authoritative local
	 * data. No paper P&amp;L is recomputed here and no exchange record can appear in it.
	 */
	private PortfolioHistoryResponse paperHistory(
			User user, AccountMode mode, AccountCategory category,
			String type, HistoryWindow window) {
		return empty(mode, category, type, AccountAvailability.UNSUPPORTED, SOURCE_LOCAL_PAPER, window,
				"Simulated trade history is served by the paper-trading history endpoint, not here.");
	}

	// ---------------------------------------------------------------- helpers

	/**
	 * Newest first, ties broken on the natural identifier so the ordering is total, duplicates that a
	 * paginated exchange query can repeat across a boundary are collapsed, and a result that hit the
	 * record cap is flagged as partial rather than presented as complete.
	 */
	private PortfolioHistoryResponse finish(
			AccountMode mode,
			AccountCategory category,
			String type,
			String source,
			HistoryWindow window,
			List<PortfolioHistoryEntry> entries) {

		Comparator<PortfolioHistoryEntry> newestFirst = Comparator
				.comparing(PortfolioHistoryEntry::occurredAt, Comparator.nullsLast(Comparator.reverseOrder()))
				.thenComparing(PortfolioHistoryEntry::tradeId, Comparator.nullsLast(Comparator.reverseOrder()))
				.thenComparing(PortfolioHistoryEntry::orderId, Comparator.nullsLast(Comparator.reverseOrder()));
		entries.sort(newestFirst);

		List<PortfolioHistoryEntry> deduped = new ArrayList<>();
		for (PortfolioHistoryEntry entry : entries) {
			boolean duplicate = deduped.stream().anyMatch(existing -> sameRecord(existing, entry));
			if (!duplicate) {
				deduped.add(entry);
			}
		}

		boolean complete = deduped.size() < window.limit();
		return new PortfolioHistoryResponse(
				mode, category, AccountAvailability.AVAILABLE, source, type,
				window.from(), window.to(), complete, deduped,
				complete ? null
						: "Reached the record cap of " + window.limit()
								+ "; this window is partial, request a narrower period or page further.");
	}

	/** Identity of a history record, so a repeated record is recognised rather than double counted. */
	private static boolean sameRecord(PortfolioHistoryEntry a, PortfolioHistoryEntry b) {
		if (a.tradeId() != null || b.tradeId() != null) {
			return java.util.Objects.equals(a.tradeId(), b.tradeId())
					&& java.util.Objects.equals(a.symbol(), b.symbol())
					&& a.entryType().equals(b.entryType());
		}
		if (a.orderId() != null || b.orderId() != null) {
			return java.util.Objects.equals(a.orderId(), b.orderId())
					&& java.util.Objects.equals(a.symbol(), b.symbol())
					&& a.entryType().equals(b.entryType());
		}
		// Income without a transaction id still has a natural key of type+symbol+time.
		return java.util.Objects.equals(a.status(), b.status())
				&& java.util.Objects.equals(a.symbol(), b.symbol())
				&& java.util.Objects.equals(a.occurredAt(), b.occurredAt())
				&& a.entryType().equals(b.entryType());
	}

	private static PortfolioHistoryEntry toEntry(
			AccountMode mode, AccountCategory category, String type, ExchangeOrderSnapshot order) {
		return new PortfolioHistoryEntry(
				type, mode, category, SOURCE_EXCHANGE,
				order.symbol(), order.orderId(), null, order.side(), order.status(),
				order.price(), order.originalQuantity(), order.cumulativeQuoteQuantity(),
				null, null, null,
				order.updatedAt() != null ? order.updatedAt() : order.createdAt());
	}

	private static PortfolioHistoryResponse empty(
			AccountMode mode, AccountCategory category, String type,
			AccountAvailability availability, String source, HistoryWindow window, String message) {
		return new PortfolioHistoryResponse(
				mode, category, availability, source, type,
				window == null ? null : window.from(),
				window == null ? null : window.to(),
				true, List.of(), message);
	}
}
