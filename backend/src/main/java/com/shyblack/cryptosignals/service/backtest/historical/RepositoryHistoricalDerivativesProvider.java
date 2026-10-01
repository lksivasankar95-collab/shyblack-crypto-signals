package com.shyblack.cryptosignals.service.backtest.historical;

import com.shyblack.cryptosignals.entity.research.MarketFundingRate;
import com.shyblack.cryptosignals.entity.research.MarketLiquidation;
import com.shyblack.cryptosignals.entity.research.MarketOpenInterest;
import com.shyblack.cryptosignals.market.DerivativesSnapshot;
import com.shyblack.cryptosignals.repository.research.MarketFundingRateRepository;
import com.shyblack.cryptosignals.repository.research.MarketLiquidationRepository;
import com.shyblack.cryptosignals.repository.research.MarketOpenInterestRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads OI / funding / liquidation from the persisted research tables using
 * strictly as-of queries ({@code ts <= time}). Each field carries its own
 * availability flag; unavailable data stays null (never zero).
 */
@Component
public class RepositoryHistoricalDerivativesProvider implements HistoricalDerivativesProvider {

	private final MarketOpenInterestRepository oiRepository;
	private final MarketFundingRateRepository fundingRepository;
	private final MarketLiquidationRepository liquidationRepository;

	public RepositoryHistoricalDerivativesProvider(MarketOpenInterestRepository oiRepository,
			MarketFundingRateRepository fundingRepository,
			MarketLiquidationRepository liquidationRepository) {
		this.oiRepository = oiRepository;
		this.fundingRepository = fundingRepository;
		this.liquidationRepository = liquidationRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public DerivativesSnapshot asOf(String symbol, Instant time) {
		if (symbol == null || time == null) {
			return DerivativesSnapshot.unavailable();
		}
		String normalized = symbol.toUpperCase();

		BigDecimal openInterest = null;
		BigDecimal oiChangePct = null;
		boolean oiAvailable = false;
		Optional<MarketOpenInterest> oi =
				oiRepository.findTopBySymbolAndTsLessThanEqualOrderByTsDesc(normalized, time);
		if (oi.isPresent() && oi.get().getSumOpenInterest() != null) {
			oiAvailable = true;
			openInterest = oi.get().getSumOpenInterest();
			Optional<MarketOpenInterest> previous =
					oiRepository.findTopBySymbolAndTsLessThanOrderByTsDesc(normalized, oi.get().getTs());
			if (previous.isPresent()) {
				oiChangePct = pct(previous.get().getSumOpenInterest(), openInterest);
			}
		}

		BigDecimal fundingRate = null;
		BigDecimal markPrice = null;
		boolean fundingAvailable = false;
		Optional<MarketFundingRate> funding =
				fundingRepository.findTopBySymbolAndFundingTimeLessThanEqualOrderByFundingTimeDesc(normalized, time);
		if (funding.isPresent() && funding.get().getLastFundingRate() != null) {
			fundingAvailable = true;
			fundingRate = funding.get().getLastFundingRate();
			markPrice = funding.get().getMarkPrice();
		}

		BigDecimal longVolume = null;
		BigDecimal shortVolume = null;
		boolean liquidationAvailable = false;
		List<MarketLiquidation> liqs = liquidationRepository
				.findBySymbolAndTsBetweenOrderByTsAsc(normalized, time.minus(1, ChronoUnit.HOURS), time);
		if (!liqs.isEmpty()) {
			liquidationAvailable = true;
			longVolume = BigDecimal.ZERO;
			shortVolume = BigDecimal.ZERO;
			for (MarketLiquidation l : liqs) {
				if (l.getLongVolume() != null) longVolume = longVolume.add(l.getLongVolume());
				if (l.getShortVolume() != null) shortVolume = shortVolume.add(l.getShortVolume());
			}
		}

		return new DerivativesSnapshot(markPrice, null, fundingRate, openInterest, oiChangePct,
				longVolume, shortVolume, fundingAvailable, oiAvailable, liquidationAvailable);
	}

	private static BigDecimal pct(BigDecimal from, BigDecimal to) {
		if (from == null || to == null || from.signum() == 0) {
			return null;
		}
		return to.subtract(from).divide(from, 8, RoundingMode.HALF_UP)
				.multiply(BigDecimal.valueOf(100)).setScale(6, RoundingMode.HALF_UP);
	}
}
