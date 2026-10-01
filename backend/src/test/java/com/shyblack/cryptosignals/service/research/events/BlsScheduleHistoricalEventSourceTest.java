package com.shyblack.cryptosignals.service.research.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class BlsScheduleHistoricalEventSourceTest {

	private static String table(String cells) {
		return "<html><table class=\"release-calendar\"><tr>" + cells + "</tr></table></html>";
	}

	@Test
	void parsesCpiWithEasternStandardTime_toUtc() {
		// January 08:30 ET = EST (UTC-5) -> 13:30Z
		List<NormalizedEvent> events = BlsScheduleHistoricalEventSource.parseMonth(
				table("<td>11 Consumer Price Index December 2023 08:30 AM</td>"), 2024, 1);
		assertThat(events).hasSize(1);
		NormalizedEvent e = events.get(0);
		assertThat(e.eventTime()).isEqualTo(Instant.parse("2024-01-11T13:30:00Z"));
		assertThat(e.externalEventId()).isEqualTo("BLS_CPI_2024-01-11");
		assertThat(e.category()).isEqualTo("MACRO");
		assertThat(e.eventType()).isEqualTo("CPI");
		assertThat(e.eventStage()).isEqualTo("OFFICIAL_CONFIRMATION");
		assertThat(e.sourceTier()).isEqualTo("TIER_1");
		assertThat(e.expected()).isNull();
		assertThat(e.actual()).isNull();
		assertThat(e.surprise()).isNull();
		assertThat(e.timestampQuality()).isEqualTo("EXACT");
		assertThat(e.assets()).isEqualTo("BTC:HIGH;ETH:HIGH");
	}

	@Test
	void parsesCpiWithEasternDaylightTime_toUtc() {
		// July 08:30 ET = EDT (UTC-4) -> 12:30Z
		List<NormalizedEvent> events = BlsScheduleHistoricalEventSource.parseMonth(
				table("<td>11 Consumer Price Index June 2024 08:30 AM</td>"), 2024, 7);
		assertThat(events).hasSize(1);
		assertThat(events.get(0).eventTime()).isEqualTo(Instant.parse("2024-07-11T12:30:00Z"));
	}

	@Test
	void skipsNextMonthSpilloverCells_capturedOnTheirOwnMonthPage() {
		// trailing cell is February (day resets) -> skipped here, not mis-dated.
		List<NormalizedEvent> events = BlsScheduleHistoricalEventSource.parseMonth(
				table("<td>31 Employment Situation December 2023 08:30 AM</td>"
						+ "<td>2 Employment Situation January 2024 08:30 AM</td>"),
				2024, 1);
		assertThat(events).extracting(NormalizedEvent::eventTime)
				.containsExactly(Instant.parse("2024-01-31T13:30:00Z"));
	}

	@Test
	void impossibleDate_doesNotCrashAndIsSkipped() {
		// Feb 30 does not exist -> the month must not fail; the bad cell is dropped.
		List<NormalizedEvent> events = BlsScheduleHistoricalEventSource.parseMonth(
				table("<td>30 Consumer Price Index January 2026 08:30 AM</td>"), 2026, 2);
		assertThat(events).isEmpty();
	}

	@Test
	void importsOnlyTargetHighImpactPrograms() {
		List<NormalizedEvent> events = BlsScheduleHistoricalEventSource.parseMonth(
				table("<td>5 Employment Situation December 2023 08:30 AM</td>"
						+ "<td>12 Producer Price Index December 2023 08:30 AM</td>"
						+ "<td>18 Job Openings and Labor Turnover Survey November 2023 10:00 AM</td>"),
				2024, 1);
		assertThat(events).extracting(NormalizedEvent::eventType).containsExactlyInAnyOrder("NFP", "PPI");
	}
}
