package com.shyblack.cryptosignals.service.research.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A normalized historical event, ready to serialize to the governed EVENT JSONL
 * consumed by {@code ResearchDataImportService}. Expected/actual/surprise are
 * taken verbatim from the source and left null when not auditable — never
 * computed here.
 */
public record NormalizedEvent(
		String externalEventId,
		Instant eventTime,
		Instant confirmationTimestamp,
		String category,
		String eventType,
		String eventStage,
		String source,
		String sourceTier,
		String headline,
		BigDecimal expected,
		BigDecimal actual,
		BigDecimal surprise,
		/** EXACT | APPROXIMATE | UNKNOWN (see docs) */
		String timestampQuality,
		/** "BTC:HIGH;ETH:HIGH" (matches the EVENT importer schema). */
		String assets
) {

	public Map<String, Object> toMap() {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("external_event_id", externalEventId);
		m.put("event_time", eventTime == null ? null : eventTime.toString());
		m.put("confirmation_timestamp", confirmationTimestamp == null ? null : confirmationTimestamp.toString());
		m.put("category", category);
		m.put("event_type", eventType);
		m.put("event_stage", eventStage);
		m.put("source", source);
		m.put("source_tier", sourceTier);
		m.put("headline", headline);
		m.put("expected", expected);
		m.put("actual", actual);
		m.put("surprise", surprise);
		m.put("timestamp_quality", timestampQuality);
		m.put("assets", assets);
		return m;
	}
}
