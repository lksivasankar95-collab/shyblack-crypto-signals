package com.shyblack.cryptosignals.repository.research;

import com.shyblack.cryptosignals.entity.research.MarketLiquidation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketLiquidationRepository extends JpaRepository<MarketLiquidation, UUID> {

	List<MarketLiquidation> findBySymbolAndTsBetweenOrderByTsAsc(String symbol, Instant start, Instant end);

	long countByDatasetVersion(String datasetVersion);
}
