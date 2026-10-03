package com.shyblack.cryptosignals.service.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shyblack.cryptosignals.dto.portfolio.PortfolioOrderView;
import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.LiveOrder;
import com.shyblack.cryptosignals.entity.FuturesOrder;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderStatus;
import com.shyblack.cryptosignals.entity.enums.LiveOrderStatus;
import com.shyblack.cryptosignals.exchange.ExchangeOrderSnapshot;
import com.shyblack.cryptosignals.exchange.ExchangeTradingAdapter;
import com.shyblack.cryptosignals.exchange.futures.FuturesExchangeAdapter;
import com.shyblack.cryptosignals.repository.ExchangeCredentialRepository;
import com.shyblack.cryptosignals.repository.FuturesOrderRepository;
import com.shyblack.cryptosignals.repository.LiveOrderRepository;
import com.shyblack.cryptosignals.repository.PortfolioExchangeBalanceRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Which open orders can actually be cancelled from the Portfolio screen.
 *
 * <p>The exchange owns the truth about which orders rest, but the existing cancel
 * services address a locally persisted {@code LiveOrder} / {@code FuturesOrder} and
 * then resolve the exchange order by client order id. Publishing the exchange's own
 * order id here would therefore hand the UI an identifier that is rejected when
 * used, so {@code cancelId} carries the local key — and is null whenever no
 * addressable local record exists.
 *
 * <p>A cancel control is derived from that, so "no broken buttons" holds by
 * construction: an order we never placed, or one already terminal, advertises no
 * cancel at all.
 */
class PortfolioOrderCancelIdTest {

	private final ExchangeTradingAdapter spot = mock(ExchangeTradingAdapter.class);
	private final FuturesExchangeAdapter futures = mock(FuturesExchangeAdapter.class);
	private final ExchangeCredentialRepository credentials =
			mock(ExchangeCredentialRepository.class);
	private final PortfolioExchangeBalanceRepository balances =
			mock(PortfolioExchangeBalanceRepository.class);
	private final LiveUserStreamEventProcessor events =
			mock(LiveUserStreamEventProcessor.class);
	private final LiveOrderRepository liveOrders = mock(LiveOrderRepository.class);
	private final FuturesOrderRepository futuresOrders = mock(FuturesOrderRepository.class);

	private PortfolioHistoryService service;
	private User user;

	@BeforeEach
	void setUp() {
		service = new PortfolioHistoryService(
				credentials, balances, spot, futures, events, liveOrders, futuresOrders);
		user = new User();
		user.setId(UUID.randomUUID());
		ExchangeCredential credential = new ExchangeCredential();
		credential.setId(UUID.randomUUID());
		credential.setUser(user);
		credential.setExchange(ExchangeName.BINANCE);
		when(credentials.findByUser_IdAndExchange(user.getId(), ExchangeName.BINANCE))
				.thenReturn(Optional.of(credential));
		when(liveOrders.findByAccount_UserOrderByCreatedAtDesc(user)).thenReturn(List.of());
		when(futuresOrders.findByAccount_UserOrderByCreatedAtDesc(user)).thenReturn(List.of());
	}

	private static ExchangeOrderSnapshot spotOrder(String clientOrderId, String status) {
		Instant now = Instant.parse("2024-01-01T00:00:00Z");
		return new ExchangeOrderSnapshot("BTCUSDT", 900L, clientOrderId, "BUY", "LIMIT",
				status, new BigDecimal("100"), null, new BigDecimal("1"),
				new BigDecimal("0.4"), new BigDecimal("40"), now, now);
	}

	private List<PortfolioOrderView> spotOrders() {
		return service.openOrders(
				user, AccountMode.LIVE, AccountCategory.SPOT, null).orders();
	}

	@Test
	void anOrderWePlacedCarriesTheLocalKey() {
		LiveOrder local = new LiveOrder();
		local.setId(UUID.randomUUID());
		local.setClientOrderId("ours-1");
		local.setStatus(LiveOrderStatus.SUBMITTED);
		when(liveOrders.findByAccount_UserOrderByCreatedAtDesc(user))
				.thenReturn(List.of(local));
		when(spot.getOpenOrders(any(), isNull())).thenReturn(List.of(spotOrder("ours-1", "NEW")));

		PortfolioOrderView view = only(spotOrders());

		assertThat(view.cancelId()).isEqualTo(local.getId().toString());
		assertThat(view.cancellable()).isTrue();
	}

	@Test
	void anOrderPlacedOutsideTheApplicationCarriesNoKey() {
		when(spot.getOpenOrders(any(), isNull()))
				.thenReturn(List.of(spotOrder("foreign-9", "NEW")));

		PortfolioOrderView view = only(spotOrders());

		assertThat(view.cancelId())
				.as("there is no local record the cancel service could address")
				.isNull();
		assertThat(view.cancellable()).isFalse();
	}

	@Test
	void anAlreadyCancelledLocalOrderStopsAdvertisingCancel() {
		LiveOrder local = new LiveOrder();
		local.setId(UUID.randomUUID());
		local.setClientOrderId("ours-2");
		local.setStatus(LiveOrderStatus.CANCELLED);
		when(liveOrders.findByAccount_UserOrderByCreatedAtDesc(user))
				.thenReturn(List.of(local));
		when(spot.getOpenOrders(any(), isNull())).thenReturn(List.of(spotOrder("ours-2", "NEW")));

		assertThat(only(spotOrders()).cancelId())
				.as("a cancel already in flight must not offer a second one")
				.isNull();
	}

	@Test
	void aCancelInFlightIsNotOfferedAgain() {
		LiveOrder local = new LiveOrder();
		local.setId(UUID.randomUUID());
		local.setClientOrderId("ours-3");
		local.setStatus(LiveOrderStatus.CANCEL_REQUESTED);
		when(liveOrders.findByAccount_UserOrderByCreatedAtDesc(user))
				.thenReturn(List.of(local));
		when(spot.getOpenOrders(any(), isNull()))
				.thenReturn(List.of(spotOrder("ours-3", "NEW")));

		assertThat(only(spotOrders()).cancellable())
				.as("double submit must not be possible")
				.isFalse();
	}

	@Test
	void aPartiallyFilledOrderWePlacedIsStillCancellable() {
		LiveOrder local = new LiveOrder();
		local.setId(UUID.randomUUID());
		local.setClientOrderId("ours-4");
		local.setStatus(LiveOrderStatus.PARTIALLY_FILLED);
		when(liveOrders.findByAccount_UserOrderByCreatedAtDesc(user))
				.thenReturn(List.of(local));
		when(spot.getOpenOrders(any(), isNull()))
				.thenReturn(List.of(spotOrder("ours-4", "PARTIALLY_FILLED")));

		PortfolioOrderView view = only(spotOrders());
		assertThat(view.cancelId()).isNotNull();
		assertThat(view.cancellable()).isTrue();
		assertThat(view.remainingQuantity())
				.as("remaining is original minus executed")
				.isEqualByComparingTo("0.6");
	}

	@Test
	void theExchangeIdentifiersRemainAvailableForDisplay() {
		when(spot.getOpenOrders(any(), isNull()))
				.thenReturn(List.of(spotOrder("foreign-10", "NEW")));

		PortfolioOrderView view = only(spotOrders());

		assertThat(view.orderId()).isEqualTo(900L);
		assertThat(view.clientOrderId()).isEqualTo("foreign-10");
	}

	@Test
	void paperAccountsNeverAdvertiseACancel() {
		// The paper engine opens and closes a position directly and works no order
		// book, so there is nothing to cancel and no identifier to invent.
		var response = service.openOrders(
				user, AccountMode.PAPER, AccountCategory.SPOT, null);

		assertThat(response.orders()).isEmpty();
		assertThat(response.availability())
				.isEqualTo(com.shyblack.cryptosignals.entity.enums.AccountAvailability.UNSUPPORTED);
	}

	@Test
	void futuresResolvesItsOwnLocalKey() {
		FuturesOrder local = new FuturesOrder();
		local.setId(UUID.randomUUID());
		local.setClientOrderId("f-ours-1");
		local.setStatus(FuturesOrderStatus.SUBMITTED);
		when(futuresOrders.findByAccount_UserOrderByCreatedAtDesc(user))
				.thenReturn(List.of(local));

		Instant now = Instant.parse("2024-01-01T00:00:00Z");
		var snapshot = new com.shyblack.cryptosignals.exchange.futures.FuturesOrderSnapshot(
				"BTCUSDT", 901L, "f-ours-1", "BUY", "BOTH", "LIMIT", "NEW",
				Boolean.TRUE,
				new BigDecimal("100"), null, null, new BigDecimal("1"),
				new BigDecimal("0"), new BigDecimal("100"),
				now, now);
		when(futures.getOpenOrders(any(), isNull())).thenReturn(List.of(snapshot));

		var orders = service.openOrders(
				user, AccountMode.LIVE, AccountCategory.FUTURES, null).orders();

		assertThat(orders).hasSize(1);
		assertThat(orders.get(0).cancelId()).isEqualTo(local.getId().toString());
		assertThat(orders.get(0).cancellable()).isTrue();
	}

	@Test
	void anotherUsersOrderIsNeverAddressable() {
		// Only the caller's own orders are considered, so a stale UI holding a
		// foreign client order id resolves to nothing.
		LiveOrder mine = new LiveOrder();
		mine.setId(UUID.randomUUID());
		mine.setClientOrderId("ours-5");
		mine.setStatus(LiveOrderStatus.SUBMITTED);
		when(liveOrders.findByAccount_UserOrderByCreatedAtDesc(user))
				.thenReturn(List.of(mine));
		when(spot.getOpenOrders(any(), isNull()))
				.thenReturn(List.of(spotOrder("someone-else", "NEW")));

		assertThat(only(spotOrders()).cancelId()).isNull();
	}

	private static PortfolioOrderView only(List<PortfolioOrderView> orders) {
		assertThat(orders).hasSize(1);
		return orders.get(0);
	}
}
