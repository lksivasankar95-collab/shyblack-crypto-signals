package com.shyblack.cryptosignals.entity.enums;

/**
 * Backtest run lifecycle. A run stays in RUNNING only while a worker is
 * actively processing candles; on any completion path it lands in one of
 * the terminal states.
 */
public enum BacktestStatus {
	QUEUED,
	RUNNING,
	COMPLETED,
	FAILED,
	CANCELLED
}
