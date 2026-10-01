package com.shyblack.cryptosignals.service.portfolio;

import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountAvailability;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.repository.ExchangeCredentialRepository;
import com.shyblack.cryptosignals.repository.PortfolioAccountConnectionRepository;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Owns the lifecycle of every Binance user-data stream.
 *
 * <p>Enforces the required state machine:
 *
 * <pre>
 *   start  -> REST reconciliation -> CONNECTED -> WebSocket increments
 *   drop   -> RECONNECTING/STALE  -> REST reconciliation -> resume
 * </pre>
 *
 * <p>A dropped stream is never resumed without a REST reconciliation first, because events emitted
 * while disconnected cannot be assumed irrelevant. Reconciliation failure escalates the scope to
 * ERROR rather than leaving it looking fresh.
 *
 * <p>At most one stream exists per {@code (userId, accountCategory)}: {@link #start} is idempotent
 * and concurrent calls cannot create a second connection. Only LIVE SPOT and LIVE FUTURES are ever
 * started — PAPER, OPTIONS and MAIN are rejected before any credential lookup, so a simulated
 * account can never open an exchange stream or gain exchange rows.
 */
@Service
public class LiveUserStreamManager {

	private static final Logger log = LoggerFactory.getLogger(LiveUserStreamManager.class);

	/** Observable lifecycle of one scope's stream. */
	public enum StreamState {
		/** No stream registered for the scope. */
		DISCONNECTED,
		/** A stream has been requested but is not yet connected. */
		CONNECTING,
		/** Socket is live. */
		CONNECTED,
		/** Dropped; reconciling over REST before resuming. */
		RECONNECTING,
		/** Deliberately stopped. */
		STOPPED,
		/** Reconciliation or authentication failed. */
		ERROR
	}

	private static final List<AccountCategory> STREAM_SCOPES =
			List.of(AccountCategory.SPOT, AccountCategory.FUTURES);

	private final UserStreamConnector connector;
	private final LivePortfolioReconcileService reconcileService;
	private final LiveUserStreamEventProcessor processor;
	private final ExchangeCredentialRepository credentialRepository;
	private final PortfolioAccountConnectionRepository connectionRepository;

	/** Keyed by userId+category, so scopes of different users can never share a stream. */
	private final Map<String, Session> sessions = new ConcurrentHashMap<>();
	private final AtomicInteger reconcileRuns = new AtomicInteger();
	private final AtomicInteger reconcileFailures = new AtomicInteger();

	public LiveUserStreamManager(
			UserStreamConnector connector,
			LivePortfolioReconcileService reconcileService,
			LiveUserStreamEventProcessor processor,
			ExchangeCredentialRepository credentialRepository,
			PortfolioAccountConnectionRepository connectionRepository) {
		this.connector = connector;
		this.reconcileService = reconcileService;
		this.processor = processor;
		this.credentialRepository = credentialRepository;
		this.connectionRepository = connectionRepository;
	}

	/**
	 * Starts the stream for one scope, reconciling over REST first.
	 *
	 * <p>Idempotent: a second call for a scope that is already streaming returns without opening a
	 * second connection.
	 */
	public synchronized StreamState start(User user, AccountCategory category) {
		if (user == null || !STREAM_SCOPES.contains(category)) {
			log.debug("Refusing to start a user-data stream for user={} scope={}", user, category);
			return StreamState.DISCONNECTED;
		}
		if (user.getAccountType() != AccountType.LIVE) {
			// Defensive: a simulated account must never open an exchange stream.
			return StreamState.DISCONNECTED;
		}

		String key = key(user.getId(), category);
		Session existing = sessions.get(key);
		if (existing != null && existing.handle != null) {
			existing.state = StreamState.CONNECTED;
			return existing.state;
		}

		Optional<ExchangeCredential> credential =
				credentialRepository.findByUser_IdAndExchange(user.getId(), ExchangeName.BINANCE);
		if (credential.isEmpty()) {
			log.info("No exchange credential for user={} scope={}; stream not started", user.getId(), category);
			markNotConnected(user, category, "No exchange credential is connected.");
			return StreamState.DISCONNECTED;
		}

		Session session = sessions.computeIfAbsent(key, k -> new Session(user, category));
		session.user = user;
		session.state = StreamState.CONNECTING;

		// REST first: the stream is never treated as the source of a complete picture.
		var report = reconcile(user, category);
		if (!report.succeeded()) {
			session.state = StreamState.ERROR;
			markError(user, category, "Reconciliation failed before stream start: " + report.message());
			return StreamState.ERROR;
		}

		try {
			session.handle = connector.open(new UserStreamConnector.Request(
					user.getId(),
					category,
					credential.get(),
					raw -> processor.process(user, category, raw),
					() -> handleDrop(user, category)));
			session.state = StreamState.CONNECTED;
			markConnected(user, category);
			return StreamState.CONNECTED;
		} catch (RuntimeException ex) {
			session.state = StreamState.ERROR;
			markError(user, category, "Could not open the user-data stream: " + ex.getMessage());
			return StreamState.ERROR;
		}
	}

	/** Stops one scope's stream. Idempotent. */
	public synchronized StreamState stop(User user, AccountCategory category) {
		String key = key(user.getId(), category);
		Session session = sessions.remove(key);
		if (session == null) {
			return StreamState.DISCONNECTED;
		}
		if (session.handle != null) {
			session.handle.close();
		}
		session.state = StreamState.STOPPED;
		return StreamState.STOPPED;
	}

	public StreamState stateOf(UUID userId, AccountCategory category) {
		Session session = sessions.get(key(userId, category));
		return session == null ? StreamState.DISCONNECTED : session.state;
	}

	public boolean isRunning(UUID userId, AccountCategory category) {
		Session session = sessions.get(key(userId, category));
		return session != null && session.handle != null && session.handle.isOpen();
	}

	/** Number of registered scopes. Used to prove no duplicate streams were created. */
	public int activeStreamCount() {
		return sessions.size();
	}

	/** How many REST reconciliations this manager has driven, for diagnostics and tests. */
	public int reconcileRunCount() {
		return reconcileRuns.get();
	}

	public int reconcileFailureCount() {
		return reconcileFailures.get();
	}

	@PreDestroy
	public void shutdown() {
		sessions.values().forEach(session -> {
			if (session.handle != null) {
				session.handle.close();
			}
		});
		sessions.clear();
	}

	// ------------------------------------------------------------- internals

	/**
	 * Called by the connector whenever the socket drops. Reconciles over REST so nothing emitted
	 * while disconnected is silently lost, and escalates to ERROR when reconciliation itself fails.
	 */
	private synchronized void handleDrop(User user, AccountCategory category) {
		Session session = sessions.get(key(user.getId(), category));
		if (session == null) {
			// No stream is registered for this scope, so there is nothing to reconcile. This also
			// guarantees a stale signal cannot touch a scope it does not own.
			return;
		}
		if (session.state == StreamState.RECONNECTING || session.state == StreamState.ERROR) {
			// A reconciliation for this scope is already in flight or already failed. Repeating it
			// would be a reconnect storm against the exchange, so the duplicate is dropped here.
			log.debug("Ignoring duplicate disconnect signal scope={} user={} state={}",
					category, user.getId(), session.state);
			return;
		}
		session.state = StreamState.RECONNECTING;
		log.info("User-data stream dropped; reconciling over REST user={} scope={}",
				user.getId(), category);

		var report = reconcile(user, category);
		if (report.succeeded()) {
			markConnected(user, category);
			if (session != null) {
				session.state = StreamState.CONNECTED;
			}
		} else {
			markError(user, category, "Reconciliation after disconnect failed: " + report.message());
			if (session != null) {
				session.state = StreamState.ERROR;
			}
		}
	}

	private LivePortfolioReconcileService.ReconciliationReport reconcile(User user, AccountCategory category) {
		reconcileRuns.incrementAndGet();
		try {
			var report = category == AccountCategory.SPOT
					? reconcileService.reconcileSpot(user)
					: reconcileService.reconcileFutures(user);
			if (!report.succeeded()) {
				reconcileFailures.incrementAndGet();
			}
			return report;
		} catch (RuntimeException ex) {
			reconcileFailures.incrementAndGet();
			return new LivePortfolioReconcileService.ReconciliationReport(
					com.shyblack.cryptosignals.entity.enums.AccountMode.LIVE,
					category, false, 0, 0, false, ex.getMessage());
		}
	}

	private void markConnected(User user, AccountCategory category) {
		updateConnection(user, category, AccountAvailability.AVAILABLE,
				ExchangeConnectionStatus.CONNECTED, "User-data stream connected.");
	}

	private void markNotConnected(User user, AccountCategory category, String message) {
		updateConnection(user, category, AccountAvailability.NOT_CONNECTED,
				ExchangeConnectionStatus.NOT_CONNECTED, message);
	}

	private void markError(User user, AccountCategory category, String message) {
		updateConnection(user, category, AccountAvailability.ERROR,
				ExchangeConnectionStatus.FAILED, truncate(message));
	}

	private void updateConnection(
			User user,
			AccountCategory category,
			AccountAvailability availability,
			ExchangeConnectionStatus status,
			String message) {
		try {
			var connection = connectionRepository.findByUserAndAccountModeAndAccountCategory(
					user, com.shyblack.cryptosignals.entity.enums.AccountMode.LIVE, category)
					.orElseGet(() -> {
						var created = new com.shyblack.cryptosignals.entity.PortfolioAccountConnection();
						created.setUser(user);
						created.setAccountMode(
								com.shyblack.cryptosignals.entity.enums.AccountMode.LIVE);
						created.setAccountCategory(category);
						created.setExchange(ExchangeName.BINANCE);
						return created;
					});
			connection.setAvailability(availability);
			connection.setConnectionStatus(status);
			connection.setLastSyncMessage(truncate(message));
			connectionRepository.save(connection);
		} catch (RuntimeException ex) {
			log.warn("Could not persist connection status user={} scope={} reason={}",
					user.getId(), category, ex.getMessage());
		}
	}

	private static String truncate(String message) {
		if (message == null) {
			return null;
		}
		return message.length() <= 200 ? message : message.substring(0, 197) + "...";
	}

	private static String key(UUID userId, AccountCategory category) {
		return userId + ":" + category.name();
	}

	private static final class Session {
		private final AccountCategory category;
		private User user;
		private volatile StreamState state = StreamState.DISCONNECTED;
		private volatile UserStreamConnector.Handle handle;

		private Session(User user, AccountCategory category) {
			this.user = user;
			this.category = category;
		}
	}
}