package com.shyblack.cryptosignals.market;

import com.shyblack.cryptosignals.entity.enums.TradingMode;
import java.time.LocalDate;

/**
 * The authoritative identity of one tradable Binance instrument.
 *
 * <p><b>A symbol alone is not an instrument.</b> {@code BTCUSDT} exists simultaneously as a spot
 * pair, as a USDⓈ-M perpetual and as a quarterly futures contract. They are different instruments
 * with different prices, and treating them as one is how a futures price ends up marking a spot
 * position. This record is the unit of identity: the market it belongs to, the exchange symbol that
 * addresses it on that market, and the contract details that distinguish one futures contract from
 * another on the same symbol.
 *
 * <p>Every field is populated from Binance {@code exchangeInfo}. Nothing here is derived by
 * string-surgery on the symbol, because splitting {@code BTCUSDT} into {@code BTC/USDT} loses the
 * contract identity that makes a futures instrument distinct from the spot pair.
 *
 * @param marketType which market this instrument trades on. Never {@code null}: this is the field
 *     that keeps the two markets apart
 * @param exchangeSymbol the symbol Binance addresses this instrument with, e.g. {@code BTCUSDT}
 * @param baseAsset the underlying asset, e.g. {@code BTC}
 * @param quoteAsset the asset it is quoted in, e.g. {@code USDT}
 * @param contractType {@code PERPETUAL} or {@code CURRENT_QUARTER}/{@code NEXT_QUARTER} for
 *     futures; {@code null} for spot, which has no contract type
 * @param contractExpiry settlement date of a dated futures contract; {@code null} for perpetuals
 *     and for spot
 */
public record MarketInstrument(
		TradingMode marketType,
		String exchangeSymbol,
		String baseAsset,
		String quoteAsset,
		String contractType,
		LocalDate contractExpiry) {

	public MarketInstrument {
		if (marketType == null) {
			throw new IllegalArgumentException("marketType is required: a symbol alone is not an instrument");
		}
		if (exchangeSymbol == null || exchangeSymbol.isBlank()) {
			throw new IllegalArgumentException("exchangeSymbol is required");
		}
		exchangeSymbol = exchangeSymbol.trim().toUpperCase();
		// A perpetual has no expiry. Binance's delivery-date field carries sentinels rather than
		// omitting it, so normalise here as well as at the source: a perpetual labelled with a
		// settlement date is a mislabelled instrument.
		if (isPerpetualType(contractType)) {
			contractExpiry = null;
		}
	}

	private static boolean isPerpetualType(String contractType) {
		return contractType != null && contractType.trim().toUpperCase().startsWith("PERPETUAL");
	}

	public boolean isSpot() {
		return marketType == TradingMode.SPOT;
	}

	public boolean isFutures() {
		return marketType == TradingMode.FUTURES;
	}

	public boolean isPerpetual() {
		return contractType != null && "PERPETUAL".equalsIgnoreCase(contractType);
	}

	/**
	 * Human-readable label for a user-facing list.
	 *
	 * <p>Derived, never hard-coded per instrument:
	 * <ul>
	 *   <li><b>Spot</b> reads as {@code BTC/USDT}, the conventional pair notation, because a spot
	 *       pair has no contract identity beyond its two assets.</li>
	 *   <li><b>Perpetual</b> keeps the exchange symbol and is qualified as {@code BTCUSDT Perpetual},
	 *       because collapsing it to {@code BTC/USDT} would make it indistinguishable from spot.</li>
	 *   <li><b>Dated futures</b> additionally carries its expiry, which is the only thing that tells
	 *       two contracts on the same symbol apart.</li>
	 * </ul>
	 *
	 * <p>Every component comes from Binance metadata; the quote separator is presentation only.
	 */
	public String displaySymbol() {
		if (isSpot()) {
			String base = baseAsset != null && !baseAsset.isBlank() ? baseAsset : exchangeSymbol;
			String quote = quoteAsset != null && !quoteAsset.isBlank() ? quoteAsset : "USDT";
			return base + "/" + quote;
		}
		StringBuilder label = new StringBuilder(exchangeSymbol);
		if (contractType != null && !contractType.isBlank()) {
			label.append(' ').append(humanize(contractType));
		}
		if (contractExpiry != null) {
			label.append(' ').append(contractExpiry);
		}
		return label.toString();
	}

	/** Short qualification shown next to a futures symbol, e.g. {@code Perpetual}. */
	public String contractLabel() {
		if (isSpot()) {
			return null;
		}
		String label = contractType == null || contractType.isBlank()
				? null
				: humanize(contractType);
		if (contractExpiry != null) {
			label = label == null ? contractExpiry.toString() : label + " " + contractExpiry;
		}
		return label;
	}

	/** The asset whose price this instrument tracks, for display next to the price. */
	public String nameOrBase() {
		return baseAsset != null && !baseAsset.isBlank() ? baseAsset : exchangeSymbol;
	}

	private static String humanize(String contractType) {
		return switch (contractType.trim().toUpperCase()) {
			case "PERPETUAL" -> "Perpetual";
			case "CURRENT_QUARTER", "CURRENT_QUARTER_DELIVERING" -> "Quarterly";
			case "NEXT_QUARTER", "NEXT_QUARTER_DELIVERING" -> "Next Quarterly";
			case "PERPETUAL_DELIVERING" -> "Perpetual Delivery";
			default -> contractType;
		};
	}
}