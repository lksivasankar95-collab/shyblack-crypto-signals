package com.shyblack.cryptosignals.service.research.events;

import com.shyblack.cryptosignals.config.HttpClientFactory;
import com.shyblack.cryptosignals.config.ResearchEventProperties;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real historical FOMC statement events from the Federal Reserve.
 *
 * <p>Enumeration comes from the official FOMC calendar page
 * ({@code /monetarypolicy/fomccalendars.htm}); the exact release time is read
 * from each statement page, which states "For release at 2:00 p.m. EST/EDT".
 * The ET timezone label in the source is used directly (−5h EST / −4h EDT), so no
 * guess or convention is applied. Statements without a stated time are skipped
 * (ineligible), never given a fabricated timestamp.</p>
 */
@Component
public class FederalReserveHistoricalEventSource implements HistoricalEventSource {

	private static final Logger log = LoggerFactory.getLogger(FederalReserveHistoricalEventSource.class);
	private static final String UA = "ShyBlackResearchBot/1.0 (research dataset; contact: research@shyblack.local)";

	private static final Pattern STATEMENT_LINK = Pattern.compile("monetary(\\d{8})a\\.htm");
	private static final Pattern RELEASE_TIME = Pattern.compile(
			"(?is)For release at\\s+(\\d{1,2}):(\\d{2})\\s*(a\\.?m\\.?|p\\.?m\\.?)\\s*(EST|EDT)");
	private static final DateTimeFormatter DATE_ID = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	private final RestClient rest;
	private final ResearchEventProperties properties;

	public FederalReserveHistoricalEventSource(ResearchEventProperties properties) {
		this.properties = properties;
		this.rest = RestClient.builder()
				.baseUrl("https://www.federalreserve.gov")
				.requestFactory(HttpClientFactory.withDefaultTimeouts())
				.defaultHeader("User-Agent", UA)
				.build();
	}

	@Override public String sourceKey() { return "fed"; }
	@Override public String sourceName() { return "Federal Reserve"; }
	@Override public String sourceTier() { return "TIER_1"; }

	@Override
	public EventSourceResult collect(Instant from, Instant to) {
		String calendar;
		try {
			calendar = fetchWithRetry("/monetarypolicy/fomccalendars.htm");
		} catch (Exception ex) {
			return EventSourceResult.of(sourceKey(), EventSourceStatus.FAILED, List.of(),
					"calendar fetch failed: " + ex.getMessage());
		}
		List<LocalDate> dates = parseStatementDates(calendar, from, to);
		List<NormalizedEvent> events = new ArrayList<>();
		int skippedNoTime = 0;
		for (LocalDate date : dates) {
			try {
				String html = fetchWithRetry("/newsevents/pressreleases/monetary"
						+ date.format(DateTimeFormatter.BASIC_ISO_DATE) + "a.htm");
				Instant time = parseReleaseInstant(date, html);
				if (time == null) {
					skippedNoTime++;
					continue;
				}
				events.add(new NormalizedEvent(
						"FED_FOMC_" + date + "_STATEMENT", time, time, "CENTRAL_BANK", "FOMC",
						"OFFICIAL_CONFIRMATION", "Federal Reserve", "TIER_1",
						"FOMC statement " + date, null, null, null, "EXACT", "BTC:HIGH;ETH:HIGH"));
			} catch (Exception ex) {
				log.warn("[FED] statement {} failed: {}", date, ex.getMessage());
			}
		}
		EventSourceStatus status = events.isEmpty() ? EventSourceStatus.NO_HISTORICAL_WINDOW
				: EventSourceStatus.SUCCESS;
		return EventSourceResult.of(sourceKey(), status, events,
				"statementsInWindow=" + dates.size() + " events=" + events.size()
						+ " skippedNoTime=" + skippedNoTime);
	}

	private String fetchWithRetry(String path) {
		RuntimeException last = null;
		for (int attempt = 1; attempt <= properties.maxRetries() + 1; attempt++) {
			try {
				String body = rest.get().uri(path).accept(MediaType.TEXT_HTML).retrieve().body(String.class);
				if (body == null || body.isBlank()) {
					throw new IllegalStateException("empty response");
				}
				return body;
			} catch (RuntimeException ex) {
				last = ex;
				if (attempt <= properties.maxRetries()) {
					try {
						Thread.sleep(properties.retryBackoffMs() * attempt);
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						throw new IllegalStateException("interrupted", ie);
					}
				}
			}
		}
		throw new IllegalStateException("fetch failed after retries: " + path, last);
	}

	/** Statement dates (from {@code monetaryYYYYMMDDa.htm} links) within [from,to). */
	public static List<LocalDate> parseStatementDates(String calendarHtml, Instant from, Instant to) {
		Set<LocalDate> dates = new LinkedHashSet<>();
		Matcher m = STATEMENT_LINK.matcher(calendarHtml);
		while (m.find()) {
			LocalDate date = LocalDate.parse(m.group(1), DateTimeFormatter.BASIC_ISO_DATE);
			Instant t = date.atStartOfDay(ZoneOffset.UTC).toInstant();
			if (!t.isBefore(from) && t.isBefore(to)) {
				dates.add(date);
			}
		}
		return new ArrayList<>(dates);
	}

	/** Exact release instant given the statement date and its HTML. */
	public static Instant parseReleaseInstant(LocalDate date, String statementHtml) {
		Matcher m = RELEASE_TIME.matcher(statementHtml);
		if (!m.find()) {
			return null;
		}
		int hour = Integer.parseInt(m.group(1));
		int minute = Integer.parseInt(m.group(2));
		String ampm = m.group(3).replace(".", "").toLowerCase();
		if (ampm.startsWith("p") && hour != 12) hour += 12;
		if (ampm.startsWith("a") && hour == 12) hour = 0;
		String zone = m.group(4).toUpperCase();
		ZoneOffset offset = "EST".equals(zone) ? ZoneOffset.ofHours(-5) : ZoneOffset.ofHours(-4);
		return date.atTime(hour, minute).toInstant(offset);
	}
}
