package com.shyblack.cryptosignals.service.backtest.research;

import com.shyblack.cryptosignals.service.backtest.engine.BacktestConfig;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalCandle;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalEvent;
import com.shyblack.cryptosignals.service.backtest.strategy.BacktestStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Controlled parameter-sensitivity runner: evaluates a bounded list of explicit
 * parameter variants (each a params JSON) over the SAME window with a fresh
 * strategy instance, and reports descriptive metrics. It does NOT select a
 * winner or optimise — that keeps results honest and reproducible.
 */
public final class SensitivityRunner {

	private SensitivityRunner() {
	}

	public record Variant(String label, String paramsJson) {}

	public static List<ResearchWindowResult> run(BacktestConfig base,
			Function<String, BacktestStrategy> strategyFactory,
			List<HistoricalCandle> candles, List<HistoricalEvent> events, List<Variant> variants) {
		List<ResearchWindowResult> out = new ArrayList<>();
		if (variants == null) {
			return out;
		}
		for (Variant variant : variants) {
			BacktestStrategy strategy = strategyFactory.apply(variant.paramsJson());
			out.add(WalkForwardEngine.evaluate("SENS_" + variant.label(), base, strategy, candles, events,
					base.startDate(), base.endDate(), variant.paramsJson()));
		}
		return out;
	}
}
