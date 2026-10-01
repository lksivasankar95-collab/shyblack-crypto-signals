package com.shyblack.cryptosignals.service.portfolio;

import com.shyblack.cryptosignals.dto.portfolio.PortfolioAccountView;
import com.shyblack.cryptosignals.entity.FuturesPosition;
import com.shyblack.cryptosignals.entity.LiveOrder;
import com.shyblack.cryptosignals.entity.LiveTradingAccount;
import com.shyblack.cryptosignals.entity.FuturesTradingAccount;
import com.shyblack.cryptosignals.entity.Portfolio;
import com.shyblack.cryptosignals.entity.PortfolioAccountConnection;
import com.shyblack.cryptosignals.entity.Position;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountAvailability;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.LiveOrderPurpose;
import com.shyblack.cryptosignals.entity.enums.LiveOrderStatus;
import com.shyblack.cryptosignals.entity.enums.PositionStatus;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.repository.PortfolioAccountConnectionRepository;
import com.shyblack.cryptosignals.repository.PositionRepository;
import com.shyblack.cryptosignals.service.futures.FuturesQueryService;
import com.shyblack.cryptosignals.service.live.LiveTradingQueryService;
import com.shyblack.cryptosignals.service.paper.PaperTradingAccountService;
import com.shyblack.cryptosignals.service.paper.PaperTradingPnLService;
import com.shyblack.cryptosignals.service.paper.PaperTradingQueryService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

	/** Order states that still represent a live exposure on the exchange. */
	private static final Set<LiveOrderStatus> LIVE_ACTIVE_ORDER_STATUSES = Set.of(
			LiveOrderStatus.CREATED,
			LiveOrderStatus.SUBMITTING,
			LiveOrderStatus.SUBMITTED,
			LiveOrderStatus.ACKNOWLEDGED,
			LiveOrderStatus.PARTIALLY_FILLED,
			LiveOrderStatus.FILLED);

	/** Order states that mean the exchange took (part of) the position. */
	private static final Set<LiveOrderStatus> LIVE_FILLED_ORDER_STATUSES = Set.of(
			LiveOrderStatus.FILLED, LiveOrderStatus.PARTIALLY_FILLED);

	private final PositionRepository positionRepository;
	private final PortfolioAccountConnectionRepository connectionRepository;
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
		int openPositions = (int) orders.stream()
				.filter(o -> o.getPurpose() == LiveOrderPurpose.ENTRY)
				.filter(o -> LIVE_ACTIVE_ORDER_STATUSES.contains(o.getStatus()))
				.count();
		int totalPositions = (int) orders.stream()
				.filter(o -> o.getPurpose() == LiveOrderPurpose.ENTRY)
				.filter(o -> LIVE_FILLED_ORDER_STATUSES.contains(o.getStatus()))
				.count();

		// Spot has no position entity and no authoritative unrealized/realized P&L source yet, so
		// those stay null. Only the two balances the reconciliation job caches are exposed.
		return new PortfolioAccountView(
				AccountMode.LIVE,
				AccountCategory.SPOT,
				liveAvailability(account.getConnectionStatus(), account.getLastValidatedAt(), connection.orElse(null)),
				account.getExchange(),
				account.getConnectionStatus(),
				account.getQuoteCurrency(),
				account.getCachedTotalBalance(),
				account.getCachedAvailableBalance(),
				null,
				null,
				null,
				totalPositions,
				openPositions,
				orders.size(),
				account.getLastValidatedAt(),
				connection.map(PortfolioAccountConnection::getLastSyncMessage).orElse(null));
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

		List<FuturesPosition> open = futuresQueryService.openPositions(user);
		List<FuturesPosition> closed = futuresQueryService.closedPositions(user);
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
				sumFuturesRealizedPnl(closed),
				account.getUnrealizedPnl(),
				open.size() + closed.size(),
				open.size(),
				orderCount,
				account.getLastValidatedAt(),
				connection.map(PortfolioAccountConnection::getLastSyncMessage).orElse(null));
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

	/**
	 * Realized P&amp;L of locally recorded closed futures positions. This is our own ledger, not an
	 * authoritative exchange income statement, which only becomes available once income endpoints are
	 * integrated.
	 */
	private static BigDecimal sumFuturesRealizedPnl(List<FuturesPosition> closed) {
		return sumStrict(closed, FuturesPosition::getRealizedPnl);
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