package com.shyblack.cryptosignals.controller;

import com.shyblack.cryptosignals.dto.live.LiveAccountResponse;
import com.shyblack.cryptosignals.dto.live.LiveActivateRequest;
import com.shyblack.cryptosignals.dto.live.LiveConnectRequest;
import com.shyblack.cryptosignals.dto.live.LiveOrderResponse;
import com.shyblack.cryptosignals.dto.live.LivePerformanceResponse;
import com.shyblack.cryptosignals.entity.LiveOrder;
import com.shyblack.cryptosignals.entity.LiveTradingAccount;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.exception.BadRequestException;
import com.shyblack.cryptosignals.exception.ResourceNotFoundException;
import com.shyblack.cryptosignals.repository.UserRepository;
import com.shyblack.cryptosignals.security.UserPrincipal;
import com.shyblack.cryptosignals.service.live.LiveTradingAccountService;
import com.shyblack.cryptosignals.service.live.LiveTradingExecutionService;
import com.shyblack.cryptosignals.service.live.LiveTradingQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * All endpoints resolve the authenticated user from SecurityContext — IDs
 * from the client are never trusted for authority. Cross-user access
 * attempts get a 404.
 */
@RestController
@RequestMapping("/api/v1/live-trading")
@RequiredArgsConstructor
@Tag(name = "Live Trading", description = "Real exchange trading — signed orders. Off by default.")
@SecurityRequirement(name = "bearer-jwt")
public class LiveTradingController {

	private final LiveTradingAccountService accountService;
	private final LiveTradingQueryService queryService;
	private final LiveTradingExecutionService executionService;
	private final UserRepository userRepository;

	@Operation(summary = "Get the live trading account (or 404 if never connected)")
	@GetMapping("/account")
	public LiveAccountResponse account() {
		LiveTradingAccount account = queryService.findAccount(currentUser())
				.orElseThrow(() -> new ResourceNotFoundException("Live account not connected"));
		return toDto(account);
	}

	@Operation(summary = "Connect (or re-validate) a live exchange account using a stored credential")
	@PostMapping("/connection")
	public LiveAccountResponse connect(@Valid @RequestBody LiveConnectRequest request) {
		LiveTradingAccount account = accountService.connect(currentUser(), request.exchange());
		return toDto(account);
	}

	@Operation(summary = "Validate credentials against the exchange")
	@PostMapping("/connection/validate")
	public LiveAccountResponse validate() {
		LiveTradingAccount account = accountService.refreshBalance(currentUser());
		return toDto(account);
	}

	@Operation(summary = "Disconnect (clears cached balance, disables trading)")
	@DeleteMapping("/connection")
	public LiveAccountResponse disconnect() {
		return toDto(accountService.disconnect(currentUser()));
	}

	@Operation(summary = "Enable live trading. Requires the safety acknowledgement flag.")
	@PostMapping("/activate")
	public LiveAccountResponse activate(@Valid @RequestBody LiveActivateRequest request) {
		if (!request.acknowledged()) {
			throw new BadRequestException("Safety acknowledgement is required");
		}
		return toDto(accountService.activate(currentUser()));
	}

	@Operation(summary = "Disable live trading (existing protective orders remain, no new entries)")
	@PostMapping("/deactivate")
	public LiveAccountResponse deactivate() {
		return toDto(accountService.deactivate(currentUser()));
	}

	@Operation(summary = "Trigger the kill switch — blocks new entries immediately")
	@PostMapping("/kill-switch")
	public LiveAccountResponse killSwitch() {
		return toDto(accountService.triggerKillSwitch(currentUser(), "user"));
	}

	@Operation(summary = "Release the kill switch")
	@DeleteMapping("/kill-switch")
	public LiveAccountResponse releaseKillSwitch() {
		return toDto(accountService.releaseKillSwitch(currentUser()));
	}

	@Operation(summary = "List open (non-terminal) live orders")
	@GetMapping("/orders")
	public List<LiveOrderResponse> openOrders() {
		return queryService.openOrders(currentUser()).stream().map(LiveTradingController::toDto).toList();
	}

	@Operation(summary = "Get one live order (must be owned by the caller)")
	@GetMapping("/orders/{id}")
	public LiveOrderResponse getOrder(@PathVariable UUID id) {
		return toDto(queryService.findOwnedOrder(currentUser(), id)
				.orElseThrow(() -> new ResourceNotFoundException("Live order not found: " + id)));
	}

	@Operation(summary = "Cancel a live order (must be owned by the caller and not terminal)")
	@PostMapping("/orders/{id}/cancel")
	public LiveOrderResponse cancelOrder(@PathVariable UUID id) {
		LiveOrder owned = queryService.findOwnedOrder(currentUser(), id)
				.orElseThrow(() -> new ResourceNotFoundException("Live order not found: " + id));
		return toDto(executionService.cancel(owned.getId(), "manual"));
	}

	@Operation(summary = "Order history (terminal states only)")
	@GetMapping("/history")
	public List<LiveOrderResponse> history() {
		return queryService.history(currentUser()).stream().map(LiveTradingController::toDto).toList();
	}

	@Operation(summary = "Aggregate live-trading performance")
	@GetMapping("/performance")
	public LivePerformanceResponse performance() {
		LiveTradingQueryService.Aggregate a = queryService.performance(currentUser());
		return new LivePerformanceResponse(
				a.totalOrders(), a.filledEntries(), a.rejections(), a.totalFees(), a.totalNotional());
	}

	// ------------------------------------------------------------------

	private User currentUser() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
			throw new BadRequestException("Authentication required");
		}
		return userRepository.findById(principal.getId())
				.orElseThrow(() -> new ResourceNotFoundException("User not found"));
	}

	private static LiveAccountResponse toDto(LiveTradingAccount a) {
		return new LiveAccountResponse(
				a.getId(), a.getExchange(), a.getConnectionStatus(),
				a.isEnabled(), a.isKillSwitchActive(), a.getQuoteCurrency(),
				a.getCachedAvailableBalance(), a.getCachedTotalBalance(),
				a.getMaxNotionalPerTrade(), a.getMaxActivePositions(),
				a.getDailyLossLimitPct(), a.getLastValidatedAt(),
				a.getLastValidationMessage());
	}

	private static LiveOrderResponse toDto(LiveOrder o) {
		return new LiveOrderResponse(
				o.getId(), o.getSignalId(), o.getParentOrderId(),
				o.getClientOrderId(), o.getExchangeOrderId(),
				o.getSymbol(), o.getSide(), o.getType(), o.getPurpose(),
				o.getStatus(),
				o.getRequestedQuantity(), o.getExecutedQuantity(),
				o.remainingQuantity(),
				o.getPrice(), o.getStopPrice(), o.getAvgFillPrice(),
				o.getCumulativeQuoteQty(), o.getFees(), o.getFeeAsset(),
				o.getRejectReason(),
				o.getSubmittedAt(), o.getLastFillAt(), o.getCompletedAt(),
				o.getCreatedAt());
	}
}
