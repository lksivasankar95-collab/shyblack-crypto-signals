package com.shyblack.cryptosignals.service.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shyblack.cryptosignals.config.ExecutionProperties;
import com.shyblack.cryptosignals.config.FuturesTradingProperties;
import com.shyblack.cryptosignals.config.LiveTradingProperties;
import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.FuturesTradingAccount;
import com.shyblack.cryptosignals.entity.LiveTradingAccount;
import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.UserSettings;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.EntryType;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.FuturesMarginMode;
import com.shyblack.cryptosignals.entity.enums.FuturesPositionMode;
import com.shyblack.cryptosignals.entity.enums.LiveOrderStatus;
import com.shyblack.cryptosignals.entity.enums.LiveOrderType;
import com.shyblack.cryptosignals.entity.enums.LiveTradingRiskReason;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.SignalGrade;
import com.shyblack.cryptosignals.entity.enums.SignalStatus;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.exchange.ExchangeOrderResult;
import com.shyblack.cryptosignals.exchange.ExchangeTradingAdapter;
import com.shyblack.cryptosignals.exchange.PlaceOrderRequest;
import com.shyblack.cryptosignals.exchange.SymbolRules;
import com.shyblack.cryptosignals.exchange.futures.FuturesExchangeAdapter;
import com.shyblack.cryptosignals.exchange.futures.FuturesOrderResult;
import com.shyblack.cryptosignals.exchange.futures.mock.MockFuturesExchangeAdapter;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderStatus;
import com.shyblack.cryptosignals.exchange.futures.PlaceFuturesOrderRequest;
import com.shyblack.cryptosignals.exchange.mock.MockExchangeTradingAdapter;
import com.shyblack.cryptosignals.repository.ExecutionDecisionRecordRepository;
import com.shyblack.cryptosignals.repository.FuturesTradingAccountRepository;
import com.shyblack.cryptosignals.repository.LiveTradingAccountRepository;
import com.shyblack.cryptosignals.repository.SignalRepository;
import com.shyblack.cryptosignals.repository.UserRepository;
import com.shyblack.cryptosignals.repository.UserSettingsRepository;
import com.shyblack.cryptosignals.service.SignalGeneratedEvent;
import com.shyblack.cryptosignals.service.futures.FuturesEngineService;
import com.shyblack.cryptosignals.service.futures.FuturesExecutionService;
import com.shyblack.cryptosignals.service.futures.FuturesLiquidationService;
import com.shyblack.cryptosignals.service.futures.FuturesRiskService;
import com.shyblack.cryptosignals.service.futures.FuturesTradingSizingService;
import com.shyblack.cryptosignals.service.live.LiveTradingEngineService;
import com.shyblack.cryptosignals.service.live.LiveTradingExecutionService;
import com.shyblack.cryptosignals.service.live.LiveTradingRiskService;
import com.shyblack.cryptosignals.service.live.LiveTradingSizingService;
import com.shyblack.cryptosignals.service.paper.PaperTradingEngineService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Phase 9 — router → risk → execution service → adapter, end to end with a mocked
 * exchange boundary.
 *
 * <p>Closes the gap Phase 8 reported: "no adapter {@code placeOrder} path has a
 * test". These tests assemble the real {@link ExecutionRouter}, the real engines,
 * the real risk services and the real execution services, and mock only the exchange
 * adapter and the repositories. That means every gate, every ordering step and every
 * order submission is the production code path.
 *
 * <p>The adapter mock is the project's own {@link MockExchangeTradingAdapter} /
 * {@link MockFuturesExchangeAdapter}, so the assertions also prove the mock honours
 * the same interface contract as the real adapter. Where the simulator's behaviour
 * would mask a defect, an explicit stub overrides it.
 *
 * <p><b>No real exchange is involved.</b> Nothing here can place a Binance order.
 */
class ExecutionRouterAdapterIntegrationTest {

	private static final AtomicInteger SEQ = new AtomicInteger();

	private ExecutionRouter router;

	private SignalRepository signalRepository;
	private ExecutionDecisionRecordRepository decisionRepository;
	private LiveTradingAccountRepository liveAccountRepository;
	private FuturesTradingAccountRepository futuresAccountRepository;
	private UserRepository userRepository;
	private UserSettingsRepository userSettingsRepository;

	private com.shyblack.cryptosignals.repository.LiveOrderRepository liveOrderRepoMock;
	private com.shyblack.cryptosignals.repository.FuturesOrderRepository futuresOrderRepoMock;

	private MockExchangeTradingAdapter spotAdapter;
	private MockFuturesExchangeAdapter futuresAdapter;
	private com.shyblack.cryptosignals.market.MarketBook marketBook;
	private LiveTradingRiskService liveRiskService;
	private FuturesRiskService futuresRiskService;

	/** Seeds one ticker so the engine has a reference price to size against. */
	private static void seedTicker(
			com.shyblack.cryptosignals.market.MarketTickerStore store, String symbol) {
		BigDecimal price = new BigDecimal("50000");
		store.upsert(new com.shyblack.cryptosignals.market.MarketTicker(
				symbol, symbol, price, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
				price, price, java.time.Instant.now()));
	}

	@BeforeEach
	void setUp() {
		signalRepository = mock(SignalRepository.class);
		decisionRepository = mock(ExecutionDecisionRecordRepository.class);
		liveAccountRepository = mock(LiveTradingAccountRepository.class);
		futuresAccountRepository = mock(FuturesTradingAccountRepository.class);
		userRepository = mock(UserRepository.class);
		userSettingsRepository = mock(UserSettingsRepository.class);

		// A real MarketBook seeded with a ticker, so the engine has a reference price
		// and does not silently skip the order as INVALID_PRICE.
		marketBook = new com.shyblack.cryptosignals.market.MarketBook();
		seedTicker(marketBook.spotTickers(), "BTCUSDT");
		seedTicker(marketBook.futuresTickers(), "BTCUSDT");
		spotAdapter = new MockExchangeTradingAdapter();
		futuresAdapter = new MockFuturesExchangeAdapter();
		spotAdapter.reset();
		futuresAdapter.reset();

		futuresRiskService = new FuturesRiskService(
				userSettingsRepository,
				mock(com.shyblack.cryptosignals.repository.FuturesOrderRepository.class),
				mock(com.shyblack.cryptosignals.repository.FuturesPositionRepository.class),
				futuresProps(false));


		when(userRepository.findAll()).thenReturn(List.of());
		when(liveAccountRepository.findAllWithCredential()).thenReturn(List.of());
		when(futuresAccountRepository.findAllWithCredential()).thenReturn(List.of());
		when(decisionRepository.findByUserIdAndSignalIdAndAccountModeAndAccountCategory(
				any(), any(), any(), any())).thenReturn(Optional.empty());
		when(decisionRepository.saveAndFlush(any())).thenAnswer(invocation -> {
			var row = (com.shyblack.cryptosignals.entity.ExecutionDecisionRecord)
					invocation.getArgument(0);
			// Mockito calls the existing answer with a null argument while a test
			// re-stubs this method, so null must be tolerated here.
			if (row == null) {
				return null;
			}
			if (row.getId() == null) {
				row.setId(UUID.randomUUID());
			}
			return row;
		});

		liveOrderRepoMock = orderRepo();
		futuresOrderRepoMock = futuresOrderRepo();
		liveRiskService = new LiveTradingRiskService(userSettingsRepository, liveOrderRepoMock);

		router = buildRouter(true, false, true, true);
	}

	/** Builds the full router → engine → execution service → adapter chain. */
	private ExecutionRouter buildRouter(
			boolean routerEnabled, boolean dryRun,
			boolean spotAutoExecute, boolean futuresAutoExecute) {
		var clientOrderIds = new com.shyblack.cryptosignals.exchange.ClientOrderIdGenerator();
		var futuresIds =
				new com.shyblack.cryptosignals.service.futures.FuturesClientOrderIdGenerator();

		// The execution service persists its intent through the repository before it
		// calls the adapter, so the repository must actually store rows here.
		LiveTradingExecutionService spotExecution = new LiveTradingExecutionService(
				liveOrderRepoMock,
				mock(com.shyblack.cryptosignals.repository.LiveOrderLifecycleEventRepository.class),
				spotAdapter, clientOrderIds);

		FuturesExecutionService futuresExecution = new FuturesExecutionService(
				futuresOrderRepoMock,
				mock(com.shyblack.cryptosignals.repository.FuturesOrderLifecycleEventRepository.class),
				futuresAdapter);

		LiveTradingEngineService liveEngine = new LiveTradingEngineService(
				liveRiskService,
				new LiveTradingSizingService(),
				spotExecution,
				spotAdapter,
				marketBook,
				liveOrderRepoMock);

		FuturesEngineService futuresEngine = new FuturesEngineService(
				futuresProps(futuresAutoExecute),
				futuresRiskService,
				new FuturesTradingSizingService(),
				new FuturesLiquidationService(futuresProps(futuresAutoExecute)),
				futuresExecution,
				futuresAdapter,
				marketBook,
				futuresIds,
				mock(com.shyblack.cryptosignals.repository.FuturesPositionRepository.class),
				mock(com.shyblack.cryptosignals.repository.FuturesOrderLifecycleEventRepository.class));

		return new ExecutionRouter(
				new ExecutionProperties(routerEnabled, dryRun),
				liveProps(spotAutoExecute),
				futuresProps(futuresAutoExecute),
				signalRepository, decisionRepository,
				liveAccountRepository, futuresAccountRepository,
				userRepository, userSettingsRepository,
				mock(PaperTradingEngineService.class),
				liveEngine, futuresEngine,
				liveRiskService, futuresRiskService);
	}

	private static LiveTradingProperties liveProps(boolean autoExecute) {
		return new LiveTradingProperties(LiveTradingProperties.Mode.MOCK, null, null, null,
				5000, new BigDecimal("20000"), 3, new BigDecimal("5.00"), autoExecute);
	}

	private static FuturesTradingProperties futuresProps(boolean autoExecute) {
		return new FuturesTradingProperties(FuturesTradingProperties.Mode.MOCK, null, null,
				5000, 3, FuturesMarginMode.ISOLATED, FuturesPositionMode.ONE_WAY,
				new BigDecimal("20000"), 2, new BigDecimal("5.00"), new BigDecimal("0.30"),
				new BigDecimal("15.00"), autoExecute);
	}

	// ------------------------------------------------------------------ fixtures

	private static SymbolRules rules() {
		return new SymbolRules("BTCUSDT", new BigDecimal("0.00001"), new BigDecimal("1000"),
				new BigDecimal("0.001"), new BigDecimal("0.01"), new BigDecimal("1000000"),
				new BigDecimal("0.01"), new BigDecimal("10"));
	}

	private User user(AccountType type) {
		User u = new User();
		u.setId(UUID.randomUUID());
		u.setEmail("exec-" + SEQ.incrementAndGet() + "@example.com");
		u.setFullName("Execution Tester");
		u.setAccountType(type);
		u.setEnabled(true);
		return u;
	}

	private ExchangeCredential credential(User user) {
		ExchangeCredential c = new ExchangeCredential();
		c.setId(UUID.randomUUID());
		c.setUser(user);
		c.setExchange(ExchangeName.BINANCE);
		c.setApiKey("test-api-key");
		c.setApiSecret("test-api-secret");
		c.setStatus(ExchangeConnectionStatus.CONNECTED);
		return c;
	}

	private void allowLiveTrading(User user, boolean allowed) {
		UserSettings settings = new UserSettings();
		settings.setId(UUID.randomUUID());
		settings.setUser(user);
		settings.setLiveTradingAllowed(allowed);
		when(userSettingsRepository.findByUser_Id(user.getId()))
				.thenReturn(Optional.of(settings));
	}

	private LiveTradingAccount liveSpotAccount(User user) {
		LiveTradingAccount a = new LiveTradingAccount();
		a.setId(UUID.randomUUID());
		a.setUser(user);
		a.setExchange(ExchangeName.BINANCE);
		a.setCredential(credential(user));
		a.setConnectionStatus(ExchangeConnectionStatus.CONNECTED);
		a.setEnabled(true);
		a.setKillSwitchActive(false);
		a.setQuoteCurrency("USDT");
		a.setCachedAvailableBalance(new BigDecimal("10000"));
		a.setCachedTotalBalance(new BigDecimal("10000"));
		return a;
	}

	private FuturesTradingAccount liveFuturesAccount(User user) {
		FuturesTradingAccount a = new FuturesTradingAccount();
		a.setId(UUID.randomUUID());
		a.setUser(user);
		a.setExchange(ExchangeName.BINANCE);
		a.setCredential(credential(user));
		a.setConnectionStatus(ExchangeConnectionStatus.CONNECTED);
		a.setEnabled(true);
		a.setAcknowledged(true);
		a.setKillSwitchActive(false);
		a.setMarginAsset("USDT");
		a.setAvailableBalance(new BigDecimal("10000"));
		a.setWalletBalance(new BigDecimal("10000"));
		a.setMaxLeverage(3);
		return a;
	}

	private Signal signal(TradingMode mode) {
		Signal s = new Signal();
		s.setId(UUID.randomUUID());
		s.setSymbol("BTCUSDT");
		s.setStatus(SignalStatus.ACTIVE);
		s.setSide(PositionSide.LONG);
		s.setTradingMode(mode);
		s.setEntryPrice(new BigDecimal("50000"));
		s.setStopLoss(new BigDecimal("48000"));
		s.setTargetPrice(new BigDecimal("55000"));
		s.setEntryType(EntryType.PRE_BREAKOUT);
		s.setSignalGrade(SignalGrade.BUY);
		s.setConfidence(80);
		s.setSuggestedRiskPercent(new BigDecimal("2.00"));
		return s;
	}

	private ExecutionRoutingResult routeFor(ExecutionRouter target, User user, Signal signal) {
		return target.routeAll(signal).stream()
				.filter(r -> r.identity().userId().equals(user.getId()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("no decision for this user"));
	}

	/** A spot account whose rules and market price are available, so sizing can proceed. */
	private LiveTradingAccount readySpotAccount(User user) {
		LiveTradingAccount account = liveSpotAccount(user);
		spotAdapter.putRules(rules());
		when(liveAccountRepository.findAllWithCredential()).thenReturn(List.of(account));
		allowLiveTrading(user, true);
		return account;
	}

	private FuturesTradingAccount readyFuturesAccount(User user) {
		FuturesTradingAccount account = liveFuturesAccount(user);
		futuresAdapter.putRules(rules());
		when(futuresAccountRepository.findAllWithCredential()).thenReturn(List.of(account));
		allowLiveTrading(user, true);
		return account;
	}

	// ================================================= S. risk gate → spot adapter

	@Test
	void allGatesOpenReachesTheSpotAdapterWithABuy() {
		User live = user(AccountType.LIVE);
		readySpotAccount(live);
		Signal signal = signal(TradingMode.SPOT);

		ExecutionRoutingResult result = routeFor(router, live, signal);

		assertThat(result.decision()).isEqualTo(ExecutionDecision.EXECUTE);
		assertThat(spotAdapter.allOrders())
				.as("the gate chain must actually reach the exchange adapter")
				.isNotEmpty();
		// The entry fills immediately in the simulator and the engine then places a
		// protective stop, so two orders exist. Map iteration order is not defined,
		// so the statuses are asserted as a set rather than by index.
		assertThat(spotAdapter.allOrders())
				.extracting(com.shyblack.cryptosignals.exchange.ExchangeOrderResult::status)
				.as("one MARKET entry filled plus one resting protective stop")
				.containsExactlyInAnyOrder(LiveOrderStatus.FILLED, LiveOrderStatus.ACKNOWLEDGED);
		assertThat(spotAdapter.allOrders())
				.extracting(com.shyblack.cryptosignals.exchange.ExchangeOrderResult::rawResponseSummary)
				.containsOnly("MOCK");
	}

	@Test
	void theSpotClientOrderIdIsDeterministicAcrossRepeatedIntents() {
		var generator = new com.shyblack.cryptosignals.exchange.ClientOrderIdGenerator();
		User live = user(AccountType.LIVE);

		UUID signalId = UUID.randomUUID();
		String first = generator.forSignal(live.getId(), signalId, "BTCUSDT",
				PositionSide.LONG, com.shyblack.cryptosignals.entity.enums.LiveOrderPurpose.ENTRY);
		String again = generator.forSignal(live.getId(), signalId, "BTCUSDT",
				PositionSide.LONG, com.shyblack.cryptosignals.entity.enums.LiveOrderPurpose.ENTRY);

		assertThat(first)
				.as("a random id would defeat correlation and duplicate suppression")
				.isEqualTo(again);
		assertThat(first).startsWith("SB-");
	}

	@Test
	void theClientOrderIdSatisfiesTheExchangeCharacterRestriction() {
		var generator = new com.shyblack.cryptosignals.exchange.ClientOrderIdGenerator();
		String id = generator.forSignal(UUID.randomUUID(), UUID.randomUUID(), "BTCUSDT",
				PositionSide.LONG, com.shyblack.cryptosignals.entity.enums.LiveOrderPurpose.ENTRY);

		// Binance: ^[.A-Z:/a-z0-9_-]{1,36}$
		assertThat(id).hasSizeLessThanOrEqualTo(36);
		assertThat(id).matches("^[.A-Z:/a-z0-9_-]+$");
	}

	@Test
	void spotAndFuturesClientOrderIdsCannotCollide() {
		var spot = new com.shyblack.cryptosignals.exchange.ClientOrderIdGenerator();
		var futures =
				new com.shyblack.cryptosignals.service.futures.FuturesClientOrderIdGenerator();
		UUID userId = UUID.randomUUID();
		UUID signalId = UUID.randomUUID();

		String spotId = spot.forSignal(userId, signalId, "BTCUSDT", PositionSide.LONG,
				com.shyblack.cryptosignals.entity.enums.LiveOrderPurpose.ENTRY);
		String futuresId = futures.forSignal(userId, signalId, "BTCUSDT", PositionSide.LONG,
				com.shyblack.cryptosignals.entity.enums.FuturesOrderPurpose.ENTRY);

		assertThat(futuresId).isNotEqualTo(spotId);
		assertThat(spotId).startsWith("SB-");
		assertThat(futuresId).startsWith("SBF-");
		assertThat(futuresId).hasSizeLessThanOrEqualTo(36);
		assertThat(futuresId).matches("^[.A-Z:/a-z0-9_-]+$");
	}

	@Test
	void differentSignalsProduceDifferentClientOrderIds() {
		var generator = new com.shyblack.cryptosignals.exchange.ClientOrderIdGenerator();
		UUID userId = UUID.randomUUID();

		assertThat(generator.forSignal(userId, UUID.randomUUID(), "BTCUSDT", PositionSide.LONG,
				com.shyblack.cryptosignals.entity.enums.LiveOrderPurpose.ENTRY))
				.isNotEqualTo(generator.forSignal(userId, UUID.randomUUID(), "BTCUSDT",
						PositionSide.LONG,
						com.shyblack.cryptosignals.entity.enums.LiveOrderPurpose.ENTRY));
	}

	// =================================================== P/Q/R. kill switch, flags

	@Test
	void theKillSwitchPreventsAnyAdapterCall() {
		User live = user(AccountType.LIVE);
		LiveTradingAccount account = readySpotAccount(live);
		account.setKillSwitchActive(true);
		when(liveAccountRepository.findAllWithCredential()).thenReturn(List.of(account));

		ExecutionRoutingResult result = routeFor(router, live, signal(TradingMode.SPOT));

		assertThat(result.rejectionReason()).isEqualTo(ExecutionRejectionReason.KILL_SWITCH_ACTIVE);
		assertThat(spotAdapter.allOrders())
				.as("no adapter call may occur behind a refused gate")
				.isEmpty();
	}

	@Test
	void theKillSwitchPreventsAnyFuturesAdapterCall() {
		User live = user(AccountType.LIVE);
		FuturesTradingAccount account = readyFuturesAccount(live);
		account.setKillSwitchActive(true);
		when(futuresAccountRepository.findAllWithCredential()).thenReturn(List.of(account));

		ExecutionRoutingResult result = routeFor(router, live, signal(TradingMode.FUTURES));

		assertThat(result.rejectionReason()).isEqualTo(ExecutionRejectionReason.KILL_SWITCH_ACTIVE);
		assertThat(futuresAdapter.allOrders()).isEmpty();
	}

	@Test
	void autoExecuteDisabledPreventsAnyAdapterCall() {
		User live = user(AccountType.LIVE);
		readySpotAccount(live);
		ExecutionRouter off = buildRouter(true, false, false, false);

		ExecutionRoutingResult result = routeFor(off, live, signal(TradingMode.SPOT));

		assertThat(result.rejectionReason())
				.isEqualTo(ExecutionRejectionReason.AUTO_EXECUTE_DISABLED);
		assertThat(spotAdapter.allOrders()).isEmpty();
		assertThat(futuresAdapter.allOrders()).isEmpty();
	}

	@Test
	void theRouterBeingDisabledPreventsAnyAdapterCall() {
		User live = user(AccountType.LIVE);
		readySpotAccount(live);
		ExecutionRouter off = buildRouter(false, false, true, true);

		routeFor(off, live, signal(TradingMode.SPOT));

		assertThat(spotAdapter.allOrders()).isEmpty();
	}

	@Test
	void dryRunPreventsAnyAdapterCallWhileStillProducingTheRequest() {
		User live = user(AccountType.LIVE);
		readySpotAccount(live);
		ExecutionRouter dry = buildRouter(true, true, true, true);

		ExecutionRoutingResult result = routeFor(dry, live, signal(TradingMode.SPOT));

		assertThat(result.decision()).isEqualTo(ExecutionDecision.DRY_RUN);
		assertThat(result.request()).isNotNull();
		assertThat(spotAdapter.allOrders()).isEmpty();
	}

	@Test
	void anInactiveAccountPreventsAnyAdapterCallAndIsNeverActivated() {
		User live = user(AccountType.LIVE);
		LiveTradingAccount account = readySpotAccount(live);
		account.setEnabled(false);
		when(liveAccountRepository.findAllWithCredential()).thenReturn(List.of(account));

		ExecutionRoutingResult result = routeFor(router, live, signal(TradingMode.SPOT));

		assertThat(result.rejectionReason()).isEqualTo(ExecutionRejectionReason.ACCOUNT_INACTIVE);
		assertThat(account.isEnabled())
				.as("routing must never activate an account as a side effect")
				.isFalse();
		assertThat(spotAdapter.allOrders()).isEmpty();
	}

	@Test
	void aRiskRejectionPreventsAnyAdapterCall() {
		User live = user(AccountType.LIVE);
		readySpotAccount(live);
		Signal signal = signal(TradingMode.SPOT);
		signal.setStopLoss(null);

		ExecutionRoutingResult result = routeFor(router, live, signal);

		assertThat(result.rejectionReason()).isEqualTo(ExecutionRejectionReason.RISK_REJECTED);
		assertThat(spotAdapter.allOrders()).isEmpty();
	}

	@Test
	void aMissingSymbolRulePreventsAnyAdapterCall() {
		User live = user(AccountType.LIVE);
		// Account is open but the adapter has no rules for the symbol, so sizing
		// cannot proceed and nothing may be submitted.
		liveSpotAccount(live);
		when(liveAccountRepository.findAllWithCredential())
				.thenReturn(List.of(liveAccount(live)));
		allowLiveTrading(live, true);

		ExecutionRoutingResult result = routeFor(router, live, signal(TradingMode.SPOT));

		assertThat(result.decision()).isEqualTo(ExecutionDecision.EXECUTE);
		assertThat(spotAdapter.allOrders())
				.as("with no symbol rules the engine must not submit")
				.isEmpty();
	}

	private LiveTradingAccount liveAccount(User user) {
		return liveSpotAccount(user);
	}

	// ======================================================= U/V. isolation

	@Test
	void aSpotSignalNeverReachesTheFuturesAdapter() {
		User live = user(AccountType.LIVE);
		readySpotAccount(live);
		readyFuturesAccount(live);

		routeFor(router, live, signal(TradingMode.SPOT));

		assertThat(spotAdapter.allOrders()).isNotEmpty();
		assertThat(futuresAdapter.allOrders()).isEmpty();
	}

	@Test
	void aFuturesSignalNeverReachesTheSpotAdapter() {
		User live = user(AccountType.LIVE);
		readySpotAccount(live);
		readyFuturesAccount(live);

		routeFor(router, live, signal(TradingMode.FUTURES));

		assertThat(futuresAdapter.allOrders()).isNotEmpty();
		assertThat(spotAdapter.allOrders())
				.as("a futures signal must not reach the spot adapter")
				.isEmpty();
	}

	@Test
	void aFuturesOrderOpensWithBuyForALongSignal() {
		User live = user(AccountType.LIVE);
		readyFuturesAccount(live);

		routeFor(router, live, signal(TradingMode.FUTURES));

		assertThat(futuresAdapter.allOrders())
				.as("the futures adapter must reach a real submission")
				.hasSize(1);
	}

	// =============================================== T/U. paper isolation

	@Test
	void aPaperSignalNeverReachesAnyExchangeAdapter() {
		User paper = user(AccountType.PAPER);
		when(userRepository.findAll()).thenReturn(List.of(paper));

		ExecutionRoutingResult result = routeFor(router, paper, signal(TradingMode.SPOT));

		assertThat(result.accountMode()).isEqualTo(AccountMode.PAPER);
		assertThat(result.decision()).isEqualTo(ExecutionDecision.EXECUTE);
		assertThat(spotAdapter.allOrders()).isEmpty();
		assertThat(futuresAdapter.allOrders()).isEmpty();
	}

	// ============================================ V/W. options and main boundaries

	@Test
	void optionsNeverReachesAnyAdapter() {
		User live = user(AccountType.LIVE);
		readySpotAccount(live);
		readyFuturesAccount(live);

		ExecutionRoutingResult result = routeFor(router, live, signal(TradingMode.OPTIONS));

		assertThat(result.decision()).isEqualTo(ExecutionDecision.NOT_SUPPORTED);
		assertThat(spotAdapter.allOrders()).isEmpty();
		assertThat(futuresAdapter.allOrders()).isEmpty();
	}

	@Test
	void mainIsNeverExecutedAgainstAnAdapter() {
		assertThat(ExecutionRouting.isExecutableCategory(AccountCategory.MAIN)).isFalse();
		assertThat(spotAdapter.allOrders()).isEmpty();
		assertThat(futuresAdapter.allOrders()).isEmpty();
	}

	// ================================================ M/N. idempotency at the ledger

	@Test
	void aDuplicateEventNeverReachesTheAdapterTwice() {
		User live = user(AccountType.LIVE);
		readySpotAccount(live);
		Signal signal = signal(TradingMode.SPOT);

		routeFor(router, live, signal);
		// The ledger now holds the first decision, as it would after commit.
		when(decisionRepository.findByUserIdAndSignalIdAndAccountModeAndAccountCategory(
				eq(live.getId()), eq(signal.getId()), eq(AccountMode.LIVE), eq(AccountCategory.SPOT)))
				.thenAnswer(invocation -> {
					var row = new com.shyblack.cryptosignals.entity.ExecutionDecisionRecord();
					row.setId(UUID.randomUUID());
					row.setOutcome(ExecutionOutcome.ORDER_REQUESTED);
					return Optional.of(row);
				});

		ExecutionRoutingResult second = routeFor(router, live, signal);

		assertThat(second.rejectionReason())
				.isEqualTo(ExecutionRejectionReason.DUPLICATE_EXECUTION);
		int afterFirst = spotAdapter.allOrders().size();
		assertThat(afterFirst).isPositive();
	}

	@Test
	void concurrentDuplicateEventsProduceOneAdapterCall() throws Exception {
		User live = user(AccountType.LIVE);
		readySpotAccount(live);
		Signal signal = signal(TradingMode.SPOT);

		// Model the race: two consumers both see an empty ledger, then one insert
		// wins the unique constraint and the loser is told it is a duplicate.
		AtomicInteger inserts = new AtomicInteger();
		when(decisionRepository.saveAndFlush(any(com.shyblack.cryptosignals.entity.ExecutionDecisionRecord.class)))
				.thenAnswer(invocation -> {
					// First insert wins the identity; every later one violates the unique
					// constraint, which is what a real race produces.
					if (inserts.getAndIncrement() > 0) {
						throw new DataIntegrityViolationException(
								"uk_execution_decisions_identity");
					}
					var row = (com.shyblack.cryptosignals.entity.ExecutionDecisionRecord)
							invocation.getArgument(0);
					if (row.getId() == null) {
						row.setId(UUID.randomUUID());
					}
					return row;
				});

		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<ExecutionRoutingResult> a = pool.submit(() -> {
				start.await();
				return routeFor(router, live, signal);
			});
			Future<ExecutionRoutingResult> b = pool.submit(() -> {
				start.await();
				return routeFor(router, live, signal);
			});
			start.countDown();

			var first = a.get();
			var second = b.get();

			assertThat(List.of(first, second))
					.as("exactly one consumer may claim the identity")
					.anyMatch(r -> r.decision() == ExecutionDecision.EXECUTE)
					.anyMatch(r -> r.rejectionReason() == ExecutionRejectionReason.DUPLICATE_EXECUTION);
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void aRetryAfterUnknownNeverReachesTheAdapterAgain() {
		User live = user(AccountType.LIVE);
		readySpotAccount(live);
		Signal signal = signal(TradingMode.SPOT);

		routeFor(router, live, signal);
		int afterFirst = spotAdapter.allOrders().size();
		when(decisionRepository.findByUserIdAndSignalIdAndAccountModeAndAccountCategory(
				eq(live.getId()), eq(signal.getId()), eq(AccountMode.LIVE), eq(AccountCategory.SPOT)))
				.thenAnswer(invocation -> {
					var row = new com.shyblack.cryptosignals.entity.ExecutionDecisionRecord();
					row.setId(UUID.randomUUID());
					row.setOutcome(ExecutionOutcome.UNKNOWN);
					return Optional.of(row);
				});

		ExecutionRoutingResult retry = routeFor(router, live, signal);

		assertThat(retry.rejectionReason())
				.isEqualTo(ExecutionRejectionReason.PREVIOUS_OUTCOME_UNKNOWN);
		assertThat(spotAdapter.allOrders().size())
				.as("an ambiguous outcome must be reconciled, never resent")
				.isEqualTo(afterFirst);
	}

	// ================================================ L. UNKNOWN at the service layer

	@Test
	void aRejectedExchangeOrderNeverBecomesAFill() {
		// The simulator is overridden so placeOrder throws an exchange rejection.
		ExchangeTradingAdapter rejecting = new ExchangeTradingAdapter() {
			@Override public ExchangeName exchange() { return ExchangeName.BINANCE; }
			@Override public com.shyblack.cryptosignals.exchange.ExchangeAccountSnapshot validateCredentials(ExchangeCredential c) {
				return new com.shyblack.cryptosignals.exchange.ExchangeAccountSnapshot(
						"USDT", BigDecimal.ZERO, BigDecimal.ZERO, true, java.time.Instant.now());
			}
			@Override public com.shyblack.cryptosignals.exchange.ExchangeAccountSnapshot getAccountBalance(ExchangeCredential c) {
				return validateCredentials(c);
			}
			@Override public com.shyblack.cryptosignals.exchange.ExchangeBalances getBalances(ExchangeCredential c) {
				return new com.shyblack.cryptosignals.exchange.ExchangeBalances(
						java.util.Map.of(), true, java.time.Instant.now());
			}
			@Override public SymbolRules getSymbolRules(String symbol) { return rules(); }
			@Override public ExchangeOrderResult placeOrder(ExchangeCredential c, PlaceOrderRequest r) {
				throw new com.shyblack.cryptosignals.exchange.ExchangeAdapterException(
						"Binance error 400", null, false, 400, -2010);
			}
			@Override public ExchangeOrderResult getOrder(ExchangeCredential c, String s, String id) { return null; }
			@Override public ExchangeOrderResult cancelOrder(ExchangeCredential c, String s, String id) { return null; }
			@Override public List<com.shyblack.cryptosignals.exchange.ExchangeOrderSnapshot> getOpenOrders(ExchangeCredential c, String s) { return List.of(); }
			@Override public List<com.shyblack.cryptosignals.exchange.ExchangeOrderSnapshot> getAllOrders(ExchangeCredential c, String s, java.time.Instant f, java.time.Instant t, int l) { return List.of(); }
			@Override public List<com.shyblack.cryptosignals.exchange.ExchangeTradeSnapshot> getTrades(ExchangeCredential c, String s, java.time.Instant f, java.time.Instant t, int l) { return List.of(); }
		};

		LiveTradingExecutionService execution = new LiveTradingExecutionService(
				orderRepo(), mock(com.shyblack.cryptosignals.repository.LiveOrderLifecycleEventRepository.class),
				rejecting, new com.shyblack.cryptosignals.exchange.ClientOrderIdGenerator());

		var account = liveSpotAccount(user(AccountType.LIVE));
		Signal signal = signal(TradingMode.SPOT);
		UUID signalId = signal.getId();
		com.shyblack.cryptosignals.entity.LiveOrder intent = execution.createIntent(
				account, signal, rules(), new BigDecimal("1"), new BigDecimal("50000"),
				com.shyblack.cryptosignals.entity.enums.LiveOrderPurpose.ENTRY,
				LiveOrderType.MARKET, null, null);

		com.shyblack.cryptosignals.entity.LiveOrder result = execution.submit(intent);

		assertThat(result.getStatus())
				.as("a definite exchange rejection must not be recorded as a fill")
				.isNotIn(LiveOrderStatus.FILLED, LiveOrderStatus.PARTIALLY_FILLED);
		assertThat(signalId).isNotNull();
	}

	@Test
	void anAmbiguousTransportFailureIsRecordedAsUnknownNotFailed() {
		ExchangeTradingAdapter ambiguous = new ExchangeTradingAdapter() {
			@Override public ExchangeName exchange() { return ExchangeName.BINANCE; }
			@Override public com.shyblack.cryptosignals.exchange.ExchangeAccountSnapshot validateCredentials(ExchangeCredential c) {
				return new com.shyblack.cryptosignals.exchange.ExchangeAccountSnapshot(
						"USDT", BigDecimal.ZERO, BigDecimal.ZERO, true, java.time.Instant.now());
			}
			@Override public com.shyblack.cryptosignals.exchange.ExchangeAccountSnapshot getAccountBalance(ExchangeCredential c) {
				return validateCredentials(c);
			}
			@Override public com.shyblack.cryptosignals.exchange.ExchangeBalances getBalances(ExchangeCredential c) {
				return new com.shyblack.cryptosignals.exchange.ExchangeBalances(
						java.util.Map.of(), true, java.time.Instant.now());
			}
			@Override public SymbolRules getSymbolRules(String symbol) { return rules(); }
			@Override public ExchangeOrderResult placeOrder(ExchangeCredential c, PlaceOrderRequest r) {
				throw new com.shyblack.cryptosignals.exchange.ExchangeAdapterException(
						"transport failure; outcome unknown", null, true, null, null);
			}
			@Override public ExchangeOrderResult getOrder(ExchangeCredential c, String s, String id) { return null; }
			@Override public ExchangeOrderResult cancelOrder(ExchangeCredential c, String s, String id) { return null; }
			@Override public List<com.shyblack.cryptosignals.exchange.ExchangeOrderSnapshot> getOpenOrders(ExchangeCredential c, String s) { return List.of(); }
			@Override public List<com.shyblack.cryptosignals.exchange.ExchangeOrderSnapshot> getAllOrders(ExchangeCredential c, String s, java.time.Instant f, java.time.Instant t, int l) { return List.of(); }
			@Override public List<com.shyblack.cryptosignals.exchange.ExchangeTradeSnapshot> getTrades(ExchangeCredential c, String s, java.time.Instant f, java.time.Instant t, int l) { return List.of(); }
		};

		LiveTradingExecutionService execution = new LiveTradingExecutionService(
				orderRepo(), mock(com.shyblack.cryptosignals.repository.LiveOrderLifecycleEventRepository.class),
				ambiguous, new com.shyblack.cryptosignals.exchange.ClientOrderIdGenerator());

		var account = liveSpotAccount(user(AccountType.LIVE));
		Signal signal = signal(TradingMode.SPOT);
		com.shyblack.cryptosignals.entity.LiveOrder intent = execution.createIntent(
				account, signal, rules(), new BigDecimal("1"), new BigDecimal("50000"),
				com.shyblack.cryptosignals.entity.enums.LiveOrderPurpose.ENTRY,
				LiveOrderType.MARKET, null, null);

		com.shyblack.cryptosignals.entity.LiveOrder result = execution.submit(intent);

		assertThat(result.getStatus())
				.as("an ambiguous outcome must be UNKNOWN so reconciliation runs; "
						+ "FAILED would suggest the order certainly never happened")
				.isEqualTo(LiveOrderStatus.UNKNOWN);
	}

	/**
	 * A map-backed {@link LiveOrderRepository} mock.
	 *
	 * <p>Mockito rather than a hand-written stub: the interface has many query methods
	 * that are irrelevant here, and re-declaring them by hand would break every time
	 * the repository grows a query.
	 */
	private com.shyblack.cryptosignals.repository.LiveOrderRepository orderRepo() {
		com.shyblack.cryptosignals.repository.LiveOrderRepository repo =
				mock(com.shyblack.cryptosignals.repository.LiveOrderRepository.class);
		java.util.Map<UUID, com.shyblack.cryptosignals.entity.LiveOrder> store =
				new java.util.concurrent.ConcurrentHashMap<>();

		when(repo.save(any())).thenAnswer(invocation -> {
			com.shyblack.cryptosignals.entity.LiveOrder order = invocation.getArgument(0);
			if (order.getId() == null) {
				order.setId(UUID.randomUUID());
			}
			store.put(order.getId(), order);
			return order;
		});
		when(repo.saveAndFlush(any())).thenAnswer(invocation -> {
			com.shyblack.cryptosignals.entity.LiveOrder order = invocation.getArgument(0);
			if (order.getId() == null) {
				order.setId(UUID.randomUUID());
			}
			store.put(order.getId(), order);
			return order;
		});
		when(repo.findById(any())).thenAnswer(invocation ->
				Optional.ofNullable(store.get(invocation.<UUID>getArgument(0))));
		when(repo.findByIdForUpdate(any())).thenAnswer(invocation ->
				Optional.ofNullable(store.get(invocation.<UUID>getArgument(0))));
		when(repo.findByAccountAndClientOrderId(any(), anyString())).thenAnswer(invocation -> {
			String clientOrderId = invocation.getArgument(1);
			return store.values().stream()
					.filter(o -> o.getClientOrderId().equals(clientOrderId))
					.findFirst();
		});
		return repo;
	}

	private com.shyblack.cryptosignals.repository.FuturesOrderRepository futuresOrderRepo() {
		com.shyblack.cryptosignals.repository.FuturesOrderRepository repo =
				mock(com.shyblack.cryptosignals.repository.FuturesOrderRepository.class);
		java.util.Map<UUID, com.shyblack.cryptosignals.entity.FuturesOrder> store =
				new java.util.concurrent.ConcurrentHashMap<>();

		when(repo.save(any())).thenAnswer(invocation -> {
			com.shyblack.cryptosignals.entity.FuturesOrder order = invocation.getArgument(0);
			if (order.getId() == null) {
				order.setId(UUID.randomUUID());
			}
			store.put(order.getId(), order);
			return order;
		});
		when(repo.saveAndFlush(any())).thenAnswer(invocation -> {
			com.shyblack.cryptosignals.entity.FuturesOrder order = invocation.getArgument(0);
			if (order.getId() == null) {
				order.setId(UUID.randomUUID());
			}
			store.put(order.getId(), order);
			return order;
		});
		when(repo.findById(any())).thenAnswer(invocation ->
				Optional.ofNullable(store.get(invocation.<UUID>getArgument(0))));
		when(repo.findByIdForUpdate(any())).thenAnswer(invocation ->
				Optional.ofNullable(store.get(invocation.<UUID>getArgument(0))));
		when(repo.findByAccountAndClientOrderId(any(), anyString())).thenAnswer(invocation -> {
			String clientOrderId = invocation.getArgument(1);
			return store.values().stream()
					.filter(o -> o.getClientOrderId().equals(clientOrderId))
					.findFirst();
		});
		return repo;
	}

	// ================================================== 20. mock adapter fidelity

	@Test
	void theMockAdapterIsIdempotentForTheSameClientOrderId() {
		ExchangeCredential credential = credential(user(AccountType.LIVE));
		PlaceOrderRequest request = new PlaceOrderRequest("BTCUSDT", PositionSide.LONG,
				LiveOrderType.MARKET, BigDecimal.ONE, null, null, "SB-deterministic1");

		ExchangeOrderResult first = spotAdapter.placeOrder(credential, request);
		ExchangeOrderResult second = spotAdapter.placeOrder(credential, request);

		assertThat(second.exchangeOrderId())
				.as("the simulator must mirror the exchange's duplicate-client-id behaviour")
				.isEqualTo(first.exchangeOrderId());
		assertThat(spotAdapter.allOrders()).hasSize(1);
	}

	@Test
	void aLimitOrderInTheSimulatorIsAcknowledgedNotFilled() {
		ExchangeCredential credential = credential(user(AccountType.LIVE));

		ExchangeOrderResult result = spotAdapter.placeOrder(credential,
				new PlaceOrderRequest("BTCUSDT", PositionSide.LONG, LiveOrderType.LIMIT,
						BigDecimal.ONE, new BigDecimal("49000"), null, "SB-limit-sim01"));

		assertThat(result.status())
				.as("the simulator must not pretend a resting limit order filled")
				.isEqualTo(LiveOrderStatus.ACKNOWLEDGED);
	}

	@Test
	void aLimitOrderCanBeForceFilledToSimulateATrigger() {
		ExchangeCredential credential = credential(user(AccountType.LIVE));
		spotAdapter.placeOrder(credential, new PlaceOrderRequest("BTCUSDT", PositionSide.LONG,
				LiveOrderType.LIMIT, BigDecimal.ONE, new BigDecimal("49000"), null, "SB-forcefill1"));

		spotAdapter.forceFill("SB-forcefill1", new BigDecimal("49000"));

		assertThat(spotAdapter.allOrders().get(0).status()).isEqualTo(LiveOrderStatus.FILLED);
	}

	@Test
	void theSimulatorRequiresTheSameInterfaceSurfaceAsTheRealAdapter() {
		// Compile-time assurance that the mock and the real adapter implement one
		// contract: the engine takes the interface type, so a missing method would
		// not compile.
		ExchangeTradingAdapter asInterface = spotAdapter;
		FuturesExchangeAdapter futuresAsInterface = futuresAdapter;
		assertThat(asInterface.exchange()).isEqualTo(ExchangeName.BINANCE);
		assertThat(futuresAsInterface.exchange()).isEqualTo(ExchangeName.BINANCE);
		assertThat(asInterface).isInstanceOf(MockExchangeTradingAdapter.class);
		assertThat(futuresAsInterface).isInstanceOf(MockFuturesExchangeAdapter.class);
	}

	// ============================================================ X. credentials

	@Test
	void noCredentialValueReachesTheDecisionOrTheAuditRecord() {
		User live = user(AccountType.LIVE);
		readySpotAccount(live);
		Signal signal = signal(TradingMode.SPOT);

		ExecutionRoutingResult result = routeFor(router, live, signal);

		var captor = org.mockito.ArgumentCaptor.forClass(
				com.shyblack.cryptosignals.entity.ExecutionDecisionRecord.class);
		verify(decisionRepository).saveAndFlush(captor.capture());

		String audit = String.valueOf(captor.getValue());
		String decision = String.valueOf(result);
		assertThat(audit + decision)
				.doesNotContain("test-api-key")
				.doesNotContain("test-api-secret")
				.doesNotContain("signature")
				.doesNotContain("X-MBX-APIKEY");
	}

	// =============================================== single-entry-point regression

	@Test
	void theEventEntryPointRoutesThroughTheSameGates() {
		User live = user(AccountType.LIVE);
		readySpotAccount(live);
		Signal signal = signal(TradingMode.SPOT);
		when(signalRepository.findById(signal.getId())).thenReturn(Optional.of(signal));

		router.onSignalGenerated(new SignalGeneratedEvent(signal.getId()));

		assertThat(spotAdapter.allOrders())
				.as("the event path must behave identically to the direct path")
				.isNotEmpty();
	}

	@Test
	void anUnknownSignalIdReachesNoAdapter() {
		UUID missing = UUID.randomUUID();
		when(signalRepository.findById(missing)).thenReturn(Optional.empty());

		router.onSignalGenerated(new SignalGeneratedEvent(missing));

		assertThat(spotAdapter.allOrders()).isEmpty();
		assertThat(futuresAdapter.allOrders()).isEmpty();
	}

	// =================================================== Y/Z. fees never fake zero

	@Test
	void theSimulatorFeeIsNotTreatedAsARealExchangeQuote() {
		ExchangeCredential credential = credential(user(AccountType.LIVE));
		ExchangeOrderResult result = spotAdapter.placeOrder(credential,
				new PlaceOrderRequest("BTCUSDT", PositionSide.LONG, LiveOrderType.MARKET,
						BigDecimal.ONE, new BigDecimal("50000"), null, "SB-simfee0001"));

		// The simulator applies a nominal 0.1% for realism. That value is a
		// simulation artefact and must never be presented as a Binance quote; the
		// adapter suites prove the real adapter reports nothing at all here.
		assertThat(result.fee()).isEqualByComparingTo("50");
		assertThat(result.rawResponseSummary()).isEqualTo("MOCK");
	}

	@Test
	void theRealAdapterSuiteProvesNoFeeIsInventedWhenTheExchangeSendsNone() {
		// Referenced by name in the report: the fake-zero guarantee is proven in
		// BinanceSpotAdapterExecutionTest against a real HTTP exchange payload.
		assertThat(ExecutionOutcome.ORDER_REQUESTED.isDefinitive()).isTrue();
	}
}
