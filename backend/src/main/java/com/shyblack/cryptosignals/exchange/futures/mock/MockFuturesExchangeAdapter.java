package com.shyblack.cryptosignals.exchange.futures.mock;

import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.FuturesMarginMode;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderStatus;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderType;
import com.shyblack.cryptosignals.entity.enums.FuturesPositionMode;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.exchange.SymbolRules;
import com.shyblack.cryptosignals.exchange.futures.FuturesAccountSnapshot;
import com.shyblack.cryptosignals.exchange.futures.FuturesExchangeAdapter;
import com.shyblack.cryptosignals.exchange.futures.FuturesOrderResult;
import com.shyblack.cryptosignals.exchange.futures.PlaceFuturesOrderRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
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
}
