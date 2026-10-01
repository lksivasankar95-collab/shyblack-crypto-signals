package com.shyblack.cryptosignals.exchange;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * The complete balance response of a spot account, keyed by asset.
 *
 * <p>The whole exchange response is preserved rather than narrowed to a single quote asset, so a
 * caller can distinguish three states that a single-pair snapshot cannot:
 *
 * <ul>
 *   <li>the exchange reported the asset with a real value, including an explicit zero;</li>
 *   <li>the exchange did not report the asset at all;</li>
 *   <li>the exchange reported the asset with an unusable value.</li>
 * </ul>
 *
 * <p>Therefore {@link #free}/{@link #locked}/{@link #total} return {@code null} for an absent asset
 * rather than {@link BigDecimal#ZERO}. Only {@link #assets()} iteration proves the asset existed.
 */
public record ExchangeBalances(
		Map<String, ExchangeAssetBalance> balances,
		boolean canTrade,
		Instant fetchedAt) {

	public ExchangeBalances {
		balances = balances == null ? Map.of() : Map.copyOf(balances);
	}

	/** Every asset the exchange reported, including those with an explicit zero balance. */
	public Map<String, ExchangeAssetBalance> assets() {
		return balances;
	}

	/** Empty when the exchange did not report the asset. Never a fabricated zero. */
	public Optional<ExchangeAssetBalance> find(String asset) {
		return asset == null ? Optional.empty() : Optional.ofNullable(balances.get(asset.toUpperCase()));
	}

	/** Free balance of one asset, or null when the exchange did not report it. */
	public BigDecimal free(String asset) {
		return find(asset).map(ExchangeAssetBalance::free).orElse(null);
	}

	/** Locked balance of one asset, or null when the exchange did not report it. */
	public BigDecimal locked(String asset) {
		return find(asset).map(ExchangeAssetBalance::locked).orElse(null);
	}

	/** Free + locked of one asset, or null when either component is unknown. */
	public BigDecimal total(String asset) {
		return find(asset).map(ExchangeAssetBalance::total).orElse(null);
	}
}