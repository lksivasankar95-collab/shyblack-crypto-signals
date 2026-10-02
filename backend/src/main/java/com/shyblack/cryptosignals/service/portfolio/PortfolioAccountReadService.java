package com.shyblack.cryptosignals.service.portfolio;

import com.shyblack.cryptosignals.dto.portfolio.PortfolioAccountView;
import com.shyblack.cryptosignals.dto.portfolio.PortfolioPositionView;
import com.shyblack.cryptosignals.dto.portfolio.PortfolioPositionsResponse;
import com.shyblack.cryptosignals.entity.LiveOrder;
import com.shyblack.cryptosignals.entity.LiveTradingAccount;
import com.shyblack.cryptosignals.entity.FuturesTradingAccount;
import com.shyblack.cryptosignals.entity.Portfolio;
import com.shyblack.cryptosignals.entity.PortfolioAccountConnection;
import com.shyblack.cryptosignals.entity.PortfolioExchangeBalance;
import com.shyblack.cryptosignals.entity.PortfolioExchangePosition;
import com.shyblack.cryptosignals.entity.Position;
import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountAvailability;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.PositionStatus;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.repository.PortfolioAccountConnectionRepository;
import com.shyblack.cryptosignals.repository.PortfolioExchangeBalanceRepository;
import com.shyblack.cryptosignals.repository.PortfolioExchangePositionRepository;
import com.shyblack.cryptosignals.repository.PositionRepository;
import com.shyblack.cryptosignals.repository.SignalRepository;
import com.shyblack.cryptosignals.service.futures.FuturesQueryService;
import com.shyblack.cryptosignals.service.live.LiveTradingQueryService;
import com.shyblack.cryptosignals.service.paper.PaperTradingAccountService;
import com.shyblack.cryptosignals.service.paper.PaperTradingPnLService;
import com.shyblack.cryptosignals.service.paper.PaperTradingQueryService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only, normalized view over the account scopes of the unified Portfolio.
 *
 * <p>This class is the single boundary that answers "what does this user's account look like for
 * {@code mode x category}". It adds no persistence model and performs no write other than lazily
 * materialising the paper account row that the paper module already creates on demand. It never
 * executes, never prices a signal and never touches an exchange.
 *
 * <p>Isolation guarantees:
 * <ul>
 *   <li>PAPER and LIVE are read from disjoint sources and can never be combined in one view.</li>
 *   <li>SPOT and FUTURES paper scopes partition positions by {@code Signal.tradingMode}; the shared
 *       paper wallet is reported at MAIN only, so capital is never counted twice.</li>
 *   <li>OPTIONS is reported as {@link AccountAvailability#UNSUPPORTED} with no figures at all,
 *       because no options engine, account or exchange API exists.</li>
 * </ul>
 *
 * <p>Every numeric field is null unless the underlying source actually provides it. An unknown
 * mark price, balance or count is never reported as zero.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortfolioAccountReadService {

	/**
	 * Cached exchange balances older than this are reported as {@link AccountAvailability#STALE}
	 * instead of as current. Three times the 60s balance-refresh interval of the existing live and
	 * futures reconciliation jobs.
	 */
	static final Duration STALE_AFTER = Duration.ofSeconds(180);

	static final String OPTIONS_UNSUPPORTED_MESSAGE =
			"Options is a reserved capability: no engine, no account and no exchange options API is "
					+ "integrated, so no data can be reported.";

	static final String LIVE_MAIN_UNAVAILABLE_MESSAGE =
			"Binance exposes no single main-wallet balance. Spot and futures are separate wallets, so "
					+ "summing them would be wrong; read each category individually instead.";

	static final String PAPER_SHARED_WALLET_MESSAGE =
			"Capital sits in one shared paper wallet reported at MAIN. It is not split per category, so "
					+ "balance and equity are unavailable here; position-level figures are exact.";

	/**
	 * Realized P&amp;L is reported as null for both live scopes in the <em>summary</em> view.
	 * Exchange income records are available, but they are time-windowed and paginated, so reading them
	 * inline on every summary request would be unbounded work on a hot path. They are served instead by
	 * the transaction-history endpoint, which resolves an explicit window. The locally recorded position
	 * ledger is NOT substituted and is never labelled as exchange realized P&amp;L.
	 */
	static final String LIVE_REALIZED_PNL_UNAVAILABLE_MESSAGE =
			"Realized P&L is not part of this summary because exchange income records are time-windowed "
					+ "and paginated. Read it from the transaction history, which reports the exchange's own "
					+ "REALIZED_PNL income records. Local position P&L is never presented as exchange "
					+ "realized P&L.";

	static final String LIVE_SPOT_NO_POSITION_MESSAGE =
			"Spot exposes balances, not open positions. A wallet asset balance is never presented as a "
					+ "trading position, so position figures stay unavailable.";

	private final PositionRepository positionRepository;
	private final SignalRepository signalRepository;
	private final PortfolioAccountConnectionRepository connectionRepository;
	private final PortfolioExchangeBalanceRepository balanceRepository;
	private final PortfolioExchangePositionRepository exchangePositionRepository;
	private final PaperTradingAccountService paperAccountService;
	private final PaperTradingQueryService paperQueryService;
	private final PaperTradingPnLService paperPnL;
	private final LiveTradingQueryService liveQueryService;
	private final FuturesQueryService futuresQueryService;

	/**
	 * Normalized view of a single account scope.
	 *
	 * @param user the authenticated owner; every read is filtered by this entity, so a caller can
	 *     never observe another user's account regardless of which scope is requested
	 */
	public PortfolioAccountView getAccount(User user, AccountMode mode, AccountCategory category) {
		Objects.requireNonNull(user, "user is required");
		Objects.requireNonNull(mode, "accountMode is required");
		Objects.requireNonNull(category, "accountCategory is required");
		return mode.isPaper() ? paperView(user, category) : liveView(user, category);
	}

	/** All four categories of one mode, in declaration order, without mixing modes. */
	public List<PortfolioAccountView> getOverview(User user, AccountMode mode) {
		Objects.requireNonNull(user, "user is required");
		Objects.requireNonNull(mode, "accountMode is required");
		List<PortfolioAccountView> views = new ArrayList<>(AccountCategory.values().length);
		for (AccountCategory category : AccountCategory.values()) {
			views.add(getAccount(user, mode, category));
		}
		return List.copyOf(views);
	}

	// ---------------------------------------------------------------- PAPER

		/**
	 * Positions for a single scope, drawn from exactly the sources the account views use.
	 *
	 * <p>Scope rules, so a response can never mix wallets or markets:
	 * <ul>
	 *   <li>PAPER {@code MAIN} — every simulated position. A position with no originating signal has
	 *       no market category and is reported with a null category rather than being guessed.</li>
	 *   <li>PAPER {@code SPOT}/{@code FUTURES} — filtered by {@code Signal.tradingMode}.</li>
	 *   <li>LIVE {@code FUTURES} — the exchange-authoritative position snapshot.</li>
	 *   <li>LIVE {@code SPOT} — {@code UNSUPPORTED}: spot has no open-position concept, and a
	 *       wallet asset balance is never presented as a position.</li>
	 *   <li>LIVE {@code MAIN} — {@code UNAVAILABLE}: serving it would mix the spot and futures
	 *       wallets, which the architecture forbids.</li>
	 *   <li>Options, in either mode — {@code UNSUPPORTED}.</li>
	 * </ul>
	 */
	public PortfolioPositionsResponse listPositions(User user, AccountMode mode, AccountCategory category) {
		Objects.requireNonNull(user, "user is required");
		Objects.requireNonNull(mode, "accountMode is required");
		Objects.requireNonNull(category, "accountCategory is required");

		if (category == AccountCategory.OPTIONS) {
			return new PortfolioPositionsResponse(
					mode, category, AccountAvailability.UNSUPPORTED, List.of(), OPTIONS_UNSUPPORTED_MESSAGE);
		}
		if (mode.isPaper()) {
			return new PortfolioPositionsResponse(
					mode, category, AccountAvailability.AVAILABLE, paperPositions(user, category), null);
		}
		if (category == AccountCategory.MAIN) {
			return new PortfolioPositionsResponse(
					mode, category, AccountAvailability.UNAVAILABLE, List.of(), LIVE_MAIN_UNAVAILABLE_MESSAGE);
		}
		if (category == AccountCategory.SPOT) {
			return new PortfolioPositionsResponse(
					mode, category, AccountAvailability.UNSUPPORTED, List.of(), LIVE_SPOT_NO_POSITION_MESSAGE);
		}
		return new PortfolioPositionsResponse(
				mode, category, AccountAvailability.AVAILABLE, liveFuturesPositions(user), null);
	}

	private List<PortfolioPositionView> paperPositions(User user, AccountCategory category) {
		List<Position> scoped = category == AccountCategory.MAIN
				? positionRepository.findByPortfolio_UserAndPortfolio_AccountTypeOrderByCreatedAtDesc(
						user, AccountType.PAPER)
				: positionRepository.findByOwnerAndAccountTypeAndSignalTradingMode(
						user, AccountType.PAPER, category.tradingMode().orElseThrow());

		// One batched lookup rather than a query per position.
		List<UUID> signalIds = scoped.stream()
				.map(Position::getSignalId)
				.filter(java.util.Objects::nonNull)
				.distinct()
				.toList();
		java.util.Map<UUID, TradingMode> modeBySignal = new java.util.HashMap<>();
		if (!signalIds.isEmpty()) {
			for (Signal signal : signalRepository.findAllById(signalIds)) {
				modeBySignal.put(signal.getId(), signal.getTradingMode());
			}
		}

		List<PortfolioPositionView> views = new ArrayList<>(scoped.size());
		for (Position position : scoped) {
			AccountCategory positionCategory = (position.getSignalId() == null)
					? null
					: toCategory(modeBySignal.get(position.getSignalId()));
			views.add(new PortfolioPositionView(
					AccountMode.PAPER,
					positionCategory,
					position.getSymbol(),
					position.getSide(),
					position.remainingQty(),
					position.getEntryPrice(),
					paperQueryService.currentPrice(position),
					position.getStopLoss(),
					position.getTakeProfit1(),
					position.getTakeProfit2(),
					position.getTakeProfit3(),
					position.getLiquidationPrice(),
					null,
					position.getNotional(),
					markKnownUnrealized(position),
					position.getRealizedPnl(),
					position.getStatus()));
		}
		return List.copyOf(views);
	}

	private List<PortfolioPositionView> liveFuturesPositions(User user) {
		return exchangePositionRepository.findByUserAndExchangeOrderBySymbolAsc(user, ExchangeName.BINANCE)
				.stream()
				.map(p -> new PortfolioPositionView(
						AccountMode.LIVE,
						AccountCategory.FUTURES,
						p.getSymbol(),
						p.getPositionSide(),
						p.quantity(),
						p.getEntryPrice(),
						p.getMarkPrice(),
						// The exchange position response carries no stop loss and no take-profit
						// ladder. Deriving a stop from the mark price would be fabrication, so these
						// stay null and only REST or our own orders can ever supply them.
						null,
						null,
						null,
						null,
						p.getLiquidationPrice(),
						p.getLeverage(),
						p.getNotional(),
						p.getUnrealizedProfit(),
						null,
						p.getPositionAmount() == null || p.getPositionAmount().signum() == 0
								? PositionStatus.CLOSED
								: PositionStatus.OPEN))
				.toList();
	}

	/** Per-position unrealized P&amp;L, or null when the mark price is unknown. */
	private BigDecimal markKnownUnrealized(Position position) {
		BigDecimal mark = paperQueryService.currentPrice(position);
		if (mark == null) {
			return null;
		}
		return paperPnL.grossPnl(position.getSide(), position.getEntryPrice(), mark, position.remainingQty());
	}

	/** A market mode mapped to its account category, or null when it cannot be attributed. */
	private static AccountCategory toCategory(TradingMode mode) {
		if (mode == null) {
			return null;
		}
		return switch (mode) {
			case SPOT -> AccountCategory.SPOT;
			case FUTURES -> AccountCategory.FUTURES;
			case OPTIONS -> AccountCategory.OPTIONS;
		};
	}

	private PortfolioAccountView paperView(User user, AccountCategory category) {
		if (category == AccountCategory.OPTIONS) {
			return unsupported(AccountMode.PAPER, category);
		}

		// Reuses the paper module's own lazy account materialisation so the read model resolves the
		// exact same row the paper APIs and the paper execution engine operate on.
		Portfolio portfolio = paperAccountService.getOrCreate(user);
		Optional<PortfolioAccountConnection> connection =
				connectionRepository.findByUserAndAccountModeAndAccountCategory(
						user, AccountMode.PAPER, category);

		if (category == AccountCategory.MAIN) {
			return paperMainView(user, portfolio, connection.orElse(null));
		}
		return paperCategoryView(user, portfolio, category, connection.orElse(null));
	}

	/**
	 * The aggregate paper account. {@code accountCategory} is intentionally not consulted: a legacy
	 * row with SQL NULL is presented as MAIN, and the row itself is never rewritten.
	 */
	private PortfolioAccountView paperMainView(
			User user, Portfolio portfolio, PortfolioAccountConnection connection) {

		List<Position> all = positionRepository.findByPortfolio_UserAndPortfolio_AccountTypeOrderByCreatedAtDesc(
				user, AccountType.PAPER);
		List<Position> open = filterByStatus(all, PositionStatus.OPEN);

		BigDecimal unrealized = unrealized(open);
		BigDecimal equity = (unrealized == null)
				? null
				: portfolio.getAvailableBalance().add(portfolio.getInvested()).add(unrealized);

		return new PortfolioAccountView(
				AccountMode.PAPER,
				AccountCategory.MAIN,
				AccountAvailability.AVAILABLE,
				portfolio.getExchange(),
				connection != null ? connection.getConnectionStatus() : null,
				portfolio.getQuoteCurrency(),
				equity,
				portfolio.getAvailableBalance(),
				portfolio.getInvested(),
				portfolio.getRealizedPnl(),
				unrealized,
				all.size(),
				open.size(),
				null,
				connection != null ? connection.getLastSyncedAt() : null,
				null);
	}

	/**
	 * A single paper market category. Balance and equity stay null on purpose: the wallet is shared
	 * with the other categories, so any per-category balance would double count capital.
	 */
	private PortfolioAccountView paperCategoryView(
			User user,
			Portfolio portfolio,
			AccountCategory category,
			PortfolioAccountConnection connection) {

		TradingMode mode = category.tradingMode().orElseThrow();
		List<Position> scoped = positionRepository.findByOwnerAndAccountTypeAndSignalTradingMode(
				user, AccountType.PAPER, mode);
		List<Position> open = filterByStatus(scoped, PositionStatus.OPEN);
		List<Position> closed = filterByStatus(scoped, PositionStatus.CLOSED);

		return new PortfolioAccountView(
				AccountMode.PAPER,
				category,
				AccountAvailability.AVAILABLE,
				portfolio.getExchange(),
				connection != null ? connection.getConnectionStatus() : null,
				portfolio.getQuoteCurrency(),
				null,
				null,
				sumNotional(open),
				sumStrict(closed, Position::getRealizedPnl),
				unrealized(open),
				scoped.size(),
				open.size(),
				null,
				connection != null ? connection.getLastSyncedAt() : null,
				// A recorded synchronization message explains this scope better than the generic
				// shared-wallet note, so it takes precedence; absent one, the note stands.
				connection != null && connection.getLastSyncMessage() != null
						? connection.getLastSyncMessage()
						: PAPER_SHARED_WALLET_MESSAGE);
	}

	// ----------------------------------------------------------------- LIVE

	private PortfolioAccountView liveView(User user, AccountCategory category) {
		if (category == AccountCategory.OPTIONS) {
			return unsupported(AccountMode.LIVE, category);
		}
		if (category == AccountCategory.MAIN) {
			// Derived aggregate with no single source: Binance keeps spot and futures wallets apart.
			return unavailable(AccountMode.LIVE, AccountCategory.MAIN, LIVE_MAIN_UNAVAILABLE_MESSAGE);
		}
		return category == AccountCategory.SPOT ? liveSpotView(user) : liveFuturesView(user);
	}

	private PortfolioAccountView liveSpotView(User user) {
		Optional<LiveTradingAccount> found = liveQueryService.findAccount(user);
		if (found.isEmpty()) {
			return notConnected(AccountMode.LIVE, AccountCategory.SPOT, ExchangeName.BINANCE);
		}
		LiveTradingAccount account = found.get();
		Optional<PortfolioAccountConnection> connection =
				connectionRepository.findByUserAndAccountModeAndAccountCategory(
						user, AccountMode.LIVE, AccountCategory.SPOT);

		List<LiveOrder> orders = liveQueryService.allOrders(user);

		// Authoritative per-asset balances last written by the read sync. Null when the quote asset
		// was not reported, which is missing data rather than a zero balance.
		String quoteAsset = account.getQuoteCurrency();
		Optional<PortfolioExchangeBalance> quoteBalance = quoteAsset == null
				? Optional.empty()
				: balanceRepository.findByUserAndExchangeAndAsset(user, ExchangeName.BINANCE, quoteAsset);

		// Binance Spot has no open-position concept, so a wallet asset balance is never presented as
		// a trading position. Balances stay valid; position figures stay unavailable.
		return new PortfolioAccountView(
				AccountMode.LIVE,
				AccountCategory.SPOT,
				liveSpotAvailability(account, connection.orElse(null)),
				account.getExchange(),
				account.getConnectionStatus(),
				quoteAsset,
				quoteBalance.map(PortfolioExchangeBalance::total).orElse(null),
				quoteBalance.map(PortfolioExchangeBalance::getFree).orElse(null),
				null,
				null,
				null,
				null,
				null,
				orders.size(),
				connection.map(PortfolioAccountConnection::getLastSyncedAt)
						.orElse(account.getLastValidatedAt()),
				connection.map(PortfolioAccountConnection::getLastSyncMessage)
						.orElse(LIVE_SPOT_NO_POSITION_MESSAGE));
	}

	private PortfolioAccountView liveFuturesView(User user) {
		Optional<FuturesTradingAccount> found = futuresQueryService.findAccount(user);
		if (found.isEmpty()) {
			return notConnected(AccountMode.LIVE, AccountCategory.FUTURES, ExchangeName.BINANCE);
		}
		FuturesTradingAccount account = found.get();
		Optional<PortfolioAccountConnection> connection =
				connectionRepository.findByUserAndAccountModeAndAccountCategory(
						user, AccountMode.LIVE, AccountCategory.FUTURES);

		// Authoritative open positions as last reported by the exchange position-risk endpoint.
		List<PortfolioExchangePosition> open =
				exchangePositionRepository.findByUserAndExchangeOrderBySymbolAsc(user, ExchangeName.BINANCE);
		int orderCount = futuresQueryService.openOrders(user).size()
				+ futuresQueryService.history(user).size();

		return new PortfolioAccountView(
				AccountMode.LIVE,
				AccountCategory.FUTURES,
				liveAvailability(account.getConnectionStatus(), account.getLastValidatedAt(), connection.orElse(null)),
				account.getExchange(),
				account.getConnectionStatus(),
				account.getMarginAsset(),
				account.getWalletBalance(),
				account.getAvailableBalance(),
				account.getUsedMargin(),
				null,
				account.getUnrealizedPnl(),
				null,
				open.size(),
				orderCount,
				connection.map(PortfolioAccountConnection::getLastSyncedAt)
						.orElse(account.getLastValidatedAt()),
				connection.map(PortfolioAccountConnection::getLastSyncMessage)
						.orElse(LIVE_REALIZED_PNL_UNAVAILABLE_MESSAGE));
	}

	/**
	 * Spot balances are read from the exchange-authoritative snapshot table, which only the read sync
	 * writes. With no synchronization record there is no authoritative balance yet, so the scope is
	 * UNAVAILABLE even though the account object itself reports a connected status — that status is
	 * still surfaced separately in {@code connectionStatus}.
	 */
	private AccountAvailability liveSpotAvailability(
			LiveTradingAccount account, PortfolioAccountConnection connection) {
		// No synchronization record means the read model has never obtained an authoritative balance
		// snapshot, so the scope cannot be AVAILABLE no matter what the cached account fields say.
		if (connection == null || connection.getAvailability() == null) {
			return AccountAvailability.UNAVAILABLE;
		}
		// Otherwise reuse the shared rule, which also keeps the guarantee that a recorded status can
		// never upgrade a scope to AVAILABLE while the account itself is not connected.
		return liveAvailability(account.getConnectionStatus(), account.getLastValidatedAt(), connection);
	}

	/**
	 * Availability for an exchange-backed scope. An explicit synchronization record wins, except
	 * that it can never upgrade a scope to AVAILABLE while the account itself is not connected.
	 */
	private AccountAvailability liveAvailability(
			ExchangeConnectionStatus status,
			Instant lastValidatedAt,
			PortfolioAccountConnection connection) {

		AccountAvailability derived = switch (status == null ? ExchangeConnectionStatus.NOT_CONNECTED : status) {
			case CONNECTED -> isStale(lastValidatedAt) ? AccountAvailability.STALE : AccountAvailability.AVAILABLE;
			case CONNECTING -> AccountAvailability.SYNCING;
			case NOT_CONNECTED -> AccountAvailability.NOT_CONNECTED;
			case REVOKED -> AccountAvailability.DISCONNECTED;
			case FAILED -> AccountAvailability.ERROR;
		};

		if (connection == null || connection.getAvailability() == null) {
			return derived;
		}
		AccountAvailability recorded = connection.getAvailability();
		if (recorded == AccountAvailability.AVAILABLE && derived != AccountAvailability.AVAILABLE) {
			return derived;
		}
		return recorded;
	}

	private static boolean isStale(Instant lastValidatedAt) {
		return lastValidatedAt != null
				&& lastValidatedAt.plus(STALE_AFTER).isBefore(Instant.now());
	}

	// ------------------------------------------------------------- helpers

	/**
	 * Open P&amp;L for a set of positions. Returns null when any position has no known mark price:
	 * a partial sum would understate the total and {@code PaperTradingPnLService.grossPnl} would
	 * silently coerce an unknown exit price, so the whole figure is reported as unknown instead.
	 */
	private BigDecimal unrealized(List<Position> positions) {
		if (positions.isEmpty()) {
			return BigDecimal.ZERO;
		}
		BigDecimal total = BigDecimal.ZERO;
		for (Position position : positions) {
			BigDecimal mark = paperQueryService.currentPrice(position);
			if (mark == null) {
				return null;
			}
			total = total.add(
					paperPnL.grossPnl(position.getSide(), position.getEntryPrice(), mark, position.remainingQty()));
		}
		return total;
	}

	/** Sum of a nullable money attribute; null when any element is unknown. */
	private static <T> BigDecimal sumStrict(List<T> items, java.util.function.Function<T, BigDecimal> value) {
		if (items.isEmpty()) {
			return BigDecimal.ZERO;
		}
		BigDecimal total = BigDecimal.ZERO;
		for (T item : items) {
			BigDecimal v = value.apply(item);
			if (v == null) {
				return null;
			}
			total = total.add(v);
		}
		return total;
	}

	/** Notional currently deployed, which is the paper engine's own definition of {@code invested}. */
	private static BigDecimal sumNotional(List<Position> open) {
		return sumStrict(open, Position::getNotional);
	}

	private static List<Position> filterByStatus(List<Position> positions, PositionStatus status) {
		return positions.stream().filter(p -> p.getStatus() == status).toList();
	}

	private static PortfolioAccountView unsupported(AccountMode mode, AccountCategory category) {
		return new PortfolioAccountView(
				mode, category, AccountAvailability.UNSUPPORTED, null, null, null,
				null, null, null, null, null, null, null, null, null,
				OPTIONS_UNSUPPORTED_MESSAGE);
	}

	private static PortfolioAccountView unavailable(AccountMode mode, AccountCategory category, String message) {
		return new PortfolioAccountView(
				mode, category, AccountAvailability.UNAVAILABLE, null, null, null,
				null, null, null, null, null, null, null, null, null,
				message);
	}

	private static PortfolioAccountView notConnected(
			AccountMode mode, AccountCategory category, ExchangeName exchange) {
		return new PortfolioAccountView(
				mode, category, AccountAvailability.NOT_CONNECTED, exchange, null, null,
				null, null, null, null, null, null, null, null, null,
				"No exchange account is connected for this scope.");
	}
}