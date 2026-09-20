package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.BacktestEquityPoint;
import com.shyblack.cryptosignals.entity.BacktestRun;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BacktestEquityPointRepository extends JpaRepository<BacktestEquityPoint, UUID> {

	List<BacktestEquityPoint> findByRunOrderByTimeAsc(BacktestRun run);

	void deleteByRun(BacktestRun run);
}
