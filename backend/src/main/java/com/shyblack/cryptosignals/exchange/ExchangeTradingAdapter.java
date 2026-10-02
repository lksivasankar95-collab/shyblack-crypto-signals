package com.shyblack.cryptosignals.exchange;

import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import java.time.Instant;
import java.util.List;

/**
 * The one and only door between local code and a real exchange. Nothing else
 * in the app is permitted to hit exchange REST APIs directly for signed
 * (money-moving) operations. Implementations:
 *
 *   BinanceLiveTradingAdapter — signed calls to real Binance (testnet by default).
 *   MockExchangeTradingAdapter — in-process simulator for tests + local dev.
 *
 * The chosen implementation is selected via {@code app.live-trading.mode}.
 */
public interface ExchangeTradingAdapter {

	ExchangeName exchange();

	/** Verify credentials by making a signed request that requires trading permissions. */
	ExchangeAccountSnapshot validateCredentials(ExchangeCredential credential);

	ExchangeAccountSnapshot getAccountBalance(ExchangeCredential credential);

	/**
	 * Complete per-asset balance response. Unlike {@link #getAccountBalance}, which narrows to the
	 * configured quote asset for the trading engine, this preserves every asset the exchange
	 * reported so a caller can tell an explicit zero apart from an absent asset.
	 */
	ExchangeBalances getBalances(ExchangeCredential credential);

	/**
	 * Read-only order history.
	 *
	 * <p>Order and trade history is never reconstructed from local orders or signals; these methods
	 * report only what the exchange itself returns.
	 *
	 * @param symbol required by the exchange for the full-history and trade endpoints; null returns
	 *     the globally open orders only
	 */
	List<ExchangeOrderSnapshot> getOpenOrders(ExchangeCredential credential, String symbol);

	/** @param symbol required by the exchange for the all-orders endpoint */
	List<ExchangeOrderSnapshot> getAllOrders(
			ExchangeCredential credential, String symbol, Instant from, Instant to, int limit);

	/** @param symbol required by the exchange for the trade-history endpoint */
	List<ExchangeTradeSnapshot> getTrades(
			ExchangeCredential credential, String symbol, Instant from, Instant to, int limit);

	SymbolRules getSymbolRules(String symbol);

	ExchangeOrderResult placeOrder(ExchangeCredential credential, PlaceOrderRequest request);

	ExchangeOrderResult getOrder(ExchangeCredential credential, String symbol, String clientOrderId);

	ExchangeOrderResult cancelOrder(ExchangeCredential credential, String symbol, String clientOrderId);
}
