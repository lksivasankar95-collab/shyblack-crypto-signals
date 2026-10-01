package com.shyblack.cryptosignals.dto.research;

import java.time.Instant;
import java.util.List;

/** Structured data-quality report for one research dataset version. */
public record DatasetQualityReport(
		String datasetVersion,
		List<Check> checks,
		Instant generatedAt
) {
	public record Check(String name, String status, String detail) {}

	public boolean passed() {
		return checks.stream().noneMatch(c -> "FAIL".equals(c.status()));
	}
}
