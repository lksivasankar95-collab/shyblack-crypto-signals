package com.shyblack.cryptosignals.exchange.mock;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.enums.LiveOrderStatus;
import com.shyblack.cryptosignals.entity.enums.LiveOrderType;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.exchange.ExchangeOrderResult;
import com.shyblack.cryptosignals.exchange.PlaceOrderRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MockExchangeTradingAdapterTest {

	private final MockExchangeTradingAdapter adapter = new MockExchangeTradingAdapter();
	private final ExchangeCredential credential = new ExchangeCredential();

	@Test
	void marketOrderFillsImmediately() {
		ExchangeOrderResult r = adapter.placeOrder(credential, new PlaceOrderRequest(
				"BTCUSDT", PositionSide.LONG, LiveOrderType.MARKET,
				new BigDecimal("0.01"),
				new BigDecimal("50000"),
				null,
				"SB-abcMARKET"));
		assertThat(r.status()).isEqualTo(LiveOrderStatus.FILLED);
		assertThat(r.executedQuantity()).isEqualByComparingTo("0.01");
		assertThat(r.avgFillPrice()).isEqualByComparingTo("50000");
	}

	@Test
	void duplicateClientOrderId_isIdempotent() {
		PlaceOrderRequest req = new PlaceOrderRequest(
				"BTCUSDT", PositionSide.LONG, LiveOrderType.MARKET,
				new BigDecimal("0.01"),
				new BigDecimal("50000"),
				null,
				"SB-DUPE");
		ExchangeOrderResult first = adapter.placeOrder(credential, req);
		ExchangeOrderResult second = adapter.placeOrder(credential, req);
		assertThat(first.exchangeOrderId()).isEqualTo(second.exchangeOrderId());
	}

	@Test
	void stopLossLimit_isAcknowledgedNotFilled() {
		ExchangeOrderResult r = adapter.placeOrder(credential, new PlaceOrderRequest(
				"BTCUSDT", PositionSide.LONG, LiveOrderType.STOP_LOSS_LIMIT,
				new BigDecimal("0.01"),
				new BigDecimal("49500"),
				new BigDecimal("49000"),
				"SB-SL"));
		assertThat(r.status()).isEqualTo(LiveOrderStatus.ACKNOWLEDGED);
		assertThat(r.executedQuantity()).isEqualByComparingTo("0");
	}

	@Test
	void forceFill_transitionsToFilled() {
		adapter.placeOrder(credential, new PlaceOrderRequest(
				"BTCUSDT", PositionSide.LONG, LiveOrderType.STOP_LOSS_LIMIT,
				new BigDecimal("0.01"),
				new BigDecimal("49500"),
				new BigDecimal("49000"),
				"SB-SL2"));
		adapter.forceFill("SB-SL2", new BigDecimal("49500"));
		ExchangeOrderResult r = adapter.getOrder(credential, "BTCUSDT", "SB-SL2");
		assertThat(r.status()).isEqualTo(LiveOrderStatus.FILLED);
		assertThat(r.avgFillPrice()).isEqualByComparingTo("49500");
	}

	@Test
	void cancel_terminatesOrder() {
		adapter.placeOrder(credential, new PlaceOrderRequest(
				"BTCUSDT", PositionSide.LONG, LiveOrderType.STOP_LOSS_LIMIT,
				new BigDecimal("0.01"),
				new BigDecimal("49500"),
				new BigDecimal("49000"),
				"SB-SL3"));
		ExchangeOrderResult r = adapter.cancelOrder(credential, "BTCUSDT", "SB-SL3");
		assertThat(r.status()).isEqualTo(LiveOrderStatus.CANCELLED);
	}
}
