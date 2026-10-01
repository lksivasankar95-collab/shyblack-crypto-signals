package com.shyblack.cryptosignals.repository.research;

import com.shyblack.cryptosignals.entity.research.MarketOpenInterest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketOpenInterestRepository extends JpaRepository<MarketOpenInterest, UUID> {

	/** As-of lookup — the most recent observation at or before {@code time}. */
	Optional<MarketOpenInterest> findTopBySymbolAndTsLessThanEqualOrderByTsDesc(String symbol, Instant time);

	/** Previous observation, used to compute an as-of OI change without look-ahead. */
	Optional<MarketOpenInterest> findTopBySymbolAndTsLessThanOrderByTsDesc(String symbol, Instant time);

	long countByDatasetVersion(String datasetVersion);
}
