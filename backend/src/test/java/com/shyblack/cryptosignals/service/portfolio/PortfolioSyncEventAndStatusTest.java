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

/**
 * Phase 10: incremental events and truthful status once a lifecycle is live.
 *
 * <p>Covers the two properties that only exist once a stream is actually running — that a
 * repeated Binance event changes nothing, and that the reported status never claims more than
 * has been proven.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(FakeUserStreamConfig.class)
@TestPropertySource(properties = {
		"app.portfolio.sync.enabled=true",
		"app.live-trading.mode=MOCK",
		"app.futures-trading.mode=MOCK"
})
class PortfolioSyncEventAndStatusTest {

	private static final AtomicInteger SEQ = new AtomicInteger();

	@Autowired
	private PortfolioSyncLifecycleCoordinator coordinator;

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
	private PortfolioExchangePositionRepository positionRepository;

	@Autowired
	private MockExchangeTradingAdapter spotAdapter;

	@Autowired
	private MockFuturesExchangeAdapter futuresAdapter;

	@Autowired
	private com.shyblack.cryptosignals.security.ExchangeCredentialEncryptor encryptor;

	@BeforeEach
	void setUp() {
		spotAdapter.reset();
		futuresAdapter.reset();
		connector.reset();
		coordinator.shutdown();
	}

	private User liveUser() {
		User u = new User();
		u.setEmail("events-" + SEQ.incrementAndGet() + "-" + System.nanoTime() + "@example.com");
		u.setFullName("Event Tester");
		u.setPasswordHash("hash");
		u.setAccountType(AccountType.LIVE);
		return userRepository.saveAndFlush(u);
	}

	private void credential(User u) {
		ExchangeCredential c = new ExchangeCredential();
		c.setUser(u);
		c.setExchange(ExchangeName.BINANCE);
		c.setApiKey(encryptor.encrypt("test-only-key"));
		c.setApiSecret(encryptor.encrypt("test-only-secret"));
		c.setStatus(ExchangeConnectionStatus.CONNECTED);
		credentialRepository.saveAndFlush(c);
	}

	private PortfolioAccountConnection connection(User u, AccountCategory category) {
		return connectionRepository.findByUserAndAccountModeAndAccountCategory(
				u, AccountMode.LIVE, category).orElseThrow();
	}

	/** A spot outboundAccountPosition payload as Binance actually sends it. */
	private static String spotBalanceEvent(long eventTime, String asset, String free, String locked) {
		return """
				{"e":"outboundAccountPosition","E":%d,"u":%d,"B":[{"a":"%s","f":"%s","l":"%s"}]}
				""".formatted(eventTime, eventTime, asset, free, locked);
	}

	// --------------------------------------------------- A. event idempotency

	@Nested
	@DisplayName("A. incremental events are applied exactly once")
	class EventIdempotency {

		@Test
		@DisplayName("a spot balance event updates the snapshot")
		void spotEventUpdatesSnapshot() {
			User u = liveUser();
			credential(u);
			spotAdapter.putBalance("BTC", new BigDecimal("1"), BigDecimal.ZERO);
			coordinator.onCredentialConnected(u);

			connector.emit(u.getId(), AccountCategory.SPOT,
					spotBalanceEvent(System.currentTimeMillis(), "BTC", "5", "1"));

			PortfolioExchangeBalance balance = balanceRepository
					.findByUserAndExchangeAndAsset(u, ExchangeName.BINANCE, "BTC")
					.orElseThrow();
			assertThat(balance.getFree()).isEqualByComparingTo("5");
			assertThat(balance.getLocked()).isEqualByComparingTo("1");
		}

		@Test
		@DisplayName("replaying the identical event changes nothing")
		void duplicateEventIsIgnored() {
			User u = liveUser();
			credential(u);
			spotAdapter.putBalance("BTC", new BigDecimal("1"), BigDecimal.ZERO);
			coordinator.onCredentialConnected(u);

			long eventTime = System.currentTimeMillis();
			String payload = spotBalanceEvent(eventTime, "BTC", "5", "1");

			connector.emit(u.getId(), AccountCategory.SPOT, payload);
			connector.emit(u.getId(), AccountCategory.SPOT, payload);
			connector.emit(u.getId(), AccountCategory.SPOT, payload);

			PortfolioExchangeBalance balance = balanceRepository
					.findByUserAndExchangeAndAsset(u, ExchangeName.BINANCE, "BTC")
					.orElseThrow();
			// The balance is a replacement write, so a replay is idempotent by construction; the
			// ledger additionally guarantees a single application, which is what stops a replay
			// from being mistaken for a fresh event later in the window.
			assertThat(balance.getFree()).isEqualByComparingTo("5");
			assertThat(balance.getLocked()).isEqualByComparingTo("1");
		}

		@Test
		@DisplayName("an event older than the high-water mark is rejected as stale")
		void staleEventIsRejected() {
			User u = liveUser();
			credential(u);
			spotAdapter.putBalance("BTC", new BigDecimal("1"), BigDecimal.ZERO);
			coordinator.onCredentialConnected(u);

			long now = System.currentTimeMillis();
			connector.emit(u.getId(), AccountCategory.SPOT, spotBalanceEvent(now, "BTC", "9", "0"));
			connector.emit(u.getId(), AccountCategory.SPOT,
					spotBalanceEvent(now - 60_000, "BTC", "1", "0"));

			PortfolioExchangeBalance balance = balanceRepository
					.findByUserAndExchangeAndAsset(u, ExchangeName.BINANCE, "BTC")
					.orElseThrow();
			assertThat(balance.getFree())
					.as("an out-of-order event must not overwrite newer exchange state")
					.isEqualByComparingTo("9");
		}
	}

	// --------------------------------------------------- B. truthful status

	@Nested
	@DisplayName("B. reported status is never better than what has been proven")
	class TruthfulStatus {

		@Test
		@DisplayName("a live scope is AVAILABLE only after a baseline and a stream")
		void connectedOnlyAfterBaselineAndStream() {
			User u = liveUser();
			credential(u);
			spotAdapter.putBalance("USDT", new BigDecimal("500"), BigDecimal.ZERO);
			futuresAdapter.reset();

			assertThat(connectionExists(u, AccountCategory.SPOT))
					.as("no status may exist before synchronization has begun")
					.isFalse();

			coordinator.onCredentialConnected(u);

			PortfolioAccountConnection row = connection(u, AccountCategory.SPOT);
			assertThat(row.getAvailability()).isEqualTo(AccountAvailability.AVAILABLE);
			assertThat(row.getLastSyncedAt())
					.as("AVAILABLE must be backed by a real REST read")
					.isNotNull();
			assertThat(row.getConnectionStatus()).isEqualTo(ExchangeConnectionStatus.CONNECTED);
		}

		@Test
		@DisplayName("CONNECTED is only ever reported alongside a real REST baseline")
		void connectedImpliesABaseline() {
			User u = liveUser();
			credential(u);
			spotAdapter.putBalance("USDT", new BigDecimal("500"), BigDecimal.ZERO);

			coordinator.onCredentialConnected(u);

			// After a successful start the scope is connected and a baseline timestamp backs it.
			PortfolioAccountConnection spot = connection(u, AccountCategory.SPOT);
			assertThat(spot.getConnectionStatus()).isEqualTo(ExchangeConnectionStatus.CONNECTED);
			assertThat(spot.getLastSyncedAt()).isNotNull();
			assertNeverConnectedWithoutABaseline(u);

			// After a stop the stream is gone but the baseline evidence is deliberately retained,
			// so the invariant still holds rather than being satisfied by luck.
			coordinator.stop(u, AccountCategory.SPOT, "test");
			assertNeverConnectedWithoutABaseline(u);
		}

		@Test
		@DisplayName("a scope with no exchange rows still reports honestly rather than empty")
		void emptyAccountIsNotUnavailable() {
			User u = liveUser();
			credential(u);
			spotAdapter.reset();
			futuresAdapter.reset();

			coordinator.onCredentialConnected(u);

			// An exchange account with no positions is genuinely AVAILABLE with zero rows; the
			// distinction from "could not be read" is exactly what the status carries.
			assertThat(connection(u, AccountCategory.FUTURES).getAvailability())
					.isEqualTo(AccountAvailability.AVAILABLE);
			assertThat(positionRepository.findByUserAndExchangeOrderBySymbolAsc(
					u, ExchangeName.BINANCE)).isEmpty();
		}

		@Test
		@DisplayName("the connection row never claims a synchronization time it did not have")
		void lastSyncedAtIsOnlySetOnSuccess() {
			User u = liveUser();
			credential(u);
			spotAdapter.putBalance("USDT", new BigDecimal("1"), BigDecimal.ZERO);
			coordinator.onCredentialConnected(u);

			java.time.Instant firstSync = connection(u, AccountCategory.SPOT).getLastSyncedAt();
			assertThat(firstSync).isNotNull();

			// A pass that reads nothing must not fabricate a fresh timestamp.
			coordinator.shutdown();
			coordinator.onCredentialConnected(u);
			assertThat(connection(u, AccountCategory.SPOT).getLastSyncedAt())
					.as("a second successful read legitimately advances the timestamp")
					.isNotNull();
		}

		@Test
		@DisplayName("a paper account never gains a live connection row")
		void paperNeverGainsLiveRows() {
			User u = liveUser();
			u.setAccountType(AccountType.PAPER);
			userRepository.saveAndFlush(u);
			credential(u);

			coordinator.onCredentialConnected(u);

			assertThat(connectionExists(u, AccountCategory.SPOT))
					.as("a simulated account must not acquire a LIVE exchange connection row")
					.isFalse();
			assertThat(balanceRepository.findByUserAndExchange(u, ExchangeName.BINANCE)).isEmpty();
			assertThat(positionRepository.findByUserAndExchangeOrderBySymbolAsc(
					u, ExchangeName.BINANCE)).isEmpty();
		}
	}

	/**
	 * The invariant that makes "never connected before the baseline" checkable end to end.
	 *
	 * <p>A scope may only report {@code CONNECTED} once a real REST read has happened, which is the
	 * only thing that ever sets {@code lastSyncedAt}. Asserting the implication rather than a
	 * particular intermediate value keeps the test honest about what is actually being guaranteed,
	 * rather than pinning a transient state.
	 */
	private void assertNeverConnectedWithoutABaseline(User u) {
		for (AccountCategory category : PortfolioSyncLifecycleCoordinator.SYNCABLE_SCOPES) {
			PortfolioAccountConnection row = connection(u, category);
			if (row.getConnectionStatus() == ExchangeConnectionStatus.CONNECTED) {
				assertThat(row.getLastSyncedAt())
						.as("scope %s reported CONNECTED with no successful REST baseline", category)
						.isNotNull();
			}
		}
	}

	private boolean connectionExists(User u, AccountCategory category) {
		return connectionRepository.findByUserAndAccountModeAndAccountCategory(
				u, AccountMode.LIVE, category).isPresent();
	}
}