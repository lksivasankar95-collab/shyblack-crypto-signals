package com.shyblack.cryptosignals.service.research.events;

import java.util.List;

/** Result of one adapter run. */
public record EventSourceResult(
		String sourceKey,
		EventSourceStatus status,
		List<NormalizedEvent> events,
		String note
) {
	public static EventSourceResult of(String key, EventSourceStatus status, List<NormalizedEvent> events, String note) {
		return new EventSourceResult(key, status, events == null ? List.of() : events, note);
	}
}
