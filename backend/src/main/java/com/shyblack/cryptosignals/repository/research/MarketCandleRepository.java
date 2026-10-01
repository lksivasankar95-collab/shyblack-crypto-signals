package com.shyblack.cryptosignals.repository.research;

import com.shyblack.cryptosignals.entity.research.MarketCandle;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketCandleRepository extends JpaRepository<MarketCandle, UUID> {

	List<MarketCandle> findBySymbolAndTimeframeAndOpenTimeBetweenOrderByOpenTimeAsc(
			String symbol, String timeframe, Instant start, Instant end);

	long countByDatasetVersion(String datasetVersion);
}
