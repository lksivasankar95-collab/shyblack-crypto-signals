package com.shyblack.cryptosignals.exchange.futures.mock;

import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.FuturesMarginMode;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderStatus;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderType;
import com.shyblack.cryptosignals.entity.enums.FuturesPositionMode;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.exchange.ExchangeAdapterException;
import com.shyblack.cryptosignals.exchange.SymbolRules;
import com.shyblack.cryptosignals.exchange.futures.FuturesIncomeSnapshot;
import com.shyblack.cryptosignals.exchange.futures.FuturesOrderSnapshot;
import com.shyblack.cryptosignals.exchange.futures.FuturesTradeSnapshot;
import com.shyblack.cryptosignals.exchange.futures.FuturesAccountSnapshot;
import com.shyblack.cryptosignals.exchange.futures.FuturesExchangeAdapter;
import com.shyblack.cryptosignals.exchange.futures.FuturesExchangePosition;
import com.shyblack.cryptosignals.exchange.futures.FuturesOrderResult;
import com.shyblack.cryptosignals.exchange.futures.PlaceFuturesOrderRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process Binance USDT-M simulator. Used when
 * {@code app.futures-trading.mode=MOCK}. NEVER touches real Binance.
 *
 * Idempotent by clientOrderId, market orders fill immediately, STOP_MARKET
 * orders stay ACKNOWLEDGED until {@link #forceFill}.
 */
public class MockFuturesExchangeAdapter implements FuturesExchangeAdapter {

	private final Map<String, FuturesOrderResult> orders = new ConcurrentHashMap<>();
	private final Map<String, SymbolRules> rules = new ConcurrentHashMap<>();
	private final Map<String, Integer> leverages = new ConcurrentHashMap<>();
	private final Map<String, FuturesMarginMode> marginModes = new ConcurrentHashMap<>();
	private final Map<String, FuturesExchangePosition> positions = new ConcurrentHashMap<>();
	private final List<FuturesOrderSnapshot> seededOrders = new CopyOnWriteArrayList<>();
	private final List<FuturesTradeSnapshot> seededTrades = new CopyOnWriteArrayList<>();
	private final List<FuturesIncomeSnapshot> seededIncome = new CopyOnWriteArrayList<>();
	private BigDecimal walletBalance = new BigDecimal("10000");
	private BigDecimal availableBalance = new BigDecimal("10000");
	private FuturesPositionMode positionMode = FuturesPositionMode.ONE_WAY;

	public MockFuturesExchangeAdapter() {
		putRules(new SymbolRules(
				"BTCUSDT",
				new BigDecimal("0.001"),
				new BigDecimal("1000"),
				new BigDecimal("0.001"),
				new BigDecimal("0.10"),
				new BigDecimal("1000000"),
				new BigDecimal("0.10"),
				new BigDecimal("5")));
	}

	public void putRules(SymbolRules symbolRules) { rules.put(symbolRules.symbol().toUpperCase(), symbolRules); }
	public void setBalances(BigDecimal wallet, BigDecimal available) {
		this.walletBalance = wallet; this.availableBalance = available;
	}
	public void setPositionMode(FuturesPositionMode mode) { this.positionMode = mode; }

	@Override public ExchangeName exchange() { return ExchangeName.BINANCE; }

	@Override public FuturesAccountSnapshot validateCredentials(ExchangeCredential c) { return snapshot(); }
	@Override public FuturesAccountSnapshot getAccount(ExchangeCredential c) { return snapshot(); }

	private FuturesAccountSnapshot snapshot() {
		return new FuturesAccountSnapshot(
				"USDT", walletBalance, availableBalance, walletBalance,
				BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
				positionMode, FuturesMarginMode.ISOLATED, true, Instant.now());
	}

	@Override
	public List<FuturesExchangePosition> getPositions(ExchangeCredential credential) {
		return List.copyOf(positions.values());
	}

	/** Seeds an authoritative exchange position. Never touches the real exchange. */
	public void putPosition(FuturesExchangePosition position) {
		positions.put(positionKey(position.symbol(), position.positionSide()), position);
	}

	/** Models the exchange reporting a flat (closed) position for a symbol. */
	public void clearPosition(String symbol, PositionSide side) {
		positions.remove(positionKey(symbol, side));
	}

	private static String positionKey(String symbol, PositionSide side) {
		return symbol.toUpperCase() + ":" + side.name();
	}

	/**
	 * Restores pristine state. Required because this adapter is a singleton bean: without it, state
	 * seeded by one test leaks into every other test sharing the cached Spring context.
	 */
	public void reset() {
		orders.clear();
		rules.clear();
		leverages.clear();
		marginModes.clear();
		positions.clear();
		seededOrders.clear();
		seededTrades.clear();
		seededIncome.clear();
		walletBalance = new BigDecimal("10000");
		availableBalance = new BigDecimal("10000");
		positionMode = FuturesPositionMode.ONE_WAY;
	}

	@Override
	public void setLeverage(ExchangeCredential credential, String symbol, int leverage) {
		leverages.put(symbol.toUpperCase(), leverage);
	}

	@Override
	public void setMarginMode(ExchangeCredential credential, String symbol, FuturesMarginMode marginMode) {
		marginModes.put(symbol.toUpperCase(), marginMode);
	}

	public Integer effectiveLeverage(String symbol) { return leverages.get(symbol.toUpperCase()); }
	public FuturesMarginMode effectiveMarginMode(String symbol) { return marginModes.get(symbol.toUpperCase()); }

	@Override public SymbolRules getSymbolRules(String symbol) { return rules.get(symbol.toUpperCase()); }

	@Override
	public FuturesOrderResult placeOrder(ExchangeCredential credential, PlaceFuturesOrderRequest req) {
		FuturesOrderResult prior = orders.get(req.clientOrderId());
		if (prior != null) return prior;

		String exchangeOrderId = "MOCK-F-" + UUID.randomUUID().toString().substring(0, 10);
		BigDecimal fillPrice = req.price();
		FuturesOrderStatus status = req.type() == FuturesOrderType.MARKET
				? FuturesOrderStatus.FILLED
				: FuturesOrderStatus.ACKNOWLEDGED;
		BigDecimal executed = status == FuturesOrderStatus.FILLED
				? req.quantity() : BigDecimal.ZERO;
		BigDecimal cumQuote = fillPrice == null ? BigDecimal.ZERO
				: executed.multiply(fillPrice);
		BigDecimal fee = cumQuote.multiply(new BigDecimal("0.0004")); // 0.04% mock taker fee

		FuturesOrderResult result = new FuturesOrderResult(
				exchangeOrderId, req.clientOrderId(), req.symbol(),
				req.side(), req.positionSide(), status, req.reduceOnly(),
				req.quantity(), executed, cumQuote,
				status == FuturesOrderStatus.FILLED ? fillPrice : null,
				status == FuturesOrderStatus.FILLED ? fee : BigDecimal.ZERO,
				status == FuturesOrderStatus.FILLED ? "USDT" : null,
				Instant.now(), "MOCK");
		orders.put(req.clientOrderId(), result);
		return result;
	}

	@Override
	public FuturesOrderResult getOrder(ExchangeCredential c, String symbol, String clientOrderId) {
		return orders.get(clientOrderId);
	}

	@Override
	public FuturesOrderResult cancelOrder(ExchangeCredential c, String symbol, String clientOrderId) {
		FuturesOrderResult existing = orders.get(clientOrderId);
		if (existing == null) return null;
		FuturesOrderResult cancelled = new FuturesOrderResult(
				existing.exchangeOrderId(), clientOrderId, symbol,
				existing.side(), existing.positionSide(),
				FuturesOrderStatus.CANCELLED, existing.reduceOnly(),
				existing.requestedQuantity(), existing.executedQuantity(),
				existing.cumulativeQuoteQty(), existing.avgFillPrice(),
				existing.fee(), existing.feeAsset(), Instant.now(), "MOCK cancel");
		orders.put(clientOrderId, cancelled);
		return cancelled;
	}

	// ---- Read-only history fixtures ------------------------------------

	@Override
	public List<FuturesOrderSnapshot> getOpenOrders(ExchangeCredential credential, String symbol) {
		return seededOrders.stream()
				.filter(o -> symbol == null || symbol.isBlank() || symbol.equalsIgnoreCase(o.symbol()))
				.toList();
	}

	@Override
	public List<FuturesOrderSnapshot> getAllOrders(
			ExchangeCredential credential, String symbol, Instant from, Instant to, int limit) {
		if (symbol == null || symbol.isBlank()) {
			throw new ExchangeAdapterException(
					"A symbol is required for the exchange all-orders endpoint", null, false, null, null);
		}
		return seededOrders.stream()
				.filter(o -> symbol.equalsIgnoreCase(o.symbol()))
				.filter(o -> inWindow(o.createdAt(), from, to))
				.limit(limit)
				.toList();
	}

	@Override
	public List<FuturesTradeSnapshot> getTrades(
			ExchangeCredential credential, String symbol, Instant from, Instant to, int limit) {
		return seededTrades.stream()
				.filter(t -> symbol == null || symbol.isBlank() || symbol.equalsIgnoreCase(t.symbol()))
				.filter(t -> inWindow(t.tradedAt(), from, to))
				.limit(limit)
				.toList();
	}

	@Override
	public List<FuturesIncomeSnapshot> getIncome(
			ExchangeCredential credential, String incomeType, Instant from, Instant to, int limit) {
		return seededIncome.stream()
				.filter(i -> incomeType == null || incomeType.isBlank()
						|| incomeType.equalsIgnoreCase(i.incomeType()))
				.filter(i -> inWindow(i.time(), from, to))
				.limit(limit)
				.toList();
	}

	/** Seeds exchange-reported futures order history. Test-only; never an exchange call. */
	public void putOrder(FuturesOrderSnapshot order) {
		seededOrders.add(order);
	}

	/** Seeds exchange-reported futures fill history. Test-only; never an exchange call. */
	public void putTrade(FuturesTradeSnapshot trade) {
		seededTrades.add(trade);
	}

	/** Seeds exchange-reported income records. Test-only; never an exchange call. */
	public void putIncome(FuturesIncomeSnapshot income) {
		seededIncome.add(income);
	}

	/**
	 * Drops every seeded record.
	 *
	 * <p>This adapter is a singleton for the whole application context, so its seeded history
	 * otherwise accumulates across test methods and a later test reads rows an earlier one
	 * declared. Clearing in {@code @BeforeEach} is what keeps each assertion about only the
	 * records its own test seeded. Test-only; never an exchange call.
	 */
	public void clearSeeded() {
		seededOrders.clear();
		seededTrades.clear();
		seededIncome.clear();
		positions.clear();
	}

	private static boolean inWindow(Instant value, Instant from, Instant to) {
		if (value == null) {
			return true;
		}
		if (from != null && value.isBefore(from)) {
			return false;
		}
		return to == null || value.isBefore(to);
	}

	// Test hooks --------------------------------------------------------

	public void forceFill(String clientOrderId, BigDecimal fillPrice) {
		FuturesOrderResult existing = orders.get(clientOrderId);
		if (existing == null) return;
		BigDecimal executed = existing.requestedQuantity();
		BigDecimal cumQuote = executed.multiply(fillPrice);
		orders.put(clientOrderId, new FuturesOrderResult(
				existing.exchangeOrderId(), clientOrderId, existing.symbol(),
				existing.side(), existing.positionSide(),
				FuturesOrderStatus.FILLED, existing.reduceOnly(),
				existing.requestedQuantity(), executed, cumQuote, fillPrice,
				cumQuote.multiply(new BigDecimal("0.0004")), "USDT",
				Instant.now(), "MOCK force-fill"));
	}

	/**
	 * Every order this simulator was asked to place, newest last.
	 *
	 * <p>Mirrors {@code MockExchangeTradingAdapter.allOrders()} so a test can assert
	 * that a futures submission actually reached the exchange boundary rather than
	 * inferring it from a downstream side effect. The spot mock has the same accessor;
	 * the futures mock lacked it, which made "no order was sent" unprovable here.
	 */
	public List<FuturesOrderResult> allOrders() {
		return List.copyOf(orders.values());
	}
}
