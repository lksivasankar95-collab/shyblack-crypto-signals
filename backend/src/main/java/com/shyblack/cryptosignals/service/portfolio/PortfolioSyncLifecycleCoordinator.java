package com.shyblack.cryptosignals.service.portfolio;

import com.shyblack.cryptosignals.config.PortfolioSyncProperties;
import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountAvailability;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.repository.ExchangeCredentialRepository;
import com.shyblack.cryptosignals.repository.PortfolioAccountConnectionRepository;
import com.shyblack.cryptosignals.repository.UserRepository;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The lifecycle coordinator that was missing: it decides <em>when</em> a LIVE Portfolio scope
 * begins and stops synchronising, and delegates every unit of real work to the components that
 * already own it.
 *
 * <p>This class deliberately contains no exchange logic. It never calls an adapter, never
 * persists a balance, a position or a fill, and never touches an order endpoint. All of that
 * belongs to:
 *
 * <ul>
 *   <li>{@link LivePortfolioSyncService} — the authoritative REST baseline read;</li>
 *   <li>{@link LiveUserStreamManager} — REST-before-stream ordering, per-scope idempotency,
 *       reconnect with reconciliation, and the PAPER/OPTIONS/MAIN refusal;</li>
 *   <li>{@link LiveUserStreamEventProcessor} — deduplication, the high-water mark and every
 *       write driven by a user-data event.</li>
 * </ul>
 *
 * <p>Ordering guaranteed here and preserved there: <b>REST baseline first, stream second.</b>
 * {@link LiveUserStreamManager#start} already refuses to open a socket when reconciliation
 * fails, so a stream can never be established on top of an unknown account state.
 *
 * <p>Four trigger points, and no others:
 * <ol>
 *   <li>a LIVE credential the exchange has actually accepted (REST-first validation succeeds);</li>
 *   <li>Settings switching an account from paper to live;</li>
 *   <li>startup, to resume scopes that were already live before a restart;</li>
 *   <li>credential deletion, disconnection or revocation, and a switch back to paper — all of
 *       which stop.</li>
 * </ol>
 *
 * <p>Safety properties:
 * <ul>
 *   <li>Nothing runs unless {@code app.portfolio.sync.enabled} is true. It defaults to
 *       <b>false</b>, so shipping this class cannot by itself open an exchange stream.</li>
 *   <li>Only {@code SPOT} and {@code FUTURES} are ever eligible. {@code MAIN} is an aggregate
 *       read-model scope and {@code OPTIONS} is a reserved capability; both are refused before
 *       any credential is looked up.</li>
 *   <li>A simulated (paper) account never starts a stream, so no exchange row or socket can
 *       exist for one.</li>
 *   <li>Starting is idempotent per {@code (user, category)}: a repeated trigger performs one
 *       reconciliation and opens one connection, never two.</li>
 *   <li>Scope keys include the user id, so one user's lifecycle can never touch another's.</li>
 *   <li>No credential, listen key, header or signed query string is ever logged.</li>
 * </ul>
 */
@Service
public class PortfolioSyncLifecycleCoordinator {

	private static final Logger log = LoggerFactory.getLogger(PortfolioSyncLifecycleCoordinator.class);

	/**
	 * The only two categories that may hold a user-data stream. MAIN aggregates the other
	 * categories and is not an account; OPTIONS has no engine, account or exchange API.
	 */
	static final List<AccountCategory> SYNCABLE_SCOPES =
			List.of(AccountCategory.SPOT, AccountCategory.FUTURES);

	private final LiveUserStreamManager streamManager;
	private final LivePortfolioReconcileService reconcileService;
	private final UserRepository userRepository;
	private final ExchangeCredentialRepository credentialRepository;
	private final PortfolioAccountConnectionRepository connectionRepository;
	private final PortfolioSyncProperties properties;

	/** Scopes with a live lifecycle, so a repeated trigger is a no-op rather than a re-open. */
	private final Set<String> active = ConcurrentHashMap.newKeySet();

	/** Scopes with an attempt already running, so concurrent triggers cannot both proceed. */
	private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

	public PortfolioSyncLifecycleCoordinator(
			LiveUserStreamManager streamManager,
			LivePortfolioReconcileService reconcileService,
			UserRepository userRepository,
			ExchangeCredentialRepository credentialRepository,
			PortfolioAccountConnectionRepository connectionRepository,
			PortfolioSyncProperties properties) {
		this.streamManager = streamManager;
		this.reconcileService = reconcileService;
		this.userRepository = userRepository;
		this.credentialRepository = credentialRepository;
		this.connectionRepository = connectionRepository;
		this.properties = properties;
	}

	// ------------------------------------------------------------- triggers

	/**
	 * A LIVE credential was validated against the exchange and accepted.
	 *
	 * <p>Called only after a successful authenticated read. A credential the exchange rejected,
	 * or a validation that failed for transport reasons, never reaches this method, so a
	 * temporary network problem cannot start — or permanently damage — a lifecycle.
	 *
	 * <p>Both market scopes are started. The validated scope proves the key is usable on that
	 * market; the other scope is still attempted and simply reports its own honest outcome, so
	 * a spot-only key ends up connected on spot and honestly unavailable on futures.
	 */
	public void onCredentialConnected(User user) {
		if (!isEligible(user)) {
			log.debug("Portfolio sync not started: account is not live or sync is disabled user={}",
					user == null ? null : user.getId());
			return;
		}
		if (!hasUsableCredential(user)) {
			// Nothing to sync with. Says so, and never invents a scope.
			markBothScopesNotConnected(user, "No exchange credential is connected.");
			return;
		}
		for (AccountCategory category : SYNCABLE_SCOPES) {
			start(user, category);
		}
	}

	/**
	 * The account mode changed in Settings.
	 *
	 * <p>Switching to live starts both scopes. Switching back to paper stops them, because a
	 * socket kept open for an account the user has left in simulation serves no purpose and the
	 * event processor would reject every payload anyway.
	 */
	public void onAccountModeChanged(User user, AccountType previous, AccountType current) {
		if (user == null || current == null || current == previous) {
			return;
		}
		if (current == AccountType.LIVE) {
			onCredentialConnected(user);
		} else {
			stop(user, "The account is no longer in live mode.");
		}
	}

	/**
	 * The credential was deleted, disconnected or revoked.
	 *
	 * <p>Stops the lifecycle so no Binance socket outlives the credential it was opened with.
	 * The snapshot rows are deliberately left in place: they are the last known-good state and
	 * are better than being wiped by a disconnect. The connection row is marked not-connected
	 * so no caller can mistake them for current.
	 */
	public void onCredentialRemoved(User user) {
		stop(user, "The exchange credential was disconnected.");
	}

	/**
	 * Resume scopes that were already live before this process started.
	 *
	 * <p>Without this, every deploy would silently stop synchronising an account that had been
	 * connected all along, and the user would have to re-validate the credential by hand. Only
	 * users whose stored credential is already {@code CONNECTED} and whose account is in live
	 * mode are considered, so this cannot manufacture a lifecycle for an unvalidated key.
	 */
	@Transactional(readOnly = true)
	public void resumeAlreadyConnected() {
		if (!properties.isEnabled()) {
			return;
		}
		for (User user : userRepository.findByAccountType(AccountType.LIVE)) {
			Optional<ExchangeCredential> credential =
					credentialRepository.findByUser_IdAndExchange(user.getId(), ExchangeName.BINANCE);
			boolean connected = credential
					.map(c -> c.getStatus() == ExchangeConnectionStatus.CONNECTED)
					.orElse(Boolean.FALSE);
			if (connected) {
				log.info("Resuming LIVE Portfolio synchronization user={}", user.getId());
				onCredentialConnected(user);
			}
		}
	}

	// --------------------------------------------------------------- actions

	/**
	 * Starts one scope, unless it is ineligible, already active, or an attempt is in flight.
	 *
	 * @return the resulting stream state
	 */
	public LiveUserStreamManager.StreamState start(User user, AccountCategory category) {
		if (!isEligible(user) || !isSyncable(category)) {
			return LiveUserStreamManager.StreamState.DISCONNECTED;
		}
		String key = key(user.getId(), category);
		if (active.contains(key)) {
			// Already synchronising. Returning here is what makes a repeated trigger — a duplicate
			// connection event, a manual refresh, a reconnect racing a validation — cost nothing
			// and open no second socket.
			return LiveUserStreamManager.StreamState.CONNECTED;
		}
		if (!inFlight.add(key)) {
			// Another attempt for this exact scope is already running; joining it would risk a
			// second reconciliation and a second listen key.
			return LiveUserStreamManager.StreamState.CONNECTING;
		}
		try {
			// The REST read is in flight and nothing has been proven yet, so the scope is marked
			// SYNCING rather than left showing its previous state. This is what stops a caller from
			// reading CONNECTED while the baseline that justifies it is still outstanding.
			markSyncing(user, category);
			LiveUserStreamManager.StreamState state = streamManager.start(user, category);
			if (state == LiveUserStreamManager.StreamState.CONNECTED) {
				active.add(key);
			} else {
				// The REST baseline did not succeed, so no stream was opened and the previous
				// known-good snapshot is left untouched. Not marking it active means a later
				// trigger is free to retry instead of being permanently suppressed.
				log.info("Portfolio sync did not start user={} scope={} state={}",
						user.getId(), category, state);
			}
			return state;
		} finally {
			inFlight.remove(key);
		}
	}

	/** Stops both scopes for one user. Idempotent, and safe for a user that never started. */
	public void stop(User user, String reason) {
		if (user == null) {
			return;
		}
		for (AccountCategory category : SYNCABLE_SCOPES) {
			stop(user, category, reason);
		}
	}

	/** Stops one scope. Idempotent. */
	public void stop(User user, AccountCategory category, String reason) {
		if (user == null || category == null) {
			return;
		}
		active.remove(key(user.getId(), category));
		inFlight.remove(key(user.getId(), category));
		streamManager.stop(user, category);
		log.info("Stopped LIVE Portfolio synchronization user={} scope={} reason={}",
				user.getId(), category, reason);
	}

	/**
	 * Re-reads any scope the user-data stream has flagged {@code STALE}.
	 *
	 * <p>{@link LiveUserStreamEventProcessor} marks a scope stale when an event proves that a
	 * REST read is required — a spot deposit or withdrawal reports neither the new free nor the
	 * new locked balance, so nothing can be derived from it. Without this pass such a scope would
	 * stay stale indefinitely, because a reconciliation only happens on a socket drop and a
	 * healthy socket never drops.
	 *
	 * <p>Only stale scopes are read, so the pass is a no-op when idle. Reconciliation never starts
	 * a socket and never fabricates a value: it re-reads through the existing REST baseline.
	 */
	@Transactional
	public void reconcileStaleScopes() {
		if (!properties.isEnabled() || !properties.shouldReconcileStaleScopes()) {
			return;
		}
		for (User user : userRepository.findByAccountType(AccountType.LIVE)) {
			for (AccountCategory category : SYNCABLE_SCOPES) {
				if (!isStale(user, category)) {
					continue;
				}
				try {
					var report = category == AccountCategory.SPOT
							? reconcileService.reconcileSpot(user)
							: reconcileService.reconcileFutures(user);
					log.debug("Stale-scope reconciliation user={} scope={} succeeded={}",
							user.getId(), category, report.succeeded());
				} catch (RuntimeException ex) {
					// A failure here must never propagate into the scheduler and must never change
					// the stored credential: the key may still be perfectly valid.
					log.warn("Stale-scope reconciliation failed user={} scope={} reason={}",
							user.getId(), category, ex.getMessage());
				}
			}
		}
	}

	/**
	 * Stops every scope this coordinator started and releases the registry.
	 *
	 * <p>Delegated so the connector's per-scope sockets and listen keys are released exactly once.
	 * The local registries are cleared too, so the coordinator's own state stays coherent with the
	 * manager's instead of reporting scopes that no longer exist.
	 */
	@PreDestroy
	public void shutdown() {
		streamManager.shutdown();
		active.clear();
		inFlight.clear();
	}

	// --------------------------------------------------------------- queries

	/** True when this scope currently holds a coordinator-started lifecycle. */
	public boolean isActive(UUID userId, AccountCategory category) {
		return active.contains(key(userId, category));
	}

	/** Number of active scopes. Used by tests to prove no duplicate lifecycle was created. */
	public int activeScopeCount() {
		return active.size();
	}

	/** True when the coordinator is enabled and the account is in live mode. */
	public boolean isEligible(User user) {
		return properties.isEnabled()
				&& user != null
				&& user.getAccountType() == AccountType.LIVE;
	}

	// -------------------------------------------------------------- helpers

	/** MAIN and OPTIONS can never be widened into a stream, whatever is configured. */
	private boolean isSyncable(AccountCategory category) {
		return category != null
				&& SYNCABLE_SCOPES.contains(category)
				&& properties.allowsStreamFor(category.name());
	}

	/**
	 * Records that a scope is mid-synchronisation.
	 *
	 * <p>Status only. Availability {@code SYNCING} maps to connection status {@code CONNECTING} in
	 * the existing model, which is exactly the truth at this point: an attempt is under way and no
	 * baseline has been established yet.
	 *
	 * <p>Never downgrades a scope that is already unavailable because no credential exists; that
	 * state is more specific than "still working on it".
	 */
	private void markSyncing(User user, AccountCategory category) {
		try {
			var connection = connectionRepository.findByUserAndAccountModeAndAccountCategory(
					user, AccountMode.LIVE, category)
					.orElse(null);
			if (connection == null) {
				// No row yet, and none is created here: the row is created by the baseline read that
				// is about to run, which is the only thing that can fill in an honest status.
				return;
			}
			if (connection.getAvailability() == AccountAvailability.NOT_CONNECTED
					|| connection.getAvailability() == AccountAvailability.UNSUPPORTED) {
				return;
			}
			connection.setAvailability(AccountAvailability.SYNCING);
			connection.setConnectionStatus(ExchangeConnectionStatus.CONNECTING);
			connectionRepository.save(connection);
		} catch (RuntimeException ex) {
			log.debug("Could not record syncing status user={} scope={} reason={}",
					user.getId(), category, ex.getMessage());
		}
	}

	private boolean hasUsableCredential(User user) {
		return credentialRepository.findByUser_IdAndExchange(user.getId(), ExchangeName.BINANCE)
				.map(credential -> credential.getStatus() != ExchangeConnectionStatus.REVOKED)
				.orElse(Boolean.FALSE);
	}

	private boolean isStale(User user, AccountCategory category) {
		try {
			return connectionRepository
					.findByUserAndAccountModeAndAccountCategory(
							user, com.shyblack.cryptosignals.entity.enums.AccountMode.LIVE, category)
					.map(connection -> connection.getAvailability() == AccountAvailability.STALE)
					.orElse(Boolean.FALSE);
		} catch (RuntimeException ex) {
			log.debug("Could not read staleness user={} scope={} reason={}",
					user.getId(), category, ex.getMessage());
			return false;
		}
	}

	/**
	 * Records the honest reason a scope cannot sync.
	 *
	 * <p>Status only. No balance, position or order is touched, because nothing was read.
	 */
	private void markBothScopesNotConnected(User user, String message) {
		for (AccountCategory category : SYNCABLE_SCOPES) {
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
				connection.setAvailability(AccountAvailability.NOT_CONNECTED);
				connection.setConnectionStatus(ExchangeConnectionStatus.NOT_CONNECTED);
				connection.setLastSyncMessage(truncate(message));
				connectionRepository.save(connection);
			} catch (RuntimeException ex) {
				log.debug("Could not record not-connected status user={} scope={} reason={}",
						user.getId(), category, ex.getMessage());
			}
		}
	}

	private static String key(UUID userId, AccountCategory category) {
		return userId + ":" + category.name();
	}

	/** Matches the existing 200-character diagnostic column convention. */
	private static String truncate(String message) {
		if (message == null) {
			return null;
		}
		return message.length() <= 200 ? message : message.substring(0, 197) + "...";
	}
}