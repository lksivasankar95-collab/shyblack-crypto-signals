package com.shyblack.cryptosignals.dto.market;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One quote, carrying the full identity of the instrument it belongs to.
 *
 * <p>{@code symbol} is what Binance addresses the instrument with and is <b>not</b> sufficient to
 * identify it: {@code BTCUSDT} is a live spot pair and a live futures perpetual at the same moment.
 * The identity fields below travel with every quote so the client never has to infer the market
 * from the symbol it was sent.
 *
 * <p>{@code displaySymbol} is the human-facing label and is expected to be distinct from
 * {@code exchangeSymbol} for futures, where contract type is part of the identity. Rendering it is
 * the client's job; deriving it is the server's, from Binance metadata rather than string surgery.
 *
 * @param symbol alias of {@code exchangeSymbol}, kept for existing consumers
 * @param exchangeSymbol the symbol Binance addresses this instrument with
 * @param marketType {@code SPOT} or {@code FUTURES}; the field that keeps the two markets apart
 * @param displaySymbol human-facing label, e.g. {@code BTC/USDT} spot or {@code BTCUSDT Perpetual}
 * @param name underlying asset name, e.g. {@code BTC}
 * @param baseAsset underlying asset, e.g. {@code BTC}
 * @param quoteAsset quote asset, e.g. {@code USDT}
 * @param contractType {@code PERPETUAL}, {@code CURRENT_QUARTER}, {@code NEXT_QUARTER}, ... for
 *     futures; {@code null} for spot, which has no contract type
 */
public record MarketTickerResponse(
		String symbol,
		String exchangeSymbol,
		String marketType,
		String displaySymbol,
		String name,
		String baseAsset,
		String quoteAsset,
		String contractType,
		BigDecimal price,
		BigDecimal change24h,
		BigDecimal changePercent24h,
		BigDecimal volume24h,
		BigDecimal high24h,
		BigDecimal low24h,
		Instant updatedAt
) {
}