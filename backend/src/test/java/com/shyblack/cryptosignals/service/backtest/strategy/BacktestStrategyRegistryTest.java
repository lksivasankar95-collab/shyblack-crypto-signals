package com.shyblack.cryptosignals.service.backtest.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shyblack.cryptosignals.exception.BadRequestException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class BacktestStrategyRegistryTest {

	private final BacktestStrategyRegistry registry = new BacktestStrategyRegistry(List.of(
			new TrendPullbackBacktestStrategy(new ObjectMapper()),
			new EmaTrendFollowingBacktestStrategy(new ObjectMapper()),
			new EmaRsiBacktestStrategy(),
			new NfmBacktestStrategy()));

	@Test
	void listsRegisteredStrategiesWithMarketTypeAndName() {
		Map<String, String> marketById = registry.list().stream().collect(Collectors.toMap(
				BacktestStrategyRegistry.StrategyDescriptor::id,
				BacktestStrategyRegistry.StrategyDescriptor::marketType));
		assertThat(marketById)
				.containsEntry("TREND_PULLBACK", "SPOT")
				.containsEntry("EMA_TREND_FOLLOWING", "SPOT")
				.containsEntry("NFM_FUTURES", "FUTURES");
		registry.list().forEach(d -> {
			assertThat(d.name()).isNotBlank();
			assertThat(d.version()).isNotBlank();
		});
	}

	@Test
	void resolvesExactlyTheRequestedImplementation() {
		assertThat(registry.create("NFM_FUTURES", null)).isInstanceOf(NfmBacktestStrategy.class);
		assertThat(registry.create("TREND_PULLBACK", null))
				.isInstanceOf(TrendPullbackBacktestStrategy.class);
		assertThat(registry.create("EMA_TREND_FOLLOWING", null))
				.isInstanceOf(EmaTrendFollowingBacktestStrategy.class);
	}

	@Test
	void unknownStrategyIsRejectedNoSilentFallback() {
		assertThatThrownBy(() -> registry.create("does-not-exist", null))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("Unknown strategy");
	}
}
