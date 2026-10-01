package com.shyblack.cryptosignals.service.research;

/** Result metrics for one file import. */
public record ResearchImportSummary(
		String sourceFile,
		ResearchImportKind kind,
		String datasetVersion,
		String checksum,
		long rowsRead,
		long rowsInserted,
		long rowsDuplicate,
		long rowsRejected,
		boolean skipped,
		String status,
		long durationMs
) {
}
