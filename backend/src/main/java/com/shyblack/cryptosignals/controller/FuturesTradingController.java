package com.shyblack.cryptosignals.controller;

import com.shyblack.cryptosignals.dto.futures.FuturesAccountResponse;
import com.shyblack.cryptosignals.dto.futures.FuturesActivateRequest;
import com.shyblack.cryptosignals.dto.futures.FuturesConnectRequest;
import com.shyblack.cryptosignals.dto.futures.FuturesOrderResponse;
import com.shyblack.cryptosignals.dto.futures.FuturesPositionResponse;
import com.shyblack.cryptosignals.entity.FuturesOrder;
import com.shyblack.cryptosignals.entity.FuturesPosition;
import com.shyblack.cryptosignals.entity.FuturesTradingAccount;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.exception.BadRequestException;
import com.shyblack.cryptosignals.exception.ResourceNotFoundException;
import com.shyblack.cryptosignals.repository.UserRepository;
import com.shyblack.cryptosignals.security.UserPrincipal;
import com.shyblack.cryptosignals.service.futures.FuturesAccountService;
import com.shyblack.cryptosignals.service.futures.FuturesCloseService;
import com.shyblack.cryptosignals.service.futures.FuturesExecutionService;
import com.shyblack.cryptosignals.service.futures.FuturesQueryService;
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

@RestController
@RequestMapping("/api/v1/futures-trading")
@RequiredArgsConstructor
@Tag(name = "Futures Trading", description = "Binance USDT-M FUTURES — real leveraged orders. Off by default.")
@SecurityRequirement(name = "bearer-jwt")
public class FuturesTradingController {

	private final FuturesAccountService accountService;
	private final FuturesQueryService queryService;
	private final FuturesExecutionService executionService;
	private final FuturesCloseService closeService;
	private final UserRepository userRepository;

	@Operation(summary = "Get the futures account (404 if never connected)")
	@GetMapping("/account")
	public FuturesAccountResponse account() {
		return toDto(queryService.findAccount(currentUser())
				.orElseThrow(() -> new ResourceNotFoundException("Futures account not connected")));
	}

	@Operation(summary = "Connect / validate credentials against Binance Futures")
	@PostMapping("/connection")
	public FuturesAccountResponse connect(@Valid @RequestBody FuturesConnectRequest request) {
		return toDto(accountService.connect(currentUser(), request.exchange()));
	}

	@Operation(summary = "Re-validate + refresh balances")
	@PostMapping("/connection/validate")
	public FuturesAccountResponse validate() {
		return toDto(accountService.refreshBalance(currentUser()));
	}

	@Operation(summary = "Disconnect the futures account")
	@DeleteMapping("/connection")
	public FuturesAccountResponse disconnect() {
		return toDto(accountService.deactivate(currentUser()));
	}

	@Operation(summary = "Explicit safety acknowledgement — required before activate")
	@PostMapping("/acknowledge")
	public FuturesAccountResponse acknowledge(@Valid @RequestBody FuturesActivateRequest request) {
		return toDto(accountService.acknowledge(currentUser(), request.acknowledged()));
	}

	@Operation(summary = "Enable live Futures trading. Requires prior acknowledgement.")
	@PostMapping("/activate")
	public FuturesAccountResponse activate(@Valid @RequestBody FuturesActivateRequest request) {
		if (!request.acknowledged()) throw new BadRequestException("Safety acknowledgement is required");
		// Store the ack alongside activation so it's captured server-side.
		accountService.acknowledge(currentUser(), true);
		return toDto(accountService.activate(currentUser()));
	}

	@Operation(summary = "Disable live futures trading")
	@PostMapping("/deactivate")
	public FuturesAccountResponse deactivate() {
		return toDto(accountService.deactivate(currentUser()));
	}

	@Operation(summary = "Trigger the futures kill switch")
	@PostMapping("/kill-switch")
	public FuturesAccountResponse killSwitch() {
		return toDto(accountService.triggerKillSwitch(currentUser(), "user"));
	}

	@Operation(summary = "Release the futures kill switch")
	@DeleteMapping("/kill-switch")
	public FuturesAccountResponse releaseKillSwitch() {
		return toDto(accountService.releaseKillSwitch(currentUser()));
	}

	@Operation(summary = "List open (non-terminal) futures orders")
	@GetMapping("/orders")
	public List<FuturesOrderResponse> openOrders() {
		return queryService.openOrders(currentUser()).stream().map(FuturesTradingController::toDto).toList();
	}

	@Operation(summary = "Get a single order (must be owned by the caller)")
	@GetMapping("/orders/{id}")
	public FuturesOrderResponse getOrder(@PathVariable UUID id) {
		return toDto(queryService.findOwnedOrder(currentUser(), id)
				.orElseThrow(() -> new ResourceNotFoundException("Futures order not found: " + id)));
	}

	@Operation(summary = "Cancel a pending futures order (must be owned by the caller)")
	@PostMapping("/orders/{id}/cancel")
	public FuturesOrderResponse cancelOrder(@PathVariable UUID id) {
		FuturesOrder owned = queryService.findOwnedOrder(currentUser(), id)
				.orElseThrow(() -> new ResourceNotFoundException("Futures order not found: " + id));
		return toDto(executionService.cancel(owned.getId(), "manual"));
	}

	@Operation(summary = "Order history (terminal only)")
	@GetMapping("/history")
	public List<FuturesOrderResponse> history() {
		return queryService.history(currentUser()).stream().map(FuturesTradingController::toDto).toList();
	}

	@Operation(summary = "List open futures positions")
	@GetMapping("/positions")
	public List<FuturesPositionResponse> openPositions() {
		return queryService.openPositions(currentUser()).stream().map(FuturesTradingController::toDto).toList();
	}

	@Operation(summary = "List closed futures positions")
	@GetMapping("/positions/history")
	public List<FuturesPositionResponse> positionsHistory() {
		return queryService.closedPositions(currentUser()).stream().map(FuturesTradingController::toDto).toList();
	}

	@Operation(summary = "Manually close an open futures position via a REDUCE_ONLY market order")
	@PostMapping("/positions/{id}/close")
	public FuturesOrderResponse closePosition(@PathVariable UUID id) {
		return toDto(closeService.closePosition(currentUser(), id));
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

	private static FuturesAccountResponse toDto(FuturesTradingAccount a) {
		return new FuturesAccountResponse(
				a.getId(), a.getExchange(), a.getConnectionStatus(),
				a.isEnabled(), a.isKillSwitchActive(), a.isAcknowledged(),
				a.getMarginAsset(), a.getMarginMode(), a.getPositionMode(),
				a.getMaxLeverage(), a.getMaxNotionalPerTrade(), a.getMaxActivePositions(),
				a.getDailyLossLimitPct(),
				a.getWalletBalance(), a.getAvailableBalance(), a.getMarginBalance(),
				a.getUsedMargin(), a.getMaintenanceMargin(), a.getUnrealizedPnl(),
				a.getRealizedPnlToday(), a.getTotalFundingPaid(),
				a.getLastValidatedAt(), a.getLastValidationMessage());
	}

	private static FuturesOrderResponse toDto(FuturesOrder o) {
		return new FuturesOrderResponse(
				o.getId(), o.getSignalId(), o.getParentOrderId(),
				o.getClientOrderId(), o.getExchangeOrderId(),
				o.getSymbol(), o.getSide(), o.getPositionSide(), o.getType(), o.getPurpose(),
				o.getStatus(), o.isReduceOnly(), o.getLeverage(),
				o.getRequestedQuantity(), o.getExecutedQuantity(),
				o.getAvgFillPrice(), o.getCumulativeQuoteQty(), o.getFees(), o.getFeeAsset(),
				o.getRejectReason(), o.getSubmittedAt(), o.getLastFillAt(), o.getCompletedAt(),
				o.getCreatedAt());
	}

	private static FuturesPositionResponse toDto(FuturesPosition p) {
		return new FuturesPositionResponse(
				p.getId(), p.getSignalId(), p.getEntryOrderId(), p.getStopOrderId(),
				p.getSymbol(), p.getPositionSide(), p.getMarginMode(), p.getLeverage(),
				p.getQuantity(), p.getEntryPrice(), p.getExitPrice(),
				p.getStopLoss(), p.getTakeProfit(), p.getInitialMargin(), p.getLiquidationPrice(),
				p.getRealizedPnl(), p.getUnrealizedPnl(),
				p.getTradingFees(), p.getFundingFees(),
				p.getStatus(), p.getProtectionStatus(),
				p.getOpenedAt(), p.getClosedAt(), p.getCreatedAt());
	}
}
