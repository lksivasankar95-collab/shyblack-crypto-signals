package com.shyblack.cryptosignals.service.research.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FederalReserveHistoricalEventSourceTest {

	private static final Instant FROM = Instant.parse("2023-09-01T00:00:00Z");
	private static final Instant TO = Instant.parse("2026-10-01T00:00:00Z");

	@Test
	void enumeratesStatementDatesWithinWindow() {
		String calendar = "<a href=\"/newsevents/pressreleases/monetary20210127a.htm\">x</a>"
				+ "<a href=\"/newsevents/pressreleases/monetary20240131a.htm\">y</a>"
				+ "<a href=\"/newsevents/pressreleases/monetary20260916a.htm\">z</a>";
		List<LocalDate> dates = FederalReserveHistoricalEventSource.parseStatementDates(calendar, FROM, TO);
		assertThat(dates).containsExactly(LocalDate.of(2024, 1, 31), LocalDate.of(2026, 9, 16));
	}

	@Test
	void parsesEasternStandardTime_toUtc() {
		String html = "<p>For release at 2:00 p.m. EST</p>";
		Instant t = FederalReserveHistoricalEventSource.parseReleaseInstant(LocalDate.of(2024, 1, 31), html);
		assertThat(t).isEqualTo(Instant.parse("2024-01-31T19:00:00Z")); // EST = UTC-5
	}

	@Test
	void parsesEasternDaylightTime_toUtc() {
		String html = "<p>For release at 2:00 p.m. EDT</p>";
		Instant t = FederalReserveHistoricalEventSource.parseReleaseInstant(LocalDate.of(2024, 9, 18), html);
		assertThat(t).isEqualTo(Instant.parse("2024-09-18T18:00:00Z")); // EDT = UTC-4
	}

	@Test
	void missingTime_isNotFabricated() {
		Instant t = FederalReserveHistoricalEventSource.parseReleaseInstant(
				LocalDate.of(2024, 1, 31), "<p>Statement</p>");
		assertThat(t).isNull();
	}
}
