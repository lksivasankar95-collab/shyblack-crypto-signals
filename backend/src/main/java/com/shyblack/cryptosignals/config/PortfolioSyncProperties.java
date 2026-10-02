package com.shyblack.cryptosignals.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Whether the LIVE Portfolio synchronization lifecycle is active.
 *
 * <p>Deliberately separate from every trading flag. This governs <em>reading</em> exchange
 * account state for the Portfolio; it never places, cancels or modifies an order, and it is
 * independent of {@code app.live-trading.auto-execute} and
 * {@code app.futures-trading.auto-execute}, which are untouched by it.
 *
 * <p>Defaults to <b>off</b>. A deployment must opt in explicitly, so shipping this code can
 * never by itself open exchange streams. It is also off in the test profile, so no test can
 * reach an exchange socket through this path.
 *
 * @param enabled master switch for the whole lifecycle
 * @param scopes which market accounts may be synchronised; SPOT and FUTURES only. MAIN and
 *     OPTIONS are structurally incapable of holding a stream and are rejected regardless
 *     of what is configured here
 * @param baselineOnlyStartScopes when true, only the REST baseline is driven and no
 *     user-data stream is opened. Useful for an operator who wants accurate snapshots
 *     without a persistent socket
 * @param staleReconcileIntervalMs how often to re-check a scope the user-data stream has
 *     flagged {@code STALE} (for example after a deposit). Only scopes reported stale are
 *     read, so this is cheap when idle
 * @param staleReconcileEnabled whether that stale re-check runs at all
 */
@ConfigurationProperties(prefix = "app.portfolio.sync")
public record PortfolioSyncProperties(
		Boolean enabled,
		java.util.List<String> scopes,
		Boolean baselineOnlyStartScopes,
		Long staleReconcileIntervalMs,
		Boolean staleReconcileEnabled) {

	public PortfolioSyncProperties {
		scopes = scopes == null || scopes.isEmpty() ? null : scopes;
	}

	public boolean isEnabled() {
		return enabled != null && enabled;
	}

	/**
	 * True when this scope may hold a user-data stream.
	 *
	 * <p>Only SPOT and FUTURES are ever eligible. MAIN is an aggregate read-model scope and
	 * OPTIONS is a reserved capability, so neither can be widened by configuration.
	 */
	public boolean allowsStreamFor(String category) {
		return category != null
				&& (category.equalsIgnoreCase("SPOT") || category.equalsIgnoreCase("FUTURES"));
	}

	/** True when a user-data stream may be opened for a configured scope. */
	public boolean shouldOpenStreams() {
		return !baselineOnlyStartScopes();
	}

	public boolean shouldReconcileStaleScopes() {
		return staleReconcileEnabled == null || staleReconcileEnabled;
	}

	public long staleReconcileInterval() {
		return staleReconcileIntervalMs == null || staleReconcileIntervalMs <= 0
				? 60_000L
				: staleReconcileIntervalMs;
	}
}