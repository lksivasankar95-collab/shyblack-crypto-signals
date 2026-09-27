package com.shyblack.cryptosignals.service.backtest.strategy;

/**
 * Implemented by backtest strategies that accept per-run parameters.
 *
 * The registry hands {@link #create(String)} a fresh, immutable instance for
 * each run, so two concurrent runs with different parameters never share
 * mutable state. The annotated {@code @Component} instance itself only acts
 * as a factory and always runs with default parameters.
 */
public interface ConfigurableBacktestStrategy {

    /**
     * @param paramsJson JSON object of strategy parameters (may be null/blank,
     *                   in which case the registered default instance is used).
     * @return a configured, run-scoped strategy instance.
     */
    BacktestStrategy create(String paramsJson);
}
