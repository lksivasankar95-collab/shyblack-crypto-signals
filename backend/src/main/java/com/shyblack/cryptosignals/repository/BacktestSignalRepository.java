package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.BacktestRun;
import com.shyblack.cryptosignals.entity.BacktestSignal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BacktestSignalRepository extends JpaRepository<BacktestSignal, UUID> {

	List<BacktestSignal> findByRunOrderByCandleTimeAsc(BacktestRun run);

	void deleteByRun(BacktestRun run);
}
