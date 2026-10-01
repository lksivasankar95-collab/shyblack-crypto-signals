package com.shyblack.cryptosignals.service.portfolio;

import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.PortfolioAccountConnection;
import com.shyblack.cryptosignals.entity.PortfolioExchangeBalance;
import com.shyblack.cryptosignals.entity.PortfolioExchangePosition;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountAvailability;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.exchange.ExchangeAdapterException;
import com.shyblack.cryptosignals.exchange.ExchangeBalances;
import com.shyblack.cryptosignals.exchange.ExchangeTradingAdapter;
import com.shyblack.cryptosignals.exchange.futures.FuturesExchangeAdapter;
import com.shyblack.cryptosignals.exchange.futures.FuturesExchangePosition;
import com.shyblack.cryptosignals.repository.ExchangeCredentialRepository;
import com.shyblack.cryptosignals.repository.PortfolioAccountConnectionRepository;
import com.shyblack.cryptosignals.repository.PortfolioExchangeBalanceRepository;
import com.shyblack.cryptosignals.repository.PortfolioExchangePositionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only synchronization of authoritative exchange account data for the LIVE scopes.
 *
 * <p>Strictly a reader of the exchange. It places, cancels and modifies no order, touches no
 * execution path, no signal and no strategy, and leaves
 * {@code app.live-trading.auto-execute} / {@code app.futures-trading.auto-execute} alone.
 *
 * <p>Results land in read-model tables owned by this package ({@code portfolio_exchange_balances},
 * {@code portfolio_exchange_positions}). Account-level totals on {@code LiveTradingAccount} and
 * {@code FuturesTradingAccount} are deliberately NOT written here: those fields already have an
 * owner (the 60s live/futures reconciliation jobs), and two writers would race.
 *
 * <p>A failed exchange call is recorded as {@link AccountAvailability#ERROR} with a diagnostic and
 * never as a zero balance, a zero position or an empty-but-successful result.
 */
@Service
@RequiredArgsConstructor
public class LivePortfolioSyncService {

	private static final Logger log = LoggerFactory.getLogger(LivePortfolioSyncService.class);

	/** Binance error codes that mean "these credentials are not usable". */
	private static final List<Integer> AUTH_CODES = List.of(-2014, -2015, -1022);

	private final ExchangeCredentialRepository credentialRepository;
	private final PortfolioAccountConnectionRepository connectionRepository;
	private final PortfolioExchangeBalanceRepository balanceRepository;
	private final PortfolioExchangePositionRepository positionRepository;
	private final ExchangeTradingAdapter spotAdapter;
	private final FuturesExchangeAdapter futuresAdapter;

	/** Outcome of one scope read, exposing what actually happened. */
	public record LiveSyncOutcome(
			AccountMode accountMode,
			AccountCategory accountCategory,
			AccountAvailability availability,
			String message,
			int recordCount,
			Instant syncedAt) {

		public boolean isSuccessful() {
			return availability == AccountAvailability.AVAILABLE;
		}
	}

	/** Pulls the complete spot balance set. Read-only. */
	@Transactional
	public LiveSyncOutcome syncSpot(User user) {
		Optional<ExchangeCredential> credential = binanceCredential(user);
		if (credential.isEmpty()) {
			return record(user, AccountMode.LIVE, AccountCategory.SPOT, ExchangeName.BINANCE,
					AccountAvailability.NOT_CONNECTED, "No exchange credential is connected.", 0, null);
		}
		try {
			ExchangeBalances balances = spotAdapter.getBalances(credential.get());
			Instant fetchedAt = balances.fetchedAt() == null ? Instant.now() : balances.fetchedAt();

			List<PortfolioExchangeBalance> existing =
					balanceRepository.findByUserAndExchange(user, ExchangeName.BINANCE);
			List<PortfolioExchangeBalance> rows = new ArrayList<>(balances.assets().size());
			balances.assets().values().forEach(balance -> {
				PortfolioExchangeBalance row = upsert(existing, balance.asset());
				row.setUser(user);
				row.setExchange(ExchangeName.BINANCE);
				row.setAsset(balance.asset());
				row.setFree(balance.free());
				row.setLocked(balance.locked());
				row.setFetchedAt(fetchedAt);
				rows.add(row);
			});
			// Assets the exchange no longer reports are dropped rather than left at a stale value.
			existing.stream()
					.filter(row -> balances.find(row.getAsset()).isEmpty())
					.forEach(balanceRepository::delete);
			balanceRepository.saveAll(rows);

			return record(user, AccountMode.LIVE, AccountCategory.SPOT, ExchangeName.BINANCE,
					AccountAvailability.AVAILABLE,
					"Synced " + rows.size() + " asset balances from the exchange.",
					rows.size(), fetchedAt);
		} catch (ExchangeAdapterException ex) {
			log.warn("[LiveSync] spot balance read failed for user={} reason={}", user.getId(), ex.getMessage());
			return record(user, AccountMode.LIVE, AccountCategory.SPOT, ExchangeName.BINANCE,
					AccountAvailability.ERROR, describe(ex), 0, null);
		}
	}

	/** Pulls authoritative open futures positions. Read-only. */
	@Transactional
	public LiveSyncOutcome syncFutures(User user) {
		Optional<ExchangeCredential> credential = binanceCredential(user);
		if (credential.isEmpty()) {
			return record(user, AccountMode.LIVE, AccountCategory.FUTURES, ExchangeName.BINANCE,
					AccountAvailability.NOT_CONNECTED, "No exchange credential is connected.", 0, null);
		}
		try {
			List<FuturesExchangePosition> open = futuresAdapter.getPositions(credential.get());
			Instant fetchedAt = Instant.now();

			List<PortfolioExchangePosition> existing =
					positionRepository.findByUserAndExchangeOrderBySymbolAsc(user, ExchangeName.BINANCE);
			List<PortfolioExchangePosition> rows = new ArrayList<>(open.size());
			for (FuturesExchangePosition position : open) {
				if (!position.isOpen()) {
					continue;
				}
				PortfolioExchangePosition row = upsert(existing, position);
				row.setUser(user);
				row.setExchange(ExchangeName.BINANCE);
				row.setSymbol(position.symbol());
				row.setPositionSide(position.positionSide());
				row.setPositionAmount(position.positionAmount());
				row.setEntryPrice(position.entryPrice());
				row.setMarkPrice(position.markPrice());
				row.setLiquidationPrice(position.liquidationPrice());
				row.setLeverage(position.leverage());
				row.setMarginMode(position.marginMode());
				row.setIsolatedMargin(position.isolatedMargin());
				row.setNotional(position.notional());
				row.setUnrealizedProfit(position.unrealizedProfit());
				row.setFetchedAt(fetchedAt);
				rows.add(row);
			}
			// A position the exchange no longer reports as open is genuinely closed: remove it.
			existing.stream()
					.filter(row -> rows.stream().noneMatch(
							r -> r.getSymbol().equals(row.getSymbol())
									&& r.getPositionSide() == row.getPositionSide()))
					.forEach(positionRepository::delete);
			positionRepository.saveAll(rows);

			return record(user, AccountMode.LIVE, AccountCategory.FUTURES, ExchangeName.BINANCE,
					AccountAvailability.AVAILABLE,
					"Synced " + rows.size() + " open positions from the exchange.",
					rows.size(), fetchedAt);
		} catch (ExchangeAdapterException ex) {
			log.warn("[LiveSync] futures position read failed for user={} reason={}", user.getId(), ex.getMessage());
			return record(user, AccountMode.LIVE, AccountCategory.FUTURES, ExchangeName.BINANCE,
					AccountAvailability.ERROR, describe(ex), 0, null);
		}
	}

	// -------------------------------------------------------------- helpers

	private Optional<ExchangeCredential> binanceCredential(User user) {
		return credentialRepository.findByUser_IdAndExchange(user.getId(), ExchangeName.BINANCE);
	}

	private PortfolioExchangeBalance upsert(
			List<PortfolioExchangeBalance> existing, String asset) {
		return existing.stream()
				.filter(row -> asset.equalsIgnoreCase(row.getAsset()))
				.findFirst()
				.orElseGet(PortfolioExchangeBalance::new);
	}

	private PortfolioExchangePosition upsert(
			List<PortfolioExchangePosition> existing, FuturesExchangePosition position) {
		return existing.stream()
				.filter(row -> position.symbol().equalsIgnoreCase(row.getSymbol())
						&& position.positionSide() == row.getPositionSide())
				.findFirst()
				.orElseGet(PortfolioExchangePosition::new);
	}

	/**
	 * Upserts the scope status on the single existing connection row. The row is keyed by
	 * {@code (user, accountMode, accountCategory)} only, so this can never create a duplicate and
	 * never needs the exchange in the key. Paper scopes are never touched by this service.
	 */
	private LiveSyncOutcome record(
			User user,
			AccountMode mode,
			AccountCategory category,
			ExchangeName exchange,
			AccountAvailability availability,
			String message,
			int recordCount,
			Instant syncedAt) {

		PortfolioAccountConnection connection =
				connectionRepository.findByUserAndAccountModeAndAccountCategory(user, mode, category)
						.orElseGet(() -> {
							PortfolioAccountConnection created = new PortfolioAccountConnection();
							created.setUser(user);
							created.setAccountMode(mode);
							created.setAccountCategory(category);
							created.setExchange(exchange);
							created.setConnectionStatus(ExchangeConnectionStatus.NOT_CONNECTED);
							created.setAvailability(AccountAvailability.NOT_CONNECTED);
							return created;
						});

		connection.setAvailability(availability);
		connection.setConnectionStatus(toConnectionStatus(availability));
		connection.setLastSyncMessage(truncate(message));
		// A failed or unsynced read must not keep claiming a successful synchronization time.
		connection.setLastSyncedAt(syncedAt);
		connectionRepository.save(connection);

		return new LiveSyncOutcome(mode, category, availability, message, recordCount, syncedAt);
	}

	private static ExchangeConnectionStatus toConnectionStatus(AccountAvailability availability) {
		return switch (availability) {
			case AVAILABLE, STALE -> ExchangeConnectionStatus.CONNECTED;
			case SYNCING -> ExchangeConnectionStatus.CONNECTING;
			case NOT_CONNECTED, UNAVAILABLE -> ExchangeConnectionStatus.NOT_CONNECTED;
			case DISCONNECTED -> ExchangeConnectionStatus.REVOKED;
			case UNSUPPORTED, ERROR -> ExchangeConnectionStatus.FAILED;
		};
	}

	/**
	 * Maps an exchange failure onto a diagnostic. Only the status and Binance's own error code are
	 * used; no credential, header, signed query string or response body is ever included.
	 */
	static String describe(ExchangeAdapterException ex) {
		Integer code = ex.exchangeCode();
		Integer status = ex.httpStatus();
		if (code != null && AUTH_CODES.contains(code)) {
			return "Exchange rejected the credentials (code " + code
					+ "). Check the API key, its permissions and the permitted IP.";
		}
		if (code != null && code == -1003) {
			return "Exchange rate limit reached (code -1003). Retry later.";
		}
		if (status != null && (status == 429 || status == 418)) {
			return "Exchange rate limited or banned the request (HTTP " + status + "). Retry later.";
		}
		if (status != null && status >= 500) {
			return "Exchange server error (HTTP " + status + ").";
		}
		if (ex.retryable()) {
			return "Exchange unreachable: " + ex.getMessage();
		}
		return "Exchange read failed: " + ex.getMessage();
	}

	/** Matches the existing 200-character diagnostic column convention. */
	static String truncate(String message) {
		if (message == null) {
			return null;
		}
		return message.length() <= 200 ? message : message.substring(0, 197) + "...";
	}
}