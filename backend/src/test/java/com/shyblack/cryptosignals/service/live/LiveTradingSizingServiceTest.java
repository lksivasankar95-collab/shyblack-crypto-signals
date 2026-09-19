package com.shyblack.cryptosignals.service.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.LiveTradingAccount;
import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.exchange.SymbolRules;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LiveTradingSizingServiceTest {

	private final LiveTradingSizingService svc = new LiveTradingSizingService();

	private static SymbolRules btcRules() {
		return new SymbolRules("BTCUSDT",
				new BigDecimal("0.00001"),
				new BigDecimal("1000"),
				new BigDecimal("0.00001"),
				new BigDecimal("0.01"),
				new BigDecimal("1000000"),
				new BigDecimal("0.01"),
				new BigDecimal("10"));
	}

	private static Signal signal(BigDecimal entry, BigDecimal stop, BigDecimal riskPct) {
		Signal s = new Signal();
		s.setSymbol("BTCUSDT");
		s.setSide(PositionSide.LONG);
		s.setEntryPrice(entry);
		s.setStopLoss(stop);
		s.setSuggestedRiskPercent(riskPct);
		return s;
	}

	private static LiveTradingAccount account(BigDecimal maxNotional) {
		LiveTradingAccount a = new LiveTradingAccount();
		a.setMaxNotionalPerTrade(maxNotional);
		a.setMaxActivePositions(3);
		return a;
	}

	@Test
	void sizes_from_risk_and_normalises_to_step() {
		// balance 10_000, risk 1% => riskAmount 100
		// entry 100, stop 99, distance 1 => raw qty 100
		// notional 10000 exceeds maxNotional 500 => scale to 5 units, notional 500
		LiveTradingSizingService.Sizing s = svc.size(
				account(new BigDecimal("500")),
				signal(new BigDecimal("100"), new BigDecimal("99"), new BigDecimal("1.0")),
				btcRules(),
				new BigDecimal("10000"),
				new BigDecimal("100"));
		assertThat(s.ok()).isTrue();
		assertThat(s.quantity()).isEqualByComparingTo("5");
		assertThat(s.notional()).isEqualByComparingTo("500");
	}

	@Test
	void rejects_when_balance_below_min_notional() {
		LiveTradingSizingService.Sizing s = svc.size(
				account(null),
				signal(new BigDecimal("100"), new BigDecimal("99"), new BigDecimal("1.0")),
				btcRules(),
				new BigDecimal("5"), // too small — min notional is 10
				new BigDecimal("100"));
		assertThat(s.ok()).isFalse();
		assertThat(s.reason()).isNotBlank();
	}

	@Test
	void rejects_when_stop_equals_entry() {
		LiveTradingSizingService.Sizing s = svc.size(
				account(new BigDecimal("500")),
				signal(new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("1.0")),
				btcRules(),
				new BigDecimal("10000"),
				new BigDecimal("100"));
		assertThat(s.ok()).isFalse();
		assertThat(s.reason()).isEqualTo("INVALID_STOP_LOSS");
	}
}
