package com.shyblack.cryptosignals.signal.nfm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import com.shyblack.cryptosignals.dto.market.KlineResponse;
import com.shyblack.cryptosignals.dto.strategy.NfmFuturesConfig;
import com.shyblack.cryptosignals.entity.enums.MarketRegime;
import com.shyblack.cryptosignals.entity.enums.NewsEventCategory;
import com.shyblack.cryptosignals.entity.enums.NewsEventStage;
import com.shyblack.cryptosignals.entity.enums.NewsEventType;
import com.shyblack.cryptosignals.entity.enums.NewsImpact;
import com.shyblack.cryptosignals.entity.enums.NewsSourceTier;
import com.shyblack.cryptosignals.market.DerivativesSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the NFM reaction-indexing defect: an event occurring
 * inside the latest in-progress candle used to fall back to candle 0, producing
 * a bogus "since-inception" reaction (observed up to +335%). Reaction must be
 * measured from the correct candle and must never read future data.
 */
class NfmReactionIndexingTest {

	private static final Instant T0 = Instant.parse("2024-01-01T00:00:00Z");

	private static List<KlineResponse> klines(double[] closes) {
		List<KlineResponse> out = new ArrayList<>();
		for (int i = 0; i < closes.length; i++) {
			Instant open = T0.plus(i, ChronoUnit.HOURS);
			BigDecimal c = BigDecimal.valueOf(closes[i]);
			out.add(new KlineResponse(open.toEpochMilli(), c, c, c, c, BigDecimal.ONE,
					open.plus(1, ChronoUnit.HOURS).toEpochMilli()));
		}
		return out;
	}

	private static NfmEventView event(Instant time) {
		return new NfmEventView(UUID.randomUUID(), NewsEventType.CPI, NewsEventCategory.MACRO,
				NewsEventStage.REPORT, NewsSourceTier.TIER_1, NewsImpact.HIGH, null, null, null, null, null,
				time);
	}

	private static BigDecimal reaction(List<KlineResponse> klines, Instant eventTime) {
		NfmAssessment a = NfmFuturesAnalyzer.analyze("BTCUSDT", event(klines, eventTime), klines,
				DerivativesSnapshot.unavailable(), MarketRegime.NEUTRAL, NfmFuturesConfig.defaults());
		return a.priceReactionPct();
	}

	private static NfmEventView event(List<KlineResponse> ignored, Instant time) {
		return event(time);
	}

	/** closes[0] is deliberately tiny to expose any candle-0 fallback. */
	private static double[] closes(int n, double first, double base) {
		double[] a = new double[n];
		a[0] = first;
		for (int i = 1; i < n; i++) {
			a[i] = base + i;
		}
		return a;
	}

	private static List<KlineResponse> head(List<KlineResponse> k, int size) {
		return new ArrayList<>(k.subList(0, size));
	}

	/** 1. Event inside the latest candle maps to that candle (reaction ~0), never candle 0. */
	@Test
	void eventInsideLatestCandleUsesCurrentCandleNotFirst() {
		List<KlineResponse> k = klines(closes(40, 1.0, 100));
		Instant eventTime = T0.plus(39, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES);
		BigDecimal reaction = reaction(k, eventTime);
		assertThat(reaction).isNotNull();
		assertThat(reaction.doubleValue()).isCloseTo(0.0, offset(0.5));
		// Pre-fix fallback produced ~ (139 - 1) / 1 * 100 = 13800%.
		assertThat(reaction.doubleValue()).isLessThan(5.0);
	}

	/** 2. A valid in-range event resolves to the correct start candle (not candle 0). */
	@Test
	void eventInMidHistoryUsesCorrectStartCandle() {
		List<KlineResponse> k = head(klines(closes(40, 1.0, 100)), 36);
		// event at 10:30 -> first candle open >= event is index 11 (close 111); current index 35 (close 135)
		Instant eventTime = T0.plus(10, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES);
		BigDecimal reaction = reaction(k, eventTime);
		double expected = (135.0 - 111.0) / 111.0 * 100.0; // 21.6216
		assertThat(reaction.doubleValue()).isCloseTo(expected, offset(0.01));
	}

	/** 3. Event after the last candle clamps to the last candle; no future data, no bogus value. */
	@Test
	void eventAfterLastCandleDoesNotReadFutureOrFirstCandle() {
		List<KlineResponse> k = klines(closes(40, 1.0, 100));
		Instant eventTime = T0.plus(39, ChronoUnit.HOURS).plus(90, ChronoUnit.MINUTES);
		BigDecimal reaction = reaction(k, eventTime);
		assertThat(reaction.doubleValue()).isCloseTo(0.0, offset(0.5));
	}

	/** 4. Event exactly at a candle open maps to that candle (existing valid behaviour intact). */
	@Test
	void eventAtCandleOpenUsesThatCandle() {
		double[] c = new double[40];
		for (int i = 0; i < 40; i++) {
			c[i] = 100.0 + i;
		}
		List<KlineResponse> k = head(klines(c), 36);
		Instant eventTime = T0.plus(5, ChronoUnit.HOURS); // exactly open of index 5
		BigDecimal reaction = reaction(k, eventTime);
		double expected = (135.0 - 105.0) / 105.0 * 100.0; // 28.5714
		assertThat(reaction.doubleValue()).isCloseTo(expected, offset(0.01));
	}
}
