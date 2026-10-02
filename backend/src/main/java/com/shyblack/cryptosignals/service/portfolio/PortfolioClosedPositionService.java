package com.shyblack.cryptosignals.service.portfolio;

import com.shyblack.cryptosignals.dto.portfolio.PortfolioClosedPositionView;
import com.shyblack.cryptosignals.dto.portfolio.PortfolioClosedPositionsResponse;
import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.Position;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountAvailability;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.PositionStatus;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.exchange.ExchangeAdapterException;
import com.shyblack.cryptosignals.exchange.futures.FuturesExchangeAdapter;
import com.shyblack.cryptosignals.exchange.futures.FuturesTradeSnapshot;
import com.shyblack.cryptosignals.repository.ExchangeCredentialRepository;
import com.shyblack.cryptosignals.repository.PositionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closed positions for one account scope.
 *
 * <p>Scope discipline:
 *
 * <ul>
 *   <li>LIVE FUTURES reconstructs each round trip from the exchange's own {@code userTrades} fills.
 *       Nothing is inferred from a signal, a local order or a stored position, and no realized P&amp;L
 *       is recomputed from prices — it is summed from the {@code realizedPnl} the exchange attributed
 *       to each fill.</li>
 *   <li>PAPER reads the paper module's own closed {@link Position} rows, which are authoritative local
 *       records. No paper P&amp;L is recomputed and no exchange record can appear here.</li>
 *   <li>LIVE SPOT is {@link AccountAvailability#UNSUPPORTED}: spot holds wallet assets rather than a
 *       leveraged position lifecycle, so there is no position to close.</li>
 *   <li>OPTIONS and MAIN are never served, so spot and futures histories can never be merged.</li>
 * </ul>
 *
 * <p>When a round trip cannot be fully observed inside the requested window, the affected fields are
 * null and {@link PortfolioClosedPositionsResponse#partial()} is true. Nothing is filled in with a
 * guess, and no field is ever defaulted to zero.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortfolioClosedPositionService {

	private static final Logger log = LoggerFactory.getLogger(PortfolioClosedPositionService.class);

	static final String SPOT_NO_POSITION_MESSAGE =
			"Spot holds wallet assets rather than leveraged positions, so it has no closed-position "
					+ "record. Spot activity is shown under wallet holdings, order history and trade history.";

	static final String PAPER_ORDER_ID_MESSAGE =
			"A simulated round trip has no exchange order or trade identifiers, because no exchange "
					+ "order was ever placed. The paper execution record is the only identity it has.";

	static final String PARTIAL_MESSAGE =
			"Some round trips were only partly inside the requested window, so their entry price, "
					+ "duration or fees are shown as unavailable rather than estimated. Narrow or widen "
					+ "the window to cover the whole round trip.";

	private final ExchangeCredentialRepository credentialRepository;
	private final FuturesExchangeAdapter futuresAdapter;
	private final PositionRepository positionRepository;

	/**
	 * Closed positions for one scope over an explicit window.
	 *
	 * @param symbol optional symbol narrowing. Null means every symbol in the window.
	 */
	public PortfolioClosedPositionsResponse closedPositions(
			User user,
			AccountMode mode,
			AccountCategory category,
			HistoryWindow window,
			String symbol) {

		Objects.requireNonNull(user, "user is required");
		Objects.requireNonNull(mode, "accountMode is required");
		Objects.requireNonNull(category, "accountCategory is required");
		Objects.requireNonNull(window, "window is required");

		if (category == AccountCategory.OPTIONS) {
			return empty(mode, category, "Options is a reserved capability and has no positions to close.");
		}
		if (category == AccountCategory.MAIN) {
			return empty(mode, category,
					"Closed positions are reported per market scope; MAIN would mix the spot and "
							+ "futures records.");
		}
		if (mode.isPaper()) {
			return paperClosedPositions(user, mode, category);
		}
		if (category == AccountCategory.SPOT) {
			return new PortfolioClosedPositionsResponse(
					mode, category, AccountAvailability.UNSUPPORTED, null, false, List.of(),
					SPOT_NO_POSITION_MESSAGE);
		}
		return liveFuturesClosedPositions(user, mode, category, window, symbol);
	}

	// ----------------------------------------------------------------- PAPER

	/**
	 * Paper round trips come straight from the paper engine's own closed positions, so entry price,
	 * exit price, fees and timestamps are the engine's recorded values rather than a reconstruction.
	 */
	private PortfolioClosedPositionsResponse paperClosedPositions(
			User user, AccountMode mode, AccountCategory category) {

		TradingMode marketMode = category.tradingMode().orElseThrow();
		List<Position> closed = positionRepository
				.findByOwnerAndAccountTypeAndSignalTradingMode(user, AccountType.PAPER, marketMode)
				.stream()
				.filter(position -> position.getStatus() == PositionStatus.CLOSED)
				.sorted(Comparator.comparing(
								Position::getClosedAt,
								Comparator.nullsLast(Comparator.reverseOrder()))
						.thenComparing(Position::getId, Comparator.nullsLast(Comparator.reverseOrder())))
				.toList();

		List<PortfolioClosedPositionView> views = new ArrayList<>(closed.size());
		for (Position position : closed) {
			views.add(new PortfolioClosedPositionView(
					mode,
					category,
					position.getSymbol(),
					position.getSide() == null ? null : position.getSide().name(),
					position.getEntryPrice(),
					// The engine's recorded average exit price is preferred: a partial exit ladder
					// fills at several prices and the last recorded exit price is not the average.
					position.getAverageExitPrice() != null
							? position.getAverageExitPrice()
							: position.getExitPrice(),
					position.originalQty(),
					position.getRealizedPnl(),
					fees(position.getEntryFee(), position.getExitFee()),
					null,
					null,
					null,
					null,
					position.getOpenedAt(),
					position.getClosedAt(),
					duration(position.getOpenedAt(), position.getClosedAt()),
					List.of(),
					List.of()));
		}

		return new PortfolioClosedPositionsResponse(
				mode, category, AccountAvailability.AVAILABLE, PortfolioHistoryService.SOURCE_LOCAL_PAPER,
				false, views, views.isEmpty() ? null : PAPER_ORDER_ID_MESSAGE);
	}

	/**
	 * Entry plus exit fee, or null when either side is unrecorded. A partial sum would understate the
	 * cost and a zero would claim the engine recorded no fee at all, so neither is substituted.
	 */
	private static BigDecimal fees(BigDecimal entryFee, BigDecimal exitFee) {
		if (entryFee == null || exitFee == null) {
			return null;
		}
		return entryFee.add(exitFee);
	}

	private static Duration duration(Instant openedAt, Instant closedAt) {
		if (openedAt == null || closedAt == null) {
			return null;
		}
		Duration elapsed = Duration.between(openedAt, closedAt);
		return elapsed.isNegative() ? null : elapsed;
	}

	// ----------------------------------------------------------------- LIVE

	/**
	 * Reconstructs futures round trips from the exchange's own fills.
	 *
	 * <p>Algorithm, per {@code symbol + positionSide}: walk the fills oldest first while tracking the
	 * running signed quantity. A session is a maximal run of fills that starts at zero, never crosses
	 * zero, and returns to zero. A session that returns to zero is a closed position.
	 *
	 * <p>Two facts are recorded rather than invented:
	 * <ul>
	 *   <li>When a session begins while the running quantity is already non-zero, the round trip
	 *       started before the window, so its opening fills are unobservable and the entry price is
	 *       left null.</li>
	 *   <li>Funding fees are account-level income records with no position attribution, so
	 *       {@code funding} is always null.</li>
	 * </ul>
	 */
	private PortfolioClosedPositionsResponse liveFuturesClosedPositions(
			User user, AccountMode mode, AccountCategory category, HistoryWindow window, String symbol) {

		ExchangeCredential credential = credentialRepository
				.findByUser_IdAndExchange(user.getId(), ExchangeName.BINANCE)
				.orElse(null);
		if (credential == null) {
			return new PortfolioClosedPositionsResponse(
					mode, category, AccountAvailability.NOT_CONNECTED, null, false, List.of(),
					"No exchange credential is connected for this account, so no closed futures "
							+ "position can be read. Simulated positions are never substituted here.");
		}

		List<FuturesTradeSnapshot> trades;
		try {
			trades = futuresAdapter.getTrades(
					credential, symbol, window.from(), window.to(), window.limit());
		} catch (ExchangeAdapterException ex) {
			log.warn("[PortfolioClosedPositions] futures fill read failed for user={} reason={}",
					user.getId(), ex.getMessage());
			return new PortfolioClosedPositionsResponse(
					mode, category, AccountAvailability.ERROR, PortfolioHistoryService.SOURCE_EXCHANGE,
					false, List.of(), LivePortfolioSyncService.describe(ex));
		}

		List<PortfolioClosedPositionView> views = reconstruct(mode, category, trades);
		boolean partial = views.stream().anyMatch(view -> view.entryPrice() == null);
		return new PortfolioClosedPositionsResponse(
				mode, category, AccountAvailability.AVAILABLE, PortfolioHistoryService.SOURCE_EXCHANGE,
				partial, views, partial ? PARTIAL_MESSAGE : null);
	}

	private List<PortfolioClosedPositionView> reconstruct(
			AccountMode mode, AccountCategory category, List<FuturesTradeSnapshot> trades) {

		// Oldest first per instrument, so a session is walked in the order the exchange filled it.
		Map<String, List<FuturesTradeSnapshot>> byInstrument = new LinkedHashMap<>();
		for (FuturesTradeSnapshot trade : trades) {
			if (trade == null || trade.symbol() == null || trade.quantity() == null) {
				continue;
			}
			byInstrument
					.computeIfAbsent(trade.symbol() + "|" + String.valueOf(trade.positionSide()),
							key -> new ArrayList<>())
					.add(trade);
		}

		List<PortfolioClosedPositionView> views = new ArrayList<>();
		for (List<FuturesTradeSnapshot> fills : byInstrument.values()) {
			fills.sort(Comparator
					.comparing(FuturesTradeSnapshot::tradedAt, Comparator.nullsLast(Comparator.naturalOrder()))
					.thenComparing(FuturesTradeSnapshot::tradeId, Comparator.nullsLast(Comparator.naturalOrder())));
			walk(mode, category, fills, views);
		}
		views.sort(Comparator.comparing(
						PortfolioClosedPositionView::closedAt, Comparator.nullsLast(Comparator.reverseOrder()))
				.thenComparing(PortfolioClosedPositionService::lastTradeId,
						Comparator.<Long>nullsLast(Comparator.<Long>reverseOrder())));
		return List.copyOf(views);
	}

	/** Tie-breaker key: the newest trade id behind a round trip, or null when none was reported. */
	private static Long lastTradeId(PortfolioClosedPositionView view) {
		List<Long> ids = view.tradeIds();
		return ids.isEmpty() ? null : ids.get(ids.size() - 1);
	}

	private void walk(
			AccountMode mode,
			AccountCategory category,
			List<FuturesTradeSnapshot> fills,
			List<PortfolioClosedPositionView> out) {

		BigDecimal running = BigDecimal.ZERO;
		Session session = null;
		// Set when a fill flipped the exposure past zero. The round trip that the flip opened cannot
		// be reconstructed, because the same fill both closed one position and opened the next and the
		// exchange reports a single realized P&L for it. Rather than split one fill's quantity and P&L
		// across two records by guesswork, the next session is forced to report an unknown entry price.
		boolean nextSessionUnclean = false;

		for (FuturesTradeSnapshot fill : fills) {
			BigDecimal signed = signedDelta(fill);
			if (signed == null) {
				// Without a usable side the fill cannot be attributed to a round trip, so the running
				// quantity can no longer be trusted and any open session is abandoned rather than
				// completed with a figure that would be wrong.
				session = null;
				nextSessionUnclean = true;
				continue;
			}

			if (session == null) {
				// The direction is the sign of the exposure this session is closing, which is not
				// always the sign of the fill that closes it. After a flip, the position is already
				// short while the fill that flattens it is a buy, so taking the fill's sign would
				// label the record LONG and count the closing fill as an opening one.
				int direction = running.signum() != 0 ? running.signum() : signed.signum();
				session = new Session(
						fill, direction, !nextSessionUnclean && running.signum() == 0);
				nextSessionUnclean = false;
			}

			running = running.add(signed);
			session.add(fill, signed, running);

			if (running.signum() == 0) {
				out.add(session.toView(mode, category));
				session = null;
			} else if (running.signum() != session.direction) {
				// Exposure flipped sign without passing through zero: the previous round trip is over.
				out.add(session.toView(mode, category));
				session = null;
				nextSessionUnclean = true;
			}
		}
		// A session still open at the end of the window is not a closed position, so it is not emitted.
	}

	/**
	 * Fill quantity signed in the direction of exposure: a buy increases a long or a one-way position
	 * and decreases a short one. Null when the side is missing or unrecognised.
	 */
	private static BigDecimal signedDelta(FuturesTradeSnapshot fill) {
		String side = fill.side();
		if (side == null) {
			return null;
		}
		BigDecimal quantity = fill.quantity();
		if (quantity == null) {
			return null;
		}
		boolean increases = switch (side.trim().toUpperCase()) {
			case "BUY" -> !"SHORT".equalsIgnoreCase(fill.positionSide());
			case "SELL" -> "SHORT".equalsIgnoreCase(fill.positionSide());
			default -> false;
		};
		return increases ? quantity.abs() : quantity.abs().negate();
	}

	/** One reconstructed round trip for a single instrument and position side. */
	private static final class Session {

		private final String symbol;
		private final String positionSide;
		private final int direction;
		private final boolean cleanBaseline;
		private final Set<Long> orderIds = new LinkedHashSet<>();
		private final Set<Long> tradeIds = new LinkedHashSet<>();

		private BigDecimal entryNotional = BigDecimal.ZERO;
		private BigDecimal entryQuantity = BigDecimal.ZERO;
		private BigDecimal exitNotional = BigDecimal.ZERO;
		private BigDecimal exitQuantity = BigDecimal.ZERO;
		private BigDecimal fees = BigDecimal.ZERO;
		private boolean everyFeeReported = true;
		private BigDecimal realizedPnl = BigDecimal.ZERO;
		private boolean everyRealizedPnlReported = true;
		private BigDecimal peakQuantity = BigDecimal.ZERO;
		private Instant openedAt;
		private Instant closedAt;

		private Session(FuturesTradeSnapshot first, int direction, boolean cleanBaseline) {
			this.symbol = first.symbol();
			this.positionSide = first.positionSide();
			this.direction = direction;
			this.cleanBaseline = cleanBaseline;
		}

		private void add(FuturesTradeSnapshot fill, BigDecimal signed, BigDecimal runningAfter) {
			if (fill.price() == null) {
				// Price-weighted averages are impossible without a price, so this side stays empty and
				// the corresponding average is reported as unknown instead of as a partial mean.
				if (signed.signum() == direction) {
					entryQuantity = null;
				} else {
					exitQuantity = null;
				}
			} else {
				BigDecimal quantity = signed.abs();
				BigDecimal notional = fill.price().multiply(quantity);
				if (signed.signum() == direction) {
					if (entryQuantity != null) {
						entryQuantity = entryQuantity.add(quantity);
						entryNotional = entryNotional.add(notional);
					}
				} else {
					if (exitQuantity != null) {
						exitQuantity = exitQuantity.add(quantity);
						exitNotional = exitNotional.add(notional);
					}
				}
			}

			// The size of a round trip is the largest exposure it held, not a sum of its fills.
			if (runningAfter != null) {
				peakQuantity = peakQuantity.max(runningAfter.abs());
			}
			if (fill.commission() != null) {
				fees = fees.add(fill.commission());
			} else {
				everyFeeReported = false;
			}
// Realized P&L is only meaningful on a fill that *reduces* exposure. An opening
			// fill legitimately carries none, so requiring one there would make a perfectly
			// ordinary round trip report "unknown" for its realized P&L.
			// Realized P&L is only meaningful on a fill that *reduces* exposure. An opening
			// fill legitimately carries none, so demanding one there would make an ordinary
			// round trip report "unknown" for a figure the exchange did publish on its close.
			if (signed.signum() != direction) {
				if (fill.realizedPnl() != null) {
					realizedPnl = realizedPnl.add(fill.realizedPnl());
				} else {
					everyRealizedPnlReported = false;
				}
			}
			if (fill.orderId() != null) {
				orderIds.add(fill.orderId());
			}
			if (fill.tradeId() != null) {
				tradeIds.add(fill.tradeId());
			}
			if (openedAt == null || (fill.tradedAt() != null && fill.tradedAt().isBefore(openedAt))) {
				openedAt = fill.tradedAt();
			}
			if (closedAt == null || (fill.tradedAt() != null && fill.tradedAt().isAfter(closedAt))) {
				closedAt = fill.tradedAt();
			}
		}

		private PortfolioClosedPositionView toView(AccountMode mode, AccountCategory category) {
			// The entry average is only trustworthy when the session actually started at zero. A round
			// trip that began before the window has opening fills this read cannot see, so reporting a
			// mean of the visible decreasing fills as an entry price would be fabrication.
			BigDecimal entry = cleanBaseline ? average(entryNotional, entryQuantity) : null;
			BigDecimal exit = average(exitNotional, exitQuantity);

			return new PortfolioClosedPositionView(
					mode,
					category,
					symbol,
					side(),
					entry,
					exit,
					peakQuantity.signum() > 0 ? peakQuantity : exitQuantity,
					everyRealizedPnlReported ? realizedPnl : null,
					everyFeeReported ? fees : null,
					// Funding fees are account-level income records; Binance provides no position
					// attribution for them, so this stays unknown rather than being guessed.
					null,
					null,
					null,
					null,
					openedAt,
					closedAt,
					duration(openedAt, closedAt),
					new ArrayList<>(orderIds),
					new ArrayList<>(tradeIds));
		}

		private String side() {
			if (positionSide != null && !positionSide.isBlank()
					&& !"BOTH".equalsIgnoreCase(positionSide)) {
				return positionSide.trim().toUpperCase();
			}
			return direction > 0 ? "LONG" : "SHORT";
		}

		private static BigDecimal average(BigDecimal notional, BigDecimal quantity) {
			if (quantity == null || quantity.signum() == 0 || notional == null) {
				return null;
			}
			return notional.divide(quantity, 12, RoundingMode.HALF_UP).stripTrailingZeros();
		}
	}

	private static PortfolioClosedPositionsResponse empty(
			AccountMode mode, AccountCategory category, String message) {
		return new PortfolioClosedPositionsResponse(
				mode, category, AccountAvailability.UNSUPPORTED, null, false, List.of(), message);
	}
}