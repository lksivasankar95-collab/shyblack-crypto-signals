package com.shyblack.cryptosignals.controller;

import com.shyblack.cryptosignals.dto.portfolio.PortfolioAccountView;
import com.shyblack.cryptosignals.dto.portfolio.PortfolioOverviewResponse;
import com.shyblack.cryptosignals.dto.portfolio.PortfolioPositionsResponse;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.exception.BadRequestException;
import com.shyblack.cryptosignals.exception.ResourceNotFoundException;
import com.shyblack.cryptosignals.repository.UserRepository;
import com.shyblack.cryptosignals.security.UserPrincipal;
import com.shyblack.cryptosignals.service.portfolio.PortfolioAccountReadService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Read-only unified Portfolio API.
 *
 * <p>Replaces the former {@code /api/v1/portfolios} and {@code /api/v1/positions} stubs, which
 * returned an empty list and threw on lookup. Both were dead: a repository-wide search found no
 * backend consumer other than their own controllers, and the Flutter portfolio/position
 * data sources that referenced them are DI-wired but never watched by a screen or a test.
 *
 * <p>Every response covers exactly one account mode, so PAPER and LIVE can never appear together.
 * All aggregation stays in {@link PortfolioAccountReadService}; this controller only resolves the
 * authenticated user and delegates.
 *
 * <p>No write endpoint exists here. Order placement, cancellation and capital mutation remain on
 * the dedicated paper-trading, live-trading and futures-trading APIs.
 */
@RestController
@RequestMapping("/api/v1/portfolio")
@Tag(name = "Portfolio", description = "Unified PAPER / LIVE portfolio read model")
@SecurityRequirement(name = "bearer-jwt")
@RequiredArgsConstructor
public class PortfolioController {

	/**
	 * Account mode used when the caller supplies none. Deterministic and echoed back in
	 * {@link PortfolioOverviewResponse#accountMode()}, so a client never has to guess. PAPER is the
	 * simulated account the application starts with.
	 */
	private static final AccountMode DEFAULT_MODE = AccountMode.PAPER;

	private final PortfolioAccountReadService readService;
	private final UserRepository userRepository;

	@Operation(summary = "Portfolio overview for one account mode",
			description = "Returns all four account categories (MAIN, SPOT, FUTURES, OPTIONS). "
					+ "Unsupported or unavailable categories are returned explicitly rather than omitted.")
	@GetMapping
	public PortfolioOverviewResponse overview(
			@Parameter(description = "PAPER or LIVE. Defaults to PAPER.")
			@RequestParam(name = "mode", required = false) AccountMode mode) {
		AccountMode effective = mode == null ? DEFAULT_MODE : mode;
		List<PortfolioAccountView> accounts = readService.getOverview(currentUser(), effective);
		return new PortfolioOverviewResponse(effective, List.of(AccountCategory.values()), accounts);
	}

	@Operation(summary = "A single account scope",
			description = "Returns one MAIN, SPOT, FUTURES or OPTIONS scope. Numeric fields are null "
					+ "whenever the underlying source cannot provide them.")
	@GetMapping("/{category}")
	public PortfolioAccountView account(
			@Parameter(description = "MAIN, SPOT, FUTURES or OPTIONS")
			@PathVariable AccountCategory category,
			@Parameter(description = "PAPER or LIVE. Defaults to PAPER.")
			@RequestParam(name = "mode", required = false) AccountMode mode) {
		return readService.getAccount(currentUser(), mode == null ? DEFAULT_MODE : mode, category);
	}

	@Operation(summary = "Positions for one account scope",
			description = "Scope-isolated: a SPOT request never returns futures positions and a "
					+ "futures request never returns spot balances. An empty list is accompanied by an "
					+ "availability that explains whether the scope is genuinely empty or unsupported.")
	@GetMapping("/{category}/positions")
	public PortfolioPositionsResponse positions(
			@Parameter(description = "MAIN, SPOT, FUTURES or OPTIONS")
			@PathVariable AccountCategory category,
			@Parameter(description = "PAPER or LIVE. Defaults to PAPER.")
			@RequestParam(name = "mode", required = false) AccountMode mode) {
		return readService.listPositions(currentUser(), mode == null ? DEFAULT_MODE : mode, category);
	}

	/**
	 * Resolves the caller from the security context. The user id is never taken from a request
	 * parameter, so one user can never address another user's portfolio.
	 */
	private User currentUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
			throw new BadRequestException("Authentication required");
		}
		return userRepository.findById(principal.getId())
				.orElseThrow(() -> new ResourceNotFoundException("User not found"));
	}
}