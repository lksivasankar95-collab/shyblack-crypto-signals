package com.shyblack.cryptosignals.exchange;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SymbolRulesTest {

	private static final SymbolRules BTC = new SymbolRules(
			"BTCUSDT",
			new BigDecimal("0.00001"),
			new BigDecimal("1000"),
			new BigDecimal("0.00001"),
			new BigDecimal("0.01"),
			new BigDecimal("1000000"),
			new BigDecimal("0.01"),
			new BigDecimal("10"));

	@Test
	void normalizeQuantity_snapsDown_toStepSize() {
		assertThat(BTC.normalizeQuantity(new BigDecimal("0.123456789")))
				.isEqualByComparingTo("0.12345");
	}

	@Test
	void normalizePrice_snapsToTick() {
		assertThat(BTC.normalizePrice(new BigDecimal("42000.123")))
				.isEqualByComparingTo("42000.12");
	}

	@Test
	void meetsMinNotional_rejectsBelowMin() {
		// qty=0.001 * 5000 = 5 < 10 -> false
		assertThat(BTC.meetsMinNotional(new BigDecimal("0.001"), new BigDecimal("5000")))
				.isFalse();
	}

	@Test
	void meetsMinNotional_acceptsAboveMin() {
		assertThat(BTC.meetsMinNotional(new BigDecimal("0.002"), new BigDecimal("50000")))
				.isTrue();
	}

	@Test
	void meetsMinQty_enforcesRange() {
		assertThat(BTC.meetsMinQty(new BigDecimal("0.000001"))).isFalse();
		assertThat(BTC.meetsMinQty(new BigDecimal("0.5"))).isTrue();
	}
}
