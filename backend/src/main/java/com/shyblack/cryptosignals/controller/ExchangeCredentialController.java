package com.shyblack.cryptosignals.controller;

import com.shyblack.cryptosignals.dto.settings.ExchangeCredentialConnectionResponse;
import com.shyblack.cryptosignals.dto.settings.ExchangeCredentialRequest;
import com.shyblack.cryptosignals.dto.settings.ExchangeCredentialView;
import com.shyblack.cryptosignals.exception.BadRequestException;
import com.shyblack.cryptosignals.security.UserPrincipal;
import com.shyblack.cryptosignals.service.ExchangeCredentialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings/exchanges")
@RequiredArgsConstructor
@Tag(name = "Settings", description = "Exchange API credentials and connectivity")
@SecurityRequirement(name = "bearer-jwt")
public class ExchangeCredentialController {

	private final ExchangeCredentialService exchangeCredentialService;

	@Operation(summary = "List the authenticated user's exchange credentials (masked)")
	@GetMapping
	public List<ExchangeCredentialView> list() {
		return exchangeCredentialService.list(currentPrincipal());
	}

	@Operation(summary = "Connect / save an exchange API credential (encrypted at rest)")
	@PostMapping
	public ExchangeCredentialView create(@Valid @RequestBody ExchangeCredentialRequest request) {
		return exchangeCredentialService.create(currentPrincipal(), request);
	}

	@Operation(summary = "Test an exchange connection (simulated, no real network call)")
	@PostMapping("/{id}/test-connection")
	public ExchangeCredentialConnectionResponse testConnection(@PathVariable UUID id) {
		return exchangeCredentialService.testConnection(currentPrincipal(), id);
	}

	@Operation(summary = "Delete an exchange credential")
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id) {
		exchangeCredentialService.delete(currentPrincipal(), id);
	}

	private static UserPrincipal currentPrincipal() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
			throw new BadRequestException("Authentication required");
		}
		return principal;
	}
}