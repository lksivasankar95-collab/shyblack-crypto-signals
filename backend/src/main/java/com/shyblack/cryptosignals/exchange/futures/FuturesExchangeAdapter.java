package com.shyblack.cryptosignals.exchange.futures;

import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.FuturesMarginMode;
import com.shyblack.cryptosignals.exchange.SymbolRules;
import java.util.List;

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

	/**
	 * Authoritative open positions from the exchange position-risk endpoint. This is the exchange's
	 * own view and is independent of local signals, local orders and the locally recorded position
	 * shadow used by the execution engine. Flat (zero amount) positions are omitted, so an empty list
	 * means "the exchange reports no open position", which is a real state rather than missing data.
	 */
	List<FuturesExchangePosition> getPositions(ExchangeCredential credential);

	/** Configure per-symbol leverage before placing the entry order. */
	void setLeverage(ExchangeCredential credential, String symbol, int leverage);

	/** Configure margin mode per symbol. Only ISOLATED is exercised in this drop. */
	void setMarginMode(ExchangeCredential credential, String symbol, FuturesMarginMode marginMode);

	SymbolRules getSymbolRules(String symbol);

	FuturesOrderResult placeOrder(ExchangeCredential credential, PlaceFuturesOrderRequest request);

	FuturesOrderResult getOrder(ExchangeCredential credential, String symbol, String clientOrderId);

	FuturesOrderResult cancelOrder(ExchangeCredential credential, String symbol, String clientOrderId);
}
