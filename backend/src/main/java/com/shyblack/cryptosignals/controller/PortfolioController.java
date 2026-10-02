package com.shyblack.cryptosignals.controller;

import com.shyblack.cryptosignals.dto.portfolio.PortfolioAccountView;
import com.shyblack.cryptosignals.dto.portfolio.PortfolioClosedPositionsResponse;
import com.shyblack.cryptosignals.dto.portfolio.PortfolioHistoryFilter;
import com.shyblack.cryptosignals.dto.portfolio.PortfolioHistoryResponse;
import com.shyblack.cryptosignals.dto.portfolio.PortfolioHoldingsResponse;
import com.shyblack.cryptosignals.dto.portfolio.PortfolioOpenOrdersResponse;
import com.shyblack.cryptosignals.dto.portfolio.PortfolioOverviewResponse;
import com.shyblack.cryptosignals.dto.portfolio.PortfolioSyncStatusResponse;
import com.shyblack.cryptosignals.dto.portfolio.PortfolioPositionsResponse;
import com.shyblack.cryptosignals.entity.PortfolioAccountConnection;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountAvailability;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.exception.BadRequestException;
import com.shyblack.cryptosignals.exception.ResourceNotFoundException;
import com.shyblack.cryptosignals.repository.PortfolioAccountConnectionRepository;
import com.shyblack.cryptosignals.repository.UserRepository;
import com.shyblack.cryptosignals.security.UserPrincipal;
import com.shyblack.cryptosignals.service.portfolio.HistoryWindow;
import com.shyblack.cryptosignals.service.portfolio.PortfolioAccountReadService;
import com.shyblack.cryptosignals.service.portfolio.PortfolioClosedPositionService;
import com.shyblack.cryptosignals.service.portfolio.PortfolioHistoryService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Read-only unified Portfolio API.
 *
 * <p>Replaces the former {@code /api/v1/portfolios} and {@code /api/v1/positions} stubs, which
 * returned an empty list and threw on lookup. Both were dead: a repository-wide search found no
 * backend consumer other than their own controllers, and the Flutter portfolio/position
 * data sources that referenced them are DI-wired but never watched by a screen or a test.
 *
 * <p>Every response covers exactly one account mode, so PAPER and LIVE can never appear together.
 * All aggregation stays in {@link PortfolioAccountReadService}; this controller only resolves the
 * authenticated user and delegates.
 *
 * <p><b>Account mode is not a Portfolio choice.</b> It comes from the account mode the user selected
 * in Settings, which the settings service already stores on {@code User.accountType}. A caller that
 * supplies no mode therefore gets the account it actually configured, and there is no server-side
 * default that could serve a simulated account to a user who chose the live one. An explicit
 * {@code mode} parameter is still accepted so a diagnostic or admin read can address a scope
 * deliberately, but omitting it is the normal path.
 *
 * <p>No write endpoint exists here. Order placement, cancellation and capital mutation remain on
 * the dedicated paper-trading, live-trading and futures-trading APIs.
 */
@RestController
@RequestMapping("/api/v1/portfolio")
@Tag(name = "Portfolio", description = "Unified PAPER / LIVE portfolio read model")
@SecurityRequirement(name = "bearer-jwt")
@RequiredArgsConstructor
public class PortfolioController {

	private final PortfolioAccountReadService readService;
	private final PortfolioHistoryService historyService;
	private final PortfolioClosedPositionService closedPositionService;
	private final PortfolioAccountConnectionRepository connectionRepository;
	private final UserRepository userRepository;

	/**
	 * Resolves the account mode for a request.
	 *
	 * <p>An explicit request wins, which keeps a deliberate diagnostic read possible. Otherwise the
	 * mode is the one the user selected in Settings, read from the account record the settings
	 * service writes. The mode is never defaulted to PAPER here: doing so would let an unavailable
	 * live account silently render as a simulated one.
	 */
	private AccountMode resolveMode(User user, AccountMode requested) {
		if (requested != null) {
			return requested;
		}
		AccountType configured = user.getAccountType();
		return configured == null ? AccountMode.PAPER : AccountMode.fromAccountType(configured);
	}

	@Operation(summary = "Portfolio overview for one account mode",
			description = "Returns all four account categories (MAIN, SPOT, FUTURES, OPTIONS). "
					+ "Unsupported or unavailable categories are returned explicitly rather than omitted. "
					+ "With no mode parameter the account mode selected in Settings is used.")
	@GetMapping
	public PortfolioOverviewResponse overview(
			@Parameter(description = "PAPER or LIVE. Defaults to the account mode set in Settings.")
			@RequestParam(name = "mode", required = false) AccountMode mode) {
		User user = currentUser();
		AccountMode effective = resolveMode(user, mode);
		List<PortfolioAccountView> accounts = readService.getOverview(user, effective);
		return new PortfolioOverviewResponse(effective, List.of(AccountCategory.values()), accounts);
	}

	@Operation(summary = "A single account scope",
			description = "Returns one MAIN, SPOT, FUTURES or OPTIONS scope. Numeric fields are null "
					+ "whenever the underlying source cannot provide them.")
	@GetMapping("/{category}")
	public PortfolioAccountView account(
			@Parameter(description = "MAIN, SPOT, FUTURES or OPTIONS")
			@PathVariable AccountCategory category,
			@Parameter(description = "PAPER or LIVE. Defaults to the account mode set in Settings.")
			@RequestParam(name = "mode", required = false) AccountMode mode) {
		User user = currentUser();
		return readService.getAccount(user, resolveMode(user, mode), category);
	}

	@Operation(summary = "Positions for one account scope",
			description = "Scope-isolated: a SPOT request never returns futures positions and a "
					+ "futures request never returns spot balances. An empty list is accompanied by an "
					+ "availability that explains whether the scope is genuinely empty or unsupported.")
	@GetMapping("/{category}/positions")
	public PortfolioPositionsResponse positions(
			@Parameter(description = "MAIN, SPOT, FUTURES or OPTIONS")
			@PathVariable AccountCategory category,
			@Parameter(description = "PAPER or LIVE. Defaults to the account mode set in Settings.")
			@RequestParam(name = "mode", required = false) AccountMode mode) {
		User user = currentUser();
		return readService.listPositions(user, resolveMode(user, mode), category);
	}

	@Operation(summary = "Per-asset wallet holdings for one account scope",
			description = "Authoritative exchange wallet balances. These are holdings, not trading "
					+ "positions: Binance Spot has no open-position concept. No valuation is derived, "
					+ "because no approved price feed is available for one.")
	@GetMapping("/{category}/holdings")
	public PortfolioHoldingsResponse holdings(
			@PathVariable AccountCategory category,
			@RequestParam(name = "mode", required = false) AccountMode mode) {
		User user = currentUser();
		return historyService.holdings(user, resolveMode(user, mode), category);
	}

	@Operation(summary = "Orders resting on the exchange right now",
			description = "The exchange's own open-orders state. An order whose state cannot be "
					+ "determined is reported as UNKNOWN and is still listed. Nothing here places, "
					+ "modifies or cancels an order.")
	@GetMapping("/{category}/open-orders")
	public PortfolioOpenOrdersResponse openOrders(
			@PathVariable AccountCategory category,
			@RequestParam(name = "mode", required = false) AccountMode mode,
			@RequestParam(name = "symbol", required = false) String symbol,
			@RequestParam(name = "side", required = false) String side,
			@RequestParam(name = "status", required = false) String status) {
		User user = currentUser();
		AccountMode effective = resolveMode(user, mode);
		return historyService.openOrders(user, effective, category, symbol);
	}

	@Operation(summary = "Positions that have been closed in the window",
			description = "Live futures round trips are reconstructed from the exchange's own fills, and "
					+ "simulated round trips come from the paper engine's closed records. A field that "
					+ "cannot be determined is null and partial is reported as true; nothing is estimated.")
	@GetMapping("/{category}/closed-positions")
	public PortfolioClosedPositionsResponse closedPositions(
			@PathVariable AccountCategory category,
			@RequestParam(name = "mode", required = false) AccountMode mode,
			@RequestParam(name = "symbol", required = false) String symbol,
			@RequestParam(name = "from", required = false) Instant from,
			@RequestParam(name = "to", required = false) Instant to,
			@RequestParam(name = "limit", required = false) Integer limit) {
		User user = currentUser();
		AccountMode effective = resolveMode(user, mode);
		HistoryWindow window = HistoryWindow.resolve(from, to, limit);
		return closedPositionService.closedPositions(user, effective, category, window, symbol);
	}

	@Operation(summary = "Account income records for one account scope",
			description = "Every income record the exchange published in the window, with the exchange's "
					+ "own income type preserved. Binance Spot publishes no income endpoint, so the spot "
					+ "scope reports that explicitly instead of an empty list.")
	@GetMapping("/{category}/transaction-history")
	public PortfolioHistoryResponse transactionHistory(
			@PathVariable AccountCategory category,
			@RequestParam(name = "mode", required = false) AccountMode mode,
			@RequestParam(name = "symbol", required = false) String symbol,
			@RequestParam(name = "from", required = false) Instant from,
			@RequestParam(name = "to", required = false) Instant to,
			@RequestParam(name = "limit", required = false) Integer limit) {
		User user = currentUser();
		AccountMode effective = resolveMode(user, mode);
		HistoryWindow window = HistoryWindow.resolve(from, to, limit);
		return historyService.transactions(user, effective, category, window, symbol);
	}

	@Operation(summary = "Funding fees paid or received in the window",
			description = "Read as its own exchange income type. Never summed into realized P&L and "
					+ "never attributed to an individual position, because the exchange provides no "
					+ "such attribution.")
	@GetMapping("/{category}/funding-fees")
	public PortfolioHistoryResponse fundingFees(
			@PathVariable AccountCategory category,
			@RequestParam(name = "mode", required = false) AccountMode mode,
			@RequestParam(name = "symbol", required = false) String symbol,
			@RequestParam(name = "from", required = false) Instant from,
			@RequestParam(name = "to", required = false) Instant to,
			@RequestParam(name = "limit", required = false) Integer limit) {
		User user = currentUser();
		AccountMode effective = resolveMode(user, mode);
		HistoryWindow window = HistoryWindow.resolve(from, to, limit);
		return historyService.fundingFees(user, effective, category, window, symbol);
	}

	@Operation(summary = "Orders, fills or income for one account scope",
			description = "Read-only exchange history over an explicit window. Records are read from "
					+ "the exchange and are never reconstructed from local orders or signals. A window is "
					+ "always returned, and a result that hit the record cap is marked partial.")
	@GetMapping("/{category}/history")
	public PortfolioHistoryResponse history(
			@PathVariable AccountCategory category,
			@Parameter(description = "PAPER or LIVE. Defaults to the account mode set in Settings.")
			@RequestParam(name = "mode", required = false) AccountMode mode,
			@Parameter(description = "ORDER, TRADE or INCOME. Defaults to ORDER.")
			@RequestParam(name = "type", required = false) String type,
			@RequestParam(name = "symbol", required = false) String symbol,
			@RequestParam(name = "side", required = false) String side,
			@RequestParam(name = "orderType", required = false) String orderType,
			@RequestParam(name = "status", required = false) String status,
			@RequestParam(name = "positionSide", required = false) String positionSide,
			@RequestParam(name = "from", required = false) Instant from,
			@RequestParam(name = "to", required = false) Instant to,
			@RequestParam(name = "limit", required = false) Integer limit) {
		User user = currentUser();
		AccountMode effective = resolveMode(user, mode);
		HistoryWindow window = HistoryWindow.resolve(from, to, limit);
		PortfolioHistoryFilter filter =
				PortfolioHistoryFilter.of(symbol, side, orderType, status, positionSide);
		return historyService.history(user, effective, category, type, window, filter.symbol(),
				null, filter);
	}

	@Operation(summary = "Synchronization and reconciliation status for one account scope",
			description = "Connection state, data freshness and the reason a scope is not current. "
					+ "Exposes no credential, listen key or internal identifier.")
	@GetMapping("/{category}/sync-status")
	public PortfolioSyncStatusResponse syncStatus(
			@PathVariable AccountCategory category,
			@RequestParam(name = "mode", required = false) AccountMode mode) {
		User user = currentUser();
		AccountMode effective = resolveMode(user, mode);
		PortfolioAccountView view = readService.getAccount(user, effective, category);
		Optional<PortfolioAccountConnection> connection = connectionRepository
				.findByUserAndAccountModeAndAccountCategory(user, effective, category);

		/*
		 * The connection row is the single authority for a scope's synchronisation state: it is
		 * written by both the REST snapshot sync and the user-stream event processor. The read
		 * model's availability is derived from the credential/account records instead, so taking
		 * availability from one source and the timestamps from the other can report
		 * "connected, synced just now" alongside "not connected". Availability is therefore taken
		 * from the connection row whenever it exists, and the read model is the fallback only.
		 */
		AccountAvailability availability = connection
				.map(PortfolioAccountConnection::getAvailability)
				.filter(value -> value != null)
				.orElse(view.availability());

		boolean stale = availability == AccountAvailability.STALE
				|| (view.availability() == AccountAvailability.STALE);

		String message = connection
				.map(PortfolioAccountConnection::getLastSyncMessage)
				.filter(value -> !value.isBlank())
				.orElse(view.statusMessage());

		return new PortfolioSyncStatusResponse(
				effective,
				category,
				connection.map(PortfolioAccountConnection::getConnectionStatus).orElse(null),
				availability,
				connection.map(PortfolioAccountConnection::getLastSyncedAt).orElse(null),
				connection.map(PortfolioAccountConnection::getLastEventAt).orElse(null),
				stale,
				message);
	}

	/**
	 * Resolves the caller from the security context. The user id is never taken from a request
	 * parameter, so one user can never address another user's portfolio.
	 */
	private User currentUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
			throw new BadRequestException("Authentication required");
		}
		return userRepository.findById(principal.getId())
				.orElseThrow(() -> new ResourceNotFoundException("User not found"));
	}
}