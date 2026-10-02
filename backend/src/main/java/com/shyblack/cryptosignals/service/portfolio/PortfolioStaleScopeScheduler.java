package com.shyblack.cryptosignals.service.portfolio;

import com.shyblack.cryptosignals.config.PortfolioSyncProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic re-read of scopes the user-data stream has flagged {@code STALE}.
 *
 * <p>Exists because a healthy socket never drops, and a stale flag is raised precisely when a
 * socket is <em>healthy</em>: a spot deposit or withdrawal reports neither the new free nor the
 * new locked balance, so the event proves a REST read is needed without being able to supply
 * it. Without this pass such a scope would stay stale until an unrelated disconnect.
 *
 * <p>Split from the coordinator so the coordinator itself has no scheduling concern and stays
 * callable directly from tests. The whole bean is conditional on the master switch, so with
 * synchronization disabled — the default, and the test profile — nothing here is created and no
 * timer exists.
 */
@Component
@ConditionalOnProperty(
		name = "app.portfolio.sync.enabled",
		havingValue = "true")
public class PortfolioStaleScopeScheduler {

	private final PortfolioSyncLifecycleCoordinator coordinator;
	private final PortfolioSyncProperties properties;

	public PortfolioStaleScopeScheduler(
			PortfolioSyncLifecycleCoordinator coordinator,
			PortfolioSyncProperties properties) {
		this.coordinator = coordinator;
		this.properties = properties;
	}

	/**
	 * Fixed delay rather than a cron, so a slow pass delays only itself and can never overlap
	 * with the next one.
	 */
	@Scheduled(
			initialDelayString = "${app.portfolio.sync.stale-reconcile-interval-ms:60000}",
			fixedDelayString = "${app.portfolio.sync.stale-reconcile-interval-ms:60000}")
	public void reconcileStaleScopes() {
		if (!properties.shouldReconcileStaleScopes()) {
			return;
		}
		coordinator.reconcileStaleScopes();
	}
}