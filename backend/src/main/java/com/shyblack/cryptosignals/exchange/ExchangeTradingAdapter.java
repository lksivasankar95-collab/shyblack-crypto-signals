package com.shyblack.cryptosignals.exchange;

import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;

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

	SymbolRules getSymbolRules(String symbol);

	ExchangeOrderResult placeOrder(ExchangeCredential credential, PlaceOrderRequest request);

	ExchangeOrderResult getOrder(ExchangeCredential credential, String symbol, String clientOrderId);

	ExchangeOrderResult cancelOrder(ExchangeCredential credential, String symbol, String clientOrderId);
}
