package com.shyblack.cryptosignals.service.backtest.engine;

import com.shyblack.cryptosignals.entity.BacktestEquityPoint;
import com.shyblack.cryptosignals.entity.BacktestRun;
import com.shyblack.cryptosignals.entity.BacktestSignal;
import com.shyblack.cryptosignals.entity.BacktestTrade;
import com.shyblack.cryptosignals.entity.enums.BacktestExitReason;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalCandle;
import com.shyblack.cryptosignals.service.backtest.strategy.BacktestStrategy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Candle-by-candle backtest engine. The event ordering per candle is fixed:
 *
 *   1. Evaluate an open position against this candle (SL / TP / LIQ).
 *   2. If a position was opened by a signal at the PREVIOUS candle's close,
 *      fill its entry at THIS candle's open + slippage
 *      (NEXT_CANDLE_OPEN model).
 *   3. Run the strategy against candles ≤ this one. If a signal fires,
 *      queue it for entry at the NEXT candle's open.
 *   4. Snapshot equity (mark-to-market on the candle's close).
 *
 * This ordering guarantees no look-ahead: the strategy never sees future
 * candles, and entries never use the candle whose close triggered them.
 */
public final class BacktestEngine {

	private BacktestEngine() {}

	public static Result run(BacktestRun run, BacktestConfig config, BacktestStrategy strategy,
			List<HistoricalCandle> candles, AtomicBoolean cancelled) {

		int n = candles.size();
		BacktestPortfolio portfolio = new BacktestPortfolio(
				config.initialCapital(), config.tradingMode(), config.leverage());

		List<BacktestSignal> signals = new ArrayList<>();
		List<BacktestTrade> trades = new ArrayList<>();
		List<BacktestEquityPoint> equity = new ArrayList<>();

		BacktestStrategy.Signal pendingSignal = null;
		UUID pendingSignalId = null;

		int processed = 0;
		for (int i = 0; i < n; i++) {
			if (cancelled != null && cancelled.get()) {
				return new Result(signals, trades, equity, processed, "CANCELLED_RUN");
			}
			HistoricalCandle candle = candles.get(i);
			if (!candle.isValid()) continue;

			// 1) Evaluate an open position first — a stop can fire before any new entry.
			if (portfolio.hasOpenPosition()) {
				BacktestPortfolio.OpenPosition open = portfolio.openPosition();
				BacktestExecutionSimulator.Trigger trigger = BacktestExecutionSimulator.evaluate(
						candle, open.side, open.stopLoss, open.takeProfit, open.liquidationPrice,
						config.sameCandlePolicy(), config.tradingMode(), config.slippagePct());
				if (!(trigger instanceof BacktestExecutionSimulator.NoTrigger)) {
					BigDecimal exitPrice = triggerExitPrice(trigger);
					BacktestExitReason reason = triggerExitReason(trigger);
					BigDecimal exitFee = exitFee(open.entryPrice.multiply(open.quantity), config.feePct());
					BacktestPortfolio.ClosedPosition closed = portfolio.closePosition(exitPrice, exitFee);
					trades.add(toTrade(run, closed, reason, candle.openTime()));
				}
			}

			// 2) Fill any queued entry at THIS candle's open.
			if (pendingSignal != null && !portfolio.hasOpenPosition()) {
				BigDecimal entryPrice = BacktestExecutionSimulator.applySlippage(
						candle.open(), pendingSignal.side(), true, config.slippagePct());
				BigDecimal qty = BacktestExecutionSimulator.riskBasedQuantity(
						portfolio.availableBalance(), config.riskPerTradePct(),
						entryPrice, pendingSignal.stopLoss());
				if (qty.signum() > 0) {
					BigDecimal notional = qty.multiply(entryPrice);
					BigDecimal entryFee = notional.multiply(config.feePct())
							.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
					BigDecimal liq = BacktestExecutionSimulator.estimateLiquidation(
							config.tradingMode(), pendingSignal.side(), entryPrice, config.leverage());
					portfolio.openPosition(pendingSignalId, config.symbol(), pendingSignal.side(),
							entryPrice, qty, pendingSignal.stopLoss(), pendingSignal.takeProfit(),
							entryFee, liq, candle.openTime());
				}
				pendingSignal = null;
				pendingSignalId = null;
			}

			// 3) Ask the strategy — only for candles ≤ current. Strategy sees NO future data.
			List<HistoricalCandle> history = candles.subList(0, i + 1);
			BacktestStrategy.Signal emitted = strategy.evaluate(history, i).orElse(null);
			if (emitted != null && !portfolio.hasOpenPosition() && i + 1 < n) {
				pendingSignal = emitted;
				pendingSignalId = UUID.randomUUID();
				signals.add(toSignal(run, config, strategy, emitted, candle.closeTime(), pendingSignalId));
			}

			// 4) Equity snapshot (mark-to-market on the candle close).
			BigDecimal equityValue = portfolio.equity(candle.close());
			BigDecimal drawdown = portfolio.drawdownAgainst(equityValue);
			BigDecimal drawdownPct = portfolio.peakEquity().signum() == 0
					? BigDecimal.ZERO
					: drawdown.multiply(BigDecimal.valueOf(100))
							.divide(portfolio.peakEquity(), 4, RoundingMode.HALF_UP);
			equity.add(toEquityPoint(run, candle.closeTime(), equityValue, portfolio,
					portfolio.unrealized(candle.close()), drawdown, drawdownPct));

			processed++;
		}

		// 5) End-of-test — close any open position at the final candle close.
		if (portfolio.hasOpenPosition() && !candles.isEmpty()) {
			HistoricalCandle last = candles.get(candles.size() - 1);
			BacktestPortfolio.OpenPosition open = portfolio.openPosition();
			BigDecimal exitPrice = BacktestExecutionSimulator.applySlippage(
					last.close(), open.side, false, config.slippagePct());
			BigDecimal exitFee = exitFee(open.entryPrice.multiply(open.quantity), config.feePct());
			BacktestPortfolio.ClosedPosition closed = portfolio.closePosition(exitPrice, exitFee);
			trades.add(toTrade(run, closed, BacktestExitReason.END_OF_TEST, last.closeTime()));
		}
		return new Result(signals, trades, equity, processed, null);
	}

	// ── Helpers ─────────────────────────────────────────────────

	private static BigDecimal triggerExitPrice(BacktestExecutionSimulator.Trigger t) {
		if (t instanceof BacktestExecutionSimulator.TpTrigger tp) return tp.exitPrice();
		if (t instanceof BacktestExecutionSimulator.SlTrigger sl) return sl.exitPrice();
		if (t instanceof BacktestExecutionSimulator.LiquidationTrigger lq) return lq.exitPrice();
		throw new IllegalArgumentException(t.getClass().getName());
	}

	private static BacktestExitReason triggerExitReason(BacktestExecutionSimulator.Trigger t) {
		if (t instanceof BacktestExecutionSimulator.TpTrigger) return BacktestExitReason.TAKE_PROFIT;
		if (t instanceof BacktestExecutionSimulator.SlTrigger) return BacktestExitReason.STOP_LOSS;
		if (t instanceof BacktestExecutionSimulator.LiquidationTrigger) return BacktestExitReason.LIQUIDATION;
		throw new IllegalArgumentException(t.getClass().getName());
	}

	private static BigDecimal exitFee(BigDecimal referenceNotional, BigDecimal feePct) {
		return referenceNotional.multiply(feePct)
				.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
	}

	private static BacktestTrade toTrade(BacktestRun run,
			BacktestPortfolio.ClosedPosition closed, BacktestExitReason reason, java.time.Instant exitTime) {
		BacktestTrade t = new BacktestTrade();
		t.setRun(run);
		t.setSignalId(closed.entry().signalId);
		t.setSymbol(closed.entry().symbol);
		t.setSide(closed.entry().side);
		t.setQuantity(closed.entry().quantity);
		t.setEntryPrice(closed.entry().entryPrice);
		t.setExitPrice(closed.exitPrice());
		t.setNotional(closed.entry().entryPrice.multiply(closed.entry().quantity)
				.setScale(8, RoundingMode.HALF_UP));
		t.setStopLoss(closed.entry().stopLoss);
		t.setTakeProfit(closed.entry().takeProfit);
		t.setEntryFee(closed.entry().entryFee);
		t.setExitFee(closed.exitFee());
		t.setGrossPnl(closed.grossPnl());
		t.setNetPnl(closed.netPnl().subtract(closed.entry().entryFee));
		t.setLeverage(closed.entry().leverage);
		t.setExitReason(reason);
		t.setEntryTime(closed.entry().entryTime);
		t.setExitTime(exitTime);
		t.setHoldingSeconds(java.time.Duration.between(closed.entry().entryTime, exitTime).getSeconds());
		// R multiple — netPnl / |entry − stop| × quantity
		BigDecimal risk = closed.entry().stopLoss == null ? null
				: closed.entry().entryPrice.subtract(closed.entry().stopLoss).abs()
						.multiply(closed.entry().quantity);
		if (risk != null && risk.signum() > 0) {
			t.setRMultiple(t.getNetPnl().divide(risk, 4, RoundingMode.HALF_UP));
		}
		return t;
	}

	private static BacktestSignal toSignal(BacktestRun run, BacktestConfig cfg,
			BacktestStrategy strategy, BacktestStrategy.Signal emitted, java.time.Instant time, UUID id) {
		BacktestSignal s = new BacktestSignal();
		s.setId(id);
		s.setRun(run);
		s.setSymbol(cfg.symbol());
		s.setSide(emitted.side());
		s.setCandleTime(time);
		s.setReferencePrice(emitted.referencePrice());
		s.setEntryPrice(emitted.referencePrice());
		s.setStopLoss(emitted.stopLoss());
		s.setTakeProfit(emitted.takeProfit());
		s.setStrategyId(strategy.id());
		s.setStrategyVersion(strategy.version());
		s.setNotes(emitted.notes());
		return s;
	}

	private static BacktestEquityPoint toEquityPoint(BacktestRun run, java.time.Instant time,
			BigDecimal equity, BacktestPortfolio portfolio, BigDecimal unrealized,
			BigDecimal drawdown, BigDecimal drawdownPct) {
		BacktestEquityPoint p = new BacktestEquityPoint();
		p.setRun(run);
		p.setTime(time);
		p.setEquity(equity);
		p.setAvailableBalance(portfolio.availableBalance());
		p.setUnrealizedPnl(unrealized);
		p.setRealizedPnl(portfolio.realizedPnl());
		p.setPeakEquity(portfolio.peakEquity());
		p.setDrawdown(drawdown);
		p.setDrawdownPct(drawdownPct);
		return p;
	}

	// Extension on PositionSide + TradingMode used in BacktestConfig only.
	// Kept package-private for the record's convenience access.

	public record Result(
			List<BacktestSignal> signals,
			List<BacktestTrade> trades,
			List<BacktestEquityPoint> equity,
			int processedCandles,
			String cancelReason
	) {}
}
