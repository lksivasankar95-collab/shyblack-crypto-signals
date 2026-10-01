package com.shyblack.cryptosignals.repository.research;

import com.shyblack.cryptosignals.entity.research.MarketFundingRate;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketFundingRateRepository extends JpaRepository<MarketFundingRate, UUID> {

	/** As-of lookup — the most recent funding settlement at or before {@code time}. */
	Optional<MarketFundingRate> findTopBySymbolAndFundingTimeLessThanEqualOrderByFundingTimeDesc(
			String symbol, Instant time);

	long countByDatasetVersion(String datasetVersion);
}
