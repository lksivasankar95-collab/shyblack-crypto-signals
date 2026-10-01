package com.shyblack.cryptosignals.service.research.events;

import java.time.Instant;

/**
 * A source adapter that retrieves REAL historical events from an official (or
 * otherwise auditable) source. Adapters must never fabricate records; when a
 * source is unavailable they return a BLOCKED/FAILED status.
 */
public interface HistoricalEventSource {

	String sourceKey();

	String sourceName();

	/** Tier-1 official, Tier-2 institutional, etc. */
	String sourceTier();

	EventSourceResult collect(Instant from, Instant to);
}
