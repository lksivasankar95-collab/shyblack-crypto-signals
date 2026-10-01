package com.shyblack.cryptosignals.service.research;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal CSV reader for Binance Vision / normalized research files. The
 * supported sources are comma-separated with no embedded commas; a small
 * double-quote handler is included for safety. Not a general RFC-4180 parser.
 */
public final class ResearchCsv {

	private ResearchCsv() {
	}

	public static List<String> split(String line) {
		List<String> out = new ArrayList<>();
		if (line == null) {
			return out;
		}
		StringBuilder current = new StringBuilder();
		boolean inQuotes = false;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == '"') {
				inQuotes = !inQuotes;
			} else if (c == ',' && !inQuotes) {
				out.add(current.toString().trim());
				current.setLength(0);
			} else {
				current.append(c);
			}
		}
		out.add(current.toString().trim());
		return out;
	}

	/** A header row has a non-numeric first column (e.g. "open_time"). */
	public static boolean isHeader(List<String> cols) {
		if (cols.isEmpty()) {
			return true;
		}
		String first = cols.get(0);
		if (first == null || first.isBlank()) {
			return false;
		}
		try {
			Long.parseLong(first.trim());
			return false;
		} catch (NumberFormatException ex) {
			return true;
		}
	}
}
