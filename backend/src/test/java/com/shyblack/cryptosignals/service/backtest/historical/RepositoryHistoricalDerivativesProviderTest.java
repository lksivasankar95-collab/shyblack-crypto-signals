package com.shyblack.cryptosignals.service.backtest.historical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shyblack.cryptosignals.entity.research.MarketFundingRate;
import com.shyblack.cryptosignals.entity.research.MarketLiquidation;
import com.shyblack.cryptosignals.entity.research.MarketOpenInterest;
import com.shyblack.cryptosignals.market.DerivativesSnapshot;
import com.shyblack.cryptosignals.repository.research.MarketFundingRateRepository;
import com.shyblack.cryptosignals.repository.research.MarketLiquidationRepository;
import com.shyblack.cryptosignals.repository.research.MarketOpenInterestRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RepositoryHistoricalDerivativesProviderTest {

	private final MarketOpenInterestRepository oiRepository = mock(MarketOpenInterestRepository.class);
	private final MarketFundingRateRepository fundingRepository = mock(MarketFundingRateRepository.class);
	private final MarketLiquidationRepository liquidationRepository = mock(MarketLiquidationRepository.class);

	private final RepositoryHistoricalDerivativesProvider provider =
			new RepositoryHistoricalDerivativesProvider(oiRepository, fundingRepository, liquidationRepository);

	private static MarketOpenInterest oi(Instant ts, String value) {
		MarketOpenInterest oi = new MarketOpenInterest();
		oi.setTs(ts);
		oi.setSumOpenInterest(new BigDecimal(value));
		return oi;
	}

	@Test
	void asOf_buildsSnapshotFromObservationsAtOrBeforeTime() {
		Instant t2 = Instant.parse("2025-01-01T12:00:00Z");
		Instant t1 = Instant.parse("2025-01-01T11:55:00Z");
		when(oiRepository.findTopBySymbolAndTsLessThanEqualOrderByTsDesc("BTCUSDT", t2))
				.thenReturn(Optional.of(oi(t1, "100")));
		when(oiRepository.findTopBySymbolAndTsLessThanOrderByTsDesc("BTCUSDT", t1))
				.thenReturn(Optional.of(oi(t1.minusSeconds(300), "90")));
		MarketFundingRate fr = new MarketFundingRate();
		fr.setFundingTime(t2.minusSeconds(3600));
		fr.setLastFundingRate(new BigDecimal("0.0001"));
		fr.setMarkPrice(new BigDecimal("100000"));
		when(fundingRepository.findTopBySymbolAndFundingTimeLessThanEqualOrderByFundingTimeDesc("BTCUSDT", t2))
				.thenReturn(Optional.of(fr));
		when(liquidationRepository.findBySymbolAndTsBetweenOrderByTsAsc(eq("BTCUSDT"), any(), eq(t2)))
				.thenReturn(List.of());

		DerivativesSnapshot s = provider.asOf("BTCUSDT", t2);

		assertThat(s.openInterestAvailable()).isTrue();
		assertThat(s.openInterest()).isEqualByComparingTo("100");
		assertThat(s.openInterestChangePct()).isNotNull();
		assertThat(s.openInterestChangePct().doubleValue()).isGreaterThan(11.0);
		assertThat(s.fundingAvailable()).isTrue();
		assertThat(s.lastFundingRate()).isEqualByComparingTo("0.0001");
		// No liquidation rows -> unavailable, NOT zero.
		assertThat(s.liquidationAvailable()).isFalse();
		assertThat(s.longLiquidationVolume()).isNull();
		assertThat(s.shortLiquidationVolume()).isNull();
	}

	@Test
	void missingData_isUnavailableNeverZero() {
		Instant t = Instant.parse("2025-01-01T12:00:00Z");
		when(oiRepository.findTopBySymbolAndTsLessThanEqualOrderByTsDesc("ETHUSDT", t))
				.thenReturn(Optional.empty());
		when(fundingRepository.findTopBySymbolAndFundingTimeLessThanEqualOrderByFundingTimeDesc("ETHUSDT", t))
				.thenReturn(Optional.empty());
		when(liquidationRepository.findBySymbolAndTsBetweenOrderByTsAsc(eq("ETHUSDT"), any(), eq(t)))
				.thenReturn(List.of());

		DerivativesSnapshot s = provider.asOf("ETHUSDT", t);

		assertThat(s.openInterestAvailable()).isFalse();
		assertThat(s.openInterest()).isNull();
		assertThat(s.fundingAvailable()).isFalse();
		assertThat(s.lastFundingRate()).isNull();
		assertThat(s.liquidationAvailable()).isFalse();
	}

	@Test
	void liquidationAggregatedWhenPresent() {
		Instant t = Instant.parse("2025-01-01T12:00:00Z");
		MarketLiquidation l = new MarketLiquidation();
		l.setTs(t.minusSeconds(600));
		l.setLongVolume(new BigDecimal("10"));
		l.setShortVolume(new BigDecimal("3"));
		when(oiRepository.findTopBySymbolAndTsLessThanEqualOrderByTsDesc("BTCUSDT", t)).thenReturn(Optional.empty());
		when(fundingRepository.findTopBySymbolAndFundingTimeLessThanEqualOrderByFundingTimeDesc("BTCUSDT", t))
				.thenReturn(Optional.empty());
		when(liquidationRepository.findBySymbolAndTsBetweenOrderByTsAsc(eq("BTCUSDT"), any(), eq(t)))
				.thenReturn(List.of(l));

		DerivativesSnapshot s = provider.asOf("BTCUSDT", t);

		assertThat(s.liquidationAvailable()).isTrue();
		assertThat(s.longLiquidationVolume()).isEqualByComparingTo("10");
		assertThat(s.shortLiquidationVolume()).isEqualByComparingTo("3");
	}
}
