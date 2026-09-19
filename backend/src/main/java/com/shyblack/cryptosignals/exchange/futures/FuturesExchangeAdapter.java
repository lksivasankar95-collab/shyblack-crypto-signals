package com.shyblack.cryptosignals.exchange.futures;

import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.FuturesMarginMode;
import com.shyblack.cryptosignals.exchange.SymbolRules;

/**
 * The one door between local Futures code and the real exchange. Nothing in
 * the app is permitted to hit Binance USDT-M REST APIs for signed
 * (money-moving) operations except through this adapter.
 *
 * Two impls:
 *   BinanceFuturesLiveAdapter — HMAC-SHA256 signed calls, testnet default.
 *   MockFuturesExchangeAdapter — in-process simulator for tests + dev.
 */
public interface FuturesExchangeAdapter {

	ExchangeName exchange();

	FuturesAccountSnapshot validateCredentials(ExchangeCredential credential);

	FuturesAccountSnapshot getAccount(ExchangeCredential credential);

	/** Configure per-symbol leverage before placing the entry order. */
	void setLeverage(ExchangeCredential credential, String symbol, int leverage);

	/** Configure margin mode per symbol. Only ISOLATED is exercised in this drop. */
	void setMarginMode(ExchangeCredential credential, String symbol, FuturesMarginMode marginMode);

	SymbolRules getSymbolRules(String symbol);

	FuturesOrderResult placeOrder(ExchangeCredential credential, PlaceFuturesOrderRequest request);

	FuturesOrderResult getOrder(ExchangeCredential credential, String symbol, String clientOrderId);

	FuturesOrderResult cancelOrder(ExchangeCredential credential, String symbol, String clientOrderId);
}
