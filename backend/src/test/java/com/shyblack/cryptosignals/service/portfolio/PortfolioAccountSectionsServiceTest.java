package com.shyblack.cryptosignals.service.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shyblack.cryptosignals.dto.portfolio.PortfolioClosedPositionView;
import com.shyblack.cryptosignals.dto.portfolio.PortfolioHistoryResponse;
import com.shyblack.cryptosignals.dto.portfolio.PortfolioOpenOrdersResponse;
import com.shyblack.cryptosignals.dto.portfolio.PortfolioOrderView;
import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountAvailability;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.PortfolioOrderStatus;
import com.shyblack.cryptosignals.exchange.ExchangeOrderSnapshot;
import com.shyblack.cryptosignals.exchange.futures.FuturesOrderSnapshot;
import com.shyblack.cryptosignals.exchange.futures.FuturesIncomeSnapshot;
import com.shyblack.cryptosignals.exchange.futures.mock.MockFuturesExchangeAdapter;
import com.shyblack.cryptosignals.exchange.mock.MockExchangeTradingAdapter;
import com.shyblack.cryptosignals.repository.ExchangeCredentialRepository;
import com.shyblack.cryptosignals.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * The Binance-style account sections added by the Portfolio redesign: open orders,
 * transaction history and funding fees.
 *
 * <p>The recurring property is scope isolation plus refusal to invent. Paper never
 * substitutes for live, spot never substitutes for futures, and an account mode without an
 * exchange capability reports that rather than showing an empty result.
 */
@SpringBootTest
@TestPropertySource(properties = {
		"app.live-trading.mode=MOCK",
		"app.futures-trading.mode=MOCK",
		"spring.task.scheduling.enabled=false"
})
class PortfolioAccountSectionsServiceTest {

	@Autowired
	private PortfolioHistoryService service;

	@Autowired
	private MockExchangeTradingAdapter spotAdapter;

	@Autowired
	private MockFuturesExchangeAdapter futuresAdapter;

	@Autowired
	private ExchangeCredentialRepository credentialRepository;

	@Autowired
	private UserRepository userRepository;

	private User user;
	private HistoryWindow window;
	private Instant windowStart;
	private Instant windowEnd;

	@BeforeEach
	void setUp() {
		spotAdapter.clearSeeded();
		futuresAdapter.clearSeeded();

		User candidate = new User();
		candidate.setEmail("sections-" + System.nanoTime() + "@example.test");
		candidate.setFullName("Portfolio Sections Test");
		candidate.setPasswordHash("hash");
		candidate.setAccountType(AccountType.LIVE);
		user = userRepository.save(candidate);

		ExchangeCredential credential = new ExchangeCredential();
		credential.setUser(user);
		credential.setExchange(ExchangeName.BINANCE);
		credential.setApiKey("enc:key");
		credential.setApiSecret("enc:secret");
		credentialRepository.save(credential);

		// The window's end is captured once and reused, because a record seeded with
		// Instant.now() taken afterwards falls outside the closed window and would be
		// filtered out by the adapter before the service ever sees it.
		this.windowEnd = Instant.now();
		this.windowStart = windowEnd.minusSeconds(86400);
		window = HistoryWindow.resolve(windowStart, windowEnd, 200);
	}

	/** A timestamp safely inside the resolved window. */
	private Instant inWindow() {
		return windowEnd.minusSeconds(60);
	}

	// ------------------------------------------------------- A. open orders

	@Nested
	@DisplayName("A. open orders answer what is resting right now")
	class OpenOrders {

		@Test
		@DisplayName("a live spot open order is reported with its real exchange state")
		void liveSpotOpenOrder() {
			spotAdapter.putOrder(new ExchangeOrderSnapshot(
					"BTCUSDT", 1L, "c1", "BUY", "STOP_LOSS_LIMIT", "NEW",
					new BigDecimal("60000"), new BigDecimal("59000"),
					new BigDecimal("0.5"), new BigDecimal("0"),
					new BigDecimal("0"), inWindow(), inWindow()));

			PortfolioOpenOrdersResponse response =
					service.openOrders(user, AccountMode.LIVE, AccountCategory.SPOT, null);

			assertThat(response.availability()).isEqualTo(AccountAvailability.AVAILABLE);
			assertThat(response.orders()).hasSize(1);

			PortfolioOrderView order = response.orders().get(0);
			assertThat(order.status()).isEqualTo(PortfolioOrderStatus.NEW);
			assertThat(order.orderType()).isEqualTo("STOP_LOSS_LIMIT");
			assertThat(order.stopPrice()).isEqualByComparingTo("59000");
			assertThat(order.originalQuantity()).isEqualByComparingTo("0.5");
			assertThat(order.remainingQuantity())
					.as("original minus executed, and both are known here")
					.isEqualByComparingTo("0.5");
			// Spot publishes no average fill price on its order endpoints, and spot has no
			// position side or reduce-only flag.
			assertThat(order.averageFillPrice()).isNull();
			assertThat(order.positionSide()).isNull();
			assertThat(order.reduceOnly()).isNull();
		}

		@Test
		@DisplayName("an exchange status this build does not recognise stays UNKNOWN")
		void unknownExchangeStatusStaysUnknown() {
			spotAdapter.putOrder(new ExchangeOrderSnapshot(
					"ETHUSDT", 2L, "c2", "BUY", "LIMIT", "SOMETHING_NEW",
					new BigDecimal("3000"), null,
					new BigDecimal("1"), new BigDecimal("0"),
					new BigDecimal("0"), inWindow(), inWindow()));

			PortfolioOrderView order = service
					.openOrders(user, AccountMode.LIVE, AccountCategory.SPOT, null)
					.orders().get(0);

			assertThat(order.status())
					.as("HTTP 200 does not mean FILLED, and an unmapped status must not be guessed")
					.isEqualTo(PortfolioOrderStatus.UNKNOWN);
			assertThat(order.status().isFilled()).isFalse();
			// The raw string is retained so the state is still diagnosable.
			assertThat(order.rawStatus()).isEqualTo("SOMETHING_NEW");
		}

		@Test
		@DisplayName("a futures open order keeps its position side, reduce-only flag and average fill price")
		void futuresOpenOrder() {
			futuresAdapter.putOrder(new FuturesOrderSnapshot(
					"BTCUSDT", 3L, "c3", "SELL", "LONG", "LIMIT", "PARTIALLY_FILLED",
					Boolean.TRUE, new BigDecimal("62000"), null, new BigDecimal("61500"),
					new BigDecimal("0.4"), new BigDecimal("0.1"), new BigDecimal("6150"),
					inWindow(), inWindow()));

			PortfolioOrderView order = service
					.openOrders(user, AccountMode.LIVE, AccountCategory.FUTURES, null)
					.orders().get(0);

			assertThat(order.status()).isEqualTo(PortfolioOrderStatus.PARTIALLY_FILLED);
			assertThat(order.status().isFilled()).isFalse();
			assertThat(order.status().isOpen())
					.as("a partly filled order is still live on the book")
					.isTrue();
			assertThat(order.positionSide()).isEqualTo("LONG");
			assertThat(order.reduceOnly()).isTrue();
			assertThat(order.averageFillPrice()).isEqualByComparingTo("61500");
			assertThat(order.remainingQuantity()).isEqualByComparingTo("0.3");
		}

		@Test
		@DisplayName("paper has no order book, and says so instead of reporting no open orders")
		void paperHasNoOrderBook() {
			PortfolioOpenOrdersResponse response =
					service.openOrders(user, AccountMode.PAPER, AccountCategory.FUTURES, null);

			assertThat(response.availability()).isEqualTo(AccountAvailability.UNSUPPORTED);
			assertThat(response.orders()).isEmpty();
			assertThat(response.statusMessage()).contains("works no order book");
		}

		@Test
		@DisplayName("spot and futures never show each other's orders")
		void spotAndFuturesAreIsolated() {
			spotAdapter.putOrder(new ExchangeOrderSnapshot(
					"BTCUSDT", 1L, "c1", "BUY", "LIMIT", "NEW",
					new BigDecimal("1"), null, new BigDecimal("1"),
					BigDecimal.ZERO, BigDecimal.ZERO, inWindow(), inWindow()));
			futuresAdapter.putOrder(new FuturesOrderSnapshot(
					"ETHUSDT", 2L, "c2", "BUY", "BOTH", "LIMIT", "NEW",
					null, new BigDecimal("1"), null, null,
					new BigDecimal("1"), BigDecimal.ZERO, BigDecimal.ZERO,
					inWindow(), inWindow()));

			PortfolioOpenOrdersResponse spot =
					service.openOrders(user, AccountMode.LIVE, AccountCategory.SPOT, null);
			PortfolioOpenOrdersResponse futures =
					service.openOrders(user, AccountMode.LIVE, AccountCategory.FUTURES, null);

			assertThat(spot.orders()).extracting(PortfolioOrderView::symbol).containsOnly("BTCUSDT");
			assertThat(futures.orders()).extracting(PortfolioOrderView::symbol).containsOnly("ETHUSDT");
		}

		@Test
		@DisplayName("options exposes no orders")
		void optionsExposesNoOrders() {
			PortfolioOpenOrdersResponse response =
					service.openOrders(user, AccountMode.LIVE, AccountCategory.OPTIONS, null);

			assertThat(response.availability()).isEqualTo(AccountAvailability.UNSUPPORTED);
			assertThat(response.orders()).isEmpty();
		}

		@Test
		@DisplayName("MAIN refuses to serve, because it would merge two order books")
		void mainRefusesToMerge() {
			PortfolioOpenOrdersResponse response =
					service.openOrders(user, AccountMode.LIVE, AccountCategory.MAIN, null);

			assertThat(response.orders()).isEmpty();
			assertThat(response.statusMessage()).contains("would mix the spot and futures books");
		}

		@Test
		@DisplayName("no credential reports NOT_CONNECTED and substitutes no simulated orders")
		void missingCredentialNeverFallsBackToPaper() {
			credentialRepository.deleteAll();

			PortfolioOpenOrdersResponse response =
					service.openOrders(user, AccountMode.LIVE, AccountCategory.FUTURES, null);

			assertThat(response.availability()).isEqualTo(AccountAvailability.NOT_CONNECTED);
			assertThat(response.orders()).isEmpty();
			assertThat(response.statusMessage()).contains("never substituted");
		}
	}

	// ------------------------------------------------ B. transaction history

	@Nested
	@DisplayName("B. transaction history preserves the exchange's own income type")
	class Transactions {

		@Test
		@DisplayName("every income type the exchange publishes is reported as itself")
		void everyIncomeTypeIsPreserved() {
			Instant now = inWindow();
			futuresAdapter.putIncome(new FuturesIncomeSnapshot(
					"BTCUSDT", "REALIZED_PNL", new BigDecimal("100"), "USDT", 1L, now));
			futuresAdapter.putIncome(new FuturesIncomeSnapshot(
					"BTCUSDT", "FUNDING_FEE", new BigDecimal("-3"), "USDT", 2L, now));
			futuresAdapter.putIncome(new FuturesIncomeSnapshot(
					"BTCUSDT", "COMMISSION", new BigDecimal("-1.5"), "USDT", 3L, now));
			futuresAdapter.putIncome(new FuturesIncomeSnapshot(
					"BTCUSDT", "TRANSFER", new BigDecimal("50"), "USDT", 4L, now));

			PortfolioHistoryResponse response = service.transactions(
					user, AccountMode.LIVE, AccountCategory.FUTURES, window, null);

			assertThat(response.availability()).isEqualTo(AccountAvailability.AVAILABLE);
			assertThat(response.entries())
					.as("an income type this build has never seen must still appear as itself")
					.extracting(entry -> entry.status())
					.containsExactlyInAnyOrder(
							"REALIZED_PNL", "FUNDING_FEE", "COMMISSION", "TRANSFER");
		}

		@Test
		@DisplayName("income types are not summed together or relabelled")
		void incomeTypesAreNotMerged() {
			Instant now = inWindow();
			futuresAdapter.putIncome(new FuturesIncomeSnapshot(
					"BTCUSDT", "REALIZED_PNL", new BigDecimal("100"), "USDT", 1L, now));
			futuresAdapter.putIncome(new FuturesIncomeSnapshot(
					"BTCUSDT", "FUNDING_FEE", new BigDecimal("-3"), "USDT", 2L, now));

			PortfolioHistoryResponse response = service.transactions(
					user, AccountMode.LIVE, AccountCategory.FUTURES, window, null);

			// Each record keeps its own amount. Funding is never folded into realized P&L.
			assertThat(response.entries()).hasSize(2);
			assertThat(response.entries()).extracting(entry -> entry.status())
					.contains("REALIZED_PNL", "FUNDING_FEE");
		}

		@Test
		@DisplayName("spot reports that no income endpoint exists rather than an empty ledger")
		void spotHasNoIncomeEndpoint() {
			PortfolioHistoryResponse response = service.transactions(
					user, AccountMode.LIVE, AccountCategory.SPOT, window, null);

			assertThat(response.availability()).isEqualTo(AccountAvailability.UNSUPPORTED);
			assertThat(response.entries()).isEmpty();
			assertThat(response.statusMessage())
					.as("the reason must be explicit, not an empty result")
					.contains("Not available");
		}

		@Test
		@DisplayName("paper reports that a simulated account is charged no funding and publishes no income")
		void paperPublishesNoIncome() {
			PortfolioHistoryResponse response = service.transactions(
					user, AccountMode.PAPER, AccountCategory.FUTURES, window, null);

			assertThat(response.availability()).isEqualTo(AccountAvailability.UNSUPPORTED);
			assertThat(response.entries()).isEmpty();
			assertThat(response.source()).isEqualTo("LOCAL_PAPER");
		}

		@Test
		@DisplayName("options exposes no transaction records")
		void optionsExposesNoTransactions() {
			PortfolioHistoryResponse response = service.transactions(
					user, AccountMode.LIVE, AccountCategory.OPTIONS, window, null);

			assertThat(response.availability()).isEqualTo(AccountAvailability.UNSUPPORTED);
		}
	}

	// ------------------------------------------------------- C. funding fees

	@Nested
	@DisplayName("C. funding fees are their own income type")
	class Funding {

		@Test
		@DisplayName("only funding-fee records are returned")
		void onlyFundingRecords() {
			Instant now = inWindow();
			futuresAdapter.putIncome(new FuturesIncomeSnapshot(
					"BTCUSDT", "REALIZED_PNL", new BigDecimal("100"), "USDT", 1L, now));
			futuresAdapter.putIncome(new FuturesIncomeSnapshot(
					"BTCUSDT", "FUNDING_FEE", new BigDecimal("-3"), "USDT", 2L, now));

			PortfolioHistoryResponse response =
					service.fundingFees(user, AccountMode.LIVE, AccountCategory.FUTURES, window, null);

			assertThat(response.entries()).hasSize(1);
			assertThat(response.entries().get(0).status()).isEqualTo("FUNDING_FEE");
			assertThat(response.entries().get(0).realizedPnl())
					.as("the funding amount is reported, not a realized-P&L figure")
					.isEqualByComparingTo("-3");
		}

		@Test
		@DisplayName("spot has no funding, because spot is never margined")
		void spotHasNoFunding() {
			PortfolioHistoryResponse response =
					service.fundingFees(user, AccountMode.LIVE, AccountCategory.SPOT, window, null);

			assertThat(response.availability()).isEqualTo(AccountAvailability.UNSUPPORTED);
			assertThat(response.entries()).isEmpty();
			assertThat(response.statusMessage())
					.as("spot is never margined, so the reason is stated explicitly")
					.contains("Not available")
					.contains("no funding fee");
		}
	}

	// ----------------------------------------------------------- D. filters

	@Nested
	@DisplayName("D. narrowing is validated and never widens an unknown state")
	class Filters {

		@Test
		@DisplayName("an unrecognised filter value is rejected rather than silently ignored")
		void unknownFilterValueIsRejected() {
			assertThatThrownBy(() -> com.shyblack.cryptosignals.dto.portfolio.PortfolioHistoryFilter
					.of(null, null, null, "MADE_UP", null))
					.isInstanceOf(com.shyblack.cryptosignals.exception.BadRequestException.class);
			assertThatThrownBy(() -> com.shyblack.cryptosignals.dto.portfolio.PortfolioHistoryFilter
					.of(null, "SIDEWAYS", null, null, null))
					.isInstanceOf(com.shyblack.cryptosignals.exception.BadRequestException.class);
		}

		@Test
		@DisplayName("a blank filter value means no narrowing, not a filter matching nothing")
		void blankFilterMeansNoNarrowing() {
			assertThat(com.shyblack.cryptosignals.dto.portfolio.PortfolioHistoryFilter
					.of("", "  ", null, null, null).hasNarrowing())
					.isFalse();
		}

		@Test
		@DisplayName("filtering by UNKNOWN keeps undetermined orders matchable as UNKNOWN")
		void unknownStatusRemainsMatchable() {
			// Both orders are on the same symbol because the exchange requires a symbol for spot
			// order history, and the statuses are what distinguishes them.
			spotAdapter.putOrder(new ExchangeOrderSnapshot(
					"BTCUSDT", 1L, "c1", "BUY", "LIMIT", "NEW",
					new BigDecimal("1"), null, new BigDecimal("1"),
					BigDecimal.ZERO, BigDecimal.ZERO, inWindow(), inWindow()));
			spotAdapter.putOrder(new ExchangeOrderSnapshot(
					"BTCUSDT", 2L, "c2", "SELL", "LIMIT", "SOMETHING_NEW",
					new BigDecimal("1"), null, new BigDecimal("1"),
					BigDecimal.ZERO, BigDecimal.ZERO, inWindow(), inWindow()));

			PortfolioHistoryResponse response = service.history(
					user, AccountMode.LIVE, AccountCategory.SPOT, "ORDER", window, "BTCUSDT",
					null,
					com.shyblack.cryptosignals.dto.portfolio.PortfolioHistoryFilter.of(
							null, null, null, "UNKNOWN", null));

			assertThat(response.availability()).isEqualTo(AccountAvailability.AVAILABLE);
			assertThat(response.entries())
					.as("an undetermined status must not be widened into one of the known states")
					.extracting(entry -> entry.orderId())
					.containsExactly(2L);
		}
	}
}