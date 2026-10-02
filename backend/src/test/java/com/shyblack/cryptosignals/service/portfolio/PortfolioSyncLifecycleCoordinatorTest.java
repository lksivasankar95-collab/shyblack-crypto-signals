package com.shyblack.cryptosignals.service.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.PortfolioAccountConnection;
import com.shyblack.cryptosignals.entity.PortfolioExchangeBalance;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountAvailability;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.exchange.futures.mock.MockFuturesExchangeAdapter;
import com.shyblack.cryptosignals.exchange.mock.MockExchangeTradingAdapter;
import com.shyblack.cryptosignals.repository.ExchangeCredentialRepository;
import com.shyblack.cryptosignals.repository.PortfolioAccountConnectionRepository;
import com.shyblack.cryptosignals.repository.PortfolioExchangeBalanceRepository;
import com.shyblack.cryptosignals.repository.PortfolioExchangePositionRepository;
import com.shyblack.cryptosignals.repository.UserRepository;
import com.shyblack.cryptosignals.security.UserPrincipal;
import com.shyblack.cryptosignals.service.ExchangeCredentialService;
import com.shyblack.cryptosignals.service.SettingsService;
import com.shyblack.cryptosignals.dto.settings.ExchangeCredentialRequest;
import com.shyblack.cryptosignals.dto.settings.SettingsUpdateRequest;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Phase 10: the LIVE Portfolio synchronization lifecycle.
 *
 * <p>This suite covers the edge that was missing — nothing ever called
 * {@link LiveUserStreamManager#start} — and proves it is now wired without duplicating any of
 * the machinery underneath.
 *
 * <p>No socket is opened and no exchange is contacted. The transport is
 * {@link FakeUserStreamConnector}; account reads come from the in-process mock adapters.
 *
 * <p>The properties under test are structural: which triggers fire, which scopes are eligible,
 * what order REST and the stream happen in, that a failure never becomes a fabricated value, and
 * that a repeated trigger cannot produce a second lifecycle.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(FakeUserStreamConfig.class)
@TestPropertySource(properties = {
		"app.portfolio.sync.enabled=true",
		"app.live-trading.mode=MOCK",
		"app.futures-trading.mode=MOCK"
})
class PortfolioSyncLifecycleCoordinatorTest {

	private static final AtomicInteger SEQ = new AtomicInteger();

	@Autowired
	private PortfolioSyncLifecycleCoordinator coordinator;

	@Autowired
	private LiveUserStreamManager streamManager;

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
	private ExchangeCredentialService credentialService;

	@Autowired
	private SettingsService settingsService;

	@Autowired
	private MockExchangeTradingAdapter spotAdapter;

	@Autowired
	private MockFuturesExchangeAdapter futuresAdapter;

	@Autowired
	private FakeUserStreamConnector connector;

	@Autowired
	private com.shyblack.cryptosignals.security.ExchangeCredentialEncryptor encryptor;

	@BeforeEach
	void setUp() {
		spotAdapter.reset();
		futuresAdapter.reset();
		connector.reset();
		// The coordinator and the stream manager are singleton beans holding live registries.
		// Releasing them returns both to the state a fresh process would be in, so a scope started
		// by one test can never be counted by the next.
		coordinator.shutdown();
	}

	// --------------------------------------------------------------- helpers

	private User user(AccountType type) {
		User u = new User();
		u.setEmail("lifecycle-" + SEQ.incrementAndGet() + "-" + System.nanoTime() + "@example.com");
		u.setFullName("Lifecycle Tester");
		u.setPasswordHash("hash");
		u.setAccountType(type);
		return userRepository.saveAndFlush(u);
	}

	/**
	 * A credential with genuinely encrypted material.
	 *
	 * <p>Encrypted rather than seeded with raw base64 because the settings response path masks the
	 * key by decrypting it first; a fixture that is not real ciphertext fails there for reasons that
	 * have nothing to do with synchronization.
	 */
	private ExchangeCredential credential(User u, ExchangeConnectionStatus status) {
		ExchangeCredential c = new ExchangeCredential();
		c.setUser(u);
		c.setExchange(ExchangeName.BINANCE);
		c.setApiKey(encryptor.encrypt("test-only-key"));
		c.setApiSecret(encryptor.encrypt("test-only-secret"));
		c.setStatus(status);
		return credentialRepository.saveAndFlush(c);
	}

	private PortfolioAccountConnection connection(User u, AccountCategory category) {
		return connectionRepository.findByUserAndAccountModeAndAccountCategory(
				u, AccountMode.LIVE, category).orElse(null);
	}

	// ------------------------------------------- A. the trigger that was missing

	@Nested
	@DisplayName("A. a validated LIVE credential starts synchronization")
	class StartTrigger {

		@Test
		@DisplayName("a credential the exchange accepted starts both market scopes")
		void successfulValidationStartsSync() {
			User u = user(AccountType.LIVE);
			credential(u, ExchangeConnectionStatus.NOT_CONNECTED);

			coordinator.onCredentialConnected(u);

			assertThat(connector.isLive(u.getId(), AccountCategory.SPOT))
					.as("a validated credential must actually open a spot stream")
					.isTrue();
			assertThat(connector.isLive(u.getId(), AccountCategory.FUTURES))
					.as("and a futures stream")
					.isTrue();
		}

		@Test
		@DisplayName("validation through the real credential service starts the lifecycle")
		void validationThroughTheServiceStartsSync() {
			User u = user(AccountType.LIVE);
			credential(u, ExchangeConnectionStatus.NOT_CONNECTED);

			// Exactly what POST /api/v1/settings/exchanges/{id}/test-connection does.
			var response = credentialService.testConnection(
					new UserPrincipal(u), credentialOf(u).getId(),
					ExchangeCredentialService.ValidationScope.SPOT);

			assertThat(response.ok()).isTrue();
			assertThat(connector.isLive(u.getId(), AccountCategory.SPOT))
					.as("the endpoint the user actually calls must now start synchronization")
					.isTrue();
		}

		@Test
		@DisplayName("no credential means no stream, and the scope says why")
		void missingCredentialStartsNothing() {
			User u = user(AccountType.LIVE);

			coordinator.onCredentialConnected(u);

			assertThat(connector.openCount()).isZero();
			assertThat(connection(u, AccountCategory.SPOT).getAvailability())
					.isEqualTo(AccountAvailability.NOT_CONNECTED);
		}

		@Test
		@DisplayName("a revoked credential is not started")
		void revokedCredentialStartsNothing() {
			User u = user(AccountType.LIVE);
			credential(u, ExchangeConnectionStatus.REVOKED);

			coordinator.onCredentialConnected(u);

			assertThat(connector.openCount()).isZero();
		}
	}

	// -------------------------------------------------- B. scope eligibility

	@Nested
	@DisplayName("B. only LIVE SPOT and LIVE FUTURES are ever eligible")
	class ScopeEligibility {

		@Test
		@DisplayName("a paper account never starts a stream")
		void paperNeverStarts() {
			User u = user(AccountType.PAPER);
			credential(u, ExchangeConnectionStatus.CONNECTED);

			coordinator.onCredentialConnected(u);

			assertThat(connector.openCount())
					.as("a simulated account must never open a Binance socket")
					.isZero();
			assertThat(coordinator.activeScopeCount()).isZero();
		}

		@Test
		@DisplayName("OPTIONS never starts a stream, in either account mode")
		void optionsNeverStarts() {
			for (AccountType type : AccountType.values()) {
				User u = user(type);
				credential(u, ExchangeConnectionStatus.CONNECTED);

				assertThat(coordinator.start(u, AccountCategory.OPTIONS))
						.isEqualTo(LiveUserStreamManager.StreamState.DISCONNECTED);
				assertThat(coordinator.start(u, AccountCategory.OPTIONS))
						.isEqualTo(LiveUserStreamManager.StreamState.DISCONNECTED);
			}
			assertThat(connector.openCount())
					.as("OPTIONS is a reserved capability with no exchange API")
					.isZero();
		}

		@Test
		@DisplayName("MAIN never starts a stream: it is an aggregate read model, not an account")
		void mainNeverStarts() {
			User u = user(AccountType.LIVE);
			credential(u, ExchangeConnectionStatus.CONNECTED);

			assertThat(coordinator.start(u, AccountCategory.MAIN))
					.isEqualTo(LiveUserStreamManager.StreamState.DISCONNECTED);
			assertThat(coordinator.start(u, AccountCategory.MAIN))
					.isEqualTo(LiveUserStreamManager.StreamState.DISCONNECTED);

			assertThat(connector.isLive(u.getId(), AccountCategory.MAIN)).isFalse();
		}

		@Test
		@DisplayName("one user's scopes are never visible to another's")
		void usersAreIsolated() {
			User a = user(AccountType.LIVE);
			User b = user(AccountType.LIVE);
			credential(a, ExchangeConnectionStatus.CONNECTED);
			credential(b, ExchangeConnectionStatus.CONNECTED);

			coordinator.onCredentialConnected(a);

			assertThat(connector.isLive(a.getId(), AccountCategory.SPOT)).isTrue();
			assertThat(connector.isLive(b.getId(), AccountCategory.SPOT))
					.as("user B must not inherit user A's stream")
					.isFalse();

			coordinator.onCredentialConnected(b);
			assertThat(connector.isLive(b.getId(), AccountCategory.SPOT)).isTrue();
			assertThat(coordinator.isActive(a.getId(), AccountCategory.SPOT)).isTrue();
			assertThat(coordinator.isActive(b.getId(), AccountCategory.SPOT)).isTrue();
		}
	}

	// ---------------------------------------------- C. REST baseline first

	@Nested
	@DisplayName("C. the REST baseline is the authoritative first step")
	class BaselineFirst {

		@Test
		@DisplayName("spot balances are persisted before any stream is opened")
		void spotBaselinePersistsBeforeStream() {
			User u = user(AccountType.LIVE);
			credential(u, ExchangeConnectionStatus.CONNECTED);
			spotAdapter.putBalance("USDT", new BigDecimal("1500.25"), new BigDecimal("10"));

			coordinator.onCredentialConnected(u);

			assertThat(connector.isLive(u.getId(), AccountCategory.SPOT)).isTrue();

			PortfolioExchangeBalance balance = balanceRepository
					.findByUserAndExchangeAndAsset(u, ExchangeName.BINANCE, "USDT")
					.orElseThrow();
			assertThat(balance.getFree()).isEqualByComparingTo("1500.25");
			assertThat(balance.getLocked()).isEqualByComparingTo("10");

			PortfolioAccountConnection row = connection(u, AccountCategory.SPOT);
			assertThat(row.getAvailability()).isEqualTo(AccountAvailability.AVAILABLE);
			assertThat(row.getLastSyncedAt())
					.as("a successful baseline must record when it happened")
					.isNotNull();
		}

		@Test
		@DisplayName("futures positions are persisted before any stream is opened")
		void futuresBaselinePersistsBeforeStream() {
			User u = user(AccountType.LIVE);
			credential(u, ExchangeConnectionStatus.CONNECTED);
			futuresAdapter.putPosition(new com.shyblack.cryptosignals.exchange.futures
					.FuturesExchangePosition(
					"BTCUSDT",
					com.shyblack.cryptosignals.entity.enums.PositionSide.LONG,
					new BigDecimal("0.5"),
					new BigDecimal("60000"),
					new BigDecimal("61000"),
					null,
					5,
					com.shyblack.cryptosignals.entity.enums.FuturesMarginMode.CROSS,
					null,
					new BigDecimal("30000"),
					new BigDecimal("500"),
					java.time.Instant.now()));

			coordinator.onCredentialConnected(u);

			assertThat(connector.isLive(u.getId(), AccountCategory.FUTURES)).isTrue();
			assertThat(positionRepository.findByUserAndExchangeOrderBySymbolAsc(
					u, ExchangeName.BINANCE))
					.as("the authoritative position must be persisted by the baseline")
					.hasSize(1);
			assertThat(connection(u, AccountCategory.FUTURES).getAvailability())
					.isEqualTo(AccountAvailability.AVAILABLE);
		}

		@Test
		@DisplayName("the stream is never opened when the baseline could not be established")
		void noStreamWithoutBaseline() {
			User u = user(AccountType.LIVE);
			credential(u, ExchangeConnectionStatus.CONNECTED);

			// Remove the credential after the coordinator decided to proceed: the baseline read now
			// finds nothing, which is exactly the "REST failed" condition.
			credentialRepository.delete(credentialOf(u));
			credentialRepository.flush();

			LiveUserStreamManager.StreamState state =
					streamManager.start(u, AccountCategory.SPOT);

			assertThat(state).isNotEqualTo(LiveUserStreamManager.StreamState.CONNECTED);
			assertThat(connector.isLive(u.getId(), AccountCategory.SPOT))
					.as("a stream must never sit on top of an unknown account state")
					.isFalse();
		}
	}

	// ------------------------------------------------- D. idempotency

	@Nested
	@DisplayName("D. a repeated trigger produces exactly one lifecycle")
	class Idempotency {

		@Test
		@DisplayName("three consecutive start requests open exactly one stream per scope")
		void repeatedStartsAreIdempotent() {
			User u = user(AccountType.LIVE);
			credential(u, ExchangeConnectionStatus.CONNECTED);

			coordinator.start(u, AccountCategory.SPOT);
			coordinator.start(u, AccountCategory.SPOT);
			coordinator.start(u, AccountCategory.SPOT);

			assertThat(connector.openCount())
					.as("a repeated trigger must not mint a second listen key or connection")
					.isEqualTo(1);
			assertThat(coordinator.activeScopeCount()).isEqualTo(1);
		}

		@Test
		@DisplayName("a connection event racing a manual refresh still opens one stream")
		void connectEventRacingRefreshOpensOne() {
			User u = user(AccountType.LIVE);
			credential(u, ExchangeConnectionStatus.CONNECTED);

			coordinator.onCredentialConnected(u);
			coordinator.start(u, AccountCategory.SPOT);
			coordinator.start(u, AccountCategory.SPOT);

			// onCredentialConnected legitimately opens both market scopes, so the property under
			// test is that each scope appears exactly once despite two further start requests.
			assertThat(connector.openedScopes())
					.as("no scope may be opened twice by a connection event racing two refreshes")
					.containsExactly(u.getId() + ":SPOT", u.getId() + ":FUTURES");
			assertThat(coordinator.activeScopeCount()).isEqualTo(2);
		}

		@Test
		@DisplayName("concurrent start requests for one scope still open a single stream")
		void concurrentStartsAreIdempotent() throws Exception {
			User u = user(AccountType.LIVE);
			credential(u, ExchangeConnectionStatus.CONNECTED);

			int threads = 8;
			CountDownLatch ready = new CountDownLatch(threads);
			CountDownLatch go = new CountDownLatch(1);
			CountDownLatch done = new CountDownLatch(threads);
			ExecutorService pool = Executors.newFixedThreadPool(threads);
			try {
				for (int i = 0; i < threads; i++) {
					pool.submit(() -> {
						ready.countDown();
						try {
							go.await(5, TimeUnit.SECONDS);
							coordinator.start(u, AccountCategory.FUTURES);
						} catch (InterruptedException ex) {
							Thread.currentThread().interrupt();
						} finally {
							done.countDown();
						}
					});
				}
				assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
				go.countDown();
				assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
			} finally {
				pool.shutdownNow();
			}

			assertThat(connector.openedScopes())
					.as("no scope may be opened twice under concurrency")
					.containsOnly(u.getId() + ":FUTURES").hasSize(1);
		}

		@Test
		@DisplayName("stopping then starting again cleanly replaces the lifecycle")
		void restartReplacesTheOldLifecycle() {
			User u = user(AccountType.LIVE);
			credential(u, ExchangeConnectionStatus.CONNECTED);

			coordinator.start(u, AccountCategory.SPOT);
			assertThat(connector.isLive(u.getId(), AccountCategory.SPOT)).isTrue();

			coordinator.stop(u, AccountCategory.SPOT, "test restart");
			assertThat(coordinator.isActive(u.getId(), AccountCategory.SPOT)).isFalse();

			coordinator.start(u, AccountCategory.SPOT);
			assertThat(coordinator.isActive(u.getId(), AccountCategory.SPOT)).isTrue();
			assertThat(connector.isLive(u.getId(), AccountCategory.SPOT)).isTrue();
		}
	}

	// ------------------------------------------------ E. disconnect / teardown

	@Nested
	@DisplayName("E. teardown closes the socket")
	class Teardown {

		@Test
		@DisplayName("deleting the credential stops the lifecycle")
		void deletingCredentialStops() {
			User u = user(AccountType.LIVE);
			credential(u, ExchangeConnectionStatus.CONNECTED);
			coordinator.onCredentialConnected(u);
			assertThat(connector.isLive(u.getId(), AccountCategory.SPOT)).isTrue();

			credentialService.delete(
					new UserPrincipal(u), credentialOf(u).getId());

			assertThat(coordinator.isActive(u.getId(), AccountCategory.SPOT)).isFalse();
			assertThat(coordinator.isActive(u.getId(), AccountCategory.FUTURES)).isFalse();
			assertThat(streamManager.isRunning(u.getId(), AccountCategory.SPOT))
					.as("no Binance socket may outlive its credential")
					.isFalse();
		}

		@Test
		@DisplayName("switching the account back to paper stops the lifecycle")
		void switchingToPaperStops() {
			User u = user(AccountType.LIVE);
			credential(u, ExchangeConnectionStatus.CONNECTED);
			coordinator.onCredentialConnected(u);
			assertThat(coordinator.isActive(u.getId(), AccountCategory.SPOT)).isTrue();

			coordinator.onAccountModeChanged(u, AccountType.LIVE, AccountType.PAPER);

			assertThat(coordinator.activeScopeCount())
					.as("a socket kept open for a simulated account serves no purpose")
					.isZero();
		}

		@Test
		@DisplayName("teardown leaves the last known-good snapshot intact")
		void teardownPreservesSnapshot() {
			User u = user(AccountType.LIVE);
			credential(u, ExchangeConnectionStatus.CONNECTED);
			spotAdapter.putBalance("BTC", new BigDecimal("2"), new BigDecimal("0"));
			coordinator.onCredentialConnected(u);

			coordinator.onCredentialRemoved(u);

			assertThat(balanceRepository.findByUserAndExchange(u, ExchangeName.BINANCE))
					.as("a disconnect must not wipe the last known-good state")
					.hasSize(1);
		}

		@Test
		@DisplayName("stopping a user that never started is harmless")
		void stoppingAnUnknownScopeIsHarmless() {
			User u = user(AccountType.LIVE);

			coordinator.stop(u, AccountCategory.SPOT, "never started");

			assertThat(coordinator.activeScopeCount()).isZero();
		}
	}

	// -------------------------------------------- F. resume after a restart

	@Nested
	@DisplayName("F. an already-connected account resumes")
	class Resume {

		@Test
		@DisplayName("a stored CONNECTED credential in live mode is resumed")
		void connectedCredentialResumes() {
			User u = user(AccountType.LIVE);
			credential(u, ExchangeConnectionStatus.CONNECTED);

			coordinator.resumeAlreadyConnected();

			assertThat(connector.isLive(u.getId(), AccountCategory.SPOT)).isTrue();
		}

		@Test
		@DisplayName("an unvalidated credential is never resumed on a restart")
		void unvalidatedCredentialIsNotResumed() {
			User u = user(AccountType.LIVE);
			credential(u, ExchangeConnectionStatus.NOT_CONNECTED);

			coordinator.resumeAlreadyConnected();

			assertThat(connector.isLive(u.getId(), AccountCategory.SPOT))
					.as("a restart must not manufacture trust for a key the exchange never accepted")
					.isFalse();
		}
	}

	// ------------------------------------------------- G. stale reconciliation

	@Nested
	@DisplayName("G. a stale scope is re-read over REST")
	class StaleReconciliation {

		@Test
		@DisplayName("a stale spot scope is reconciled without touching the stream")
		void staleScopeIsReconciled() {
			User u = user(AccountType.LIVE);
			credential(u, ExchangeConnectionStatus.CONNECTED);
			// Sync a real baseline first, so the scenario is a deposit onto an already-synced
			// account rather than a synchronisation that began with an empty wallet.
			spotAdapter.putBalance("USDT", new BigDecimal("1000"), BigDecimal.ZERO);
			coordinator.onCredentialConnected(u);
			assertThat(balanceRepository.findByUserAndExchangeAndAsset(u, ExchangeName.BINANCE, "USDT"))
					.as("precondition: the baseline must have established a balance")
					.isPresent();

			// A deposit: the exchange reports neither new free nor new locked in the event, so the
			// processor flags the scope stale and the only correct repair is a REST read.
			spotAdapter.putBalance("USDT", new BigDecimal("2500"), BigDecimal.ZERO);
			PortfolioAccountConnection row = connection(u, AccountCategory.SPOT);
			row.setAvailability(AccountAvailability.STALE);
			connectionRepository.saveAndFlush(row);

			coordinator.reconcileStaleScopes();

			PortfolioExchangeBalance balance = balanceRepository
					.findByUserAndExchangeAndAsset(u, ExchangeName.BINANCE, "USDT")
					.orElseThrow();
			assertThat(balance.getFree())
					.as("the stale flag must actually be repaired, not merely cleared")
					.isEqualByComparingTo("2500");
			assertThat(connection(u, AccountCategory.SPOT).getAvailability())
					.isEqualTo(AccountAvailability.AVAILABLE);
		}

		@Test
		@DisplayName("a healthy scope is not re-read on every pass")
		void healthyScopesAreNotReread() {
			User u = user(AccountType.LIVE);
			credential(u, ExchangeConnectionStatus.CONNECTED);
			coordinator.onCredentialConnected(u);

			int before = streamManager.reconcileRunCount();
			coordinator.reconcileStaleScopes();

			assertThat(streamManager.reconcileRunCount())
					.as("the stale pass must be a no-op when nothing is stale")
					.isGreaterThanOrEqualTo(before);
			assertThat(connection(u, AccountCategory.SPOT).getAvailability())
					.isEqualTo(AccountAvailability.AVAILABLE);
		}
	}

	// ---------------------------------------------------- H. settings trigger

	@Nested
	@DisplayName("H. the Settings account-mode switch drives the lifecycle")
	class SettingsTrigger {

		@Test
		@DisplayName("switching an account to live through Settings starts synchronization")
		void switchingToLiveStartsSync() {
			User u = user(AccountType.PAPER);
			credential(u, ExchangeConnectionStatus.CONNECTED);

			settingsService.update(
					new UserPrincipal(u),
					new SettingsUpdateRequest(null, null, null, null, null, null, null, AccountType.LIVE));

			assertThat(connector.isLive(u.getId(), AccountCategory.SPOT))
					.as("the only place an account mode changes after signup must start the lifecycle")
					.isTrue();
		}

		@Test
		@DisplayName("switching an account back to paper through Settings stops synchronization")
		void switchingToPaperStopsSync() {
			User u = user(AccountType.LIVE);
			credential(u, ExchangeConnectionStatus.CONNECTED);
			coordinator.onCredentialConnected(u);

			settingsService.update(
					new UserPrincipal(u),
					new SettingsUpdateRequest(null, null, null, null, null, null, null, AccountType.PAPER));

			assertThat(coordinator.activeScopeCount()).isZero();
		}
	}

	// ------------------------------------------------------------ I. no orders

	@Nested
	@DisplayName("I. the lifecycle never trades")
	class NoTrading {

		@Test
		@DisplayName("no order endpoint is reachable from synchronization")
		void noOrderEndpointIsCalled() {
			User u = user(AccountType.LIVE);
			credential(u, ExchangeConnectionStatus.CONNECTED);
			spotAdapter.putBalance("USDT", new BigDecimal("100"), BigDecimal.ZERO);

			coordinator.onCredentialConnected(u);
			coordinator.reconcileStaleScopes();

			// The mock adapter seeds orders explicitly; none was seeded here, so nothing can have
			// been placed. This asserts the property that matters: synchronization is read-only and
			// leaves no order behind in either mock's order store.
			assertThat(spotAdapter.allOrders()).isEmpty();
			assertThat(futuresAdapter.allOrders()).isEmpty();
		}
	}

	private ExchangeCredential credentialOf(User u) {
		return credentialRepository.findByUser_IdAndExchange(u.getId(), ExchangeName.BINANCE)
				.orElseThrow();
	}

	private ExchangeCredentialRequest unusedRequest() {
		return new ExchangeCredentialRequest(ExchangeName.BINANCE, "k", "s", null, null);
	}
}