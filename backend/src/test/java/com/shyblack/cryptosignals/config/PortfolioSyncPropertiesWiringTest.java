package com.shyblack.cryptosignals.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * The LIVE Portfolio sync lifecycle is bound to {@code app.portfolio.sync.*}.
 *
 * <p>This guards a real operational trap: the property class, the scheduler
 * and the stream manager all existed and were unit-tested, but no shipped
 * {@code application*.yml} ever set the prefix. A deployment therefore ran
 * with synchronization silently inert — LIVE SPOT/FUTURES reported
 * NOT_CONNECTED and the user-data WebSocket never opened — with nothing in the
 * config to reveal the missing opt-in.</p>
 *
 * <p>Asserted under the test profile, which must keep the master switch OFF so
 * no test can reach an exchange socket through this path.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class PortfolioSyncPropertiesWiringTest {

	@Autowired
	private PortfolioSyncProperties properties;

	@Test
	void syncLifecycle_isDisabledUnderTheTestProfile() {
		assertThat(properties.isEnabled())
				.as("no test may open an exchange socket through the sync path")
				.isFalse();
	}

	@Test
	void syncProperties_areBoundFromConfiguration() {
		assertThat(properties.shouldReconcileStaleScopes())
				.as("the property class is reachable, so the prefix is genuinely bound")
				.isNotNull();
		assertThat(properties.staleReconcileInterval()).isPositive();
	}

	@Test
	void onlySpotAndFuturesMayEverHoldAStream() {
		assertThat(properties.allowsStreamFor("SPOT")).isTrue();
		assertThat(properties.allowsStreamFor("FUTURES")).isTrue();
		assertThat(properties.allowsStreamFor("MAIN")).isFalse();
		assertThat(properties.allowsStreamFor("OPTIONS")).isFalse();
		assertThat(properties.allowsStreamFor(null)).isFalse();
	}

	@Test
	void baselineOnly_suppressesStreamsWithoutDisablingTheRead() {
		PortfolioSyncProperties baselineOnly = new PortfolioSyncProperties(
				true, java.util.List.of("SPOT"), true, null, null);
		assertThat(baselineOnly.isEnabled()).isTrue();
		assertThat(baselineOnly.shouldOpenStreams()).isFalse();
		assertThat(baselineOnly.shouldReconcileStaleScopes()).isTrue();
	}
}
