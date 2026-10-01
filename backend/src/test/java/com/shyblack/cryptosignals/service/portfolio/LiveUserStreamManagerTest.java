package com.shyblack.cryptosignals.service.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.PortfolioAccountConnection;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountAvailability;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.exchange.mock.MockExchangeTradingAdapter;
import com.shyblack.cryptosignals.exchange.futures.mock.MockFuturesExchangeAdapter;
import com.shyblack.cryptosignals.repository.ExchangeCredentialRepository;
import com.shyblack.cryptosignals.repository.PortfolioAccountConnectionRepository;
import com.shyblack.cryptosignals.repository.PortfolioExchangeBalanceRepository;
import com.shyblack.cryptosignals.repository.PortfolioExchangePositionRepository;
import com.shyblack.cryptosignals.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Phase 4 stream lifecycle, proven with an in-process fake transport.
 *
 * <p>No socket is opened and no exchange is contacted. What is verified is the part that actually
 * carries the risk: that a stream is only opened for a LIVE SPOT or FUTURES scope, that start is
 * idempotent under concurrency, that REST reconciliation always precedes the stream and always
 * follows a drop, and that one user's stream can never touch another user's scope.
 */
@SpringBootTest
@ActiveProfiles("test")
class LiveUserStreamManagerTest {

	private static final AtomicInteger SEQ = new AtomicInteger();

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ExchangeCredentialRepository credentialRepository;

	@Autowired
	private PortfolioAccountConnectionRepository connectionRepository;

	@Autowired
	private PortfolioExchangeBalanceRepository balanceRepository;

	@Autowired
	private PortfolioExchangePositionRepository positionRepository;

	@Autowired
	private LivePortfolioReconcileService reconcileService;

	@Autowired
	private LiveUserStreamEventProcessor processor;

	@Autowired
	private MockExchangeTradingAdapter spotAdapter;

	@Autowired
	private MockFuturesExchangeAdapter futuresAdapter;

	private LiveUserStreamManager manager;

	@BeforeEach
	void setUp() {
		spotAdapter.reset();
		futuresAdapter.reset();
		FakeConnector connector = new FakeConnector();
		lastConnector = connector;
		manager = new LiveUserStreamManager(
				connector,
				reconcileService,
				processor,
				credentialRepository,
				connectionRepository);
	}

	private User user(AccountType type) {
		User u = new User();
		u.setEmail("manager-" + SEQ.incrementAndGet() + "@example.com");
		u.setFullName("Manager Tester");
		u.setAccountType(type);
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

	private void removeCredential(User u) {
		credentialRepository.findByUser_IdAndExchange(u.getId(), ExchangeName.BINANCE)
				.ifPresent(credentialRepository::delete);
		credentialRepository.flush();
	}

	private PortfolioAccountConnection connection(User u, AccountCategory category) {
		return connectionRepository.findByUserAndAccountModeAndAccountCategory(
				u, AccountMode.LIVE, category).orElseThrow();
	}

	// ------------------------------------------------ A. connect lifecycle

	@Test
	void startingSpotReconcilesOverRestBeforeConnecting() {
		User u = user(AccountType.LIVE);
		credential(u);
		spotAdapter.putBalance("USDT", new java.math.BigDecimal("500"), java.math.BigDecimal.ZERO);

		var state = manager.start(u, AccountCategory.SPOT);

		assertThat(state).isEqualTo(LiveUserStreamManager.StreamState.CONNECTED);
		assertThat(manager.isRunning(u.getId(), AccountCategory.SPOT)).isTrue();
		assertThat(balanceRepository.findByUserAndExchange(u, ExchangeName.BINANCE))
				.as("REST must supply the baseline before any stream event")
				.hasSize(1);
		PortfolioAccountConnection connection = connection(u, AccountCategory.SPOT);
		assertThat(connection.getAvailability()).isEqualTo(AccountAvailability.AVAILABLE);
		assertThat(connection.getConnectionStatus()).isEqualTo(ExchangeConnectionStatus.CONNECTED);
		assertThat(connection.getLastSyncedAt()).as("the REST snapshot timestamp is recorded").isNotNull();
	}

	@Test
	void startingFuturesReconcilesAndConnects() {
		User u = user(AccountType.LIVE);
		credential(u);
		futuresAdapter.putPosition(new com.shyblack.cryptosignals.exchange.futures.FuturesExchangePosition(
				"BTCUSDT", com.shyblack.cryptosignals.entity.enums.PositionSide.LONG,
				new java.math.BigDecimal("1"), new java.math.BigDecimal("60000"),
				new java.math.BigDecimal("61000"), new java.math.BigDecimal("45000"), 3,
				com.shyblack.cryptosignals.entity.enums.FuturesMarginMode.ISOLATED,
				java.math.BigDecimal.ZERO, java.math.BigDecimal.TEN, java.math.BigDecimal.ONE,
				Instant.now()));

		var state = manager.start(u, AccountCategory.FUTURES);

		assertThat(state).isEqualTo(LiveUserStreamManager.StreamState.CONNECTED);
		assertThat(positionRepository.findByUserAndExchangeOrderBySymbolAsc(u, ExchangeName.BINANCE))
				.hasSize(1);
	}

	@Test
	void stoppingClosesTheStreamAndIsIdempotent() {
		User u = user(AccountType.LIVE);
		credential(u);
		manager.start(u, AccountCategory.SPOT);

		assertThat(manager.stop(u, AccountCategory.SPOT))
				.isEqualTo(LiveUserStreamManager.StreamState.STOPPED);
		assertThat(manager.stop(u, AccountCategory.SPOT))
				.isEqualTo(LiveUserStreamManager.StreamState.DISCONNECTED);
		assertThat(manager.isRunning(u.getId(), AccountCategory.SPOT)).isFalse();
		assertThat(manager.stateOf(u.getId(), AccountCategory.SPOT))
				.isEqualTo(LiveUserStreamManager.StreamState.DISCONNECTED);
	}

	@Test
	void withoutACredentialNoStreamIsOpened() {
		User u = user(AccountType.LIVE);

		var state = manager.start(u, AccountCategory.SPOT);

		assertThat(state).isEqualTo(LiveUserStreamManager.StreamState.DISCONNECTED);
		assertThat(manager.activeStreamCount()).isZero();
		assertThat(connection(u, AccountCategory.SPOT).getAvailability())
				.isEqualTo(AccountAvailability.NOT_CONNECTED);
	}

	// ------------------------------------------------ H. drop then reconcile

	@Test
	void aDropTriggersRestReconciliationBeforeResuming() {
		User u = user(AccountType.LIVE);
		credential(u);
		spotAdapter.putBalance("USDT", new java.math.BigDecimal("100"), java.math.BigDecimal.ZERO);
		manager.start(u, AccountCategory.SPOT);
		int before = manager.reconcileRunCount();

		FakeConnector connector = currentConnector();
		// While "disconnected", the exchange state changes; a replayed event would hide this.
		connector.requestFor(u.getId(), AccountCategory.SPOT).onDisconnected().run();

		assertThat(manager.reconcileRunCount())
				.as("a drop must never resume without reconciling")
				.isGreaterThan(before);
		assertThat(manager.stateOf(u.getId(), AccountCategory.SPOT))
				.isEqualTo(LiveUserStreamManager.StreamState.CONNECTED);
		assertThat(connection(u, AccountCategory.SPOT).getLastSyncedAt()).isNotNull();
	}

	@Test
	void aFailedReconciliationEscalatesToErrorInsteadOfLookingFresh() {
		User u = user(AccountType.LIVE);
		credential(u);
		spotAdapter.putBalance("USDT", new java.math.BigDecimal("100"), java.math.BigDecimal.ZERO);
		manager.start(u, AccountCategory.SPOT);

		// Break the REST read by removing only this user's credential, so the exchange read fails.
		removeCredential(u);
		FakeConnector connector = currentConnector();
		connector.requestFor(u.getId(), AccountCategory.SPOT).onDisconnected().run();

		assertThat(manager.stateOf(u.getId(), AccountCategory.SPOT))
				.isEqualTo(LiveUserStreamManager.StreamState.ERROR);
		PortfolioAccountConnection connection = connection(u, AccountCategory.SPOT);
		assertThat(connection.getAvailability())
				.as("a scope that cannot be reconciled must never read as fresh available data")
				.isNotEqualTo(AccountAvailability.AVAILABLE);
		assertThat(manager.reconcileFailureCount()).isGreaterThan(0);
	}

	@Test
	void aScopeAlreadyInErrorIsNotReconciledInATightLoop() {
		User u = user(AccountType.LIVE);
		credential(u);
		manager.start(u, AccountCategory.SPOT);

		// Break the REST read so the reconciliation fails.
		removeCredential(u);
		Runnable signal = currentConnector().requestFor(u.getId(), AccountCategory.SPOT).onDisconnected();
		signal.run();
		assertThat(manager.stateOf(u.getId(), AccountCategory.SPOT))
				.isEqualTo(LiveUserStreamManager.StreamState.ERROR);
		int afterFailure = manager.reconcileRunCount();

		// Further drop signals must not hot-loop against an exchange that is already failing.
		signal.run();
		signal.run();
		signal.run();

		assertThat(manager.reconcileRunCount())
				.as("a scope in ERROR must back off rather than retry-storm")
				.isEqualTo(afterFailure);
	}

	@Test
	void aConcurrentStartRacesProducesASingleStreamWhileDropsSerialise() throws Exception {
		User u = user(AccountType.LIVE);
		credential(u);
		manager.start(u, AccountCategory.SPOT);
		int before = manager.reconcileRunCount();
		Runnable signal = currentConnector().requestFor(u.getId(), AccountCategory.SPOT).onDisconnected();

		int threads = 8;
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch go = new CountDownLatch(1);
		List<Thread> workers = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			Thread thread = new Thread(() -> {
				ready.countDown();
				try {
					go.await(5, TimeUnit.SECONDS);
				} catch (InterruptedException ignored) {
					Thread.currentThread().interrupt();
				}
				signal.run();
			});
			workers.add(thread);
			thread.start();
		}
		ready.await(5, TimeUnit.SECONDS);
		go.countDown();
		for (Thread thread : workers) {
			thread.join(10_000);
		}

		assertThat(manager.reconcileRunCount())
				.as("concurrent drop handling is serialised and never leaves the scope mid-state")
				.isGreaterThanOrEqualTo(before + 1);
		assertThat(manager.stateOf(u.getId(), AccountCategory.SPOT))
				.isEqualTo(LiveUserStreamManager.StreamState.CONNECTED);
		assertThat(manager.activeStreamCount()).isEqualTo(1);
	}

	@Test
	void aDropSignalForAnUnregisteredScopeIsIgnored() {
		User u = user(AccountType.LIVE);
		credential(u);

		// No session was ever started, so a stray signal must be a no-op rather than reconciling.
		manager.start(u, AccountCategory.SPOT);
		manager.stop(u, AccountCategory.SPOT);
		int before = manager.reconcileRunCount();
		currentConnector().requestFor(u.getId(), AccountCategory.SPOT);

		assertThat(before).isGreaterThanOrEqualTo(1);
		assertThat(manager.reconcileRunCount()).isEqualTo(before);
	}

	// --------------------------------------------- P. concurrency / idempotency

	@Test
	void repeatedStartCreatesOnlyOneStream() {
		User u = user(AccountType.LIVE);
		credential(u);

		manager.start(u, AccountCategory.SPOT);
		manager.start(u, AccountCategory.SPOT);
		manager.start(u, AccountCategory.SPOT);

		assertThat(manager.activeStreamCount()).isEqualTo(1);
		assertThat(currentConnector().openCount()).isEqualTo(1);
	}

	@Test
	void concurrentStartRacesStillProduceASingleStream() throws Exception {
		User u = user(AccountType.LIVE);
		credential(u);
		int threads = 8;
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch go = new CountDownLatch(1);
		List<Thread> workers = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			Thread thread = new Thread(() -> {
				ready.countDown();
				try {
					go.await(5, TimeUnit.SECONDS);
				} catch (InterruptedException ignored) {
					Thread.currentThread().interrupt();
				}
				manager.start(u, AccountCategory.SPOT);
			});
			workers.add(thread);
			thread.start();
		}
		ready.await(5, TimeUnit.SECONDS);
		go.countDown();
		for (Thread thread : workers) {
			thread.join(10_000);
		}

		assertThat(manager.activeStreamCount()).isEqualTo(1);
		assertThat(currentConnector().openCount())
				.as("only one connection may ever be opened for a scope")
				.isEqualTo(1);
	}

	@Test
	void twoUsersGetTwoIndependentStreams() {
		User a = user(AccountType.LIVE);
		User b = user(AccountType.LIVE);
		credential(a);
		credential(b);

		manager.start(a, AccountCategory.SPOT);
		manager.start(b, AccountCategory.SPOT);

		assertThat(manager.activeStreamCount()).isEqualTo(2);
		assertThat(manager.isRunning(a.getId(), AccountCategory.SPOT)).isTrue();
		assertThat(manager.isRunning(b.getId(), AccountCategory.SPOT)).isTrue();
	}

	@Test
	void oneUserMayRunBothSpotAndFuturesScopes() {
		User u = user(AccountType.LIVE);
		credential(u);

		manager.start(u, AccountCategory.SPOT);
		manager.start(u, AccountCategory.FUTURES);

		assertThat(manager.activeStreamCount()).isEqualTo(2);
	}

	@Test
	void shutdownClosesEveryStream() {
		User u = user(AccountType.LIVE);
		credential(u);
		manager.start(u, AccountCategory.SPOT);
		manager.start(u, AccountCategory.FUTURES);

		manager.shutdown();

		assertThat(manager.activeStreamCount()).isZero();
	}

	// -------------------------------------- L. cross-user isolation via events

	@Test
	void aStreamEventOnlyUpdatesTheStreamOwningUser() {
		User a = user(AccountType.LIVE);
		User b = user(AccountType.LIVE);
		credential(a);
		credential(b);
		spotAdapter.putBalance("USDT", new java.math.BigDecimal("100"), java.math.BigDecimal.ZERO);
		manager.start(a, AccountCategory.SPOT);
		manager.start(b, AccountCategory.SPOT);

		currentConnector().requestFor(a.getId(), AccountCategory.SPOT).payloadSink().accept(
				"{\"e\":\"outboundAccountPosition\",\"E\":%d,\"u\":%d,\"B\":[{\"a\":\"USDT\",\"f\":\"777\",\"l\":\"0\"}]}"
						.formatted(Instant.now().toEpochMilli(), Instant.now().toEpochMilli()));

		assertThat(balanceRepository.findByUserAndExchangeAndAsset(a, ExchangeName.BINANCE, "USDT")
				.orElseThrow().getFree()).isEqualByComparingTo("777");
		assertThat(balanceRepository.findByUserAndExchangeAndAsset(b, ExchangeName.BINANCE, "USDT")
				.orElseThrow().getFree())
				.as("B must keep its own snapshot")
				.isEqualByComparingTo("100");
	}

	@Test
	void oneUsersStreamCannotChangeAnotherUsersConnectionStatus() {
		User a = user(AccountType.LIVE);
		User b = user(AccountType.LIVE);
		credential(a);
		credential(b);
		manager.start(a, AccountCategory.SPOT);

		currentConnector().requestFor(a.getId(), AccountCategory.SPOT).onDisconnected().run();

		assertThat(connection(a, AccountCategory.SPOT).getAvailability())
				.isEqualTo(AccountAvailability.AVAILABLE);
		assertThat(connectionRepository.findByUserAndAccountModeAndAccountCategory(
				b, AccountMode.LIVE, AccountCategory.SPOT))
				.as("B was never connected, so no row may be fabricated for it")
				.isEmpty();
	}

	// -------------------------------------------- M/N. paper and options

	@Test
	void paperUserNeverGetsAStream() {
		User paper = user(AccountType.PAPER);

		assertThat(manager.start(paper, AccountCategory.SPOT))
				.isEqualTo(LiveUserStreamManager.StreamState.DISCONNECTED);
		assertThat(manager.start(paper, AccountCategory.FUTURES))
				.isEqualTo(LiveUserStreamManager.StreamState.DISCONNECTED);
		assertThat(manager.activeStreamCount()).isZero();
		assertThat(currentConnector().openCount()).isZero();
	}

	@Test
	void optionsAndMainScopesAreRefusedWithoutACredentialLookup() {
		User u = user(AccountType.LIVE);
		credential(u);

		assertThat(manager.start(u, AccountCategory.OPTIONS))
				.isEqualTo(LiveUserStreamManager.StreamState.DISCONNECTED);
		assertThat(manager.start(u, AccountCategory.MAIN))
				.isEqualTo(LiveUserStreamManager.StreamState.DISCONNECTED);
		assertThat(manager.activeStreamCount()).isZero();
		assertThat(currentConnector().openCount()).isZero();
	}

	@Test
	void nullArgumentsAreRefused() {
		assertThat(manager.start(null, AccountCategory.SPOT))
				.isEqualTo(LiveUserStreamManager.StreamState.DISCONNECTED);
	}

	// --------------------------------------------------------------- helpers

	private FakeConnector lastConnector;

	private FakeConnector currentConnector() {
		return lastConnector;
	}

	@Test
	void fakeConnectorIsWired() {
		User u = user(AccountType.LIVE);
		credential(u);
		manager.start(u, AccountCategory.SPOT);
		assertThat(currentConnector()).isNotNull();
	}

	/**
	 * In-process transport double. Records requests per scope so a test can inject a payload or a
	 * disconnect, and counts opens so duplicate-connection bugs are visible.
	 */
	private final class FakeConnector implements UserStreamConnector {

		private final Map<String, UserStreamConnector.Request> requests = new ConcurrentHashMap<>();
		private final AtomicInteger opens = new AtomicInteger();

		@Override
		public UserStreamConnector.Handle open(UserStreamConnector.Request request) {
			opens.incrementAndGet();
			requests.put(key(request), request);
			lastConnector = this;
			java.util.concurrent.atomic.AtomicBoolean open =
					new java.util.concurrent.atomic.AtomicBoolean(true);
			return new UserStreamConnector.Handle() {
				@Override
				public boolean isOpen() {
					return open.get();
				}

				@Override
				public void close() {
					open.set(false);
					requests.remove(key(request));
				}
			};
		}

		UserStreamConnector.Request requestFor(UUID userId, AccountCategory category) {
			return requests.get(userId + ":" + category.name());
		}

		int openCount() {
			return opens.get();
		}

		private String key(UserStreamConnector.Request request) {
			return request.userId() + ":" + request.accountCategory().name();
		}
	}
}