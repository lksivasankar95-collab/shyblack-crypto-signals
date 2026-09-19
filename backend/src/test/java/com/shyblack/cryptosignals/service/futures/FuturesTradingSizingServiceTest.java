package com.shyblack.cryptosignals.service.futures;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.FuturesTradingAccount;
import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.exchange.SymbolRules;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FuturesTradingSizingServiceTest {

	private final FuturesTradingSizingService svc = new FuturesTradingSizingService();

	private static SymbolRules rules() {
		return new SymbolRules("BTCUSDT",
				new BigDecimal("0.001"), new BigDecimal("1000"),
				new BigDecimal("0.001"),
				new BigDecimal("0.10"), new BigDecimal("1000000"),
				new BigDecimal("0.10"),
				new BigDecimal("5"));
	}

	private static Signal signal(BigDecimal entry, BigDecimal stop, PositionSide side) {
		Signal s = new Signal();
		s.setSymbol("BTCUSDT");
		s.setSide(side);
		s.setEntryPrice(entry);
		s.setStopLoss(stop);
		s.setSuggestedRiskPercent(new BigDecimal("1.00"));
		return s;
	}

	private static FuturesTradingAccount account(BigDecimal maxNotional) {
		FuturesTradingAccount a = new FuturesTradingAccount();
		a.setMaxNotionalPerTrade(maxNotional);
		a.setMaxActivePositions(2);
		a.setMaxLeverage(3);
		return a;
	}

	@Test
	void long_sized_from_risk_and_normalized() {
		FuturesTradingSizingService.Sizing s = svc.size(
				account(new BigDecimal("200")),
				signal(new BigDecimal("50000"), new BigDecimal("49500"), PositionSide.LONG),
				rules(),
				new BigDecimal("1000"),   // available margin
				new BigDecimal("50000"), // reference price
				3);
		// risk = 1% * 1000 = 10; distance = 500; raw qty = 0.02 -> step 0.001 keeps 0.02
		// notional = 0.02 * 50000 = 1000 > cap 200 -> scale to 0.004 (notional 200)
		assertThat(s.ok()).isTrue();
		assertThat(s.notional()).isEqualByComparingTo("200");
		// margin = notional / leverage = 200 / 3 ≈ 66.67
		assertThat(s.initialMargin().doubleValue()).isBetween(66.0, 67.0);
	}

	@Test
	void short_sizing_uses_absoluteStopDistance() {
		FuturesTradingSizingService.Sizing s = svc.size(
				account(new BigDecimal("200")),
				signal(new BigDecimal("50000"), new BigDecimal("50500"), PositionSide.SHORT),
				rules(),
				new BigDecimal("1000"),
				new BigDecimal("50000"),
				3);
		assertThat(s.ok()).isTrue();
	}

	@Test
	void marginInsufficient_isRejected() {
		FuturesTradingSizingService.Sizing s = svc.size(
				account(new BigDecimal("100000")),
				signal(new BigDecimal("50000"), new BigDecimal("49500"), PositionSide.LONG),
				rules(),
				new BigDecimal("1"), // effectively no available balance
				new BigDecimal("50000"),
				3);
		assertThat(s.ok()).isFalse();
	}

	@Test
	void zeroStopDistance_rejected() {
		FuturesTradingSizingService.Sizing s = svc.size(
				account(new BigDecimal("200")),
				signal(new BigDecimal("50000"), new BigDecimal("50000"), PositionSide.LONG),
				rules(),
				new BigDecimal("1000"),
				new BigDecimal("50000"),
				3);
		assertThat(s.ok()).isFalse();
		assertThat(s.reason()).isEqualTo("INVALID_STOP_LOSS");
	}

	@Test
	void leverage_doesNotChangeRiskAmount() {
		// Risk should stay = balance * riskPct. Higher leverage only reduces margin.
		var s1 = svc.size(account(null),
				signal(new BigDecimal("50000"), new BigDecimal("49500"), PositionSide.LONG),
				rules(), new BigDecimal("1000"), new BigDecimal("50000"), 1);
		var s5 = svc.size(account(null),
				signal(new BigDecimal("50000"), new BigDecimal("49500"), PositionSide.LONG),
				rules(), new BigDecimal("1000"), new BigDecimal("50000"), 3);
		assertThat(s5.riskAmount()).isEqualByComparingTo(s1.riskAmount());
	}
}
