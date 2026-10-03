package com.shyblack.cryptosignals.service.backtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shyblack.cryptosignals.config.BacktestingProperties;
import com.shyblack.cryptosignals.config.ResearchBacktestProperties;
import com.shyblack.cryptosignals.dto.backtest.BacktestConfigRequest;
import com.shyblack.cryptosignals.entity.BacktestRun;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.BacktestExecutionModel;
import com.shyblack.cryptosignals.entity.enums.BacktestSameCandlePolicy;
import com.shyblack.cryptosignals.entity.enums.BacktestStatus;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.exception.BadRequestException;
import com.shyblack.cryptosignals.repository.BacktestEquityPointRepository;
import com.shyblack.cryptosignals.repository.BacktestRunRepository;
import com.shyblack.cryptosignals.repository.BacktestSignalRepository;
import com.shyblack.cryptosignals.repository.BacktestTradeRepository;
import com.shyblack.cryptosignals.service.backtest.engine.BacktestConfig;
import com.shyblack.cryptosignals.service.backtest.strategy.BacktestStrategyRegistry;
import com.shyblack.cryptosignals.service.backtest.strategy.EmaRsiBacktestStrategy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BacktestServiceTest {

	private static final Instant START = Instant.parse("2026-09-03T11:29:56.450Z");
	private static final Instant END = Instant.parse("2026-10-03T11:29:56.450Z");

	private final BacktestRunRepository runRepo = mock(BacktestRunRepository.class);
	private final BacktestJobRunner jobRunner = mock(BacktestJobRunner.class);
	private final BacktestLimits limits = new BacktestLimits(
			new BacktestingProperties("backtest-engine/v1", 20_000, 2, 100, 365, 200),
			new ResearchBacktestProperties(false, 0, 0, 0, 0));

	private BacktestService service;

	@BeforeEach
	void setUp() {
		service = new BacktestService(runRepo,
				mock(BacktestTradeRepository.class),
				mock(BacktestSignalRepository.class),
				mock(BacktestEquityPointRepository.class),
				jobRunner,
				new BacktestingProperties("backtest-engine/v1", 20_000, 2, 100, 365, 200),
				limits,
				new BacktestStrategyRegistry(List.of(new EmaRsiBacktestStrategy())),
				new ObjectMapper().registerModule(new JavaTimeModule())
						.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));

		when(runRepo.save(any())).thenAnswer(inv -> {
			BacktestRun run = inv.getArgument(0);
			ReflectionTestUtils.setField(run, "id", java.util.UUID.randomUUID());
			return run;
		});
	}

	private BacktestConfig config() {
		return new BacktestConfig("ema-rsi", "BTCUSDT", "1h", TradingMode.SPOT,
				START, END, new BigDecimal("10000"), new BigDecimal("1"),
				new BigDecimal("0.1"), new BigDecimal("0.05"), 1,
				BacktestExecutionModel.NEXT_CANDLE_OPEN, BacktestSameCandlePolicy.SL_FIRST, null);
	}

	private BacktestConfigRequest request(JsonNode strategyParams) {
		return new BacktestConfigRequest("ema-rsi", "BTCUSDT", "1h", TradingMode.SPOT,
				START, END, new BigDecimal("10000"), new BigDecimal("1"),
				new BigDecimal("0.1"), new BigDecimal("0.05"), 1,
				BacktestExecutionModel.NEXT_CANDLE_OPEN, BacktestSameCandlePolicy.SL_FIRST,
				strategyParams);
	}

	/**
	 * Regression test for the HTTP 500 on POST /api/v1/backtests.
	 * Persisting the request snapshot must never touch Instant reflectively —
	 * java.base does not open java.time to the unnamed module, so a bare
	 * {@code new Gson().toJson(request)} throws InaccessibleObjectException.
	 */
	@Test
	void startBacktestSerialisesRequestSnapshotWithInstants() {
		BacktestRun run = service.startBacktest(mock(User.class), config(), request(null));

		assertThat(run.getStatus()).isEqualTo(BacktestStatus.QUEUED);
		assertThat(run.getConfigurationJson()).isNotBlank().doesNotContain("Exception");
		assertThat(run.getConfigurationJson()).contains(START.toString(), END.toString());
	}

	@Test
	void startBacktestSerialisesSnapshotCarryingStrategyParams() {
		ObjectMapper mapper = new ObjectMapper();
		JsonNode params = mapper.createObjectNode().put("window", 50);

		BacktestRun run = service.startBacktest(mock(User.class), config(), request(params));

		assertThat(run.getConfigurationJson()).contains("window");
	}

	@Test
	void startBacktestSucceedsWhenSnapshotIsNull() {
		assertThatCode(() -> service.startBacktest(mock(User.class), config(), null))
				.doesNotThrowAnyException();
	}

	@Test
	void spotOnlyStrategyIsRejectedInFuturesMode() {
		BacktestConfig futures = new BacktestConfig("ema-rsi", "BTCUSDT", "1h", TradingMode.FUTURES,
				START, END, new BigDecimal("10000"), new BigDecimal("1"),
				new BigDecimal("0.1"), new BigDecimal("0.05"), 1,
				BacktestExecutionModel.NEXT_CANDLE_OPEN, BacktestSameCandlePolicy.SL_FIRST, null);

		assertThat(org.junit.jupiter.api.Assertions.assertThrows(BadRequestException.class,
				() -> service.startBacktest(mock(User.class), futures, request(null))).getMessage())
				.contains("SPOT-only");
	}
}