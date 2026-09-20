package com.shyblack.cryptosignals.controller;

import com.shyblack.cryptosignals.dto.backtest.BacktestConfigRequest;
import com.shyblack.cryptosignals.dto.backtest.BacktestEquityPointResponse;
import com.shyblack.cryptosignals.dto.backtest.BacktestRunResponse;
import com.shyblack.cryptosignals.dto.backtest.BacktestSignalResponse;
import com.shyblack.cryptosignals.dto.backtest.BacktestTradeResponse;
import com.shyblack.cryptosignals.entity.BacktestEquityPoint;
import com.shyblack.cryptosignals.entity.BacktestRun;
import com.shyblack.cryptosignals.entity.BacktestSignal;
import com.shyblack.cryptosignals.entity.BacktestTrade;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.exception.BadRequestException;
import com.shyblack.cryptosignals.exception.ResourceNotFoundException;
import com.shyblack.cryptosignals.repository.UserRepository;
import com.shyblack.cryptosignals.security.UserPrincipal;
import com.shyblack.cryptosignals.service.backtest.BacktestService;
import com.shyblack.cryptosignals.service.backtest.engine.BacktestConfig;
import com.shyblack.cryptosignals.service.backtest.strategy.BacktestStrategyRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/backtests")
@RequiredArgsConstructor
@Tag(name = "Backtesting", description = "Historical simulation runs — SPOT + FUTURES.")
@SecurityRequirement(name = "bearer-jwt")
public class BacktestController {

	private final BacktestService service;
	private final UserRepository userRepository;

	@Operation(summary = "Start a new backtest — returns immediately with QUEUED status")
	@PostMapping
	public BacktestRunResponse start(@Valid @RequestBody BacktestConfigRequest request) {
		BacktestConfig cfg = new BacktestConfig(
				request.strategyId(), request.symbol(), request.timeframe(),
				request.tradingMode(), request.startDate(), request.endDate(),
				request.initialCapital(), request.riskPerTradePct(),
				request.feePct(), request.slippagePct(),
				request.leverageOrDefault(),
				request.executionModelOrDefault(),
				request.sameCandlePolicyOrDefault());
		return toDto(service.startBacktest(currentUser(), cfg, request));
	}

	@Operation(summary = "List the caller's backtest runs")
	@GetMapping
	public List<BacktestRunResponse> list() {
		return service.list(currentUser()).stream().map(BacktestController::toDto).toList();
	}

	@Operation(summary = "Get one run (must be owned by the caller)")
	@GetMapping("/{id}")
	public BacktestRunResponse get(@PathVariable UUID id) {
		return toDto(service.get(currentUser(), id));
	}

	@Operation(summary = "List trades from a completed run")
	@GetMapping("/{id}/trades")
	public List<BacktestTradeResponse> trades(@PathVariable UUID id) {
		return service.trades(currentUser(), id).stream().map(BacktestController::toDto).toList();
	}

	@Operation(summary = "List signals emitted during the run")
	@GetMapping("/{id}/signals")
	public List<BacktestSignalResponse> signals(@PathVariable UUID id) {
		return service.signals(currentUser(), id).stream().map(BacktestController::toDto).toList();
	}

	@Operation(summary = "Equity + drawdown curve for the run")
	@GetMapping("/{id}/equity")
	public List<BacktestEquityPointResponse> equity(@PathVariable UUID id) {
		return service.equity(currentUser(), id).stream().map(BacktestController::toDto).toList();
	}

	@Operation(summary = "Request cancellation of a QUEUED or RUNNING backtest")
	@PostMapping("/{id}/cancel")
	public BacktestRunResponse cancel(@PathVariable UUID id) {
		service.cancel(currentUser(), id);
		return toDto(service.get(currentUser(), id));
	}

	@Operation(summary = "Delete a completed run and its trades/signals/equity")
	@DeleteMapping("/{id}")
	public void delete(@PathVariable UUID id) {
		service.delete(currentUser(), id);
	}

	@Operation(summary = "List available strategies")
	@GetMapping("/strategies")
	public List<BacktestStrategyRegistry.StrategyDescriptor> strategies() {
		return service.listStrategies();
	}

	// ------------------------------------------------------------------

	private User currentUser() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
			throw new BadRequestException("Authentication required");
		}
		return userRepository.findById(principal.getId())
				.orElseThrow(() -> new ResourceNotFoundException("User not found"));
	}

	private static BacktestRunResponse toDto(BacktestRun r) {
		return new BacktestRunResponse(
				r.getId(), r.getStatus(), r.getStrategyId(), r.getStrategyVersion(),
				r.getEngineVersion(), r.getTradingMode(), r.getSymbol(), r.getTimeframe(),
				r.getStartDate(), r.getEndDate(),
				r.getInitialCapital(), r.getRiskPerTradePct(), r.getFeePct(), r.getSlippagePct(),
				r.getLeverage(), r.getExecutionModel(), r.getSameCandlePolicy(),
				r.getConfigurationHash(), r.getProcessedCandles(), r.getTotalCandles(),
				r.getFailureReason(), r.getStartedAt(), r.getCompletedAt(), r.getCreatedAt(),
				r.getFinalEquity(), r.getTotalNetPnl(), r.getTotalReturnPct(),
				r.getMaxDrawdown(), r.getMaxDrawdownPct(),
				r.getWinRatePct(), r.getProfitFactor(),
				r.getSharpeRatio(), r.getSortinoRatio(),
				r.getTotalFees(), r.getGrossProfit(), r.getGrossLoss(),
				r.getTotalTrades(), r.getWinningTrades(), r.getLosingTrades(),
				r.getLiquidations(),
				r.getAverageWin(), r.getAverageLoss(),
				r.getLargestWin(), r.getLargestLoss(),
				r.getExpectancy());
	}

	private static BacktestTradeResponse toDto(BacktestTrade t) {
		return new BacktestTradeResponse(
				t.getId(), t.getSignalId(), t.getSymbol(), t.getSide(),
				t.getQuantity(), t.getEntryPrice(), t.getExitPrice(), t.getNotional(),
				t.getStopLoss(), t.getTakeProfit(), t.getEntryFee(), t.getExitFee(),
				t.getGrossPnl(), t.getNetPnl(), t.getRMultiple(), t.getLeverage(),
				t.getExitReason(), t.getEntryTime(), t.getExitTime(), t.getHoldingSeconds());
	}

	private static BacktestSignalResponse toDto(BacktestSignal s) {
		return new BacktestSignalResponse(
				s.getId(), s.getSymbol(), s.getSide(), s.getCandleTime(),
				s.getReferencePrice(), s.getEntryPrice(), s.getStopLoss(), s.getTakeProfit(),
				s.getStrategyId(), s.getStrategyVersion(), s.getNotes());
	}

	private static BacktestEquityPointResponse toDto(BacktestEquityPoint p) {
		return new BacktestEquityPointResponse(
				p.getTime(), p.getEquity(), p.getAvailableBalance(),
				p.getUnrealizedPnl(), p.getRealizedPnl(),
				p.getPeakEquity(), p.getDrawdown(), p.getDrawdownPct());
	}
}
