package com.shyblack.cryptosignals.service.portfolio;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Resumes LIVE Portfolio synchronization for scopes that were already live before this process
 * started.
 *
 * <p>Without this, every deploy would silently stop synchronising an account that had been
 * connected all along: the user-data streams live in memory, so a restart closes them, and the
 * only trigger for a new one is a fresh credential validation. The user would have to re-validate
 * by hand to get their Portfolio back.
 *
 * <p>Conservative on purpose. Only users whose stored credential is already recorded as
 * {@code CONNECTED} <em>and</em> whose account is in live mode are considered, so a restart can
 * never manufacture a lifecycle for a key the exchange has not accepted.
 *
 * <p>Never fails startup: synchronization is a read-only enhancement, and an exchange that is
 * briefly unreachable during a boot must not prevent the application from starting.
 *
 * <p>Runs last so the full Spring context — repositories, adapters, the connector — is ready.
 */
@Component
@Order(Integer.MAX_VALUE)
@ConditionalOnProperty(
		name = "app.portfolio.sync.enabled",
		havingValue = "true")
public class PortfolioSyncResumeRunner implements ApplicationRunner {

	private final PortfolioSyncLifecycleCoordinator coordinator;

	public PortfolioSyncResumeRunner(PortfolioSyncLifecycleCoordinator coordinator) {
		this.coordinator = coordinator;
	}

	@Override
	public void run(ApplicationArguments args) {
		try {
			coordinator.resumeAlreadyConnected();
		} catch (RuntimeException ex) {
			log.warn("Could not resume LIVE Portfolio synchronization: {}", ex.getMessage());
		}
	}

	private static final org.slf4j.Logger log =
			org.slf4j.LoggerFactory.getLogger(PortfolioSyncResumeRunner.class);
}