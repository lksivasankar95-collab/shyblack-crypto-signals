package com.shyblack.cryptosignals.service.backtest.research;

import com.shyblack.cryptosignals.market.DerivativesSnapshot;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalCandle;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic pre-run data-quality gate. A failing HARD gate blocks ONLY the
 * run with an explicit reason list — it never coerces missing data to zero and
 * never fabricates values. Optional/absent derivatives remain UNKNOWN.
 */
public final class ValidationDataQualityGate {

	private ValidationDataQualityGate() {
	}

	public enum Status { PASS, BLOCKED }

	public record Result(Status status, List<String> failures) {
		public boolean passed() {
			return status == Status.PASS;
		}
		public static Result pass() {
			return new Result(Status.PASS, List.of());
		}
		public static Result blocked(List<String> failures) {
			return new Result(Status.BLOCKED, List.copyOf(failures));
		}
	}

	public static Result checkCandles(List<HistoricalCandle> candles, Instant from, Instant to) {
		List<String> f = new ArrayList<>();
		if (candles == null || candles.isEmpty()) {
			f.add("candles: empty dataset");
			return Result.blocked(f);
		}
		Set<Instant> seen = new HashSet<>();
		Instant prev = null;
		int i = 0;
		for (HistoricalCandle c : candles) {
			i++;
			if (c == null || c.openTime() == null) {
				f.add("candles: null candle/openTime at index " + i);
				continue;
			}
			if (!seen.add(c.openTime())) {
				f.add("candles: duplicate openTime " + c.openTime());
			}
			if (prev != null && !c.openTime().isAfter(prev)) {
				f.add("candles: not strictly chronological at " + c.openTime());
			}
			prev = c.openTime();
			if (c.open() == null || c.high() == null || c.low() == null || c.close() == null) {
				f.add("candles: missing OHLC at " + c.openTime());
				continue;
			}
			if (c.open().signum() <= 0 || c.high().signum() <= 0
					|| c.low().signum() <= 0 || c.close().signum() <= 0) {
				f.add("candles: non-positive price at " + c.openTime());
			}
			if (c.high().compareTo(c.low()) < 0) {
				f.add("candles: high<low at " + c.openTime());
			}
			if (c.low().compareTo(c.open().min(c.close())) > 0
					|| c.high().compareTo(c.open().max(c.close())) < 0) {
				f.add("candles: impossible OHLC body at " + c.openTime());
			}
			if (c.volume() != null && c.volume().signum() < 0) {
				f.add("candles: negative volume at " + c.openTime());
			}
		}
		if (from != null) {
			Instant first = candles.get(0).openTime();
			if (first != null && first.isBefore(from)) {
				f.add("candles: first candle before requested start");
			}
		}
		return f.isEmpty() ? Result.pass() : Result.blocked(f);
	}

	public static Result checkEvents(List<HistoricalEvent> events, Instant from, Instant to) {
		List<String> f = new ArrayList<>();
		Set<Object> ids = new HashSet<>();
		if (events != null) {
			int i = 0;
			for (HistoricalEvent e : events) {
				i++;
				if (e == null || e.time() == null) {
					f.add("events: null time at index " + i);
					continue;
				}
				if (e.id() == null) {
					f.add("events: missing external id at " + e.time());
				} else if (!ids.add(e.id())) {
					f.add("events: duplicate external id " + e.id());
				}
				if (from != null && e.time().isBefore(from)) f.add("events: before window " + e.time());
				if (to != null && !e.time().isBefore(to)) f.add("events: at/after window end " + e.time());
			}
		}
		return f.isEmpty() ? Result.pass() : Result.blocked(f);
	}

	/**
	 * Enforces the "missing != 0" invariant: a value must be present iff its
	 * availability flag is true. Absent derivatives stay null (UNKNOWN).
	 */
	public static Result checkDerivatives(DerivativesSnapshot d) {
		List<String> f = new ArrayList<>();
		if (d == null) {
			return Result.pass(); // no snapshot = all UNKNOWN, allowed
		}
		if (d.openInterestAvailable() != (d.openInterest() != null)) {
			f.add("derivatives: openInterest present/available mismatch (missing must stay null, never 0)");
		}
		if (d.fundingAvailable() != (d.lastFundingRate() != null)) {
			f.add("derivatives: funding present/available mismatch");
		}
		boolean liqPresent = d.longLiquidationVolume() != null || d.shortLiquidationVolume() != null;
		if (d.liquidationAvailable() != liqPresent) {
			f.add("derivatives: liquidation present/available mismatch");
		}
		return f.isEmpty() ? Result.pass() : Result.blocked(f);
	}

	public static Result checkAll(List<HistoricalCandle> candles, List<HistoricalEvent> events,
			Instant from, Instant to) {
		List<String> f = new ArrayList<>();
		f.addAll(checkCandles(candles, from, to).failures());
		f.addAll(checkEvents(events, from, to).failures());
		return f.isEmpty() ? Result.pass() : Result.blocked(f);
	}
}
