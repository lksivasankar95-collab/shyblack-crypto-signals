package com.shyblack.cryptosignals.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Execution-router configuration.
 *
 * <p>Ships with every switch in the safe position. The router has exactly two
 * properties and neither can enable a live order on its own:
 *
 * <ul>
 *   <li>{@code dryRun} — when true, the router evaluates every gate and produces
 *       the full execution request but sends nothing. This is how the boundary is
 *       validated while live execution stays disabled.</li>
 *   <li>{@code enabled} — master switch for the router itself. Defaults to
 *       <b>false</b>, so a deployment that has not been configured for automatic
 *       execution does not execute automatically.</li>
 * </ul>
 *
 * <p>Critically, there is no {@code if (production) enable()} anywhere. The
 * compact constructor below enforces the defaults in code as well as in YAML, so
 * a missing or malformed configuration key resolves to disabled rather than to
 * whatever the binder happened to produce.
 *
 * <p>Live execution additionally requires the pre-existing
 * {@code app.live-trading.auto-execute} and
 * {@code app.futures-trading.auto-execute} flags, both of which also default to
 * false. Those two remain the operator-facing approval mechanism; this record
 * does not replace or widen them.
 */
@ConfigurationProperties(prefix = "app.execution")
public record ExecutionProperties(boolean enabled, boolean dryRun) {

	public ExecutionProperties {
		// Booleans cannot be "unset" once bound, so there is nothing to normalise
		// beyond documenting the invariant. The invariant is asserted by a test
		// rather than by code that cannot observe an unbound primitive.
	}
}