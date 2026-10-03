package com.shyblack.cryptosignals.service.portfolio;

import com.shyblack.cryptosignals.dto.portfolio.PortfolioHistoryEntry;
import com.shyblack.cryptosignals.dto.portfolio.PortfolioHistoryFilter;
import com.shyblack.cryptosignals.dto.portfolio.PortfolioHistoryResponse;
import com.shyblack.cryptosignals.dto.portfolio.PortfolioHoldingsResponse;
import com.shyblack.cryptosignals.dto.portfolio.PortfolioHoldingView;
import com.shyblack.cryptosignals.dto.portfolio.PortfolioOpenOrdersResponse;
import com.shyblack.cryptosignals.dto.portfolio.PortfolioOrderView;
import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.PortfolioExchangeBalance;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountAvailability;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.PortfolioOrderStatus;
import com.shyblack.cryptosignals.exchange.ExchangeAdapterException;
import com.shyblack.cryptosignals.exchange.ExchangeOrderSnapshot;
import com.shyblack.cryptosignals.exchange.ExchangeTradeSnapshot;
import com.shyblack.cryptosignals.exchange.ExchangeTradingAdapter;
import com.shyblack.cryptosignals.exchange.futures.FuturesExchangeAdapter;
import com.shyblack.cryptosignals.exchange.futures.FuturesIncomeSnapshot;
import com.shyblack.cryptosignals.exchange.futures.FuturesOrderSnapshot;
import com.shyblack.cryptosignals.exchange.futures.FuturesTradeSnapshot;
import com.shyblack.cryptosignals.repository.ExchangeCredentialRepository;
import com.shyblack.cryptosignals.entity.LiveOrder;
import com.shyblack.cryptosignals.entity.enums.LiveOrderStatus;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderStatus;
import com.shyblack.cryptosignals.entity.FuturesOrder;
import com.shyblack.cryptosignals.repository.LiveOrderRepository;
import com.shyblack.cryptosignals.repository.FuturesOrderRepository;
import com.shyblack.cryptosignals.repository.PortfolioExchangeBalanceRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
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

	/**
	 * Raw exchange income type for funding paid or received on an open position. A separate income
	 * type from realized P&amp;L and never summed into it.
	 */
	public static final String INCOME_FUNDING_FEE = "FUNDING_FEE";

	/** Raw exchange income type for trading commission. */
	public static final String INCOME_COMMISSION = "COMMISSION";

	/** Raw exchange income type for account transfers. */
	public static final String INCOME_TRANSFER = "TRANSFER";

	/** Entry type used by the dedicated funding-fee endpoint, so it is distinguishable from income. */
	public static final String ENTRY_FUNDING_FEE = "FUNDING_FEE";

	/** Entry type used by the transaction endpoint; covers every income type the exchange reports. */
	public static final String ENTRY_INCOME = "INCOME";

	static final String SOURCE_EXCHANGE = "EXCHANGE";
	static final String SOURCE_LOCAL_PAPER = "LOCAL_PAPER";

	/**
	 * Binance Spot publishes no account income or transaction endpoint. Reporting the real records is
	 * therefore impossible rather than merely unimplemented, and no substitute is invented: the spot
	 * scope says so explicitly instead of showing an empty list that would read as "no activity".
	 */
	static final String SPOT_NO_INCOME_MESSAGE =
			"Not available: Binance Spot publishes no account income or transaction endpoint. Spot "
					+ "activity is available under order history and trade history.";

	private final ExchangeCredentialRepository credentialRepository;
	private final PortfolioExchangeBalanceRepository balanceRepository;
	private final ExchangeTradingAdapter spotAdapter;
	private final FuturesExchangeAdapter futuresAdapter;
	private final LiveUserStreamEventProcessor eventProcessor;
	private final LiveOrderRepository liveOrderRepository;
	private final FuturesOrderRepository futuresOrderRepository;

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
		return history(user, mode, category, entryType, window, symbol, null, PortfolioHistoryFilter.NONE);
	}

	/**
	 * Full history read with narrowing and an explicit income type.
	 *
	 * @param incomeType raw exchange income type for INCOME reads; null reads every income type
	 *     the exchange publishes rather than assuming a category set of our own.
	 * @param filter optional narrowing applied to the fetched window.
	 */
	@Transactional(readOnly = true)
	public PortfolioHistoryResponse history(
			User user,
			AccountMode mode,
			AccountCategory category,
			String entryType,
			HistoryWindow window,
			String symbol,
			String incomeType,
			PortfolioHistoryFilter filter) {

		Objects.requireNonNull(user, "user is required");
		Objects.requireNonNull(mode, "accountMode is required");
		Objects.requireNonNull(category, "accountCategory is required");
		Objects.requireNonNull(window, "window is required");
		PortfolioHistoryFilter narrowing = filter == null ? PortfolioHistoryFilter.NONE : filter;
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
		PortfolioHistoryResponse response = category == AccountCategory.SPOT
				? spotHistory(user, mode, category, type, window, symbol, incomeType)
				: futuresHistory(user, mode, category, type, window, symbol, incomeType);
		return narrow(response, narrowing);
	}

	/**
	 * Every income record the exchange published in the window, whatever its type.
	 *
	 * <p>The type is whatever Binance reported — {@code REALIZED_PNL}, {@code FUNDING_FEE},
	 * {@code COMMISSION}, {@code TRANSFER} or anything else it adds — so a new exchange income type
	 * appears as itself rather than being dropped or folded into a category we invented.
	 */
	@Transactional(readOnly = true)
	public PortfolioHistoryResponse transactions(
			User user, AccountMode mode, AccountCategory category, HistoryWindow window, String symbol) {
		return income(user, mode, category, window, symbol, null, ENTRY_INCOME, SPOT_NO_INCOME_MESSAGE);
	}

	/** Funding fees, read as their own exchange income type. */
	@Transactional(readOnly = true)
	public PortfolioHistoryResponse fundingFees(
			User user, AccountMode mode, AccountCategory category, HistoryWindow window, String symbol) {
		return income(user, mode, category, window, symbol, INCOME_FUNDING_FEE, ENTRY_FUNDING_FEE,
				"Not available: spot is never margined, so the exchange charges it no funding fee. "
						+ "Spot activity is available under order history and trade history.");
	}

	private PortfolioHistoryResponse income(
			User user,
			AccountMode mode,
			AccountCategory category,
			HistoryWindow window,
			String symbol,
			String incomeType,
			String entryType,
			String spotMessage) {

		Objects.requireNonNull(window, "window is required");
		if (category == AccountCategory.OPTIONS) {
			return empty(mode, category, entryType, AccountAvailability.UNSUPPORTED, null, window,
					"Options is a reserved capability and exposes no income records.");
		}
		if (mode.isPaper()) {
			return empty(mode, category, entryType, AccountAvailability.UNSUPPORTED, SOURCE_LOCAL_PAPER,
					window, "A simulated account is charged no funding fee and publishes no income "
							+ "records. Simulated trades are served by the paper-trading history endpoint.");
		}
		if (category == AccountCategory.MAIN) {
			return empty(mode, category, entryType, AccountAvailability.UNAVAILABLE, null, window,
					"Income is reported per market scope; MAIN would mix the spot and futures wallets.");
		}
		if (category == AccountCategory.SPOT) {
			// The caller supplies the scope-specific reason: spot has no income endpoint at all,
			// and spot is separately never charged a funding fee. Both are explicit rather than an
			// empty result that would read as "no activity".
			return empty(mode, category, entryType, AccountAvailability.UNSUPPORTED, SOURCE_EXCHANGE,
					window, spotMessage);
		}
		return futuresHistory(user, mode, category, "INCOME", window, symbol, incomeType);
	}

	/**
	 * Orders resting on the exchange book right now, for one scope.
	 *
	 * <p>This is the answer to "what is open", sourced from the exchange's own open-orders endpoint.
	 * An order whose state cannot be read is reported as {@code UNKNOWN} and is still listed, because
	 * an undetermined order is precisely the one that must stay visible.
	 */
	@Transactional(readOnly = true)
	public PortfolioOpenOrdersResponse openOrders(
			User user, AccountMode mode, AccountCategory category, String symbol) {
		return openOrders(user, mode, category, symbol, null, null);
	}

	/**
	 * Open orders, optionally narrowed by side and/or status.
	 *
	 * <p>{@code side} matches the reported BUY/SELL. {@code status} matches
	 * {@link PortfolioOrderStatus}; an unrecognised value narrows to nothing
	 * rather than silently widening the result set, so a typo cannot quietly
	 * return every order. Filtering happens on the mapped view because the
	 * exchange adapters take no such query parameter.</p>
	 */
	@Transactional(readOnly = true)
	public PortfolioOpenOrdersResponse openOrders(
			User user, AccountMode mode, AccountCategory category,
			String symbol, String side, String status) {

		Objects.requireNonNull(user, "user is required");
		Objects.requireNonNull(mode, "accountMode is required");
		Objects.requireNonNull(category, "accountCategory is required");

		if (category == AccountCategory.OPTIONS) {
			return noOpenOrders(mode, category, AccountAvailability.UNSUPPORTED, null,
					"Options is a reserved capability and exposes no orders.");
		}
		if (mode.isPaper()) {
			return noOpenOrders(mode, category, AccountAvailability.UNSUPPORTED, SOURCE_LOCAL_PAPER,
					"A simulated account works no order book: the paper engine opens and closes a "
							+ "position directly instead of resting an order. Simulated activity is "
							+ "served by the paper-trading endpoints.");
		}
		if (category == AccountCategory.MAIN) {
			return noOpenOrders(mode, category, AccountAvailability.UNAVAILABLE, SOURCE_EXCHANGE,
					"Open orders are reported per market scope; MAIN would mix the spot and futures books.");
		}

		ExchangeCredential credential = credentialRepository
				.findByUser_IdAndExchange(user.getId(), ExchangeName.BINANCE)
				.orElse(null);
		if (credential == null) {
			return noOpenOrders(mode, category, AccountAvailability.NOT_CONNECTED, SOURCE_EXCHANGE,
					"No exchange credential is connected for this account. Simulated orders are never "
							+ "substituted here.");
		}
		try {
			List<PortfolioOrderView> orders = new ArrayList<>();
			if (category == AccountCategory.SPOT) {
				// The exchange is the source of truth for *which* orders rest, but only a
				// locally persisted record can be cancelled through the existing
				// execution services. The two are matched by client order id, scoped to
				// this caller, so an order we never placed stays uncancellable here
				// rather than being handed an identifier that would fail.
				Map<String, String> cancellable = cancellableSpotOrderIds(user);
				for (ExchangeOrderSnapshot order : spotAdapter.getOpenOrders(credential, symbol)) {
					orders.add(toOrderView(mode, category, order,
							cancellable.get(order.clientOrderId())));
				}
			} else {
				Map<String, String> cancellable = cancellableFuturesOrderIds(user);
				for (FuturesOrderSnapshot order : futuresAdapter.getOpenOrders(credential, symbol)) {
					orders.add(toFuturesOrderView(mode, category, order,
							cancellable.get(order.clientOrderId())));
				}
			}
			return new PortfolioOpenOrdersResponse(
					mode, category, AccountAvailability.AVAILABLE, SOURCE_EXCHANGE,
					filterOpenOrders(orders, side, status), null);
		} catch (ExchangeAdapterException ex) {
			log.warn("[PortfolioHistory] open-orders read failed for user={} reason={}",
					user.getId(), ex.getMessage());
			return noOpenOrders(mode, category, AccountAvailability.ERROR, SOURCE_EXCHANGE,
					LivePortfolioSyncService.describe(ex));
		}
	}

	private PortfolioOpenOrdersResponse noOpenOrders(
			AccountMode mode, AccountCategory category,
			AccountAvailability availability, String source, String message) {
		return new PortfolioOpenOrdersResponse(mode, category, availability, source, List.of(), message);
	}

	/**
	 * Applies the optional side/status narrowing. Both are case-insensitive; a
	 * blank value means "no filter". An unrecognised status matches nothing —
	 * widening to everything would be the more dangerous failure.
	 */
	private static List<PortfolioOrderView> filterOpenOrders(
			List<PortfolioOrderView> orders, String side, String status) {
		String wantedSide = blankToNull(side);
		String wantedStatus = blankToNull(status);
		if (wantedSide == null && wantedStatus == null) return orders;

		List<PortfolioOrderView> kept = new ArrayList<>();
		for (PortfolioOrderView order : orders) {
			if (wantedSide != null && !wantedSide.equalsIgnoreCase(
					order.side() == null ? null : order.side())) {
				continue;
			}
			if (wantedStatus != null) {
				String actual = order.status() == null ? null : order.status().name();
				if (!wantedStatus.equalsIgnoreCase(actual)) continue;
			}
			kept.add(order);
		}
		return kept;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private PortfolioHistoryResponse spotHistory(
			User user, AccountMode mode, AccountCategory category,
			String type, HistoryWindow window, String symbol, String incomeType) {

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
							// Spot has no position side, so it stays null rather than being guessed.
							trade.side(), null, null, null, trade.price(), trade.quantity(), trade.quoteQuantity(),
							trade.commission(), trade.commissionAsset(), null, trade.tradedAt()));
				}
			} else {
				return empty(mode, category, type, AccountAvailability.UNSUPPORTED, SOURCE_EXCHANGE, window,
						SPOT_NO_INCOME_MESSAGE);
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
			String type, HistoryWindow window, String symbol, String incomeType) {

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
							order.symbol(), order.orderId(), null,
							order.side(), order.positionSide(),
							// The raw exchange status is preserved verbatim, so a state this build
							// does not recognise stays visible instead of being flattened.
							order.orderType(), order.status(),
							order.price(), order.originalQuantity(), order.cumulativeQuoteQuantity(),
							null, null, null,
							order.updatedAt() != null ? order.updatedAt() : order.createdAt()));
				}
			} else if ("TRADE".equals(type)) {
				for (FuturesTradeSnapshot trade : futuresAdapter.getTrades(
						credential, symbol, window.from(), window.to(), window.limit())) {
					entries.add(new PortfolioHistoryEntry(
							"TRADE", mode, category, SOURCE_EXCHANGE,
							trade.symbol(), trade.orderId(), trade.tradeId(),
							trade.side(), trade.positionSide(), null, null,
							trade.price(), trade.quantity(), trade.quoteQuantity(),
							trade.commission(), trade.commissionAsset(), trade.realizedPnl(), trade.tradedAt()));
				}
			} else if ("INCOME".equals(type)) {
				for (FuturesIncomeSnapshot income : futuresAdapter.getIncome(
						credential, incomeType, window.from(), window.to(), window.limit())) {
					entries.add(new PortfolioHistoryEntry(
							// The entry type is the exchange's own income type when a specific one was
							// requested, so the row says FUNDING_FEE rather than a generic INCOME.
							incomeType == null || incomeType.isBlank() ? ENTRY_INCOME : incomeType.toUpperCase(),
							mode, category, SOURCE_EXCHANGE,
							income.symbol(), null, income.transactionId(),
							null, null, null, income.incomeType(), null, null, null,
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
				order.symbol(), order.orderId(), null,
				// Spot reports no position side.
				order.side(), null, order.orderType(), order.status(),
				order.price(), order.originalQuantity(), order.cumulativeQuoteQuantity(),
				null, null, null,
				order.updatedAt() != null ? order.updatedAt() : order.createdAt());
	}

	// ------------------------------------------------------- open order mapping

	/**
	 * Maps a spot order onto the account view.
	 *
	 * <p>{@code remainingQuantity} is the one derived field, and only when both the original and the
	 * executed quantity are known. Spot publishes no average fill price on its order endpoints, so
	 * {@code averageFillPrice} stays null instead of being computed from cumulative quote.
	 */
	private static PortfolioOrderView toOrderView(
			AccountMode mode, AccountCategory category, ExchangeOrderSnapshot order,
			String cancelId) {
		return new PortfolioOrderView(
				cancelId,
				mode,
				category,
				order.symbol(),
				order.side(),
				// Binance Spot has no position side: it cannot be shorted.
				null,
				order.orderType(),
				PortfolioOrderStatus.fromExchange(order.status()),
				order.status(),
				order.price(),
				order.stopPrice(),
				null,
				order.originalQuantity(),
				order.executedQuantity(),
				remaining(order.originalQuantity(), order.executedQuantity()),
				// Reduce-only is a futures concept.
				null,
				order.orderId(),
				order.clientOrderId(),
				order.createdAt(),
				order.updatedAt());
	}

	/** Maps a futures order onto the account view, including its own average fill price. */
	private static PortfolioOrderView toFuturesOrderView(
			AccountMode mode, AccountCategory category, FuturesOrderSnapshot order,
			String cancelId) {
		return new PortfolioOrderView(
				cancelId,
				mode,
				category,
				order.symbol(),
				order.side(),
				order.positionSide(),
				order.orderType(),
				PortfolioOrderStatus.fromExchange(order.status()),
				order.status(),
				order.price(),
				order.stopPrice(),
				order.averageFillPrice(),
				order.originalQuantity(),
				order.executedQuantity(),
				remaining(order.originalQuantity(), order.executedQuantity()),
				order.reduceOnly(),
				order.orderId(),
				order.clientOrderId(),
				order.createdAt(),
				order.updatedAt());
	}

	/** original minus executed, or null when either is unknown. Never a bare zero. */
	private static BigDecimal remaining(BigDecimal original, BigDecimal executed) {
		if (original == null || executed == null) {
			return null;
		}
		return original.subtract(executed);
	}

	/**
	 * Maps clientOrderId to the local {@code LiveOrder} key for orders this
	 * application placed and the exchange still reports as resting.
	 *
	 * <p>Scoped to the caller's own orders and to non-terminal local states, so a
	 * stale UI cannot address another account's order, and an already
	 * filled/cancelled local record does not advertise a cancel that would be
	 * rejected. An order placed outside this application simply has no entry and
	 * therefore no cancel identifier.</p>
	 */
	private Map<String, String> cancellableSpotOrderIds(User user) {
		Map<String, String> byClientId = new HashMap<>();
		for (LiveOrder order : liveOrderRepository.findByAccount_UserOrderByCreatedAtDesc(user)) {
			if (order.getId() == null || order.getClientOrderId() == null) continue;
			if (order.getStatus() == null || !spotCancellable(order.getStatus())) continue;
			byClientId.putIfAbsent(order.getClientOrderId(), order.getId().toString());
		}
		return byClientId;
	}

	/**
	 * A local order state that could still be cancelled.
	 *
	 * <p>{@code CANCEL_REQUESTED} is deliberately excluded: a cancel is already in
	 * flight, so advertising another would offer a double submit. Terminal states
	 * are excluded because there is nothing left to cancel.
	 */
	private static boolean spotCancellable(LiveOrderStatus status) {
		return switch (status) {
			case CREATED, SUBMITTING, SUBMITTED, ACKNOWLEDGED, PARTIALLY_FILLED -> true;
			default -> false;
		};
	}

	/** Futures equivalent of {@link #spotCancellable(LiveOrderStatus)}. */
	private static boolean futuresCancellable(FuturesOrderStatus status) {
		return switch (status) {
			case CREATED, SUBMITTING, SUBMITTED, ACKNOWLEDGED, PARTIALLY_FILLED -> true;
			default -> false;
		};
	}

	/** Futures equivalent of {@link #cancellableSpotOrderIds(User)}. */
	private Map<String, String> cancellableFuturesOrderIds(User user) {
		Map<String, String> byClientId = new HashMap<>();
		for (FuturesOrder order : futuresOrderRepository.findByAccount_UserOrderByCreatedAtDesc(user)) {
			if (order.getId() == null || order.getClientOrderId() == null) continue;
			if (order.getStatus() == null || !futuresCancellable(order.getStatus())) continue;
			byClientId.putIfAbsent(order.getClientOrderId(), order.getId().toString());
		}
		return byClientId;
	}

	/**
	 * Applies the optional narrowing to an already-fetched window.
	 *
	 * <p>Only an {@link AccountAvailability#AVAILABLE} result is narrowed: an unsupported,
	 * disconnected or failed scope has nothing to narrow and its explanatory message must survive.
	 */
	private static PortfolioHistoryResponse narrow(
			PortfolioHistoryResponse response, PortfolioHistoryFilter filter) {
		if (filter == null || !filter.hasNarrowing()
				|| response.availability() != AccountAvailability.AVAILABLE) {
			return response;
		}
		List<PortfolioHistoryEntry> kept = response.entries().stream()
				.filter(entry -> matches(entry, filter))
				.toList();
		return new PortfolioHistoryResponse(
				response.accountMode(), response.accountCategory(), response.availability(),
				response.source(), response.entryType(), response.windowFrom(), response.windowTo(),
				response.complete(), kept, response.statusMessage());
	}

	private static boolean matches(PortfolioHistoryEntry entry, PortfolioHistoryFilter filter) {
		if (filter.symbol() != null && !filter.symbol().equalsIgnoreCase(entry.symbol())) {
			return false;
		}
		if (filter.side() != null && !filter.side().equalsIgnoreCase(entry.side())) {
			return false;
		}
		if (filter.positionSide() != null && !filter.positionSide().equalsIgnoreCase(entry.positionSide())) {
			return false;
		}
		if (filter.orderType() != null && !filter.orderType().equalsIgnoreCase(entry.orderType())) {
			return false;
		}
		if (filter.status() != null) {
			// Matched against the normalised state, so an UNKNOWN raw status stays matchable as
			// UNKNOWN and is never silently widened to one of the known terminal states.
			if (!filter.status().equals(PortfolioOrderStatus.fromExchange(entry.status()).name())) {
				return false;
			}
		}
		// Order type is absent on fills and income records, so a narrowing on it keeps only the
		// order records that actually report one instead of matching on an invented value.
		return true;
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
