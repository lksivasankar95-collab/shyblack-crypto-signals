package com.shyblack.cryptosignals.service.backtest.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.enums.NewsEventCategory;
import com.shyblack.cryptosignals.entity.enums.NewsEventStage;
import com.shyblack.cryptosignals.entity.enums.NewsEventType;
import com.shyblack.cryptosignals.entity.enums.NewsImpact;
import com.shyblack.cryptosignals.entity.enums.NewsSourceTier;
import com.shyblack.cryptosignals.market.DerivativesSnapshot;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalCandle;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalDerivativesProvider;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Proves the NFM backtest consumes derivatives strictly as-of the evaluated
 * candle, never from the future, and that extreme funding blocks the trade.
 */
class NfmBacktestDerivativesNoLookAheadTest {

	private static final long BASE = 1_700_000_000_000L;
	private static final long FIVE_MIN = 300_000L;

	private static final class RecordingProvider implements HistoricalDerivativesProvider {
		final List<Instant> requested = new ArrayList<>();
		DerivativesSnapshot response = DerivativesSnapshot.unavailable();

		@Override
		public DerivativesSnapshot asOf(String symbol, Instant time) {
			requested.add(time);
			return response;
		}
	}

	private static HistoricalCandle candle(int index, double price, double volume) {
		Instant open = Instant.ofEpochMilli(BASE + (long) index * FIVE_MIN);
		BigDecimal p = BigDecimal.valueOf(price).setScale(8, RoundingMode.HALF_UP);
		return new HistoricalCandle(open, p, p, p, p, BigDecimal.valueOf(volume), open.plusMillis(FIVE_MIN));
	}

	private static List<HistoricalCandle> bullish() {
		List<HistoricalCandle> list = new ArrayList<>();
		for (int i = 0; i < 30; i++) list.add(candle(i, 100, 100));
		for (int i = 0; i < 10; i++) list.add(candle(30 + i, 100 + (i + 1) * 0.5, 300));
		return list;
	}

	private static HistoricalEvent event(List<HistoricalCandle> candles) {
		return new HistoricalEvent(UUID.randomUUID(), candles.get(30).closeTime(), "BTCUSDT",
				NewsEventType.BTC_ETF, NewsEventCategory.CRYPTO_STRUCTURAL, NewsEventStage.APPROVAL,
				NewsSourceTier.TIER_1, NewsImpact.CRITICAL, null, null, null, "reuters", "etf");
	}

	@Test
	void derivativesAreRequestedAtTheEvaluatedCandleCloseTime_only() {
		RecordingProvider provider = new RecordingProvider();
		NfmBacktestStrategy strategy = new NfmBacktestStrategy(provider, null);
		List<HistoricalCandle> candles = bullish();

		strategy.evaluate(candles, 35, List.of(event(candles)));

		assertThat(provider.requested).hasSize(1);
		assertThat(provider.requested.get(0)).isEqualTo(candles.get(35).closeTime());
		// Never a future timestamp relative to the candle.
		assertThat(provider.requested).allMatch(t -> !t.isAfter(candles.get(35).closeTime()));
	}

	@Test
	void extremeFundingFromHistory_blocksChasingLong() {
		RecordingProvider provider = new RecordingProvider();
		provider.response = new DerivativesSnapshot(
				new BigDecimal("100"), null, new BigDecimal("0.0020"), new BigDecimal("1000"),
				BigDecimal.ZERO, null, null, true, true, false);
		NfmBacktestStrategy strategy = new NfmBacktestStrategy(provider, null);
		List<HistoricalCandle> candles = bullish();

		Optional<BacktestStrategy.Signal> signal =
				strategy.evaluate(candles, candles.size() - 1, List.of(event(candles)));

		assertThat(signal).isEmpty();
	}

	@Test
	void missingDerivatives_stillProducesSignal_markedUnknown() {
		// Provider returns unavailable — funding/liquidation UNKNOWN, never zero.
		NfmBacktestStrategy strategy = new NfmBacktestStrategy(null, null);
		List<HistoricalCandle> candles = bullish();

		Optional<BacktestStrategy.Signal> signal =
				strategy.evaluate(candles, candles.size() - 1, List.of(event(candles)));

		assertThat(signal).isPresent();
	}
}
