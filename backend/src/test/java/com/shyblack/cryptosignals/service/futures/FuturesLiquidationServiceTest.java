package com.shyblack.cryptosignals.service.futures;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.config.FuturesTradingProperties;
import com.shyblack.cryptosignals.entity.enums.FuturesMarginMode;
import com.shyblack.cryptosignals.entity.enums.FuturesPositionMode;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FuturesLiquidationServiceTest {

	private static final FuturesTradingProperties PROPS = new FuturesTradingProperties(
			FuturesTradingProperties.Mode.MOCK,
			"https://testnet", "wss://testnet", 5000, 3,
			FuturesMarginMode.ISOLATED, FuturesPositionMode.ONE_WAY,
			new BigDecimal("200.00"), 2, new BigDecimal("5.00"),
			new BigDecimal("0.30"), new BigDecimal("15.00"), false);

	private final FuturesLiquidationService svc = new FuturesLiquidationService(PROPS);

	@Test
	void long_reasonableStop_isSafe() {
		FuturesLiquidationService.Assessment a = svc.assess(
				PositionSide.LONG,
				new BigDecimal("50000"),
				new BigDecimal("49500"),
				3);
		assertThat(a.safe()).isTrue();
		assertThat(a.liquidationPrice()).isNotNull();
	}

	@Test
	void long_stopTooCloseToLiquidation_isRejected() {
		FuturesLiquidationService.Assessment a = svc.assess(
				PositionSide.LONG,
				new BigDecimal("50000"),
				new BigDecimal("35000"), // ~30% below entry, ~= liquidation at 3x
				3);
		assertThat(a.safe()).isFalse();
	}

	@Test
	void stopTooCloseToEntry_STOP_DISTANCE_INVALID() {
		FuturesLiquidationService.Assessment a = svc.assess(
				PositionSide.LONG,
				new BigDecimal("50000"),
				new BigDecimal("49999.9"), // 0.0002% distance << 0.30% minimum
				3);
		assertThat(a.safe()).isFalse();
		assertThat(a.reason()).isEqualTo("STOP_DISTANCE_INVALID");
	}

	@Test
	void short_reasonableStop_isSafe() {
		FuturesLiquidationService.Assessment a = svc.assess(
				PositionSide.SHORT,
				new BigDecimal("50000"),
				new BigDecimal("50500"),
				3);
		assertThat(a.safe()).isTrue();
	}
}
