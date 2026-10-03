package com.shyblack.cryptosignals.service.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shyblack.cryptosignals.dto.portfolio.PortfolioOrderView;
import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.PortfolioOrderStatus;
import com.shyblack.cryptosignals.exchange.ExchangeOrderSnapshot;
import com.shyblack.cryptosignals.exchange.ExchangeTradingAdapter;
import com.shyblack.cryptosignals.exchange.futures.FuturesExchangeAdapter;
import com.shyblack.cryptosignals.repository.ExchangeCredentialRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The {@code side} and {@code status} query parameters on
 * {@code GET /api/v1/portfolio/{category}/open-orders} were declared on the
 * controller but never forwarded to the service, so a client filtering by
 * {@code side=BUY} silently received every order. These pin the narrowing.
 */
class PortfolioOpenOrdersFilterTest {

	private final ExchangeTradingAdapter spot = mock(ExchangeTradingAdapter.class);
	private final FuturesExchangeAdapter futures = mock(FuturesExchangeAdapter.class);
	private final ExchangeCredentialRepository credentials =
			mock(ExchangeCredentialRepository.class);
	private final com.shyblack.cryptosignals.repository.PortfolioExchangeBalanceRepository balances =
			mock(com.shyblack.cryptosignals.repository.PortfolioExchangeBalanceRepository.class);
	private final LiveUserStreamEventProcessor eventProcessor =
			mock(LiveUserStreamEventProcessor.class);

	private PortfolioHistoryService service;
	private User user;
	private ExchangeCredential credential;

	@BeforeEach
	void setUp() {
		service = new PortfolioHistoryService(
				credentials, balances, spot, futures, eventProcessor,
				mock(com.shyblack.cryptosignals.repository.LiveOrderRepository.class),
				mock(com.shyblack.cryptosignals.repository.FuturesOrderRepository.class));
		user = new User();
		user.setId(UUID.randomUUID());
		credential = new ExchangeCredential();
		credential.setId(UUID.randomUUID());
		credential.setUser(user);
		credential.setExchange(ExchangeName.BINANCE);
		when(credentials.findByUser_IdAndExchange(user.getId(), ExchangeName.BINANCE))
				.thenReturn(Optional.of(credential));
		when(spot.getOpenOrders(any(), isNull())).thenReturn(List.of(
				order("BTCUSDT", "BUY", "NEW", 1L),
				order("ETHUSDT", "SELL", "NEW", 2L),
				order("SOLUSDT", "BUY", "PARTIALLY_FILLED", 3L)));
	}

	private static ExchangeOrderSnapshot order(
			String symbol, String side, String status, long id) {
		Instant now = Instant.parse("2024-01-01T00:00:00Z");
		return new ExchangeOrderSnapshot(symbol, id, "c" + id, side, "LIMIT", status,
				new BigDecimal("100"), null, new BigDecimal("1"), new BigDecimal("0.5"),
				new BigDecimal("50"), now, now);
	}

	private List<PortfolioOrderView> spotOpenOrders(String side, String status) {
		return service.openOrders(user, AccountMode.LIVE, AccountCategory.SPOT,
				null, side, status).orders();
	}

	@Test
	void noFilter_returnsEverything() {
		assertThat(spotOpenOrders(null, null)).hasSize(3);
	}

	@Test
	void sideFilter_narrowsToThatSide() {
		assertThat(spotOpenOrders("BUY", null))
				.extracting(PortfolioOrderView::symbol)
				.containsExactly("BTCUSDT", "SOLUSDT");
	}

	@Test
	void statusFilter_narrowsToThatStatus() {
		assertThat(spotOpenOrders(null, "PARTIALLY_FILLED"))
				.extracting(PortfolioOrderView::symbol)
				.containsExactly("SOLUSDT");
	}

	@Test
	void sideAndStatusCombine() {
		assertThat(spotOpenOrders("BUY", "NEW"))
				.extracting(PortfolioOrderView::symbol)
				.containsExactly("BTCUSDT");
	}

	@Test
	void filtersAreCaseInsensitiveAndTrimmed() {
		assertThat(spotOpenOrders(" buy ", " new "))
				.extracting(PortfolioOrderView::symbol)
				.containsExactly("BTCUSDT");
	}

	@Test
	void blankFilterMeansNoFilter() {
		assertThat(spotOpenOrders("  ", "")).hasSize(3);
	}

	/**
	 * A typo must narrow to nothing. Widening to the full set would be the more
	 * dangerous outcome: the UI would claim to be filtered while showing all.
	 */
	@Test
	void unrecognisedStatusMatchesNothingRatherThanEverything() {
		assertThat(spotOpenOrders(null, "NOT_A_STATUS")).isEmpty();
	}

	@Test
	void unrecognisedSideMatchesNothing() {
		assertThat(spotOpenOrders("SIDEWAYS", null)).isEmpty();
	}

	@Test
	void filtersDoNotAffectTheAvailabilityContract() {
		var response = service.openOrders(user, AccountMode.LIVE, AccountCategory.SPOT,
				null, "BUY", null);
		assertThat(response.availability())
				.isEqualTo(com.shyblack.cryptosignals.entity.enums.AccountAvailability.AVAILABLE);
		assertThat(response.orders()).hasSize(2);
	}

	@Test
	void filteredPaperStillReportsNoOrderBookRatherThanAnEmptyList() {
		var response = service.openOrders(user, AccountMode.PAPER, AccountCategory.SPOT,
				null, "BUY", null);
		assertThat(response.availability())
				.isEqualTo(com.shyblack.cryptosignals.entity.enums.AccountAvailability.UNSUPPORTED);
		assertThat(response.statusMessage()).contains("no order book");
	}

	@Test
	void legacyOverloadRemainsUnfiltered() {
		assertThat(service.openOrders(
				user, AccountMode.LIVE, AccountCategory.SPOT, null).orders())
				.hasSize(3);
	}

	@Test
	void statusFilterMatchesTheNormalisedEnumNotTheRawString() {
		// The raw exchange string is preserved on the view, but filtering keys
		// off the normalised status so PARTIALLY_FILLED lines up with
		// PortfolioOrderStatus.PARTIALLY_FILLED.
		assertThat(spotOpenOrders(null, PortfolioOrderStatus.PARTIALLY_FILLED.name()))
				.extracting(PortfolioOrderView::symbol)
				.containsExactly("SOLUSDT");
	}
}
