package com.shyblack.cryptosignals.controller;

import com.shyblack.cryptosignals.dto.nfm.MacroEventRequest;
import com.shyblack.cryptosignals.entity.NewsEvent;
import com.shyblack.cryptosignals.service.nfm.MacroEventIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin ingestion for scheduled macro / central-bank events (spec §4, §8).
 * Read access to the resulting signals is via the existing signals API.
 */
@RestController
@RequestMapping("/api/v1/admin/nfm")
@RequiredArgsConstructor
@Tag(name = "NFM Admin", description = "News Flow Momentum event ingestion")
@SecurityRequirement(name = "bearer-jwt")
public class NfmEventAdminController {

	private final MacroEventIngestionService ingestionService;

	public record MacroEventResponse(UUID id, String eventType, boolean tradeable, Instant eventTime) {}

	@Operation(summary = "Record a scheduled macro event with expected/actual values (ADMIN)")
	@PostMapping("/events")
	@PreAuthorize("hasRole('ADMIN')")
	public MacroEventResponse record(@RequestBody MacroEventRequest request) {
		NewsEvent event = ingestionService.record(request);
		return new MacroEventResponse(event.getId(),
				event.getEventType() == null ? null : event.getEventType().name(),
				event.isTradeable(), event.getEventTime());
	}
}
