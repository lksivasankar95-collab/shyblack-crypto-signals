package com.shyblack.cryptosignals.service.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.LiveOrder;
import com.shyblack.cryptosignals.entity.LiveTradingAccount;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.LiveOrderPurpose;
import com.shyblack.cryptosignals.entity.enums.LiveOrderStatus;
import com.shyblack.cryptosignals.entity.enums.LiveOrderType;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.exception.BadRequestException;
import com.shyblack.cryptosignals.exception.ResourceNotFoundException;
import com.shyblack.cryptosignals.exchange.ClientOrderIdGenerator;
import com.shyblack.cryptosignals.exchange.ExchangeAccountSnapshot;
import com.shyblack.cryptosignals.exchange.ExchangeOrderResult;
import com.shyblack.cryptosignals.exchange.ExchangeTradingAdapter;
import com.shyblack.cryptosignals.exchange.PlaceOrderRequest;
import com.shyblack.cryptosignals.exchange.SymbolRules;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.repository.LiveOrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LiveTradingCloseServiceTest {

	private LiveOrderRepository liveOrderRepository;
	private LiveTradingExecutionService executionService;
	private ExchangeTradingAdapter adapter;
	private ClientOrderIdGenerator idGen;
	private LiveTradingCloseService service;

	private User user;
	private LiveTradingAccount account;

	@BeforeEach
	void setUp() {
		liveOrderRepository = mock(LiveOrderRepository.class);
		executionService = mock(LiveTradingExecutionService.class);
		adapter = new StubAdapter();
		idGen = new ClientOrderIdGenerator();

		service = new LiveTradingCloseService(liveOrderRepository, executionService, adapter, idGen);

		user = new User();
		user.setId(UUID.randomUUID());

		account = new LiveTradingAccount();
		account.setId(UUID.randomUUID());
		account.setUser(user);
		account.setCredential(new ExchangeCredential());
	}

	@Test
	void closesFilledEntry_placingSpotSellForExecutedQuantity() {
		LiveOrder entry = filledEntry(new BigDecimal("0.01"));
		when(liveOrderRepository.findById(entry.getId())).thenReturn(Optional.of(entry));
		when(liveOrderRepository.findByAccountAndClientOrderId(any(), anyString()))
				.thenReturn(Optional.empty());
		when(liveOrderRepository.saveAndFlush(any(LiveOrder.class)))
				.thenAnswer(inv -> {
					LiveOrder o = inv.getArgument(0);
					o.setId(UUID.randomUUID());
					return o;
				});
		when(liveOrderRepository.findByAccountAndStatusInOrderByCreatedAtDesc(any(), any()))
				.thenReturn(List.of());
		when(executionService.applyResult(any(), any())).thenAnswer(inv -> {
			LiveOrder result = new LiveOrder();
			result.setId(inv.getArgument(0));
			result.setStatus(LiveOrderStatus.FILLED);
			return result;
		});

		LiveOrder closed = service.closeEntry(user, entry.getId());

		assertThat(closed.getStatus()).isEqualTo(LiveOrderStatus.FILLED);
		verify(executionService, times(1)).applyResult(any(), any());
	}

	@Test
	void cancelsSiblingStopLoss_beforeSubmittingClose() {
		LiveOrder entry = filledEntry(new BigDecimal("0.01"));
		when(liveOrderRepository.findById(entry.getId())).thenReturn(Optional.of(entry));
		when(liveOrderRepository.findByAccountAndClientOrderId(any(), anyString()))
				.thenReturn(Optional.empty());
		when(liveOrderRepository.saveAndFlush(any(LiveOrder.class)))
				.thenAnswer(inv -> {
					LiveOrder o = inv.getArgument(0);
					o.setId(UUID.randomUUID());
					return o;
				});
		LiveOrder sl = new LiveOrder();
		sl.setId(UUID.randomUUID());
		sl.setSymbol("BTCUSDT");
		sl.setPurpose(LiveOrderPurpose.STOP_LOSS);
		sl.setParentOrderId(entry.getId());
		sl.setStatus(LiveOrderStatus.ACKNOWLEDGED);
		when(liveOrderRepository.findByAccountAndStatusInOrderByCreatedAtDesc(any(), any()))
				.thenReturn(List.of(sl));
		when(executionService.applyResult(any(), any())).thenReturn(new LiveOrder());

		service.closeEntry(user, entry.getId());

		verify(executionService).cancel(sl.getId(), "manual close — parent " + entry.getId());
	}

	@Test
	void rejects_whenOrderBelongsToDifferentUser() {
		User otherUser = new User();
		otherUser.setId(UUID.randomUUID());
		LiveOrder entry = filledEntry(new BigDecimal("0.01"));
		entry.getAccount().setUser(otherUser); // owned by different user

		when(liveOrderRepository.findById(entry.getId())).thenReturn(Optional.of(entry));

		assertThatThrownBy(() -> service.closeEntry(user, entry.getId()))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void rejects_whenOrderIsNotAnEntry() {
		LiveOrder sl = new LiveOrder();
		sl.setId(UUID.randomUUID());
		sl.setAccount(account);
		sl.setPurpose(LiveOrderPurpose.STOP_LOSS);
		sl.setStatus(LiveOrderStatus.ACKNOWLEDGED);
		when(liveOrderRepository.findById(sl.getId())).thenReturn(Optional.of(sl));

		assertThatThrownBy(() -> service.closeEntry(user, sl.getId()))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("ENTRY");
	}

	@Test
	void rejects_whenEntryIsNotFilled() {
		LiveOrder pending = new LiveOrder();
		pending.setId(UUID.randomUUID());
		pending.setAccount(account);
		pending.setPurpose(LiveOrderPurpose.ENTRY);
		pending.setStatus(LiveOrderStatus.SUBMITTED);
		pending.setSymbol("BTCUSDT");
		when(liveOrderRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

		assertThatThrownBy(() -> service.closeEntry(user, pending.getId()))
				.isInstanceOf(BadRequestException.class);
	}

	@Test
	void idempotent_returnsExistingCloseIfAlreadyPlaced() {
		LiveOrder entry = filledEntry(new BigDecimal("0.01"));
		when(liveOrderRepository.findById(entry.getId())).thenReturn(Optional.of(entry));
		LiveOrder existingClose = new LiveOrder();
		existingClose.setId(UUID.randomUUID());
		existingClose.setStatus(LiveOrderStatus.FILLED);
		when(liveOrderRepository.findByAccountAndClientOrderId(any(), anyString()))
				.thenReturn(Optional.of(existingClose));
		when(liveOrderRepository.findByAccountAndStatusInOrderByCreatedAtDesc(any(), any()))
				.thenReturn(List.of());

		LiveOrder closed = service.closeEntry(user, entry.getId());

		assertThat(closed).isSameAs(existingClose);
	}

	private LiveOrder filledEntry(BigDecimal executed) {
		LiveOrder entry = new LiveOrder();
		entry.setId(UUID.randomUUID());
		entry.setAccount(account);
		entry.setSignalId(UUID.randomUUID());
		entry.setSymbol("BTCUSDT");
		entry.setSide(PositionSide.LONG);
		entry.setType(LiveOrderType.MARKET);
		entry.setPurpose(LiveOrderPurpose.ENTRY);
		entry.setStatus(LiveOrderStatus.FILLED);
		entry.setRequestedQuantity(executed);
		entry.setExecutedQuantity(executed);
		entry.setAvgFillPrice(new BigDecimal("50000"));
		return entry;
	}

	// Minimal adapter stub that returns fixed rules + a FILLED result.
	static class StubAdapter implements ExchangeTradingAdapter {
		@Override public ExchangeName exchange() { return ExchangeName.BINANCE; }

		@Override public ExchangeAccountSnapshot validateCredentials(ExchangeCredential c) {
			return new ExchangeAccountSnapshot("USDT", BigDecimal.ZERO, BigDecimal.ZERO, true, Instant.now());
		}

		@Override public ExchangeAccountSnapshot getAccountBalance(ExchangeCredential c) {
			return validateCredentials(c);
		}

		@Override public SymbolRules getSymbolRules(String symbol) {
			return new SymbolRules(symbol,
					new BigDecimal("0.00001"), new BigDecimal("1000"),
					new BigDecimal("0.00001"),
					new BigDecimal("0.01"), new BigDecimal("1000000"),
					new BigDecimal("0.01"),
					new BigDecimal("10"));
		}

		@Override public ExchangeOrderResult placeOrder(ExchangeCredential c, PlaceOrderRequest req) {
			return new ExchangeOrderResult(
					"EX-" + UUID.randomUUID().toString().substring(0, 8),
					req.clientOrderId(),
					req.symbol(),
					LiveOrderStatus.FILLED,
					req.quantity(),
					req.quantity(),
					req.quantity().multiply(req.price()),
					req.price(),
					BigDecimal.ZERO,
					"USDT",
					Instant.now(),
					"stub");
		}

		@Override public ExchangeOrderResult getOrder(ExchangeCredential c, String s, String id) { return null; }
		@Override public ExchangeOrderResult cancelOrder(ExchangeCredential c, String s, String id) { return null; }
	}
}
