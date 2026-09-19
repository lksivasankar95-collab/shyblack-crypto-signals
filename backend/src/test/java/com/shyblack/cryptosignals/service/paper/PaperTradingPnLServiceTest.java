package com.shyblack.cryptosignals.service.paper;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.config.PaperTradingProperties;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaperTradingPnLServiceTest {

	private final PaperTradingPnLService pnl = new PaperTradingPnLService(defaultProps());

	@Test
	void grossPnl_long_positiveMoveIsProfit() {
		BigDecimal result = pnl.grossPnl(PositionSide.LONG,
				new BigDecimal("100"), new BigDecimal("110"), new BigDecimal("2"));
		assertThat(result).isEqualByComparingTo("20"); // (110-100) * 2
	}

	@Test
	void grossPnl_long_negativeMoveIsLoss() {
		BigDecimal result = pnl.grossPnl(PositionSide.LONG,
				new BigDecimal("100"), new BigDecimal("90"), new BigDecimal("2"));
		assertThat(result).isEqualByComparingTo("-20");
	}

	@Test
	void grossPnl_short_negativeMoveIsProfit() {
		BigDecimal result = pnl.grossPnl(PositionSide.SHORT,
				new BigDecimal("100"), new BigDecimal("90"), new BigDecimal("2"));
		assertThat(result).isEqualByComparingTo("20"); // (100-90) * 2
	}

	@Test
	void fee_isPercentOfNotional() {
		// 0.10% of 10000 = 10
		assertThat(pnl.fee(new BigDecimal("10000"))).isEqualByComparingTo("10");
	}

	@Test
	void netPnl_subtractsFees() {
		BigDecimal net = pnl.netPnl(new BigDecimal("100"), new BigDecimal("5"), new BigDecimal("5"));
		assertThat(net).isEqualByComparingTo("90");
	}

	@Test
	void applySlippage_longEntry_paysHigher() {
		BigDecimal ref = new BigDecimal("100");
		BigDecimal fill = pnl.applySlippage(ref, PositionSide.LONG, true);
		assertThat(fill).isGreaterThan(ref);
	}

	@Test
	void applySlippage_longExit_receivesLower() {
		BigDecimal ref = new BigDecimal("100");
		BigDecimal fill = pnl.applySlippage(ref, PositionSide.LONG, false);
		assertThat(fill).isLessThan(ref);
	}

	@Test
	void applySlippage_shortEntry_receivesLower() {
		BigDecimal ref = new BigDecimal("100");
		BigDecimal fill = pnl.applySlippage(ref, PositionSide.SHORT, true);
		assertThat(fill).isLessThan(ref);
	}

	@Test
	void pctReturn_isNetOverEntryTimes100() {
		BigDecimal pct = pnl.pctReturn(new BigDecimal("100"), new BigDecimal("1000"));
		assertThat(pct).isEqualByComparingTo("10.0000");
	}

	@Test
	void pctReturn_zeroNotionalIsZero() {
		BigDecimal pct = pnl.pctReturn(new BigDecimal("100"), BigDecimal.ZERO);
		assertThat(pct).isEqualByComparingTo("0.0000");
	}

	@Test
	void notional_handlesNulls() {
		assertThat(pnl.notional(null, new BigDecimal("2"))).isEqualByComparingTo("0");
		assertThat(pnl.notional(new BigDecimal("10"), null)).isEqualByComparingTo("0");
	}

	private static PaperTradingProperties defaultProps() {
		return new PaperTradingProperties(
				new BigDecimal("0.10"),
				new BigDecimal("0.05"),
				new BigDecimal("10000.00"),
				10,
				new BigDecimal("2.00"),
				"USDT");
	}
}
