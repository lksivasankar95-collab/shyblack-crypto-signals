package com.shyblack.cryptosignals.service.research.events;

/** Per-source collection outcome (reported verbatim; never silently skipped). */
public enum EventSourceStatus {
	SUCCESS,
	NO_HISTORICAL_WINDOW,
	BLOCKED,
	FAILED,
	SKIPPED
}
