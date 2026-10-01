package com.shyblack.cryptosignals.market;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Assembles a {@link DerivativesSnapshot} from the raw Binance futures
 * derivatives client. Any missing input is surfaced as "unavailable" rather
 * than zero (spec §47).
 */
@Service
public class FuturesDerivativesService {

	private final BinanceFuturesDerivativesClient client;

	public FuturesDerivativesService(BinanceFuturesDerivativesClient client) {
		this.client = client;
	}

	public DerivativesSnapshot snapshot(String symbol) {
		Optional<BinanceFuturesDerivativesClient.PremiumIndex> premium = client.premiumIndex(symbol);
		Optional<BigDecimal> oi = client.openInterest(symbol);
		List<BinanceFuturesDerivativesClient.OpenInterestPoint> oiHist =
				safe(() -> client.openInterestHistory(symbol, "5m", 12));
		Optional<BinanceFuturesDerivativesClient.LiquidationAggregate> liq =
				client.recentLiquidations(symbol, 100);

		BigDecimal oiChangePct = null;
		if (!oiHist.isEmpty()) {
			BigDecimal first = oiHist.get(0).openInterest();
			BigDecimal last = oiHist.get(oiHist.size() - 1).openInterest();
			oiChangePct = BinanceFuturesDerivativesClient.pct(first, last);
		}

		return new DerivativesSnapshot(
				premium.map(BinanceFuturesDerivativesClient.PremiumIndex::markPrice).orElse(null),
				premium.map(BinanceFuturesDerivativesClient.PremiumIndex::indexPrice).orElse(null),
				premium.map(BinanceFuturesDerivativesClient.PremiumIndex::lastFundingRate).orElse(null),
				oi.orElse(null),
				oiChangePct,
				liq.map(BinanceFuturesDerivativesClient.LiquidationAggregate::longVolume).orElse(null),
				liq.map(BinanceFuturesDerivativesClient.LiquidationAggregate::shortVolume).orElse(null),
				premium.isPresent(),
				oi.isPresent(),
				liq.isPresent());
	}

	private static <T> List<T> safe(java.util.function.Supplier<List<T>> supplier) {
		try {
			List<T> value = supplier.get();
			return value == null ? List.of() : value;
		} catch (Exception ex) {
			return List.of();
		}
	}
}
