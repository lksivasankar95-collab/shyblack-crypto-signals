package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.BacktestRun;
import com.shyblack.cryptosignals.entity.BacktestTrade;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BacktestTradeRepository extends JpaRepository<BacktestTrade, UUID> {

	List<BacktestTrade> findByRunOrderByEntryTimeAsc(BacktestRun run);

	long countByRun(BacktestRun run);

	void deleteByRun(BacktestRun run);
}
