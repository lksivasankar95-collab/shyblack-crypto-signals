package com.shyblack.cryptosignals.signal.nfm;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.dto.market.KlineResponse;
import com.shyblack.cryptosignals.dto.strategy.NfmFuturesConfig;
import com.shyblack.cryptosignals.entity.enums.FundingState;
import com.shyblack.cryptosignals.entity.enums.LiquidationState;
import com.shyblack.cryptosignals.entity.enums.MarketRegime;
import com.shyblack.cryptosignals.entity.enums.NfmAction;
import com.shyblack.cryptosignals.entity.enums.NewsEventCategory;
import com.shyblack.cryptosignals.entity.enums.NewsEventStage;
import com.shyblack.cryptosignals.entity.enums.NewsEventType;
import com.shyblack.cryptosignals.entity.enums.NewsImpact;
import com.shyblack.cryptosignals.entity.enums.NewsSourceTier;
import com.shyblack.cryptosignals.market.DerivativesSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NfmFuturesAnalyzerTest {

	private static final long BASE_TIME = 1_700_000_000_000L;
	private static final long FIVE_MIN = 300_000L;

	private static KlineResponse candle(int index, double price, double volume) {
		long t = BASE_TIME + (long) index * FIVE_MIN;
		BigDecimal p = BigDecimal.valueOf(price).setScale(8, RoundingMode.HALF_UP);
		return new KlineResponse(t, p, p, p, p, BigDecimal.valueOf(volume), t + FIVE_MIN);
	}

	/** 30 flat candles at {@code pre}, then 10 candles moving to {@code post}, with volume spike. */
	private static List<KlineResponse> move(int flatPrice, double postPrice, double quietVol, double spikeVol) {
		List<KlineResponse> list = new ArrayList<>();
		for (int i = 0; i < 30; i++) {
			list.add(candle(i, flatPrice, quietVol));
		}
		for (int i = 0; i < 10; i++) {
			double price = flatPrice + (postPrice - flatPrice) * (i + 1) / 10.0;
			list.add(candle(30 + i, price, spikeVol));
		}
		return list;
	}

	/** 30 candles ramping pre→100, then 10 ramping to post; models a pre-extended move. */
	private static List<KlineResponse> rampThenMove(double preStart, double preEnd, double post) {
		List<KlineResponse> list = new ArrayList<>();
		for (int i = 0; i < 30; i++) {
			double price = preStart + (preEnd - preStart) * i / 29.0;
			list.add(candle(i, price, 100));
		}
		for (int i = 0; i < 10; i++) {
			double price = preEnd + (post - preEnd) * (i + 1) / 10.0;
			list.add(candle(30 + i, price, 300));
		}
		return list;
	}

	private static NfmEventView event(Instant eventTime) {
		return new NfmEventView(UUID.randomUUID(), NewsEventType.BTC_ETF, NewsEventCategory.CRYPTO_STRUCTURAL,
				NewsEventStage.REPORT, NewsSourceTier.TIER_1, NewsImpact.HIGH, 80,
				null, null, null, null, eventTime);
	}

	private static Instant eventTime() {
		return Instant.ofEpochMilli(BASE_TIME + 30L * FIVE_MIN);
	}

	private static DerivativesSnapshot derivatives(BigDecimal funding, BigDecimal oiChange) {
		return new DerivativesSnapshot(new BigDecimal("100"), new BigDecimal("100"), funding,
				new BigDecimal("1000"), oiChange, null, null, true, true, false);
	}

	@Test
	void bullishReactionWithVolume_producesLong() {
		NfmAssessment a = NfmFuturesAnalyzer.analyze("BTCUSDT", event(eventTime()),
				move(100, 105, 100, 300), derivatives(new BigDecimal("0.0001"), new BigDecimal("2.0")),
				MarketRegime.BULLISH, NfmFuturesConfig.defaults());

		assertThat(a.actionable()).isTrue();
		assertThat(a.action()).isEqualTo(NfmAction.LONG);
		assertThat(a.score()).isGreaterThanOrEqualTo(65);
		assertThat(a.entry()).isNotNull();
		assertThat(a.stopLoss()).isLessThan(a.entry());
		assertThat(a.tp1()).isGreaterThan(a.entry());
	}

	@Test
	void bearishReaction_producesShort() {
		NfmAssessment a = NfmFuturesAnalyzer.analyze("BTCUSDT", event(eventTime()),
				move(100, 95, 100, 300), derivatives(new BigDecimal("-0.0001"), new BigDecimal("-2.0")),
				MarketRegime.BEARISH, NfmFuturesConfig.defaults());

		assertThat(a.action()).isEqualTo(NfmAction.SHORT);
		assertThat(a.stopLoss()).isGreaterThan(a.entry());
		assertThat(a.tp1()).isLessThan(a.entry());
	}

	@Test
	void insufficientReaction_waitsForConfirmation() {
		NfmAssessment a = NfmFuturesAnalyzer.analyze("BTCUSDT", event(eventTime()),
				move(100, 100.05, 100, 300), derivatives(BigDecimal.ZERO, BigDecimal.ZERO),
				MarketRegime.NEUTRAL, NfmFuturesConfig.defaults());

		assertThat(a.actionable()).isFalse();
		assertThat(a.action()).isEqualTo(NfmAction.WAIT_CONFIRMATION);
	}

	@Test
	void lowVolume_waitsForConfirmation() {
		NfmAssessment a = NfmFuturesAnalyzer.analyze("BTCUSDT", event(eventTime()),
				move(100, 105, 100, 100), derivatives(BigDecimal.ZERO, new BigDecimal("2.0")),
				MarketRegime.BULLISH, NfmFuturesConfig.defaults());

		assertThat(a.action()).isEqualTo(NfmAction.WAIT_CONFIRMATION);
		assertThat(a.reason()).contains("volume");
	}

	@Test
	void extremeLongFunding_blocksChasingLong() {
		NfmAssessment a = NfmFuturesAnalyzer.analyze("BTCUSDT", event(eventTime()),
				move(100, 105, 100, 300), derivatives(new BigDecimal("0.0020"), new BigDecimal("2.0")),
				MarketRegime.BULLISH, NfmFuturesConfig.defaults());

		assertThat(a.action()).isEqualTo(NfmAction.RISK_BLOCKED);
		assertThat(a.fundingState()).isEqualTo(FundingState.EXTREME_LONG);
	}

	@Test
	void preExtendedMove_isPricedIn_andWaits() {
		// pre ramps 80 -> 100 (≈25%), then pushes to 106.
		NfmAssessment a = NfmFuturesAnalyzer.analyze("BTCUSDT", event(eventTime()),
				rampThenMove(80, 100, 106), derivatives(BigDecimal.ZERO, new BigDecimal("2.0")),
				MarketRegime.BULLISH, NfmFuturesConfig.defaults());

		assertThat(a.actionable()).isFalse();
		assertThat(a.action()).isEqualTo(NfmAction.WAIT_CONFIRMATION);
		assertThat(a.reason()).contains("priced");
	}

	@Test
	void insufficientCandles_isNoTrade() {
		List<KlineResponse> few = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			few.add(candle(i, 100, 100));
		}
		NfmAssessment a = NfmFuturesAnalyzer.analyze("BTCUSDT", event(eventTime()),
				few, derivatives(BigDecimal.ZERO, BigDecimal.ZERO), MarketRegime.NEUTRAL,
				NfmFuturesConfig.defaults());

		assertThat(a.action()).isEqualTo(NfmAction.NO_TRADE);
	}

	@Test
	void shortDisabled_isNoTrade() {
		NfmFuturesConfig cfg = NfmFuturesConfig.defaults();
		cfg.setAllowShort(false);
		NfmAssessment a = NfmFuturesAnalyzer.analyze("BTCUSDT", event(eventTime()),
				move(100, 95, 100, 300), derivatives(BigDecimal.ZERO, new BigDecimal("-2.0")),
				MarketRegime.BEARISH, cfg);

		assertThat(a.action()).isEqualTo(NfmAction.NO_TRADE);
	}

	@Test
	void fundingClassification_thresholds() {
		NfmFuturesConfig cfg = NfmFuturesConfig.defaults();
		assertThat(NfmFuturesAnalyzer.classifyFunding(null, cfg)).isEqualTo(FundingState.UNKNOWN);
		assertThat(NfmFuturesAnalyzer.classifyFunding(new BigDecimal("0.00001"), cfg)).isEqualTo(FundingState.NORMAL);
		assertThat(NfmFuturesAnalyzer.classifyFunding(new BigDecimal("0.0008"), cfg))
				.isEqualTo(FundingState.ELEVATED_LONG);
		assertThat(NfmFuturesAnalyzer.classifyFunding(new BigDecimal("0.0020"), cfg))
				.isEqualTo(FundingState.EXTREME_LONG);
		assertThat(NfmFuturesAnalyzer.classifyFunding(new BigDecimal("-0.0020"), cfg))
				.isEqualTo(FundingState.EXTREME_SHORT);
	}

	@Test
	void liquidationClassification_squeezes() {
		NfmFuturesConfig cfg = NfmFuturesConfig.defaults();
		DerivativesSnapshot longSqueeze = new DerivativesSnapshot(null, null, null, null, null,
				new BigDecimal("900"), new BigDecimal("100"), false, false, true);
		assertThat(NfmFuturesAnalyzer.classifyLiquidation(longSqueeze, cfg))
				.isEqualTo(LiquidationState.LONG_SQUEEZE);

		DerivativesSnapshot extreme = new DerivativesSnapshot(null, null, null, null, null,
				new BigDecimal("995"), new BigDecimal("5"), false, false, true);
		assertThat(NfmFuturesAnalyzer.classifyLiquidation(extreme, cfg))
				.isEqualTo(LiquidationState.EXTREME_LIQUIDATION);

		DerivativesSnapshot unavailable = DerivativesSnapshot.unavailable();
		assertThat(NfmFuturesAnalyzer.classifyLiquidation(unavailable, cfg))
				.isEqualTo(LiquidationState.UNKNOWN);
	}
}
