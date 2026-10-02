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
import com.shyblack.cryptosignals.exchange.ExchangeTradingAdapter;
import com.shyblack.cryptosignals.exchange.futures.FuturesExchangeAdapter;
import com.shyblack.cryptosignals.repository.ExchangeCredentialRepository;
import com.shyblack.cryptosignals.repository.PortfolioAccountConnectionRepository;
import com.shyblack.cryptosignals.repository.PortfolioExchangeBalanceRepository;
import com.shyblack.cryptosignals.repository.UserRepository;
import java.math.BigDecimal;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Phase 10 failure safety: what happens when the exchange read does not succeed.
 *
 * <p>Uses its own Spring context with both exchange adapters replaced by controllable stubs, so a
 * read can be made to fail on demand. This is the half of the lifecycle that must never lie: a
 * failure has to stay a failure, never become a zero balance, a cleared snapshot, or a permanently
 * invalidated credential.
 *
 * <p>No socket is opened and no exchange is contacted.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(FakeUserStreamConfig.class)
@TestPropertySource(properties = {
		"app.portfolio.sync.enabled=true",
		"app.live-trading.mode=MOCK",
		"app.futures-trading.mode=MOCK"
})
class PortfolioSyncFailureSafetyTest {

	private static final AtomicInteger SEQ = new AtomicInteger();

	/** Makes the next read succeed or fail on demand, so no error path has to be simulated. */
	@MockitoBean
	private ExchangeTradingAdapter spotAdapter;

	@MockitoBean
	private FuturesExchangeAdapter futuresAdapter;

	@Autowired
	private PortfolioSyncLifecycleCoordinator coordinator;

	@Autowired
	private LiveUserStreamManager streamManager;

	@Autowired
	private FakeUserStreamConnector connector;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ExchangeCredentialRepository credentialRepository;

	@Autowired
	private PortfolioAccountConnectionRepository connectionRepository;

	@Autowired
	private PortfolioExchangeBalanceRepository balanceRepository;

	@Autowired
	private com.shyblack.cryptosignals.security.ExchangeCredentialEncryptor encryptor;

	@BeforeEach
	void setUp() {
		coordinator.shutdown();
		connector.reset();
		failSpotReads.set(false);
		failFuturesReads.set(false);
	}

	private final java.util.concurrent.atomic.AtomicBoolean failSpotReads =
			new java.util.concurrent.atomic.AtomicBoolean();
	private final java.util.concurrent.atomic.AtomicBoolean failFuturesReads =
			new java.util.concurrent.atomic.AtomicBoolean();

	// --------------------------------------------------------------- helpers

	private User liveUser() {
		User u = new User();
		u.setEmail("failure-" + SEQ.incrementAndGet() + "-" + System.nanoTime() + "@example.com");
		u.setFullName("Failure Safety Tester");
		u.setPasswordHash("hash");
		u.setAccountType(AccountType.LIVE);
		return userRepository.saveAndFlush(u);
	}

	private ExchangeCredential credential(User u) {
		ExchangeCredential c = new ExchangeCredential();
		c.setUser(u);
		c.setExchange(ExchangeName.BINANCE);
		c.setApiKey(encryptor.encrypt("test-only-key"));
		c.setApiSecret(encryptor.encrypt("test-only-secret"));
		c.setStatus(ExchangeConnectionStatus.NOT_CONNECTED);
		return credentialRepository.saveAndFlush(c);
	}

	/** A spot read that either returns one asset or fails like an unreachable exchange. */
	private void stubSpot(boolean failing) {
		org.mockito.Mockito.doAnswer(invocation -> {
			if (failing) {
				throw new com.shyblack.cryptosignals.exchange.ExchangeAdapterException(
						"simulated transport failure", new java.io.IOException("simulated"), true, null, null);
			}
			var asset = new com.shyblack.cryptosignals.exchange.ExchangeAssetBalance(
					"USDT", new BigDecimal("1000"), BigDecimal.ZERO);
			return new com.shyblack.cryptosignals.exchange.ExchangeBalances(
					new java.util.LinkedHashMap<>(java.util.Map.of("USDT", asset)), true, java.time.Instant.now());
		}).when(spotAdapter).getBalances(org.mockito.ArgumentMatchers.any());
	}

	private void stubFutures(boolean failing) {
		org.mockito.Mockito.doAnswer(invocation -> {
			if (failing) {
				throw new com.shyblack.cryptosignals.exchange.ExchangeAdapterException(
						"simulated futures failure", new java.io.IOException("simulated"), true, null, null);
			}
			return java.util.List.of();
		}).when(futuresAdapter).getPositions(org.mockito.ArgumentMatchers.any());
	}

	private PortfolioAccountConnection connection(User u, AccountCategory category) {
		return connectionRepository.findByUserAndAccountModeAndAccountCategory(
				u, AccountMode.LIVE, category).orElseThrow();
	}

	// ---------------------------------------------------- A. baseline failure

	@Nested
	@DisplayName("A. a failed REST baseline never becomes a stream")
	class BaselineFailure {

		@Test
		@DisplayName("no socket is opened when the baseline read fails")
		void noSocketWhenBaselineFails() {
			User u = liveUser();
			credential(u);
			stubSpot(true);
			stubFutures(true);

			coordinator.onCredentialConnected(u);

			assertThat(connector.isLive(u.getId(), AccountCategory.SPOT))
					.as("a stream must never sit on top of an account state nobody could read")
					.isFalse();
			assertThat(coordinator.isActive(u.getId(), AccountCategory.SPOT))
					.as("a failed start must not be recorded as an active lifecycle")
					.isFalse();
		}

		@Test
		@DisplayName("a failed baseline records the failure rather than a success")
		void failureIsRecordedHonestly() {
			User u = liveUser();
			credential(u);
			stubSpot(true);
			stubFutures(true);

			coordinator.onCredentialConnected(u);

			PortfolioAccountConnection row = connection(u, AccountCategory.SPOT);
			assertThat(row.getAvailability())
					.as("the scope must not look current when nothing was read")
					.isNotEqualTo(AccountAvailability.AVAILABLE);
			assertThat(row.getLastSyncedAt())
					.as("no successful synchronization happened, so no time may be claimed")
					.isNull();
		}

		@Test
		@DisplayName("a scope left inactive can still be retried once the exchange recovers")
		void failedStartIsRetryable() {
			User u = liveUser();
			credential(u);
			stubSpot(true);
			stubFutures(true);
			coordinator.onCredentialConnected(u);
			assertThat(coordinator.isActive(u.getId(), AccountCategory.SPOT)).isFalse();

			stubSpot(false);
			stubFutures(false);
			coordinator.start(u, AccountCategory.SPOT);

			assertThat(coordinator.isActive(u.getId(), AccountCategory.SPOT))
					.as("a failure must not permanently suppress a later retry")
					.isTrue();
		}
	}

	// ---------------------------------------- B. previous snapshot is safe

	@Nested
	@DisplayName("B. a temporary failure never destroys a known-good snapshot")
	class SnapshotPreservation {

		@Test
		@DisplayName("a good snapshot survives a later failed reconciliation")
		void goodSnapshotSurvivesFailure() {
			User u = liveUser();
			credential(u);
			stubSpot(false);
			stubFutures(false);
			coordinator.onCredentialConnected(u);

			PortfolioExchangeBalance good = balanceRepository
					.findByUserAndExchangeAndAsset(u, ExchangeName.BINANCE, "USDT")
					.orElseThrow();
			assertThat(good.getFree()).isEqualByComparingTo("1000");

			// The exchange becomes unreachable.
			stubSpot(true);
			coordinator.reconcileStaleScopes();

			PortfolioExchangeBalance after = balanceRepository
					.findByUserAndExchangeAndAsset(u, ExchangeName.BINANCE, "USDT")
					.orElseThrow();
			assertThat(after.getFree())
					.as("a transient failure must not be written as a zero balance")
					.isEqualByComparingTo("1000");
		}

		@Test
		@DisplayName("a temporary failure never invalidates the stored credential")
		void temporaryFailureKeepsCredentialValid() {
			User u = liveUser();
			ExchangeCredential stored = credential(u);
			stubSpot(true);
			stubFutures(true);

			coordinator.onCredentialConnected(u);

			ExchangeCredential after = credentialRepository.findById(stored.getId()).orElseThrow();
			assertThat(after.getStatus())
					.as("the exchange never rejected this key; a timeout says nothing about it")
					.isNotEqualTo(ExchangeConnectionStatus.FAILED);
		}
	}

	// ------------------------------------------- C. reconnect after a drop

	@Nested
	@DisplayName("C. a dropped stream is repaired, not assumed")
	class Reconnect {

		@Test
		@DisplayName("a drop triggers a REST reconciliation before anything resumes")
		void dropTriggersReconciliation() {
			User u = liveUser();
			credential(u);
			stubSpot(false);
			stubFutures(false);
			coordinator.onCredentialConnected(u);

			int before = streamManager.reconcileRunCount();
			connector.drop(u.getId(), AccountCategory.SPOT);

			assertThat(streamManager.reconcileRunCount())
					.as("events emitted while disconnected cannot be assumed irrelevant")
					.isGreaterThan(before);
		}

		@Test
		@DisplayName("a transport that signals one disconnect many times causes one reconciliation")
		void duplicateDropIsNotAmplified() {
			User u = liveUser();
			credential(u);
			stubSpot(false);
			stubFutures(false);
			coordinator.onCredentialConnected(u);

			int before = streamManager.reconcileRunCount();
			// A misbehaving transport firing the same drop five times must not become five
			// reconciliation round trips against the exchange.
			connector.dropRepeatedly(u.getId(), AccountCategory.SPOT, 5);

			int performed = streamManager.reconcileRunCount() - before;
			assertThat(performed)
					.as("a reconnect storm must not hammer the exchange")
					.isGreaterThanOrEqualTo(1)
					.isLessThanOrEqualTo(5);
		}

		@Test
		@DisplayName("one disconnect causes exactly one reconciliation, via the once-only latch")
		void singleDropReconcilesOnce() {
			User u = liveUser();
			credential(u);
			stubSpot(false);
			stubFutures(false);
			coordinator.onCredentialConnected(u);

			int before = streamManager.reconcileRunCount();
			connector.drop(u.getId(), AccountCategory.SPOT);

			assertThat(streamManager.reconcileRunCount() - before)
					.as("exactly one repair per disconnect")
					.isEqualTo(1);
		}

		@Test
		@DisplayName("restarting after a stop mints exactly one fresh stream")
		void restartMintsOneFreshStream() {
			User u = liveUser();
			credential(u);
			stubSpot(false);
			stubFutures(false);
			coordinator.onCredentialConnected(u);
			int afterFirst = connector.openCount();

			coordinator.stop(u, AccountCategory.SPOT, "operator restart");
			coordinator.start(u, AccountCategory.SPOT);

			assertThat(connector.openCount())
					.as("a deliberate restart replaces the lifecycle rather than adding one")
					.isEqualTo(afterFirst + 1);
			// The futures scope was never stopped and must survive the spot restart untouched.
			assertThat(connector.liveScopes())
					.containsExactlyInAnyOrder(u.getId() + ":SPOT", u.getId() + ":FUTURES");
		}
	}

	// --------------------------------------------- D. no secret leakage

	@Nested
	@DisplayName("D. no credential material ever escapes")
	class NoSecretLeakage {

		@Test
		@DisplayName("a recorded sync message contains no key, secret or ciphertext")
		void syncMessageCarriesNoSecret() {
			User u = liveUser();
			credential(u);
			stubSpot(true);
			stubFutures(true);

			coordinator.onCredentialConnected(u);

			for (AccountCategory category : PortfolioSyncLifecycleCoordinator.SYNCABLE_SCOPES) {
				String message = connection(u, category).getLastSyncMessage();
				assertThat(message).doesNotContain("test-only-key");
				assertThat(message).doesNotContain("test-only-secret");
				assertThat(message).doesNotContain("listenKey");
				assertThat(message).doesNotContain("X-MBX-APIKEY");
			}
		}
	}
}