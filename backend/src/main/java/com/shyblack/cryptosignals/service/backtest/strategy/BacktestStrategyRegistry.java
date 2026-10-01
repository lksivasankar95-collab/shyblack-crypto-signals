package com.shyblack.cryptosignals.service.backtest.strategy;

import com.shyblack.cryptosignals.exception.BadRequestException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Locates the strategy implementation for a given id. Injected as
 * {@code List<BacktestStrategy>} so any new @Component strategy is
 * discovered automatically.
 */
@Component
public class BacktestStrategyRegistry {

	private final Map<String, BacktestStrategy> byId;

	public BacktestStrategyRegistry(List<BacktestStrategy> strategies) {
		this.byId = strategies.stream()
				.collect(Collectors.toUnmodifiableMap(BacktestStrategy::id, s -> s));
	}

	public BacktestStrategy require(String id) {
		BacktestStrategy strategy = byId.get(id);
		if (strategy == null) throw new BadRequestException("Unknown strategy: " + id);
		return strategy;
	}

	/**
	 * Returns the run-scoped strategy instance. Configurable strategies always
	 * get a fresh instance (so per-run state such as the cooldown cursor can
	 * never leak between runs or concurrent runs); other strategies share the
	 * stateless singleton.
	 */
	public BacktestStrategy create(String id, String paramsJson) {
		BacktestStrategy strategy = require(id);
		if (strategy instanceof ConfigurableBacktestStrategy configurable) {
			return configurable.create(paramsJson);
		}
		return strategy;
	}

	public List<StrategyDescriptor> list() {
		return byId.values().stream()
				.map(s -> new StrategyDescriptor(s.id(), humanize(s.id()), s.version(), s.marketType()))
				.toList();
	}

	/** Display name derived from the registered identifier (no hardcoded names). */
	static String humanize(String id) {
		if (id == null || id.isBlank()) {
			return id;
		}
		return id.replace('_', ' ').replace('-', ' ').trim().toUpperCase();
	}

	/** id, display name, version, and market type ("SPOT"/"FUTURES"). */
	public record StrategyDescriptor(String id, String name, String version, String marketType) {}
}
