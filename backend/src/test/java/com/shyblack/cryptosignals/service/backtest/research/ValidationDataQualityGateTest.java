package com.shyblack.cryptosignals.service.backtest.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.market.DerivativesSnapshot;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalCandle;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ValidationDataQualityGateTest {

	private static final Instant FROM = Instant.parse("2023-09-01T00:00:00Z");
	private static final Instant TO = Instant.parse("2026-10-01T00:00:00Z");

	private static HistoricalCandle candle(String openTime, double o, double h, double l, double c) {
		Instant t = Instant.parse(openTime);
		return new HistoricalCandle(t, bd(o), bd(h), bd(l), bd(c), BigDecimal.ONE, t.plusSeconds(60));
	}

	private static BigDecimal bd(double v) {
		return BigDecimal.valueOf(v).setScale(8, RoundingMode.HALF_UP);
	}

	private static HistoricalEvent event(UUID id, Instant t) {
		return new HistoricalEvent(id, t, "BTCUSDT", null, null, null, null, null, null, null, null, null, null);
	}

	@Test
	void validCandles_pass() {
		assertThat(ValidationDataQualityGate.checkCandles(List.of(
				candle("2024-01-01T00:00:00Z", 100, 101, 99, 100),
				candle("2024-01-01T00:01:00Z", 100, 102, 100, 101)), FROM, TO).passed()).isTrue();
	}

	@Test
	void duplicateAndUnsortedAndInvalidOhlc_block() {
		assertThat(ValidationDataQualityGate.checkCandles(List.of(
				candle("2024-01-01T00:00:00Z", 100, 101, 99, 100),
				candle("2024-01-01T00:00:00Z", 100, 101, 99, 100)), FROM, TO).passed()).isFalse();
		assertThat(ValidationDataQualityGate.checkCandles(List.of(
				candle("2024-01-01T00:02:00Z", 100, 101, 99, 100),
				candle("2024-01-01T00:01:00Z", 100, 101, 99, 100)), FROM, TO).passed()).isFalse();
		assertThat(ValidationDataQualityGate.checkCandles(List.of(
				candle("2024-01-01T00:00:00Z", 100, 99, 101, 100)), FROM, TO).passed()).isFalse();
	}

	@Test
	void validEvents_pass_duplicatesAndWindowViolationsBlock() {
		UUID a = UUID.randomUUID();
		assertThat(ValidationDataQualityGate.checkEvents(
				List.of(event(a, Instant.parse("2024-01-01T12:00:00Z"))), FROM, TO).passed()).isTrue();
		// duplicate external id
		assertThat(ValidationDataQualityGate.checkEvents(List.of(
				event(a, Instant.parse("2024-01-01T12:00:00Z")),
				event(a, Instant.parse("2024-01-02T12:00:00Z"))), FROM, TO).passed()).isFalse();
		// out of window
		assertThat(ValidationDataQualityGate.checkEvents(
				List.of(event(UUID.randomUUID(), Instant.parse("2020-01-01T00:00:00Z"))), FROM, TO).passed()).isFalse();
		// null time
		assertThat(ValidationDataQualityGate.checkEvents(
				List.of(event(UUID.randomUUID(), null)), FROM, TO).passed()).isFalse();
	}

	@Test
	void missingDerivativesStayUnknown_notZero() {
		// Unavailable snapshot: all null, flags false -> allowed (UNKNOWN).
		assertThat(ValidationDataQualityGate.checkDerivatives(DerivativesSnapshot.unavailable()).passed()).isTrue();
		// Missing OI coerced to zero while unavailable -> BLOCKED.
		DerivativesSnapshot coerced = new DerivativesSnapshot(null, null, null, BigDecimal.ZERO, null,
				null, null, false, false, false);
		assertThat(ValidationDataQualityGate.checkDerivatives(coerced).passed()).isFalse();
		// Funding value present but flagged unavailable -> BLOCKED.
		DerivativesSnapshot mismatch = new DerivativesSnapshot(null, null, new BigDecimal("0.0001"), null, null,
				null, null, false, false, false);
		assertThat(ValidationDataQualityGate.checkDerivatives(mismatch).passed()).isFalse();
	}

	@Test
	void emptyEventDataset_passesAsUnknownCoverage_notFake() {
		// An empty event list is a valid dataset state (coverage, not quality).
		assertThat(ValidationDataQualityGate.checkEvents(List.of(), FROM, TO).passed()).isTrue();
	}
}
