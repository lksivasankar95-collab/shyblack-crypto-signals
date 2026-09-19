package com.shyblack.cryptosignals.controller;

import com.shyblack.cryptosignals.dto.settings.SettingsMetaResponse;
import com.shyblack.cryptosignals.dto.settings.SettingsResponse;
import com.shyblack.cryptosignals.dto.settings.SettingsUpdateRequest;
import com.shyblack.cryptosignals.exception.BadRequestException;
import com.shyblack.cryptosignals.security.UserPrincipal;
import com.shyblack.cryptosignals.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
@Tag(name = "Settings", description = "User settings, preferences and limits")
@SecurityRequirement(name = "bearer-jwt")
public class SettingsController {

	private final SettingsService settingsService;

	@Operation(summary = "Get the authenticated user's settings")
	@GetMapping
	public SettingsResponse get() {
		return settingsService.get(currentPrincipal());
	}

	@Operation(summary = "Partially update settings (only non-null fields are applied)")
	@PatchMapping
	public SettingsResponse update(@Valid @RequestBody SettingsUpdateRequest request) {
		return settingsService.update(currentPrincipal(), request);
	}

	@Operation(summary = "List selectable options and configured trading ceilings")
	@GetMapping("/meta")
	public SettingsMetaResponse meta() {
		return settingsService.meta();
	}

	/**
	 * Resolves the authenticated principal from the security context; the id is
	 * never taken from the client, so all downstream lookups are IDOR-safe.
	 */
	private static UserPrincipal currentPrincipal() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
			throw new BadRequestException("Authentication required");
		}
		return principal;
	}
}