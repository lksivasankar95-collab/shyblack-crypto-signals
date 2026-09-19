package com.shyblack.cryptosignals.exchange.futures.mock;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.enums.FuturesMarginMode;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderStatus;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderType;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.exchange.futures.FuturesOrderResult;
import com.shyblack.cryptosignals.exchange.futures.PlaceFuturesOrderRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MockFuturesExchangeAdapterTest {

	private final MockFuturesExchangeAdapter adapter = new MockFuturesExchangeAdapter();
	private final ExchangeCredential credential = new ExchangeCredential();

	@Test
	void marketBuy_LONG_fillsImmediately() {
		FuturesOrderResult r = adapter.placeOrder(credential, new PlaceFuturesOrderRequest(
				"BTCUSDT", PositionSide.LONG, PositionSide.LONG,
				FuturesOrderType.MARKET, false,
				new BigDecimal("0.01"), new BigDecimal("50000"), null,
				"SBF-LONG"));
		assertThat(r.status()).isEqualTo(FuturesOrderStatus.FILLED);
		assertThat(r.executedQuantity()).isEqualByComparingTo("0.01");
	}

	@Test
	void marketSell_SHORT_fillsImmediately() {
		FuturesOrderResult r = adapter.placeOrder(credential, new PlaceFuturesOrderRequest(
				"BTCUSDT", PositionSide.SHORT, PositionSide.SHORT,
				FuturesOrderType.MARKET, false,
				new BigDecimal("0.01"), new BigDecimal("50000"), null,
				"SBF-SHORT"));
		assertThat(r.status()).isEqualTo(FuturesOrderStatus.FILLED);
	}

	@Test
	void duplicateClientOrderId_isIdempotent() {
		PlaceFuturesOrderRequest req = new PlaceFuturesOrderRequest(
				"BTCUSDT", PositionSide.LONG, PositionSide.LONG,
				FuturesOrderType.MARKET, false,
				new BigDecimal("0.01"), new BigDecimal("50000"), null,
				"SBF-DUPE");
		FuturesOrderResult first = adapter.placeOrder(credential, req);
		FuturesOrderResult second = adapter.placeOrder(credential, req);
		assertThat(first.exchangeOrderId()).isEqualTo(second.exchangeOrderId());
	}

	@Test
	void stopMarket_isAcknowledgedNotFilled() {
		FuturesOrderResult r = adapter.placeOrder(credential, new PlaceFuturesOrderRequest(
				"BTCUSDT", PositionSide.SHORT, PositionSide.LONG,
				FuturesOrderType.STOP_MARKET, true,
				new BigDecimal("0.01"), null, new BigDecimal("49000"),
				"SBF-SL"));
		assertThat(r.status()).isEqualTo(FuturesOrderStatus.ACKNOWLEDGED);
		assertThat(r.reduceOnly()).isTrue();
	}

	@Test
	void setLeverage_isPersisted() {
		adapter.setLeverage(credential, "BTCUSDT", 5);
		assertThat(adapter.effectiveLeverage("BTCUSDT")).isEqualTo(5);
	}

	@Test
	void setMarginMode_isPersisted() {
		adapter.setMarginMode(credential, "BTCUSDT", FuturesMarginMode.ISOLATED);
		assertThat(adapter.effectiveMarginMode("BTCUSDT")).isEqualTo(FuturesMarginMode.ISOLATED);
	}

	@Test
	void forceFill_makesStopFilled() {
		adapter.placeOrder(credential, new PlaceFuturesOrderRequest(
				"BTCUSDT", PositionSide.SHORT, PositionSide.LONG,
				FuturesOrderType.STOP_MARKET, true,
				new BigDecimal("0.01"), null, new BigDecimal("49000"),
				"SBF-SL2"));
		adapter.forceFill("SBF-SL2", new BigDecimal("49000"));
		FuturesOrderResult r = adapter.getOrder(credential, "BTCUSDT", "SBF-SL2");
		assertThat(r.status()).isEqualTo(FuturesOrderStatus.FILLED);
	}
}
