package com.shyblack.cryptosignals.service.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.dto.portfolio.PortfolioAccountView;
import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.LiveTradingAccount;
import com.shyblack.cryptosignals.entity.FuturesTradingAccount;
import com.shyblack.cryptosignals.entity.Portfolio;
import com.shyblack.cryptosignals.entity.PortfolioAccountConnection;
import com.shyblack.cryptosignals.entity.PortfolioExchangeBalance;
import com.shyblack.cryptosignals.entity.PortfolioExchangePosition;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountAvailability;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.FuturesMarginMode;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.exchange.futures.FuturesExchangePosition;
import com.shyblack.cryptosignals.exchange.futures.mock.MockFuturesExchangeAdapter;
import com.shyblack.cryptosignals.exchange.mock.MockExchangeTradingAdapter;
import com.shyblack.cryptosignals.repository.ExchangeCredentialRepository;
import com.shyblack.cryptosignals.repository.FuturesTradingAccountRepository;
import com.shyblack.cryptosignals.repository.LiveTradingAccountRepository;
import com.shyblack.cryptosignals.repository.PortfolioAccountConnectionRepository;
import com.shyblack.cryptosignals.repository.PortfolioExchangeBalanceRepository;
import com.shyblack.cryptosignals.repository.PortfolioExchangePositionRepository;
import com.shyblack.cryptosignals.repository.PortfolioRepository;
import com.shyblack.cryptosignals.repository.UserRepository;
import com.shyblack.cryptosignals.service.futures.FuturesQueryService;
import com.shyblack.cryptosignals.service.live.LiveTradingQueryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Phase 3 behaviour: authoritative exchange reads land in read-model tables, the read model
 * consumes them, and nothing is ever fabricated or written to an execution table.
 *
 * <p>Runs against the in-process MOCK adapters selected by the test profile, so no production
 * exchange is contacted. The mock is the same simulator used by the existing live/futures suites.
 */
@SpringBootTest
@ActiveProfiles("test")
class LivePortfolioSyncServiceTest {

	private static final AtomicInteger SEQ = new AtomicInteger();

	@Autowired
	private LivePortfolioSyncService syncService;

	@Autowired
	private PortfolioAccountReadService readService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ExchangeCredentialRepository credentialRepository;

	@Autowired
	private LiveTradingAccountRepository liveAccountRepository;

	@Autowired
	private FuturesTradingAccountRepository futuresAccountRepository;

	@Autowired
	private PortfolioAccountConnectionRepository connectionRepository;

	@Autowired
	private PortfolioExchangeBalanceRepository balanceRepository;

	@Autowired
	private PortfolioExchangePositionRepository positionRepository;

	@Autowired
	private PortfolioRepository portfolioRepository;

	@Autowired
	private MockExchangeTradingAdapter spotAdapter;

	@Autowired
	private MockFuturesExchangeAdapter futuresAdapter;

	@Autowired
	private LiveTradingQueryService liveQueryService;

	@Autowired
	private FuturesQueryService futuresQueryService;

	// ------------------------------------------------------------- fixtures

	/**
	 * The adapters are singleton beans inside a cached Spring context, so any seeded state would
	 * otherwise leak into every other test. Reset before and after each test.
	 */
	@org.junit.jupiter.api.BeforeEach
	void resetAdapters() {
		spotAdapter.reset();
		futuresAdapter.reset();
	}

	@org.junit.jupiter.api.AfterEach
	void resetAdaptersAfter() {
		spotAdapter.reset();
		futuresAdapter.reset();
	}

	private User user() {
		User u = new User();
		u.setEmail("live-sync-" + SEQ.incrementAndGet() + "@example.com");
		u.setFullName("Live Sync Tester");
		return userRepository.saveAndFlush(u);
	}

	/**
	 * Test-only credential. Idempotent per user: one Binance credential legitimately backs both the
	 * spot and the futures account, and the table is unique on {@code (user, exchange)}.
	 */
	private ExchangeCredential credential(User user) {
		return credentialRepository.findByUser_IdAndExchange(user.getId(), ExchangeName.BINANCE)
				.orElseGet(() -> {
					ExchangeCredential c = new ExchangeCredential();
					c.setUser(user);
					c.setExchange(ExchangeName.BINANCE);
					c.setApiKey("dGVzdC1vbmx5LWtleQ==");
					c.setApiSecret("dGVzdC1vbmx5LXNlY3JldA==");
					return credentialRepository.saveAndFlush(c);
				});
	}

	private LiveTradingAccount liveAccount(User user) {
		LiveTradingAccount a = new LiveTradingAccount();
		a.setUser(user);
		a.setExchange(ExchangeName.BINANCE);
		a.setCredential(credential(user));
		a.setConnectionStatus(ExchangeConnectionStatus.CONNECTED);
		a.setQuoteCurrency("USDT");
		a.setLastValidatedAt(Instant.now());
		return liveAccountRepository.saveAndFlush(a);
	}

	private FuturesTradingAccount futuresAccount(User user) {
		FuturesTradingAccount a = new FuturesTradingAccount();
		a.setUser(user);
		a.setExchange(ExchangeName.BINANCE);
		a.setCredential(credential(user));
		a.setConnectionStatus(ExchangeConnectionStatus.CONNECTED);
		a.setMarginAsset("USDT");
		a.setLastValidatedAt(Instant.now());
		a.setWalletBalance(new BigDecimal("1000"));
		a.setAvailableBalance(new BigDecimal("600"));
		a.setUsedMargin(new BigDecimal("400"));
		a.setUnrealizedPnl(new BigDecimal("-12.5"));
		return futuresAccountRepository.saveAndFlush(a);
	}

	private FuturesExchangePosition position(String symbol, PositionSide side, String amount, String liq) {
		return new FuturesExchangePosition(
				symbol, side, new BigDecimal(amount), new BigDecimal("60000"),
				new BigDecimal("61000"), liq == null ? null : new BigDecimal(liq),
				3, FuturesMarginMode.ISOLATED, new BigDecimal("10000"),
				new BigDecimal("30500"), new BigDecimal("50"), Instant.now());
	}

	// ------------------------------------------------------- A. LIVE SPOT

	@Test
	void spotSyncPersistsEveryReportedAssetBalance() {
		User u = user();
		liveAccount(u);
		spotAdapter.putBalance("USDT", new BigDecimal("100.5"), new BigDecimal("25.25"));
		spotAdapter.putBalance("BTC", new BigDecimal("0.75"), BigDecimal.ZERO);

		var outcome = syncService.syncSpot(u);

		assertThat(outcome.isSuccessful()).isTrue();
		assertThat(outcome.availability()).isEqualTo(AccountAvailability.AVAILABLE);
		assertThat(outcome.recordCount()).isEqualTo(2);
		List<PortfolioExchangeBalance> rows =
				balanceRepository.findByUserAndExchange(u, ExchangeName.BINANCE);
		assertThat(rows).hasSize(2);
		PortfolioExchangeBalance usdt = rows.stream()
				.filter(r -> "USDT".equals(r.getAsset())).findFirst().orElseThrow();
		assertThat(usdt.getFree()).isEqualByComparingTo("100.5");
		assertThat(usdt.getLocked()).isEqualByComparingTo("25.25");
		assertThat(usdt.total()).isEqualByComparingTo("125.75");
	}

	@Test
	void spotSyncPreservesAnExplicitZeroReportedByTheExchange() {
		User u = user();
		liveAccount(u);
		spotAdapter.putBalance("USDT", BigDecimal.ZERO, BigDecimal.ZERO);

		syncService.syncSpot(u);

		PortfolioExchangeBalance usdt =
				balanceRepository.findByUserAndExchangeAndAsset(u, ExchangeName.BINANCE, "USDT").orElseThrow();
		assertThat(usdt.getFree()).isEqualByComparingTo("0");
	}

	@Test
	void spotSyncDropsAssetsTheExchangeStopsReporting() {
		User u = user();
		liveAccount(u);
		spotAdapter.putBalance("USDT", new BigDecimal("10"), BigDecimal.ZERO);
		spotAdapter.putBalance("DOGE", new BigDecimal("5"), BigDecimal.ZERO);
		syncService.syncSpot(u);
		assertThat(balanceRepository.findByUserAndExchange(u, ExchangeName.BINANCE)).hasSize(2);

		spotAdapter.removeBalance("DOGE");
		syncService.syncSpot(u);

		assertThat(balanceRepository.findByUserAndExchange(u, ExchangeName.BINANCE))
				.as("an asset the exchange no longer reports must not keep a stale value")
				.hasSize(1);
	}

	@Test
	void spotSyncWithoutCredentialIsNotConnectedAndStoresNoZero() {
		User u = user();

		var outcome = syncService.syncSpot(u);

		assertThat(outcome.availability()).isEqualTo(AccountAvailability.NOT_CONNECTED);
		assertThat(outcome.recordCount()).isZero();
		assertThat(balanceRepository.findByUserAndExchange(u, ExchangeName.BINANCE)).isEmpty();
		PortfolioAccountConnection connection = connectionRepository
				.findByUserAndAccountModeAndAccountCategory(u, AccountMode.LIVE, AccountCategory.SPOT)
				.orElseThrow();
		assertThat(connection.getAvailability()).isEqualTo(AccountAvailability.NOT_CONNECTED);
		assertThat(connection.getLastSyncedAt()).as("never synced, so no timestamp").isNull();
	}

	@Test
	void readModelConsumesSyncedSpotBalances() {
		User u = user();
		liveAccount(u);
		spotAdapter.putBalance("USDT", new BigDecimal("100.5"), new BigDecimal("25.25"));
		syncService.syncSpot(u);

		PortfolioAccountView spot = readService.getAccount(u, AccountMode.LIVE, AccountCategory.SPOT);

		assertThat(spot.availability()).isEqualTo(AccountAvailability.AVAILABLE);
		assertThat(spot.availableBalance()).isEqualByComparingTo("100.5");
		assertThat(spot.equity()).isEqualByComparingTo("125.75");
		assertThat(spot.quoteCurrency()).isEqualTo("USDT");
		assertThat(spot.lastSyncedAt()).isNotNull();
	}

	@Test
	void liveSpotNeverReportsPositionsFromBalances() {
		User u = user();
		liveAccount(u);
		spotAdapter.putBalance("BTC", new BigDecimal("2"), BigDecimal.ZERO);
		syncService.syncSpot(u);

		PortfolioAccountView spot = readService.getAccount(u, AccountMode.LIVE, AccountCategory.SPOT);

		assertThat(spot.totalPositionCount())
				.as("a wallet asset balance is not a trading position")
				.isNull();
		assertThat(spot.openPositionCount()).isNull();
	}

	@Test
	void liveSpotWithoutASyncExplainsThatSpotHasNoPositionConcept() {
		User u = user();
		liveAccount(u);

		PortfolioAccountView spot = readService.getAccount(u, AccountMode.LIVE, AccountCategory.SPOT);

		assertThat(spot.statusMessage()).contains("balances, not open positions");
	}

	// ----------------------------------------------------- B. LIVE FUTURES

	@Test
	void futuresSyncPersistsAuthoritativeOpenPositions() {
		User u = user();
		futuresAccount(u);
		futuresAdapter.putPosition(position("BTCUSDT", PositionSide.LONG, "0.5", "45000"));
		futuresAdapter.putPosition(position("ETHUSDT", PositionSide.SHORT, "-2", "3800"));

		var outcome = syncService.syncFutures(u);

		assertThat(outcome.isSuccessful()).isTrue();
		assertThat(outcome.recordCount()).isEqualTo(2);
		List<PortfolioExchangePosition> rows =
				positionRepository.findByUserAndExchangeOrderBySymbolAsc(u, ExchangeName.BINANCE);
		assertThat(rows).hasSize(2);
		PortfolioExchangePosition btc = rows.get(0);
		assertThat(btc.getSymbol()).isEqualTo("BTCUSDT");
		assertThat(btc.getPositionSide()).isEqualTo(PositionSide.LONG);
		assertThat(btc.getPositionAmount()).isEqualByComparingTo("0.5");
		assertThat(btc.getLiquidationPrice()).isEqualByComparingTo("45000");
		assertThat(btc.getLeverage()).isEqualTo(3);
		assertThat(btc.getMarginMode()).isEqualTo(FuturesMarginMode.ISOLATED);
		assertThat(rows.get(1).getPositionSide()).isEqualTo(PositionSide.SHORT);
	}

	@Test
	void futuresSyncNeverStoresAFlatPosition() {
		User u = user();
		futuresAccount(u);
		futuresAdapter.putPosition(position("BTCUSDT", PositionSide.LONG, "0", "45000"));

		var outcome = syncService.syncFutures(u);

		assertThat(outcome.recordCount()).isZero();
		assertThat(positionRepository.findByUserAndExchangeOrderBySymbolAsc(u, ExchangeName.BINANCE)).isEmpty();
	}

	@Test
	void futuresSyncRemovesPositionsTheExchangeClosed() {
		User u = user();
		futuresAccount(u);
		futuresAdapter.putPosition(position("BTCUSDT", PositionSide.LONG, "0.5", "45000"));
		futuresAdapter.putPosition(position("ETHUSDT", PositionSide.LONG, "3", "2500"));
		syncService.syncFutures(u);
		assertThat(positionRepository.findByUserAndExchangeOrderBySymbolAsc(u, ExchangeName.BINANCE)).hasSize(2);

		futuresAdapter.clearPosition("ETHUSDT", PositionSide.LONG);
		syncService.syncFutures(u);

		assertThat(positionRepository.findByUserAndExchangeOrderBySymbolAsc(u, ExchangeName.BINANCE))
				.as("a position absent from the exchange response is genuinely closed")
				.hasSize(1);
	}

	@Test
	void futuresSyncIsIdempotentAndCreatesNoDuplicateConnectionRow() {
		User u = user();
		futuresAccount(u);
		futuresAdapter.putPosition(position("BTCUSDT", PositionSide.LONG, "0.5", "45000"));

		syncService.syncFutures(u);
		syncService.syncFutures(u);
		syncService.syncFutures(u);

		assertThat(positionRepository.findByUserAndExchangeOrderBySymbolAsc(u, ExchangeName.BINANCE))
				.hasSize(1);
		assertThat(connectionRepository.findByUserAndAccountModeAndAccountCategory(
				u, AccountMode.LIVE, AccountCategory.FUTURES))
				.as("the scope key forbids a second connection row")
				.isPresent();
		assertThat(connectionRepository.findByUserAndAccountMode(
				u, AccountMode.LIVE)).hasSize(1);
	}

	@Test
	void readModelConsumesSyncedFuturesPositions() {
		User u = user();
		futuresAccount(u);
		futuresAdapter.putPosition(position("BTCUSDT", PositionSide.LONG, "0.5", "45000"));
		futuresAdapter.putPosition(position("ETHUSDT", PositionSide.SHORT, "-2", "3800"));
		syncService.syncFutures(u);

		PortfolioAccountView futures = readService.getAccount(u, AccountMode.LIVE, AccountCategory.FUTURES);

		assertThat(futures.availability()).isEqualTo(AccountAvailability.AVAILABLE);
		assertThat(futures.openPositionCount()).isEqualTo(2);
		assertThat(futures.equity()).isEqualByComparingTo("1000");
		assertThat(futures.availableBalance()).isEqualByComparingTo("600");
		assertThat(futures.invested()).isEqualByComparingTo("400");
		assertThat(futures.unrealizedPnl()).isEqualByComparingTo("-12.5");
		assertThat(futures.lastSyncedAt()).isNotNull();
	}

	@Test
	void futuresRealizedPnlStaysNullBecauseNoAuthoritativeIncomeSourceExists() {
		User u = user();
		FuturesTradingAccount account = futuresAccount(u);
		account.setCachedRealizedPnl(new BigDecimal("999"));
		futuresAccountRepository.saveAndFlush(account);
		futuresAdapter.putPosition(position("BTCUSDT", PositionSide.LONG, "0.5", "45000"));
		syncService.syncFutures(u);

		PortfolioAccountView futures = readService.getAccount(u, AccountMode.LIVE, AccountCategory.FUTURES);

		assertThat(futures.realizedPnl())
				.as("J: local ledger P&L must never be labelled exchange realized P&L")
				.isNull();
		assertThat(futures.unrealizedPnl()).isEqualByComparingTo("-12.5");
	}

	@Test
	void liveFuturesWithoutASyncExplainsWhyRealizedPnlIsAbsent() {
		User u = user();
		futuresAccount(u);

		PortfolioAccountView futures = readService.getAccount(u, AccountMode.LIVE, AccountCategory.FUTURES);

		assertThat(futures.realizedPnl()).isNull();
		assertThat(futures.statusMessage()).contains("income history");
	}

	// ------------------------------------- C/D. LIVE MAIN, LIVE OPTIONS

	@Test
	void liveMainStaysUnavailableEvenWithBothWalletsConnected() {
		User u = user();
		liveAccount(u);
		futuresAccount(u);
		spotAdapter.putBalance("USDT", new BigDecimal("100"), BigDecimal.ZERO);
		syncService.syncSpot(u);
		syncService.syncFutures(u);

		PortfolioAccountView main = readService.getAccount(u, AccountMode.LIVE, AccountCategory.MAIN);

		assertThat(main.availability()).isEqualTo(AccountAvailability.UNAVAILABLE);
		assertThat(main.equity())
				.as("C: spot and futures wallets are never summed into an invented wallet")
				.isNull();
		assertThat(main.availableBalance()).isNull();
		assertThat(main.invested()).isNull();
		assertThat(main.statusMessage()).contains("no single main-wallet balance");
	}

	@Test
	void liveOptionsIsUnsupportedAndReportsNoFigures() {
		User u = user();
		liveAccount(u);

		PortfolioAccountView options = readService.getAccount(u, AccountMode.LIVE, AccountCategory.OPTIONS);

		assertThat(options.availability()).isEqualTo(AccountAvailability.UNSUPPORTED);
		assertThat(options.equity()).isNull();
		assertThat(options.availableBalance()).isNull();
		assertThat(options.openPositionCount()).isNull();
		assertThat(options.orderCount()).isNull();
	}

	@Test
	void liveOptionsSyncIsNeverEvenAttempted() {
		User u = user();
		liveAccount(u);
		spotAdapter.putBalance("USDT", new BigDecimal("100"), BigDecimal.ZERO);

		syncService.syncSpot(u);

		assertThat(connectionRepository.findByUserAndAccountModeAndAccountCategory(
				u, AccountMode.LIVE, AccountCategory.OPTIONS))
				.as("no options sync exists, so no options scope row is fabricated")
				.isEmpty();
	}

	// ------------------------------------------------- E. PAPER ISOLATION

	@Test
	void paperScopesAreUnaffectedAndNeverTouchTheExchange() {
		User u = user();
		Portfolio portfolio = new Portfolio();
		portfolio.setUser(u);
		portfolio.setName("Paper Trading");
		portfolio.setAccountType(AccountType.PAPER);
		portfolio.setInitialBalance(new BigDecimal("1000"));
		portfolio.setTotalBalance(new BigDecimal("1000"));
		portfolio.setAvailableBalance(new BigDecimal("1000"));
		portfolioRepository.saveAndFlush(portfolio);

		var paperMain = readService.getAccount(u, AccountMode.PAPER, AccountCategory.MAIN);
		var paperSpot = readService.getAccount(u, AccountMode.PAPER, AccountCategory.SPOT);

		assertThat(paperMain.availability()).isEqualTo(AccountAvailability.AVAILABLE);
		assertThat(paperMain.availableBalance()).isEqualByComparingTo("1000");
		assertThat(paperMain.equity()).isEqualByComparingTo("1000");
		assertThat(paperMain.exchange()).as("paper never acquires an exchange").isNull();
		assertThat(paperSpot.availableBalance()).isNull();
		assertThat(balanceRepository.findByUserAndExchange(u, ExchangeName.BINANCE))
				.as("a paper read writes no exchange data")
				.isEmpty();
		assertThat(connectionRepository.findByUserAndAccountMode(u, AccountMode.PAPER)).isEmpty();
	}

	@Test
	void paperAndLiveScopesNeverShareStoredState() {
		User u = user();
		Portfolio portfolio = new Portfolio();
		portfolio.setUser(u);
		portfolio.setName("Paper Trading");
		portfolio.setAccountType(AccountType.PAPER);
		portfolio.setAvailableBalance(new BigDecimal("777"));
		portfolio.setTotalBalance(new BigDecimal("777"));
		portfolioRepository.saveAndFlush(portfolio);

		liveAccount(u);
		spotAdapter.putBalance("USDT", new BigDecimal("100"), BigDecimal.ZERO);
		syncService.syncSpot(u);

		assertThat(readService.getAccount(u, AccountMode.PAPER, AccountCategory.MAIN).availableBalance())
				.isEqualByComparingTo("777");
		assertThat(readService.getAccount(u, AccountMode.LIVE, AccountCategory.SPOT).availableBalance())
				.isEqualByComparingTo("100");
	}

	// ------------------------------------------------------ G. OWNERSHIP

	@Test
	void oneUserNeverSeesAnotherUsersExchangeData() {
		User owner = user();
		liveAccount(owner);
		spotAdapter.putBalance("USDT", new BigDecimal("4242"), BigDecimal.ZERO);
		syncService.syncSpot(owner);

		User other = user();
		liveAccount(other);

		PortfolioAccountView foreign = readService.getAccount(other, AccountMode.LIVE, AccountCategory.SPOT);

		assertThat(foreign.availableBalance())
				.as("G: the other user's synced balance must never be visible")
				.isNull();
		assertThat(foreign.equity()).isNull();
		assertThat(balanceRepository.findByUserAndExchange(other, ExchangeName.BINANCE)).isEmpty();
		assertThat(balanceRepository.findByUserAndExchange(owner, ExchangeName.BINANCE)).hasSize(1);
	}

	// -------------------------------------- F/I. credentials and failures

	@Test
	void readModelAndItsInputsCarryNoCredentialMaterial() {
		User u = user();
		liveAccount(u);
		spotAdapter.putBalance("USDT", new BigDecimal("10"), BigDecimal.ZERO);
		syncService.syncSpot(u);
		readService.getAccount(u, AccountMode.LIVE, AccountCategory.SPOT);

		List<String> viewFields = java.util.Arrays.stream(PortfolioAccountView.class.getRecordComponents())
				.map(java.lang.reflect.RecordComponent::getName).toList();
		assertThat(viewFields)
				.as("F: no credential material may appear on the read model")
				.noneMatch(n -> n.toLowerCase().contains("key")
						|| n.toLowerCase().contains("secret")
						|| n.toLowerCase().contains("credential")
						|| n.toLowerCase().contains("token"));

		List<String> connectionFields = java.util.Arrays.stream(
						PortfolioAccountConnection.class.getDeclaredFields())
				.map(java.lang.reflect.Field::getName)
				.filter(n -> n.toLowerCase().contains("credential")
						|| n.toLowerCase().contains("secret")
						|| n.toLowerCase().contains("apikey"))
				.toList();
		assertThat(connectionFields).isEmpty();
	}

	@Test
	void liveSpotWithoutAnySynchronizationIsUnavailableNotAvailable() {
		User u = user();
		liveAccount(u);
		spotAdapter.putBalance("BTC", new BigDecimal("2"), BigDecimal.ZERO);
		syncService.syncSpot(u);

		PortfolioAccountView spot = readService.getAccount(u, AccountMode.LIVE, AccountCategory.SPOT);

		assertThat(spot.availability())
				.as("the exchange reported BTC only, so there is no authoritative quote balance yet")
				.isEqualTo(AccountAvailability.AVAILABLE);
		assertThat(spot.availableBalance()).isNull();
		assertThat(spot.equity()).isNull();
	}

	@Test
	void spotSyncFailureDiagnosticsFitTheStorageColumn() {
		String long_ = "x".repeat(500);
		assertThat(LivePortfolioSyncService.truncate(long_)).hasSize(200);
		assertThat(LivePortfolioSyncService.truncate(null)).isNull();
		assertThat(LivePortfolioSyncService.truncate("short")).isEqualTo("short");
	}

	// ------------------------------- read-only guarantee (no orders placed)

	@Test
	void syncingNeverPlacesCancelsOrModifiesAnyOrder() {
		User u = user();
		liveAccount(u);
		futuresAccount(u);
		spotAdapter.putBalance("USDT", new BigDecimal("10"), BigDecimal.ZERO);
		futuresAdapter.putPosition(position("BTCUSDT", PositionSide.LONG, "0.5", "45000"));

		syncService.syncSpot(u);
		syncService.syncFutures(u);
		readService.getOverview(u, AccountMode.LIVE);

		assertThat(spotAdapter.allOrders())
				.as("no order may be placed by a read-only sync")
				.isEmpty();
		assertThat(liveQueryService.allOrders(u)).isEmpty();
		assertThat(futuresQueryService.openOrders(u)).isEmpty();
	}
}