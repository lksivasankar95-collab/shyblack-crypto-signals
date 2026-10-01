package com.shyblack.cryptosignals.service.backtest;

import com.shyblack.cryptosignals.config.BacktestingProperties;
import com.shyblack.cryptosignals.config.ResearchBacktestProperties;
import org.springframework.stereotype.Component;

/**
 * Resolves the effective backtest limits. Defaults to the production
 * {@link BacktestingProperties}; only when {@code app.research.backtest.enabled}
 * is true are the research limits used. Production behaviour is unchanged.
 */
@Component
public class BacktestLimits {

	private final BacktestingProperties production;
	private final ResearchBacktestProperties research;

	public BacktestLimits(BacktestingProperties production, ResearchBacktestProperties research) {
		this.production = production;
		this.research = research;
	}

	public boolean researchEnabled() {
		return research != null && research.enabled();
	}

	public int maxCandles() {
		return researchEnabled() ? research.maxCandles() : production.maxCandles();
	}

	public int maxRangeDays() {
		return researchEnabled() ? research.maxRangeDays() : production.maxRangeDays();
	}

	public int maxConcurrentRuns() {
		return researchEnabled() ? research.maxConcurrentRuns() : production.maxConcurrentRuns();
	}
}
