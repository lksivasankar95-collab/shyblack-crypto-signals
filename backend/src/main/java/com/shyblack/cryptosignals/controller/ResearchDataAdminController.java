package com.shyblack.cryptosignals.controller;

import com.shyblack.cryptosignals.dto.research.DatasetQualityReport;
import com.shyblack.cryptosignals.entity.research.NfmValidationRun;
import com.shyblack.cryptosignals.service.backtest.research.NfmValidationResultPersistenceService;
import com.shyblack.cryptosignals.service.backtest.research.ResearchWindowResult;
import com.shyblack.cryptosignals.service.research.ResearchDataImportService;
import com.shyblack.cryptosignals.service.research.ResearchDataValidationService;
import com.shyblack.cryptosignals.service.research.ResearchImportKind;
import com.shyblack.cryptosignals.service.research.ResearchImportSummary;
import com.shyblack.cryptosignals.exception.ResourceNotFoundException;
import com.shyblack.cryptosignals.service.research.events.EventCollectionJobService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only research-data ingestion + validation. Import reads LOCAL files
 * under the configured research import root; no live exchange API is used.
 */
@RestController
@RequestMapping("/api/v1/admin/research")
@RequiredArgsConstructor
@Tag(name = "Research Data Admin", description = "Historical research dataset import + validation")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
public class ResearchDataAdminController {

	private final ResearchDataImportService importService;
	private final ResearchDataValidationService validationService;
	private final EventCollectionJobService eventCollectionJobService;
	private final NfmValidationResultPersistenceService validationResultService;

	@Operation(summary = "Start a bounded official-event collection job (ADMIN, async)")
	@PostMapping("/events/collect")
	public EventCollectionJobService.JobView collectEvents(
			@RequestParam(defaultValue = "NFM_EVENTS_2023_09_2026_09_V1") String datasetVersion,
			@RequestParam(defaultValue = "2023-09-01T00:00:00Z") String from,
			@RequestParam(defaultValue = "2026-10-01T00:00:00Z") String to) {
		return eventCollectionJobService.start(datasetVersion, Instant.parse(from), Instant.parse(to));
	}

	@Operation(summary = "Collection job status (ADMIN)")
	@GetMapping("/events/collection/{id}")
	public EventCollectionJobService.JobView collectionStatus(@PathVariable UUID id) {
		EventCollectionJobService.JobView view = eventCollectionJobService.status(id);
		if (view == null) {
			throw new ResourceNotFoundException("Collection job not found: " + id);
		}
		return view;
	}

	public record ImportRequest(
			@NotNull ResearchImportKind kind,
			@NotBlank String datasetVersion,
			String sourceDataset,
			@NotBlank String symbol,
			String timeframe,
			@NotBlank String file) {
	}

	@Operation(summary = "Import a local research data file (bulk, idempotent)")
	@PostMapping("/import")
	public ResearchImportSummary importFile(@RequestBody ImportRequest request) {
		return importService.importFile(request.kind(), request.datasetVersion(), request.sourceDataset(),
				request.symbol(), request.timeframe(), request.file());
	}

	@Operation(summary = "Run data-quality checks for a dataset version")
	@PostMapping("/validate")
	public DatasetQualityReport validate(@RequestParam @NotBlank String datasetVersion) {
		return validationService.validate(datasetVersion);
	}

	@Operation(summary = "Whether an import is currently running")
	@GetMapping("/status")
	public StatusResponse status() {
		return new StatusResponse(importService.isRunning());
	}

	@Operation(summary = "Get a persisted NFM validation run by id (read-only)")
	@GetMapping("/nmf-validation/{runId}")
	public NfmValidationRun nfmValidation(@PathVariable UUID runId) {
		return validationResultService.findByRunId(runId)
				.orElseThrow(() -> new ResourceNotFoundException("Validation run not found: " + runId));
	}

	@Operation(summary = "List persisted NFM validation runs (descriptive; no ranking)")
	@GetMapping("/nmf-validation")
	public List<NfmValidationRun> nfmValidationRuns() {
		return validationResultService.listRuns();
	}

	@Operation(summary = "List per-window results of a walk-forward run (individually auditable)")
	@GetMapping("/nmf-validation/{runId}/windows")
	public List<ResearchWindowResult> nfmValidationWindows(@PathVariable UUID runId) {
		return validationResultService.windows(runId);
	}

	@Operation(summary = "List per-variant results of a sensitivity run (descriptive; not ranked)")
	@GetMapping("/nmf-validation/{runId}/variants")
	public List<ResearchWindowResult> nfmValidationVariants(@PathVariable UUID runId) {
		return validationResultService.variants(runId);
	}

	public record StatusResponse(boolean running) {
	}
}
