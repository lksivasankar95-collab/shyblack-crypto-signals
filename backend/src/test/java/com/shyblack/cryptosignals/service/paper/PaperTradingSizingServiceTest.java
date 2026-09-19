package com.shyblack.cryptosignals.service.paper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.shyblack.cryptosignals.config.PaperTradingProperties;
import com.shyblack.cryptosignals.entity.Portfolio;
import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaperTradingSizingServiceTest {

	private final PaperTradingSizingService sizing = new PaperTradingSizingService(defaultProps());

	@Test
	void sizes_using_stopDistance_and_riskPct() {
		// available=10000, riskPct=2 -> riskAmount=200
		// entry=100, stop=95 -> distance=5 -> qty=40, notional=4000
		Portfolio portfolio = portfolio(new BigDecimal("10000"));
		Signal signal = signal(new BigDecimal("100"), new BigDecimal("95"), new BigDecimal("2.00"));

		PaperTradingSizingService.Sizing s = sizing.size(portfolio, signal, new BigDecimal("100"));
		assertThat(s).isNotNull();
		assertThat(s.quantity()).isEqualByComparingTo("40");
		assertThat(s.notional().doubleValue()).isCloseTo(4000d, within(0.01));
		assertThat(s.riskAmount()).isEqualByComparingTo("200.00");
	}

	@Test
	void scalesDown_whenNotionalExceedsAvailable() {
		// available=100, riskPct=2 -> riskAmount=2; entry=100 stop=99 -> distance=1 -> qty=2, notional=200 > 100
		// Should scale qty down to 1 (notional=100).
		Portfolio portfolio = portfolio(new BigDecimal("100"));
		Signal signal = signal(new BigDecimal("100"), new BigDecimal("99"), new BigDecimal("2.00"));

		PaperTradingSizingService.Sizing s = sizing.size(portfolio, signal, new BigDecimal("100"));
		assertThat(s).isNotNull();
		assertThat(s.notional()).isLessThanOrEqualTo(new BigDecimal("100"));
	}

	@Test
	void returnsNull_whenZeroBalance() {
		Portfolio portfolio = portfolio(BigDecimal.ZERO);
		Signal signal = signal(new BigDecimal("100"), new BigDecimal("95"), new BigDecimal("2.00"));
		assertThat(sizing.size(portfolio, signal, new BigDecimal("100"))).isNull();
	}

	@Test
	void returnsNull_whenStopEqualsEntry() {
		Portfolio portfolio = portfolio(new BigDecimal("10000"));
		Signal signal = signal(new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("2.00"));
		assertThat(sizing.size(portfolio, signal, new BigDecimal("100"))).isNull();
	}

	@Test
	void returnsNull_whenEntryPriceIsZero() {
		Portfolio portfolio = portfolio(new BigDecimal("10000"));
		Signal signal = signal(new BigDecimal("100"), new BigDecimal("95"), new BigDecimal("2.00"));
		assertThat(sizing.size(portfolio, signal, BigDecimal.ZERO)).isNull();
	}

	@Test
	void returnsNull_whenSignalHasNoStopLoss() {
		Portfolio portfolio = portfolio(new BigDecimal("10000"));
		Signal signal = new Signal();
		signal.setEntryPrice(new BigDecimal("100"));
		signal.setSide(PositionSide.LONG);
		signal.setSuggestedRiskPercent(new BigDecimal("2.00"));
		assertThat(sizing.size(portfolio, signal, new BigDecimal("100"))).isNull();
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

	private static Portfolio portfolio(BigDecimal available) {
		Portfolio p = new Portfolio();
		p.setAvailableBalance(available);
		p.setTotalBalance(available);
		p.setInvested(BigDecimal.ZERO);
		p.setInitialBalance(available);
		return p;
	}

	private static Signal signal(BigDecimal entry, BigDecimal stop, BigDecimal riskPct) {
		Signal s = new Signal();
		s.setEntryPrice(entry);
		s.setStopLoss(stop);
		s.setSide(PositionSide.LONG);
		s.setSuggestedRiskPercent(riskPct);
		return s;
	}
}
