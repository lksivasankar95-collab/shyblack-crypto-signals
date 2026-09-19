package com.shyblack.cryptosignals.exchange.mock;

import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.LiveOrderStatus;
import com.shyblack.cryptosignals.entity.enums.LiveOrderType;
import com.shyblack.cryptosignals.exchange.ExchangeAccountSnapshot;
import com.shyblack.cryptosignals.exchange.ExchangeOrderResult;
import com.shyblack.cryptosignals.exchange.ExchangeTradingAdapter;
import com.shyblack.cryptosignals.exchange.PlaceOrderRequest;
import com.shyblack.cryptosignals.exchange.SymbolRules;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process exchange simulator. Used when {@code app.live-trading.mode=MOCK}
 * — the default for dev + all automated tests. NEVER hits Binance.
 *
 * Behaviour:
 *   - Orders placed here are stored keyed by clientOrderId; the same
 *     clientOrderId is idempotent (returns the same result).
 *   - MARKET orders "fill" instantly at their reference price.
 *   - STOP_LOSS_LIMIT / TAKE_PROFIT_LIMIT stay in ACKNOWLEDGED state until
 *     an explicit test hook fills or cancels them.
 *
 * The class is deliberately test-friendly: {@link #getOrder} can be used by
 * reconciliation to observe live state; {@link #forceFill} lets tests simulate
 * a fill without racing timers.
 */
public class MockExchangeTradingAdapter implements ExchangeTradingAdapter {

	private final Map<String, ExchangeOrderResult> orders = new ConcurrentHashMap<>();
	private final Map<String, SymbolRules> rules = new ConcurrentHashMap<>();
	private BigDecimal availableBalance = new BigDecimal("10000");
	private BigDecimal totalBalance = new BigDecimal("10000");

	public MockExchangeTradingAdapter() {
		// Default reasonable rules so tests don't have to seed them.
		putRules(new SymbolRules(
				"BTCUSDT",
				new BigDecimal("0.00001"),
				new BigDecimal("1000"),
				new BigDecimal("0.00001"),
				new BigDecimal("0.01"),
				new BigDecimal("1000000"),
				new BigDecimal("0.01"),
				new BigDecimal("10")));
	}

	public void putRules(SymbolRules symbolRules) {
		rules.put(symbolRules.symbol().toUpperCase(), symbolRules);
	}

	public void setBalances(BigDecimal available, BigDecimal total) {
		this.availableBalance = available;
		this.totalBalance = total;
	}

	@Override
	public ExchangeName exchange() {
		return ExchangeName.BINANCE;
	}

	@Override
	public ExchangeAccountSnapshot validateCredentials(ExchangeCredential credential) {
		Objects.requireNonNull(credential, "credential");
		return snapshot();
	}

	@Override
	public ExchangeAccountSnapshot getAccountBalance(ExchangeCredential credential) {
		return snapshot();
	}

	private ExchangeAccountSnapshot snapshot() {
		return new ExchangeAccountSnapshot("USDT", availableBalance, totalBalance, true, Instant.now());
	}

	@Override
	public SymbolRules getSymbolRules(String symbol) {
		return rules.get(symbol.toUpperCase());
	}

	@Override
	public ExchangeOrderResult placeOrder(ExchangeCredential credential, PlaceOrderRequest request) {
		Objects.requireNonNull(credential, "credential");
		// Idempotent — same clientOrderId returns the prior result.
		ExchangeOrderResult prior = orders.get(request.clientOrderId());
		if (prior != null) return prior;

		String exchangeOrderId = "MOCK-" + UUID.randomUUID().toString().substring(0, 12);
		BigDecimal fillPrice = request.type() == LiveOrderType.MARKET
				? request.price() // caller passes the current market as reference
				: request.price();
		LiveOrderStatus status = request.type() == LiveOrderType.MARKET
				? LiveOrderStatus.FILLED
				: LiveOrderStatus.ACKNOWLEDGED;
		BigDecimal executed = status == LiveOrderStatus.FILLED
				? request.quantity()
				: BigDecimal.ZERO;
		BigDecimal cumQuote = executed.multiply(fillPrice == null ? BigDecimal.ZERO : fillPrice);
		BigDecimal fee = cumQuote.multiply(new BigDecimal("0.001")); // 0.1% mock fee

		ExchangeOrderResult result = new ExchangeOrderResult(
				exchangeOrderId,
				request.clientOrderId(),
				request.symbol(),
				status,
				request.quantity(),
				executed,
				cumQuote,
				status == LiveOrderStatus.FILLED ? fillPrice : null,
				status == LiveOrderStatus.FILLED ? fee : BigDecimal.ZERO,
				status == LiveOrderStatus.FILLED ? "USDT" : null,
				Instant.now(),
				"MOCK");
		orders.put(request.clientOrderId(), result);
		return result;
	}

	@Override
	public ExchangeOrderResult getOrder(ExchangeCredential credential, String symbol, String clientOrderId) {
		return orders.get(clientOrderId);
	}

	@Override
	public ExchangeOrderResult cancelOrder(ExchangeCredential credential, String symbol, String clientOrderId) {
		ExchangeOrderResult existing = orders.get(clientOrderId);
		if (existing == null) return null;
		ExchangeOrderResult cancelled = new ExchangeOrderResult(
				existing.exchangeOrderId(), clientOrderId, symbol,
				LiveOrderStatus.CANCELLED,
				existing.requestedQuantity(),
				existing.executedQuantity(),
				existing.cumulativeQuoteQty(),
				existing.avgFillPrice(),
				existing.fee(),
				existing.feeAsset(),
				Instant.now(),
				"MOCK cancel");
		orders.put(clientOrderId, cancelled);
		return cancelled;
	}

	// ---- Test hooks -----------------------------------------------------

	/** Force-fill a pending order — mimics an SL/TP trigger arriving via user-data stream. */
	public void forceFill(String clientOrderId, BigDecimal fillPrice) {
		ExchangeOrderResult existing = orders.get(clientOrderId);
		if (existing == null) return;
		BigDecimal executed = existing.requestedQuantity();
		BigDecimal cumQuote = executed.multiply(fillPrice);
		orders.put(clientOrderId, new ExchangeOrderResult(
				existing.exchangeOrderId(), clientOrderId, existing.symbol(),
				LiveOrderStatus.FILLED,
				existing.requestedQuantity(),
				executed,
				cumQuote,
				fillPrice,
				cumQuote.multiply(new BigDecimal("0.001")),
				"USDT",
				Instant.now(),
				"MOCK force-fill"));
	}

	public List<ExchangeOrderResult> allOrders() {
		return List.copyOf(orders.values());
	}
}
