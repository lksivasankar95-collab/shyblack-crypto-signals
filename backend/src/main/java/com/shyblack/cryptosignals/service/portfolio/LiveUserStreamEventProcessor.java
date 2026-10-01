package com.shyblack.cryptosignals.service.portfolio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.shyblack.cryptosignals.entity.PortfolioAccountConnection;
import com.shyblack.cryptosignals.entity.PortfolioExchangeBalance;
import com.shyblack.cryptosignals.entity.PortfolioExchangeEventApplied;
import com.shyblack.cryptosignals.entity.PortfolioExchangePosition;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountAvailability;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.FuturesMarginMode;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.repository.PortfolioAccountConnectionRepository;
import com.shyblack.cryptosignals.repository.PortfolioExchangeBalanceRepository;
import com.shyblack.cryptosignals.repository.PortfolioExchangeEventAppliedRepository;
import com.shyblack.cryptosignals.repository.PortfolioExchangePositionRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies Binance user-data WebSocket events to the LIVE portfolio read snapshots.
 *
 * <p>Deliberately transport-free: it takes a raw payload string and a scope, so every correctness
 * rule — parsing, validation, duplicate suppression, ordering, and persistence — is verifiable
 * without opening a socket. The socket clients only obtain a payload and hand it here.
 *
 * <p>Correctness model:
 * <ul>
 *   <li>Every write is a <em>replacement</em> of exchange-owned state, keyed by a natural identity
 *       (asset, or symbol+side). Nothing is incremented, so replaying an event cannot double a
 *       balance, a position quantity or a P&amp;L figure.</li>
 *   <li>Duplicates are additionally suppressed through a persisted ledger keyed by a natural event
 *       identity, so suppression survives a restart and is race-safe via a unique constraint.</li>
 *   <li>An event older than the scope's high-water mark is rejected rather than applied.</li>
 *   <li>A <em>gap</em> is never inferred from elapsed time: an idle account produces a large
 *       timestamp jump that is indistinguishable from a missed event, so treating it as a gap would
 *       fabricate one. Gaps are established at the reconnect boundary instead, where a
 *       reconciliation is provably required.</li>
 * </ul>
 *
 * <p>Never places, cancels or modifies an order. Never touches PAPER or OPTIONS.
 */
@Service
public class LiveUserStreamEventProcessor {

	private static final Logger log = LoggerFactory.getLogger(LiveUserStreamEventProcessor.class);

	/** Event-time tolerance for a payload dated in the future, which indicates a bogus clock. */
	private static final Duration MAX_FUTURE_SKEW = Duration.ofMinutes(5);

	/** Outcome of one event, so callers can log and react without inspecting exceptions. */
	public enum Outcome {
		/** Applied to the read snapshot. */
		APPLIED,
		/** Already applied earlier; suppressed as a replay. */
		DUPLICATE,
		/** Older than the scope high-water mark; rejected without applying. */
		STALE_REJECTED,
		/** Needs a REST snapshot to be correct (deposit, gap, or a partial event). */
		RECONCILE_REQUIRED,
		/** A well-formed event this build does not handle; ignored, never fatal. */
		UNKNOWN_EVENT,
		/** Unparseable or structurally invalid; rejected without applying. */
		MALFORMED,
		/** Not a stream-capable LIVE scope (PAPER, MAIN or OPTIONS). */
		SCOPE_REJECTED
	}

	private final PortfolioExchangeBalanceRepository balanceRepository;
	private final PortfolioExchangePositionRepository positionRepository;
	private final PortfolioAccountConnectionRepository connectionRepository;
	private final PortfolioExchangeEventAppliedRepository eventRepository;

	public LiveUserStreamEventProcessor(
			PortfolioExchangeBalanceRepository balanceRepository,
			PortfolioExchangePositionRepository positionRepository,
			PortfolioAccountConnectionRepository connectionRepository,
			PortfolioExchangeEventAppliedRepository eventRepository) {
		this.balanceRepository = balanceRepository;
		this.positionRepository = positionRepository;
		this.connectionRepository = connectionRepository;
		this.eventRepository = eventRepository;
	}

	/**
	 * Applies one raw user-data payload for one scope.
	 *
	 * <p>{@code category} must be {@link AccountCategory#SPOT} or {@link AccountCategory#FUTURES};
	 * anything else is rejected, which is what keeps Options and Main out of the stream path.
	 */
	@Transactional
	public Outcome process(User user, AccountCategory category, String rawJson) {
		if (user == null || category == null) {
			return Outcome.SCOPE_REJECTED;
		}
		if (category != AccountCategory.SPOT && category != AccountCategory.FUTURES) {
			return Outcome.SCOPE_REJECTED;
		}
		if (user.getAccountType() != AccountType.LIVE) {
			// A stream must never write exchange state for a simulated account.
			return Outcome.SCOPE_REJECTED;
		}
		if (rawJson == null || rawJson.isBlank()) {
			return Outcome.MALFORMED;
		}

		JsonObject json;
		try {
			if (!JsonParser.parseString(rawJson).isJsonObject()) {
				return Outcome.MALFORMED;
			}
			json = JsonParser.parseString(rawJson).getAsJsonObject();
		} catch (Exception ex) {
			return Outcome.MALFORMED;
		}

		String eventType = str(json, "e");
		if (eventType == null) {
			return Outcome.MALFORMED;
		}

		boolean supported = switch (category) {
			case SPOT -> List.of("outboundAccountPosition", "balanceUpdate", "executionReport")
					.contains(eventType);
			case FUTURES -> List.of("ACCOUNT_UPDATE", "ORDER_TRADE_UPDATE").contains(eventType);
			default -> false;
		};
		if (!supported) {
			log.debug("Ignoring unhandled Binance user-data event type={} scope={}", eventType, category);
			return Outcome.UNKNOWN_EVENT;
		}

		Instant eventTime = eventTime(json, category);
		if (eventTime != null && eventTime.isAfter(Instant.now().plus(MAX_FUTURE_SKEW))) {
			return Outcome.MALFORMED;
		}

		PortfolioAccountConnection connection = connection(user, category);

		// Ordering: never let an older event overwrite newer state.
		if (eventTime != null && connection.getLastEventAt() != null
				&& eventTime.isBefore(connection.getLastEventAt())) {
			return Outcome.STALE_REJECTED;
		}

		String identity = identity(json, eventType, category);
		if (identity != null && eventRepository
				.existsByUserAndAccountCategoryAndEventTypeAndEventIdentity(
						user, category, eventType, identity)) {
			return Outcome.DUPLICATE;
		}

		Outcome outcome = apply(user, category, json, eventType, eventTime, connection);

		if (outcome == Outcome.APPLIED) {
			if (identity != null) {
				recordEvent(user, category, eventType, eventTime, identity);
			}
			if (eventTime != null) {
				connection.setLastEventAt(eventTime);
			}
			connection.setAvailability(AccountAvailability.AVAILABLE);
			connection.setConnectionStatus(ExchangeConnectionStatus.CONNECTED);
			connectionRepository.save(connection);
		} else if (outcome == Outcome.RECONCILE_REQUIRED) {
			markStale(connection, "User-data event requires a REST snapshot: " + eventType);
		}
		return outcome;
	}

	// ------------------------------------------------------------- dispatch

	private Outcome apply(
			User user,
			AccountCategory category,
			JsonObject json,
			String eventType,
			Instant eventTime,
			PortfolioAccountConnection connection) {

		return switch (eventType) {
			case "outboundAccountPosition" -> applySpotBalances(user, json, eventTime);
			case "balanceUpdate" -> {
				// A deposit or withdrawal changes free/locked, which this event does not report.
				// Deriving them would be fabrication, so a REST snapshot is required instead.
				yield Outcome.RECONCILE_REQUIRED;
			}
			case "executionReport" -> {
				// Order state is intentionally not modelled yet: Phase 4 persists synchronisation
				// state only, and full order history is deferred to a later phase.
				yield Outcome.APPLIED;
			}
			case "ACCOUNT_UPDATE" -> applyFuturesAccountUpdate(user, json, eventTime);
			case "ORDER_TRADE_UPDATE" -> Outcome.APPLIED;
			default -> Outcome.UNKNOWN_EVENT;
		};
	}

	/** Spot {@code outboundAccountPosition} carries the authoritative free/locked pair per asset. */
	private Outcome applySpotBalances(User user, JsonObject json, Instant eventTime) {
		JsonArray balances = json.getAsJsonArray("B");
		if (balances == null) {
			return Outcome.RECONCILE_REQUIRED;
		}
		List<PortfolioExchangeBalance> existing =
				balanceRepository.findByUserAndExchange(user, ExchangeName.BINANCE);
		for (JsonElement element : balances) {
			JsonObject row = element.getAsJsonObject();
			String rawAsset = str(row, "a");
			if (rawAsset == null) {
				continue;
			}
			String asset = rawAsset.toUpperCase();
			PortfolioExchangeBalance target = existing.stream()
					.filter(b -> asset.equals(b.getAsset()))
					.findFirst()
					.orElseGet(PortfolioExchangeBalance::new);
			target.setUser(user);
			target.setExchange(ExchangeName.BINANCE);
			target.setAsset(asset);
			target.setFree(decimal(row, "f"));
			target.setLocked(decimal(row, "l"));
			target.setFetchedAt(eventTime == null ? Instant.now() : eventTime);
			balanceRepository.save(target);
		}
		return Outcome.APPLIED;
	}

	/**
	 * Futures {@code ACCOUNT_UPDATE} carries the authoritative position amount, entry price,
	 * unrealized P&amp;L, margin type and isolated wallet. It does NOT carry mark price or
	 * liquidation price, so those are left at their last REST values rather than guessed; REST
	 * reconciliation is what refreshes them.
	 */
	private Outcome applyFuturesAccountUpdate(User user, JsonObject json, Instant eventTime) {
		JsonObject account = json.getAsJsonObject("a");
		if (account == null) {
			return Outcome.RECONCILE_REQUIRED;
		}
		JsonArray positions = account.getAsJsonArray("P");
		if (positions == null) {
			// A balance-only ACCOUNT_UPDATE is legitimate; nothing to apply but still a real event.
			return Outcome.APPLIED;
		}

		List<PortfolioExchangePosition> existing =
				positionRepository.findByUserAndExchangeOrderBySymbolAsc(user, ExchangeName.BINANCE);
		Instant fetchedAt = eventTime == null ? Instant.now() : eventTime;

		for (JsonElement element : positions) {
			JsonObject row = element.getAsJsonObject();
			String rawSymbol = str(row, "s");
			if (rawSymbol == null) {
				continue;
			}
			String symbol = rawSymbol.toUpperCase();
			BigDecimal amount = decimal(row, "pa");
			if (amount == null) {
				continue;
			}
			PositionSide side = amount.signum() < 0 ? PositionSide.SHORT : PositionSide.LONG;
			boolean hedged = "SHORT".equalsIgnoreCase(String.valueOf(str(row, "ps")));

			if (amount.signum() == 0) {
				// Flat on the exchange: a real closed state, so the row must disappear.
				existing.stream()
						.filter(p -> p.getSymbol().equals(symbol)
								&& (hedged ? p.getPositionSide() == side : true))
						.forEach(positionRepository::delete);
				continue;
			}

			PortfolioExchangePosition target = existing.stream()
					.filter(p -> p.getSymbol().equals(symbol) && p.getPositionSide() == side)
					.findFirst()
					.orElseGet(PortfolioExchangePosition::new);
			target.setUser(user);
			target.setExchange(ExchangeName.BINANCE);
			target.setSymbol(symbol);
			target.setPositionSide(side);
			target.setPositionAmount(amount);
			target.setEntryPrice(decimal(row, "ep"));
			target.setUnrealizedProfit(decimal(row, "up"));
			target.setIsolatedMargin(decimal(row, "iw"));
			target.setMarginMode(marginMode(str(row, "mt")));
			target.setFetchedAt(fetchedAt);
			// markPrice, liquidationPrice and leverage are absent from this event and are
			// intentionally preserved from the last REST snapshot rather than defaulted.
			positionRepository.save(target);
		}
		return Outcome.APPLIED;
	}

	// ------------------------------------------------------------- identity

	/**
	 * Natural identity built only from exchange-supplied fields, so a byte-identical replay produces
	 * the same key. A generated UUID would differ on every delivery and dedupe nothing.
	 */
	static String identity(JsonObject json, String eventType, AccountCategory category) {
		if (category == AccountCategory.FUTURES) {
			Long transactionTime = longValue(json, "T");
			if ("ACCOUNT_UPDATE".equals(eventType)) {
				JsonObject account = json.getAsJsonObject("a");
				String mode = account == null ? null : str(account, "m");
				return "T=" + transactionTime + ";m=" + mode;
			}
			JsonObject order = json.getAsJsonObject("o");
			if (order == null) {
				return "T=" + transactionTime;
			}
			return "T=" + transactionTime
					+ ";i=" + str(order, "i")
					+ ";t=" + longValue(order, "t")
					+ ";X=" + str(order, "X");
		}
		Long eventTime = longValue(json, "E");
		if ("executionReport".equals(eventType)) {
			return "E=" + eventTime + ";i=" + str(json, "i") + ";X=" + str(json, "X");
		}
		return "E=" + eventTime + ";u=" + longValue(json, "u");
	}

	/** Spot uses {@code E}; futures uses the transaction time {@code T}. */
	private static Instant eventTime(JsonObject json, AccountCategory category) {
		Long millis = category == AccountCategory.FUTURES
				? longValue(json, "T")
				: longValue(json, "E");
		return millis == null ? null : Instant.ofEpochMilli(millis);
	}

	// -------------------------------------------------------------- helpers

	private PortfolioAccountConnection connection(User user, AccountCategory category) {
		return connectionRepository.findByUserAndAccountModeAndAccountCategory(
						user, AccountMode.LIVE, category)
				.orElseGet(() -> {
					PortfolioAccountConnection created = new PortfolioAccountConnection();
					created.setUser(user);
					created.setAccountMode(AccountMode.LIVE);
					created.setAccountCategory(category);
					created.setExchange(ExchangeName.BINANCE);
					created.setConnectionStatus(ExchangeConnectionStatus.NOT_CONNECTED);
					created.setAvailability(AccountAvailability.NOT_CONNECTED);
					return created;
				});
	}

	private void markStale(PortfolioAccountConnection connection, String message) {
		connection.setAvailability(AccountAvailability.STALE);
		connection.setConnectionStatus(ExchangeConnectionStatus.CONNECTED);
		connection.setLastSyncMessage(truncate(message));
		connectionRepository.save(connection);
	}

	private void recordEvent(
			User user,
			AccountCategory category,
			String eventType,
			Instant eventTime,
			String identity) {
		PortfolioExchangeEventApplied applied = new PortfolioExchangeEventApplied();
		applied.setUser(user);
		applied.setAccountCategory(category);
		applied.setExchange(ExchangeName.BINANCE);
		applied.setEventType(eventType);
		applied.setEventTime(eventTime);
		applied.setEventIdentity(identity.length() > 160 ? identity.substring(0, 160) : identity);
		applied.setAppliedAt(Instant.now());
		eventRepository.save(applied);
	}

	private static FuturesMarginMode marginMode(String raw) {
		if (raw == null) {
			return null;
		}
		return switch (raw.toUpperCase()) {
			case "ISOLATED", "ISOLATED_MARGIN" -> FuturesMarginMode.ISOLATED;
			case "CROSSED", "CROSS", "CROSSED_MARGIN" -> FuturesMarginMode.CROSS;
			default -> null;
		};
	}

	private static String str(JsonObject json, String key) {
		JsonElement element = json == null ? null : json.get(key);
		return element == null || element.isJsonNull() ? null : element.getAsString();
	}

	private static Long longValue(JsonObject json, String key) {
		JsonElement element = json == null ? null : json.get(key);
		if (element == null || element.isJsonNull()) {
			return null;
		}
		try {
			return element.getAsLong();
		} catch (Exception ex) {
			return null;
		}
	}

	private static BigDecimal decimal(JsonObject json, String key) {
		JsonElement element = json == null ? null : json.get(key);
		if (element == null || element.isJsonNull()) {
			return null;
		}
		try {
			return new BigDecimal(element.getAsString());
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private static String truncate(String message) {
		if (message == null) {
			return null;
		}
		return message.length() <= 200 ? message : message.substring(0, 197) + "...";
	}
}