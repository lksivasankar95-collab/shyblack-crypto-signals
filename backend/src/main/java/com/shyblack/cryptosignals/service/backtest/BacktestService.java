package com.shyblack.cryptosignals.service.backtest;

import com.google.gson.Gson;
import com.shyblack.cryptosignals.config.BacktestingProperties;
import com.shyblack.cryptosignals.entity.BacktestEquityPoint;
import com.shyblack.cryptosignals.entity.BacktestRun;
import com.shyblack.cryptosignals.entity.BacktestSignal;
import com.shyblack.cryptosignals.entity.BacktestTrade;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.BacktestExecutionModel;
import com.shyblack.cryptosignals.entity.enums.BacktestSameCandlePolicy;
import com.shyblack.cryptosignals.entity.enums.BacktestStatus;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.exception.BadRequestException;
import com.shyblack.cryptosignals.exception.ResourceNotFoundException;
import com.shyblack.cryptosignals.repository.BacktestEquityPointRepository;
import com.shyblack.cryptosignals.repository.BacktestRunRepository;
import com.shyblack.cryptosignals.repository.BacktestSignalRepository;
import com.shyblack.cryptosignals.repository.BacktestTradeRepository;
import com.shyblack.cryptosignals.service.backtest.engine.BacktestConfig;
import com.shyblack.cryptosignals.service.backtest.strategy.BacktestStrategy;
import com.shyblack.cryptosignals.service.backtest.strategy.BacktestStrategyRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public backtesting API. All lookups are scoped to the authenticated user
 * — cross-user access returns 404 (IDOR-safe).
 */
@Service
@RequiredArgsConstructor
public class BacktestService {

	private static final Gson GSON = new Gson();

	private final BacktestRunRepository runRepo;
	private final BacktestTradeRepository tradeRepo;
	private final BacktestSignalRepository signalRepo;
	private final BacktestEquityPointRepository equityRepo;
	private final BacktestJobRunner jobRunner;
	private final BacktestingProperties props;
	private final BacktestStrategyRegistry strategyRegistry;

	@Transactional
	public BacktestRun startBacktest(User user, BacktestConfig config, Object requestSnapshot) {
		validateConfig(config);
		BacktestStrategy strategy = strategyRegistry.require(config.strategyId());

		BacktestRun run = new BacktestRun();
		run.setUser(user);
		run.setStatus(BacktestStatus.QUEUED);
		run.setStrategyId(strategy.id());
		run.setStrategyVersion(strategy.version());
		run.setEngineVersion(props.engineVersion());
		run.setTradingMode(config.tradingMode());
		run.setSymbol(config.symbol());
		run.setTimeframe(config.timeframe());
		run.setStartDate(config.startDate());
		run.setEndDate(config.endDate());
		run.setInitialCapital(config.initialCapital());
		run.setRiskPerTradePct(config.riskPerTradePct());
		run.setFeePct(config.feePct());
		run.setSlippagePct(config.slippagePct());
		run.setLeverage(config.leverage());
		run.setExecutionModel(config.executionModel());
		run.setSameCandlePolicy(config.sameCandlePolicy());
		run.setConfigurationHash(config.hash());
		run.setConfigurationJson(GSON.toJson(requestSnapshot));

		BacktestRun saved = runRepo.save(run);
		jobRunner.enqueue(saved.getId(), config);
		return saved;
	}

	private void validateConfig(BacktestConfig config) {
		if (config.symbol() == null || config.symbol().isBlank())
			throw new BadRequestException("symbol required");
		if (config.timeframe() == null || config.timeframe().isBlank())
			throw new BadRequestException("timeframe required");
		if (config.startDate() == null || config.endDate() == null
				|| !config.startDate().isBefore(config.endDate()))
			throw new BadRequestException("invalid date range");
		Duration window = Duration.between(config.startDate(), config.endDate());
		if (window.toDays() > props.maxRangeDays())
			throw new BadRequestException("date range exceeds "
					+ props.maxRangeDays() + " days");
		if (config.initialCapital() == null || config.initialCapital().signum() <= 0)
			throw new BadRequestException("initialCapital must be positive");
		if (config.riskPerTradePct() == null || config.riskPerTradePct().signum() <= 0)
			throw new BadRequestException("riskPerTradePct must be positive");
		if (config.leverage() < 1)
			throw new BadRequestException("leverage must be >= 1");
		if (config.leverage() > 1 && config.tradingMode() != TradingMode.FUTURES)
			throw new BadRequestException("leverage > 1 requires FUTURES mode");
		if (config.feePct() == null || config.feePct().signum() < 0)
			throw new BadRequestException("feePct must be >= 0");
		if (config.slippagePct() == null || config.slippagePct().signum() < 0)
			throw new BadRequestException("slippagePct must be >= 0");
		if (config.executionModel() == null)
			throw new BadRequestException("executionModel required");
		if (config.sameCandlePolicy() == null)
			throw new BadRequestException("sameCandlePolicy required");
	}

	@Transactional(readOnly = true)
	public List<BacktestRun> list(User user) {
		return runRepo.findByUserOrderByCreatedAtDesc(user);
	}

	@Transactional(readOnly = true)
	public BacktestRun get(User user, UUID runId) {
		return runRepo.findByIdAndUser(runId, user)
				.orElseThrow(() -> new ResourceNotFoundException("Backtest not found: " + runId));
	}

	@Transactional(readOnly = true)
	public List<BacktestTrade> trades(User user, UUID runId) {
		return tradeRepo.findByRunOrderByEntryTimeAsc(get(user, runId));
	}

	@Transactional(readOnly = true)
	public List<BacktestSignal> signals(User user, UUID runId) {
		return signalRepo.findByRunOrderByCandleTimeAsc(get(user, runId));
	}

	@Transactional(readOnly = true)
	public List<BacktestEquityPoint> equity(User user, UUID runId) {
		return equityRepo.findByRunOrderByTimeAsc(get(user, runId));
	}

	@Transactional
	public boolean cancel(User user, UUID runId) {
		BacktestRun run = get(user, runId);
		if (run.getStatus() == BacktestStatus.QUEUED) {
			run.setStatus(BacktestStatus.CANCELLED);
			run.setCompletedAt(Instant.now());
			runRepo.save(run);
			jobRunner.requestCancel(runId);
			return true;
		}
		if (run.getStatus() == BacktestStatus.RUNNING) {
			return jobRunner.requestCancel(runId);
		}
		return false;
	}

	@Transactional
	public void delete(User user, UUID runId) {
		BacktestRun run = get(user, runId);
		if (run.getStatus() == BacktestStatus.RUNNING) {
			throw new BadRequestException("Cancel the run before deleting");
		}
		tradeRepo.deleteByRun(run);
		signalRepo.deleteByRun(run);
		equityRepo.deleteByRun(run);
		runRepo.delete(run);
	}

	public List<BacktestStrategyRegistry.StrategyDescriptor> listStrategies() {
		return strategyRegistry.list();
	}

	// Test-only helper (kept out of the controller surface).
	static BigDecimal safe(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
