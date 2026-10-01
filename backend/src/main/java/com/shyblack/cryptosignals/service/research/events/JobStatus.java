package com.shyblack.cryptosignals.service.research.events;

/** Lifecycle of a bounded event-collection job. */
public enum JobStatus {
	CREATED,
	RUNNING,
	COMPLETED,
	PARTIAL,
	FAILED,
	TIMEOUT
}
