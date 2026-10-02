package com.shyblack.cryptosignals.service.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shyblack.cryptosignals.dto.portfolio.PortfolioHistoryEntry;
import com.shyblack.cryptosignals.dto.portfolio.PortfolioHistoryResponse;
import com.shyblack.cryptosignals.dto.portfolio.PortfolioHoldingView;
import com.shyblack.cryptosignals.dto.portfolio.PortfolioHoldingsResponse;
import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountAvailability;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.exchange.ExchangeOrderSnapshot;
import com.shyblack.cryptosignals.exchange.ExchangeTradeSnapshot;
import com.shyblack.cryptosignals.exchange.futures.FuturesIncomeSnapshot;
import com.shyblack.cryptosignals.exchange.futures.FuturesOrderSnapshot;
import com.shyblack.cryptosignals.exchange.futures.FuturesTradeSnapshot;
import com.shyblack.cryptosignals.exchange.futures.mock.MockFuturesExchangeAdapter;
import com.shyblack.cryptosignals.exchange.mock.MockExchangeTradingAdapter;
import com.shyblack.cryptosignals.exception.BadRequestException;
import com.shyblack.cryptosignals.repository.ExchangeCredentialRepository;
import com.shyblack.cryptosignals.repository.PortfolioExchangeBalanceRepository;
import com.shyblack.cryptosignals.repository.UserRepository;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Phase 7 holdings and history behaviour.
 *
 * <p>Proves that wallet holdings and history are exchange-sourced, scope-isolated, bounded,
 * deterministically ordered, de-duplicated on a natural identity, and never mixed between modes or
 * between spot and futures.
 */
@SpringBootTest
@ActiveProfiles("test")
class PortfolioHistoryServiceTest {

	private static final AtomicInteger SEQ = new AtomicInteger();

	@Autowired
	private PortfolioHistoryService historyService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ExchangeCredentialRepository credentialRepository;

	@Autowired
	private PortfolioExchangeBalanceRepository balanceRepository;

	@Autowired
	private MockExchangeTradingAdapter spotAdapter;

	@Autowired
	private MockFuturesExchangeAdapter futuresAdapter;

	@Autowired
	private LivePortfolioSyncService syncService;

	@BeforeEach
	void resetAdapters() {
		spotAdapter.reset();
		futuresAdapter.reset();
	}

	private User liveUser() {
		User u = new User();
		u.setEmail("hist-" + SEQ.incrementAndGet() + "@example.com");
		u.setFullName("History Tester");
		u.setAccountType(AccountType.LIVE);
		return userRepository.saveAndFlush(u);
	}

	private User paperUser() {
		User u = new User();
		u.setEmail("hist-p-" + SEQ.incrementAndGet() + "@example.com");
		u.setFullName("Paper History Tester");
		u.setAccountType(AccountType.PAPER);
		return userRepository.saveAndFlush(u);
	}

	private void credential(User u) {
		ExchangeCredential c = new ExchangeCredential();
		c.setUser(u);
		c.setExchange(ExchangeName.BINANCE);
		c.setApiKey("dGVzdC1vbmx5LWtleQ==");
		c.setApiSecret("dGVzdC1vbmx5LXNlY3JldA==");
		credentialRepository.saveAndFlush(c);
	}

	private HistoryWindow window() {
		return HistoryWindow.resolve(Instant.now().minus(Duration.ofDays(3)), Instant.now(), 100);
	}

	private static ExchangeOrderSnapshot spotOrder(String symbol, long id, Instant time) {
		return new ExchangeOrderSnapshot(symbol, id, "c" + id, "BUY", "LIMIT", "FILLED",
				new BigDecimal("100"), new BigDecimal("2"), new BigDecimal("2"),
				new BigDecimal("200"), time, time);
	}

	// ------------------------------------------------------------ A. holdings

	@Test
	void spotHoldingsExposeEveryAssetWithFreeLockedAndTotal() {
		User u = liveUser();
		credential(u);
		spotAdapter.putBalance("USDT", new BigDecimal("100.5"), new BigDecimal("20.25"));
		spotAdapter.putBalance("BTC", new BigDecimal("0.1"), new BigDecimal("0.02"));
		syncService.syncSpot(u);

		PortfolioHoldingsResponse response =
				historyService.holdings(u, AccountMode.LIVE, AccountCategory.SPOT);

		assertThat(response.availability()).isEqualTo(AccountAvailability.AVAILABLE);
		assertThat(response.source()).isEqualTo("EXCHANGE");
		assertThat(response.holdings()).hasSize(2);
		assertThat(response.holdings().stream().map(h -> h.asset()))
				.containsExactly("BTC", "USDT");
		PortfolioHoldingView btc = response.holdings().get(0);
		assertThat(btc.free()).isEqualByComparingTo("0.1");
		assertThat(btc.locked()).isEqualByComparingTo("0.02");
		assertThat(btc.total()).isEqualByComparingTo("0.12");
	}

	@Test
	void holdingsCarryNoValuationBecauseNoApprovedPriceFeedExists() {
		User u = liveUser();
		credential(u);
		spotAdapter.putBalance("BTC", new BigDecimal("1"), BigDecimal.ZERO);
		syncService.syncSpot(u);

		PortfolioHoldingsResponse response =
				historyService.holdings(u, AccountMode.LIVE, AccountCategory.SPOT);

		// The rule is enforced by the shape: no quote-currency field exists, so a balance can never
		// be presented as a valued amount.
		assertThat(Arrays.stream(PortfolioHoldingView.class.getRecordComponents())
				.map(RecordComponent::getName))
				.containsExactlyInAnyOrder("asset", "free", "locked", "total");
		assertThat(response.holdings().get(0).total()).isEqualByComparingTo("1");
	}

	@Test
	void anUnsynchronisedSpotAccountReportsUnavailableNotEmpty() {
		User u = liveUser();
		credential(u);

		PortfolioHoldingsResponse response =
				historyService.holdings(u, AccountMode.LIVE, AccountCategory.SPOT);

		assertThat(response.availability()).isEqualTo(AccountAvailability.UNAVAILABLE);
		assertThat(response.holdings()).isEmpty();
		assertThat(response.statusMessage()).isNotBlank();
	}

	@Test
	void paperHasNoPerAssetWalletHoldings() {
		PortfolioHoldingsResponse response =
				historyService.holdings(paperUser(), AccountMode.PAPER, AccountCategory.SPOT);

		assertThat(response.availability()).isEqualTo(AccountAvailability.UNSUPPORTED);
		assertThat(response.holdings()).isEmpty();
	}

	@Test
	void futuresHasNoPerAssetWalletHoldings() {
		PortfolioHoldingsResponse response =
				historyService.holdings(liveUser(), AccountMode.LIVE, AccountCategory.FUTURES);

		assertThat(response.availability()).isEqualTo(AccountAvailability.UNSUPPORTED);
		assertThat(response.holdings()).isEmpty();
	}

	// --------------------------------------------------------- B. spot orders

	@Test
	void spotOrdersAreReadFromTheExchange() {
		User u = liveUser();
		credential(u);
		spotAdapter.putOrder(spotOrder("BTCUSDT", 1L, Instant.now().minusSeconds(600)));
		spotAdapter.putOrder(spotOrder("BTCUSDT", 2L, Instant.now().minusSeconds(300)));

		PortfolioHistoryResponse response = historyService.history(
				u, AccountMode.LIVE, AccountCategory.SPOT, "ORDER", window(), "BTCUSDT");

		assertThat(response.availability()).isEqualTo(AccountAvailability.AVAILABLE);
		assertThat(response.source()).isEqualTo("EXCHANGE");
		assertThat(response.entries()).hasSize(2);
		PortfolioHistoryEntry entry = response.entries().get(0);
		assertThat(entry.orderId()).isEqualTo(2L);
		assertThat(entry.side()).isEqualTo("BUY");
		assertThat(entry.status()).isEqualTo("FILLED");
		assertThat(entry.price()).isEqualByComparingTo("100");
	}

	@Test
	void historyIsOrderedNewestFirst() {
		User u = liveUser();
		credential(u);
		spotAdapter.putOrder(spotOrder("BTCUSDT", 1L, Instant.now().minusSeconds(900)));
		spotAdapter.putOrder(spotOrder("BTCUSDT", 3L, Instant.now().minusSeconds(100)));
		spotAdapter.putOrder(spotOrder("BTCUSDT", 2L, Instant.now().minusSeconds(500)));

		PortfolioHistoryResponse response = historyService.history(
				u, AccountMode.LIVE, AccountCategory.SPOT, "ORDER", window(), "BTCUSDT");

		assertThat(response.entries()).extracting(PortfolioHistoryEntry::orderId)
				.containsExactly(3L, 2L, 1L);
	}

	@Test
	void aRepeatedRecordIsNotDoubleCounted() {
		User u = liveUser();
		credential(u);
		ExchangeOrderSnapshot order = spotOrder("BTCUSDT", 7L, Instant.now().minusSeconds(300));
		// A paginated exchange query can repeat a boundary record.
		spotAdapter.putOrder(order);
		spotAdapter.putOrder(order);
		spotAdapter.putOrder(order);

		PortfolioHistoryResponse response = historyService.history(
				u, AccountMode.LIVE, AccountCategory.SPOT, "ORDER", window(), "BTCUSDT");

		assertThat(response.entries()).hasSize(1);
	}

	@Test
	void hittingTheRecordCapMarksTheWindowPartial() {
		User u = liveUser();
		credential(u);
		for (int i = 1; i <= 5; i++) {
			spotAdapter.putOrder(spotOrder("BTCUSDT", i, Instant.now().minusSeconds(600 - i)));
		}
		HistoryWindow capped = HistoryWindow.resolve(
				Instant.now().minus(Duration.ofDays(3)), Instant.now(), 3);

		PortfolioHistoryResponse response = historyService.history(
				u, AccountMode.LIVE, AccountCategory.SPOT, "ORDER", capped, "BTCUSDT");

		assertThat(response.complete())
				.as("a truncated window must never be presented as complete")
				.isFalse();
		assertThat(response.entries()).hasSize(3);
		assertThat(response.statusMessage()).contains("partial");
	}

	@Test
	void aCompleteWindowIsMarkedComplete() {
		User u = liveUser();
		credential(u);
		spotAdapter.putOrder(spotOrder("BTCUSDT", 1L, Instant.now().minusSeconds(300)));

		PortfolioHistoryResponse response = historyService.history(
				u, AccountMode.LIVE, AccountCategory.SPOT, "ORDER", window(), "BTCUSDT");

		assertThat(response.complete()).isTrue();
		assertThat(response.statusMessage()).isNull();
	}

	@Test
	void aMissingCredentialReportsNotConnectedRatherThanEmpty() {
		PortfolioHistoryResponse response = historyService.history(
				liveUser(), AccountMode.LIVE, AccountCategory.SPOT, "ORDER", window(), "BTCUSDT");

		assertThat(response.availability()).isEqualTo(AccountAvailability.NOT_CONNECTED);
		assertThat(response.entries()).isEmpty();
	}

	@Test
	void spotHistoryRequiresASymbolAndSaysSo() {
		User u = liveUser();
		credential(u);

		PortfolioHistoryResponse response = historyService.history(
				u, AccountMode.LIVE, AccountCategory.SPOT, "ORDER", window(), null);

		assertThat(response.availability()).isEqualTo(AccountAvailability.ERROR);
		assertThat(response.statusMessage()).contains("symbol");
	}

	// ----------------------------------------------------------- C. spot fills

	@Test
	void spotFillsCarryExchangeIdentityAndPreserveNulls() {
		User u = liveUser();
		credential(u);
		spotAdapter.putTrade(new ExchangeTradeSnapshot("BTCUSDT", 55L, 9L, "BUY",
				new BigDecimal("100"), new BigDecimal("2"), null,
				new BigDecimal("0.2"), "BNB", true, Instant.now().minusSeconds(200)));

		PortfolioHistoryResponse response = historyService.history(
				u, AccountMode.LIVE, AccountCategory.SPOT, "TRADE", window(), "BTCUSDT");

		assertThat(response.entries()).hasSize(1);
		PortfolioHistoryEntry fill = response.entries().get(0);
		assertThat(fill.entryType()).isEqualTo("TRADE");
		assertThat(fill.tradeId()).isEqualTo(55L);
		assertThat(fill.orderId()).isEqualTo(9L);
		assertThat(fill.fee()).isEqualByComparingTo("0.2");
		assertThat(fill.feeAsset()).isEqualTo("BNB");
		assertThat(fill.quoteQuantity())
				.as("the exchange omits a spot quote quantity, so it must stay null")
				.isNull();
		assertThat(fill.realizedPnl()).isNull();
	}

	@Test
	void repeatedFillsAreNotDoubleCounted() {
		User u = liveUser();
		credential(u);
		ExchangeTradeSnapshot trade = new ExchangeTradeSnapshot("BTCUSDT", 77L, 3L, "SELL",
				new BigDecimal("101"), new BigDecimal("1"), new BigDecimal("101"),
				new BigDecimal("0.01"), "USDT", false, Instant.now().minusSeconds(100));
		spotAdapter.putTrade(trade);
		spotAdapter.putTrade(trade);

		PortfolioHistoryResponse response = historyService.history(
				u, AccountMode.LIVE, AccountCategory.SPOT, "TRADE", window(), "BTCUSDT");

		assertThat(response.entries()).hasSize(1);
	}

	// ----------------------------------------------------- D/E. futures history

	@Test
	void futuresOrdersAreReadFromTheExchange() {
		User u = liveUser();
		credential(u);
		Instant t = Instant.now().minusSeconds(300);
		futuresAdapter.putOrder(new FuturesOrderSnapshot("BTCUSDT", 21L, "cf21", "BUY", "BOTH",
				"MARKET", "FILLED", false, null, new BigDecimal("1"), new BigDecimal("1"),
				new BigDecimal("60000"), t, t));

		PortfolioHistoryResponse response = historyService.history(
				u, AccountMode.LIVE, AccountCategory.FUTURES, "ORDER", window(), "BTCUSDT");

		assertThat(response.entries()).hasSize(1);
		assertThat(response.entries().get(0).orderId()).isEqualTo(21L);
		assertThat(response.entries().get(0).side()).isEqualTo("BUY");
	}

	@Test
	void futuresFillsCarryExchangeRealizedPnl() {
		User u = liveUser();
		credential(u);
		futuresAdapter.putTrade(new FuturesTradeSnapshot("ETHUSDT", 31L, 5L, "SELL", "SHORT",
				new BigDecimal("3000"), new BigDecimal("2"), new BigDecimal("6000"),
				new BigDecimal("2.4"), "USDT", new BigDecimal("120.5"), "USDT", true,
				Instant.now().minusSeconds(120)));

		PortfolioHistoryResponse response = historyService.history(
				u, AccountMode.LIVE, AccountCategory.FUTURES, "TRADE", window(), null);

		assertThat(response.entries()).hasSize(1);
		PortfolioHistoryEntry fill = response.entries().get(0);
		assertThat(fill.tradeId()).isEqualTo(31L);
		assertThat(fill.realizedPnl())
				.as("exchange-reported realized P&L is surfaced verbatim")
				.isEqualByComparingTo("120.5");
	}

	@Test
	void futuresIncomeRecordsAreReadForRealizedPnl() {
		User u = liveUser();
		credential(u);
		futuresAdapter.putIncome(new FuturesIncomeSnapshot("BTCUSDT", "REALIZED_PNL",
				new BigDecimal("55.25"), "USDT", 900L, Instant.now().minusSeconds(180)));

		PortfolioHistoryResponse response = historyService.history(
				u, AccountMode.LIVE, AccountCategory.FUTURES, "INCOME", window(), null);

		assertThat(response.entries()).hasSize(1);
		PortfolioHistoryEntry income = response.entries().get(0);
		assertThat(income.entryType()).isEqualTo("INCOME");
		assertThat(income.tradeId()).isEqualTo(900L);
		assertThat(income.status()).isEqualTo("REALIZED_PNL");
		assertThat(income.realizedPnl()).isEqualByComparingTo("55.25");
	}

	@Test
	void repeatedIncomeRecordsAreNotDoubleCounted() {
		User u = liveUser();
		credential(u);
		FuturesIncomeSnapshot income = new FuturesIncomeSnapshot("BTCUSDT", "REALIZED_PNL",
				new BigDecimal("10"), "USDT", 901L, Instant.now().minusSeconds(100));
		futuresAdapter.putIncome(income);
		futuresAdapter.putIncome(income);

		PortfolioHistoryResponse response = historyService.history(
				u, AccountMode.LIVE, AccountCategory.FUTURES, "INCOME", window(), null);

		assertThat(response.entries()).hasSize(1);
	}

	@Test
	void spotDoesNotPublishIncomeRecords() {
		User u = liveUser();
		credential(u);

		PortfolioHistoryResponse response = historyService.history(
				u, AccountMode.LIVE, AccountCategory.SPOT, "INCOME", window(), "BTCUSDT");

		assertThat(response.availability()).isEqualTo(AccountAvailability.UNSUPPORTED);
		assertThat(response.entries()).isEmpty();
	}

	// -------------------------------------------------- G. realized P&L policy

	@Test
	void accountRealizedPnlStaysNullBecauseLifetimeIsNotImplementableSafely() {
		User u = liveUser();
		credential(u);
		futuresAdapter.putIncome(new FuturesIncomeSnapshot("BTCUSDT", "REALIZED_PNL",
				new BigDecimal("500"), "USDT", 1L, Instant.now().minusSeconds(60)));
		syncService.syncFutures(u);

		var view = historyService.history(
				u, AccountMode.LIVE, AccountCategory.FUTURES, "INCOME", window(), null);

		assertThat(view.entries()).hasSize(1);
		// Lifetime realized P&L would require unbounded paging of a time-windowed endpoint, so it is
		// not claimed anywhere; the windowed records are available and labelled instead.
		assertThat(view.windowFrom()).isNotNull();
		assertThat(view.windowTo()).isNotNull();
	}

	// ---------------------------------------------------------- I/J. staleness

@Test
	void aLiveReadThroughIsFreshEvenWhenNoLocalSnapshotExists() {
		User u = liveUser();
		credential(u);
		spotAdapter.putOrder(spotOrder("BTCUSDT", 1L, Instant.now().minusSeconds(300)));

		// Deliberately no syncService call: there is no local snapshot at all.
		PortfolioHistoryResponse response = historyService.history(
				u, AccountMode.LIVE, AccountCategory.SPOT, "ORDER", window(), "BTCUSDT");

		// History is read from the exchange on demand, so it cannot inherit snapshot staleness and
		// cannot present a stale snapshot as current. Freshness of the *snapshot* scopes is reported
		// separately by the sync-status endpoint.
		assertThat(response.availability()).isEqualTo(AccountAvailability.AVAILABLE);
		assertThat(response.entries()).hasSize(1);
		assertThat(response.complete()).isTrue();
	}

	@Test
	void anEmptyWindowIsAvailableRatherThanUnavailable() {
		User u = liveUser();
		credential(u);

		PortfolioHistoryResponse response = historyService.history(
				u, AccountMode.LIVE, AccountCategory.SPOT, "ORDER", window(), "BTCUSDT");

		// The exchange answered; there was simply nothing in the window. That is not the same as an
		// unavailable account and must not be reported as one.
		assertThat(response.availability()).isEqualTo(AccountAvailability.AVAILABLE);
		assertThat(response.entries()).isEmpty();
		assertThat(response.statusMessage()).isNull();
	}

	// ------------------------------------------------------------ isolation

	@Test
	void paperHistoryNeverContainsExchangeRecords() {
		User paper = paperUser();
		credential(liveUser());
		spotAdapter.putOrder(spotOrder("BTCUSDT", 1L, Instant.now()));

		PortfolioHistoryResponse response = historyService.history(
				paper, AccountMode.PAPER, AccountCategory.SPOT, "ORDER", window(), "BTCUSDT");

		assertThat(response.source()).isEqualTo("LOCAL_PAPER");
		assertThat(response.entries()).isEmpty();
	}

	@Test
	void spotHistoryNeverReturnsFuturesRecords() {
		User u = liveUser();
		credential(u);
		futuresAdapter.putOrder(new FuturesOrderSnapshot("BTCUSDT", 77L, "x", "BUY", "BOTH",
				"MARKET", "FILLED", false, null, BigDecimal.ONE, BigDecimal.ONE,
				BigDecimal.TEN, Instant.now(), Instant.now()));

		PortfolioHistoryResponse response = historyService.history(
				u, AccountMode.LIVE, AccountCategory.SPOT, "ORDER", window(), "BTCUSDT");

		assertThat(response.entries())
				.as("a futures-only record must not leak into a spot request")
				.isEmpty();
	}

	@Test
	void futuresHistoryNeverReturnsSpotRecords() {
		User u = liveUser();
		credential(u);
		spotAdapter.putOrder(spotOrder("BTCUSDT", 5L, Instant.now()));

		PortfolioHistoryResponse response = historyService.history(
				u, AccountMode.LIVE, AccountCategory.FUTURES, "ORDER", window(), "BTCUSDT");

		assertThat(response.entries()).isEmpty();
	}

	@Test
	void mainHistoryIsUnavailableBecauseItWouldMixWallets() {
		User u = liveUser();
		credential(u);

		PortfolioHistoryResponse response = historyService.history(
				u, AccountMode.LIVE, AccountCategory.MAIN, "ORDER", window(), "BTCUSDT");

		assertThat(response.availability()).isEqualTo(AccountAvailability.UNAVAILABLE);
		assertThat(response.entries()).isEmpty();
	}

	// ------------------------------------------------------------- options

	@Test
	void optionsHistoryIsUnsupportedInBothModes() {
		PortfolioHistoryResponse live = historyService.history(
				liveUser(), AccountMode.LIVE, AccountCategory.OPTIONS, "ORDER", window(), "BTCUSDT");
		PortfolioHistoryResponse paper = historyService.history(
				paperUser(), AccountMode.PAPER, AccountCategory.OPTIONS, "TRADE", window(), "BTCUSDT");

		for (PortfolioHistoryResponse response : List.of(live, paper)) {
			assertThat(response.availability()).isEqualTo(AccountAvailability.UNSUPPORTED);
			assertThat(response.entries()).isEmpty();
		}
	}

	// ------------------------------------------------------------- window

	@Test
	void anOverlyWideWindowIsRejectedRatherThanAttempted() {
		Instant tooFar = Instant.now().minus(Duration.ofDays(200));

		assertThatThrownBy(() -> HistoryWindow.resolve(tooFar, Instant.now(), 100))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("days");
	}

	@Test
	void anExcessiveLimitIsRejected() {
		assertThatThrownBy(() -> HistoryWindow.resolve(
						Instant.now().minus(Duration.ofDays(1)), Instant.now(), 99_999))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("limit");
	}

	@Test
	void aNonPositiveLimitIsRejected() {
		assertThatThrownBy(() -> HistoryWindow.resolve(
						Instant.now().minus(Duration.ofDays(1)), Instant.now(), 0))
				.isInstanceOf(BadRequestException.class);
	}

	@Test
	void anInvertedWindowIsRejected() {
		assertThatThrownBy(() -> HistoryWindow.resolve(
						Instant.now(), Instant.now().minusSeconds(60), 10))
				.isInstanceOf(BadRequestException.class);
	}

	@Test
	void theDefaultWindowIsSevenDays() {
		HistoryWindow resolved = HistoryWindow.resolve(null, null, null);

		assertThat(Duration.between(resolved.from(), resolved.to()))
				.isGreaterThanOrEqualTo(Duration.ofDays(6))
				.isLessThanOrEqualTo(Duration.ofDays(7));
		assertThat(resolved.limit()).isEqualTo(HistoryWindow.DEFAULT_RECORDS);
	}
}
