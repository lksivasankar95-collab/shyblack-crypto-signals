package com.shyblack.cryptosignals.service.research.events;

import com.shyblack.cryptosignals.config.HttpClientFactory;
import com.shyblack.cryptosignals.config.ResearchEventProperties;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real historical macro events from the BLS "Schedule of Releases" monthly
 * archives ({@code /schedule/YYYY/MM_sched.htm}) — official (Tier 1), keyless,
 * exact release times in US Eastern Time (converted to UTC, DST-aware).
 *
 * <p>All network calls use the project's standard bounded HTTP timeouts
 * (connect 5s / read 15s) plus a bounded retry policy; a month that exhausts
 * retries is skipped (never retried forever). No data is fabricated.</p>
 */
@Component
public class BlsScheduleHistoricalEventSource implements HistoricalEventSource {

	private static final Logger log = LoggerFactory.getLogger(BlsScheduleHistoricalEventSource.class);
	private static final String UA = "ShyBlackResearchBot/1.0 (research dataset; contact: research@shyblack.local)";
	private static final ZoneId EASTERN = ZoneId.of("America/New_York");

	private static final Pattern TABLE = Pattern.compile("(?s)<table[^>]*release-calendar[^>]*>(.*?)</table>");
	private static final Pattern CELL = Pattern.compile("(?s)<td[^>]*>(.*?)</td>");
	private static final Pattern DAY = Pattern.compile("^(\\d{1,2})\\b");
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US);

	private static final Map<String, String> PROGRAMS = new LinkedHashMap<>();

	static {
		PROGRAMS.put("Consumer Price Index", "CPI");
		PROGRAMS.put("Producer Price Index", "PPI");
		PROGRAMS.put("Employment Situation", "NFP");
	}

	private final RestClient rest;
	private final ResearchEventProperties properties;

	public BlsScheduleHistoricalEventSource(ResearchEventProperties properties) {
		this.properties = properties;
		this.rest = RestClient.builder()
				.baseUrl("https://www.bls.gov")
				.requestFactory(HttpClientFactory.withDefaultTimeouts())
				.defaultHeader("User-Agent", UA)
				.build();
	}

	@Override public String sourceKey() { return "bls"; }
	@Override public String sourceName() { return "U.S. Bureau of Labor Statistics"; }
	@Override public String sourceTier() { return "TIER_1"; }

	@Override
	public EventSourceResult collect(Instant from, Instant to) {
		List<NormalizedEvent> events = new ArrayList<>();
		int ok = 0, failed = 0, pages = 0;
		YearMonth start = YearMonth.from(from.atZone(EASTERN));
		YearMonth end = YearMonth.from(to.atZone(EASTERN));
		for (YearMonth ym = start; !ym.isAfter(end) && pages < properties.maxPages(); ym = ym.plusMonths(1)) {
			pages++;
			try {
				List<NormalizedEvent> month = fetchMonth(ym);
				ok++;
				month.stream().filter(e -> !e.eventTime().isBefore(from) && e.eventTime().isBefore(to))
						.forEach(events::add);
				if (events.size() >= properties.maxRecords()) break;
			} catch (Exception ex) {
				failed++;
				log.warn("[BLS] month={} failed after retries: {}", ym, ex.getMessage());
			}
		}
		if (ok == 0) {
			return EventSourceResult.of(sourceKey(), EventSourceStatus.FAILED, List.of(),
					"all " + failed + " monthly pages failed");
		}
		return EventSourceResult.of(sourceKey(), EventSourceStatus.SUCCESS, events,
				"monthsOk=" + ok + " monthsFailed=" + failed + " pages=" + pages);
	}

	/** Fetches + parses one month with a bounded retry policy. Visible for the job runner. */
	public List<NormalizedEvent> fetchMonth(YearMonth ym) {
		String path = String.format("/schedule/%d/%02d_sched.htm", ym.getYear(), ym.getMonthValue());
		RuntimeException last = null;
		for (int attempt = 1; attempt <= properties.maxRetries() + 1; attempt++) {
			long started = System.currentTimeMillis();
			try {
				log.info("[BLS] START month={} attempt={} path={}", ym, attempt, path);
				String html = rest.get().uri(path).accept(MediaType.TEXT_HTML).retrieve().body(String.class);
				if (html == null || html.isBlank()) {
					throw new IllegalStateException("empty response");
				}
				List<NormalizedEvent> parsed = parseMonth(html, ym.getYear(), ym.getMonthValue());
				log.info("[BLS] END month={} attempt={} records={} durationMs={}",
						ym, attempt, parsed.size(), System.currentTimeMillis() - started);
				return parsed;
			} catch (RuntimeException ex) {
				last = ex;
				log.warn("[BLS] FAIL month={} attempt={} error={} nextRetryInMs={}",
						ym, attempt, ex.getMessage(),
						attempt <= properties.maxRetries() ? properties.retryBackoffMs() * attempt : 0);
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
		throw new IllegalStateException("BLS fetch failed after retries for " + ym + ": "
				+ (last == null ? "unknown" : last.getMessage()), last);
	}

	public static List<NormalizedEvent> parseMonth(String html, int year, int month) {
		List<NormalizedEvent> out = new ArrayList<>();
		Matcher table = TABLE.matcher(html);
		if (!table.find()) {
			return out;
		}
		Matcher cell = CELL.matcher(table.group(1));
		int curYear = year, curMonth = month, lastDay = 0;
		while (cell.find()) {
			String text = cell.group(1).replaceAll("(?s)<[^>]+>", " ")
					.replace("&nbsp;", " ").replaceAll("\\s+", " ").trim();
			Matcher dm = DAY.matcher(text);
			if (!dm.find()) {
				continue;
			}
			int day = Integer.parseInt(dm.group(1));
			if (lastDay != 0 && day < lastDay) {
				curMonth++;
				if (curMonth > 12) {
					curMonth = 1;
					curYear++;
				}
			}
			lastDay = day;
			for (Map.Entry<String, String> prog : PROGRAMS.entrySet()) {
				Pattern p = Pattern.compile(Pattern.quote(prog.getKey())
						+ "\\s+(.+?)\\s+(\\d{1,2}:\\d{2}\\s*[AaPp][Mm])");
				Matcher em = p.matcher(text);
				if (!em.find()) {
					continue;
				}
				LocalTime lt;
				try {
					lt = LocalTime.parse(em.group(2).toUpperCase(Locale.US).replaceAll("\\s+", " "), TIME);
				} catch (Exception ex) {
					continue;
				}
				Instant eventTime = ZonedDateTime.of(
						LocalDate.of(curYear, curMonth, day), lt, EASTERN).toInstant();
				String id = "BLS_" + prog.getValue() + "_" + LocalDate.of(curYear, curMonth, day);
				out.add(new NormalizedEvent(
						id, eventTime, eventTime, "MACRO", prog.getValue(), "OFFICIAL_CONFIRMATION",
						"BLS", "TIER_1", prog.getKey() + " (" + em.group(1).trim() + ")",
						null, null, null, "EXACT", "BTC:HIGH;ETH:HIGH"));
			}
		}
		return out;
	}
}
