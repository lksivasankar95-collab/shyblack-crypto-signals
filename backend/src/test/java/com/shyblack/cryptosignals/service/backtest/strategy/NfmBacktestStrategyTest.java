package com.shyblack.cryptosignals.service.backtest.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.enums.NewsEventCategory;
import com.shyblack.cryptosignals.entity.enums.NewsEventStage;
import com.shyblack.cryptosignals.entity.enums.NewsEventType;
import com.shyblack.cryptosignals.entity.enums.NewsImpact;
import com.shyblack.cryptosignals.entity.enums.NewsSourceTier;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalCandle;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NfmBacktestStrategyTest {

	private static final long BASE = 1_700_000_000_000L;
	private static final long FIVE_MIN = 300_000L;

	private static HistoricalCandle candle(int index, double price, double volume) {
		Instant open = Instant.ofEpochMilli(BASE + (long) index * FIVE_MIN);
		BigDecimal p = BigDecimal.valueOf(price).setScale(8, RoundingMode.HALF_UP);
		return new HistoricalCandle(open, p, p, p, p, BigDecimal.valueOf(volume),
				open.plusMillis(FIVE_MIN));
	}

	private static List<HistoricalCandle> move(int flat, double post, double quietVol, double spikeVol) {
		List<HistoricalCandle> list = new ArrayList<>();
		for (int i = 0; i < 30; i++) list.add(candle(i, flat, quietVol));
		for (int i = 0; i < 10; i++) {
			list.add(candle(30 + i, flat + (post - flat) * (i + 1) / 10.0, spikeVol));
		}
		return list;
	}

	private static HistoricalEvent event(List<HistoricalCandle> candles, int atIndex) {
		return new HistoricalEvent(UUID.randomUUID(), candles.get(atIndex).closeTime(), "BTCUSDT",
				NewsEventType.BTC_ETF, NewsEventCategory.CRYPTO_STRUCTURAL, NewsEventStage.APPROVAL,
				NewsSourceTier.TIER_1, NewsImpact.CRITICAL, null, null, null, "reuters", "ETF approved");
	}

	@Test
	void bullishReaction_producesLongSignal() {
		NfmBacktestStrategy strategy = new NfmBacktestStrategy();
		List<HistoricalCandle> candles = move(100, 105, 100, 300);
		Optional<BacktestStrategy.Signal> signal = strategy.evaluate(candles, candles.size() - 1,
				List.of(event(candles, 30)));

		assertThat(signal).isPresent();
		assertThat(signal.get().side()).isEqualTo(PositionSide.LONG);
		assertThat(signal.get().stopLoss()).isLessThan(signal.get().referencePrice());
		assertThat(signal.get().takeProfit()).isGreaterThan(signal.get().referencePrice());
	}

	@Test
	void noEvents_producesNoSignal() {
		NfmBacktestStrategy strategy = new NfmBacktestStrategy();
		List<HistoricalCandle> candles = move(100, 105, 100, 300);
		assertThat(strategy.evaluate(candles, candles.size() - 1, List.of())).isEmpty();
	}

	@Test
	void eventAfterCurrentCandle_isIgnored() {
		NfmBacktestStrategy strategy = new NfmBacktestStrategy();
		List<HistoricalCandle> candles = move(100, 105, 100, 300);
		// Event dated after the evaluated candle's close must never influence it.
		HistoricalEvent future = new HistoricalEvent(UUID.randomUUID(),
				candles.get(39).closeTime().plusSeconds(600), "BTCUSDT", NewsEventType.BTC_ETF,
				NewsEventCategory.CRYPTO_STRUCTURAL, NewsEventStage.APPROVAL, NewsSourceTier.TIER_1,
				NewsImpact.CRITICAL, null, null, null, "reuters", "future");
		assertThat(strategy.evaluate(candles, 35, List.of(future))).isEmpty();
	}

	@Test
	void warmupGate_blocksEarlyEvaluation() {
		NfmBacktestStrategy strategy = new NfmBacktestStrategy();
		List<HistoricalCandle> candles = move(100, 105, 100, 300);
		assertThat(strategy.evaluate(candles.subList(0, 20), 19, List.of(event(candles, 10)))).isEmpty();
	}
}
