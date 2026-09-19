package com.shyblack.cryptosignals.controller;

import com.shyblack.cryptosignals.dto.paper.PaperAccountResponse;
import com.shyblack.cryptosignals.dto.paper.PaperPerformanceResponse;
import com.shyblack.cryptosignals.dto.paper.PaperPositionResponse;
import com.shyblack.cryptosignals.entity.Portfolio;
import com.shyblack.cryptosignals.entity.Position;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.CloseReason;
import com.shyblack.cryptosignals.entity.enums.PositionStatus;
import com.shyblack.cryptosignals.exception.BadRequestException;
import com.shyblack.cryptosignals.exception.ResourceNotFoundException;
import com.shyblack.cryptosignals.repository.UserRepository;
import com.shyblack.cryptosignals.security.UserPrincipal;
import com.shyblack.cryptosignals.service.paper.PaperTradingAccountService;
import com.shyblack.cryptosignals.service.paper.PaperTradingExecutionService;
import com.shyblack.cryptosignals.service.paper.PaperTradingQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * All endpoints resolve the authenticated user from the security context;
 * the id is NEVER read from the client, so all lookups are IDOR-safe.
 */
@RestController
@RequestMapping("/api/v1/paper-trading")
@RequiredArgsConstructor
@Tag(name = "Paper Trading", description = "Simulated trading — signal-driven, no real orders")
@SecurityRequirement(name = "bearer-jwt")
public class PaperTradingController {

	private final PaperTradingAccountService accountService;
	private final PaperTradingQueryService queryService;
	private final PaperTradingExecutionService executionService;
	private final UserRepository userRepository;

	@Operation(summary = "Get the authenticated user's paper trading account")
	@GetMapping("/account")
	public PaperAccountResponse getAccount() {
		User user = currentUser();
		PaperTradingQueryService.AccountView view = queryService.loadAccount(user);
		return toAccountDto(view);
	}

	@Operation(summary = "List open paper positions")
	@GetMapping("/positions")
	public List<PaperPositionResponse> listOpenPositions() {
		return queryService.listOpen(currentUser()).stream()
				.map(this::toPositionDto)
				.toList();
	}

	@Operation(summary = "Get a single paper position (must be owned by the caller)")
	@GetMapping("/positions/{id}")
	public PaperPositionResponse getPosition(@PathVariable UUID id) {
		return toPositionDto(fetchOwned(id));
	}

	@Operation(summary = "List closed positions (trade history)")
	@GetMapping("/history")
	public List<PaperPositionResponse> history() {
		return queryService.listHistory(currentUser()).stream()
				.map(this::toPositionDto)
				.toList();
	}

	@Operation(summary = "Aggregate performance across all closed paper trades")
	@GetMapping("/performance")
	public PaperPerformanceResponse performance() {
		User user = currentUser();
		Portfolio portfolio = accountService.getOrCreate(user);
		List<Position> closed = queryService.listHistory(user);

		BigDecimal totalPnl = BigDecimal.ZERO;
		BigDecimal winSum = BigDecimal.ZERO;
		BigDecimal lossSum = BigDecimal.ZERO;
		BigDecimal best = null;
		BigDecimal worst = null;
		int winCount = 0;
		int lossCount = 0;
		for (Position p : closed) {
			BigDecimal net = safe(p.getRealizedPnl());
			totalPnl = totalPnl.add(net);
			if (net.signum() > 0) { winSum = winSum.add(net); winCount++; }
			else if (net.signum() < 0) { lossSum = lossSum.add(net.abs()); lossCount++; }
			if (best == null || net.compareTo(best) > 0) best = net;
			if (worst == null || net.compareTo(worst) < 0) worst = net;
		}
		BigDecimal avgWin = winCount == 0 ? BigDecimal.ZERO
				: winSum.divide(BigDecimal.valueOf(winCount), 8, RoundingMode.HALF_UP);
		BigDecimal avgLoss = lossCount == 0 ? BigDecimal.ZERO
				: lossSum.divide(BigDecimal.valueOf(lossCount), 8, RoundingMode.HALF_UP);
		BigDecimal profitFactor = lossSum.signum() == 0
				? (winSum.signum() == 0 ? BigDecimal.ZERO : new BigDecimal("999.99"))
				: winSum.divide(lossSum, 4, RoundingMode.HALF_UP);
		BigDecimal winRate = closed.isEmpty() ? BigDecimal.ZERO
				: BigDecimal.valueOf(winCount).multiply(BigDecimal.valueOf(100))
						.divide(BigDecimal.valueOf(closed.size()), 2, RoundingMode.HALF_UP);
		BigDecimal returnPct = portfolio.getInitialBalance().signum() == 0
				? BigDecimal.ZERO
				: totalPnl.multiply(BigDecimal.valueOf(100))
						.divide(portfolio.getInitialBalance(), 4, RoundingMode.HALF_UP);

		return new PaperPerformanceResponse(
				closed.size(), winCount, lossCount, winRate,
				totalPnl, avgWin, avgLoss, profitFactor,
				best == null ? BigDecimal.ZERO : best,
				worst == null ? BigDecimal.ZERO : worst,
				portfolio.getTotalFees(), returnPct);
	}

	@Operation(summary = "Manually close a paper position at the current market price")
	@PostMapping("/positions/{id}/close")
	public PaperPositionResponse closePosition(@PathVariable UUID id) {
		Position owned = fetchOwned(id);
		if (owned.getStatus() != PositionStatus.OPEN) {
			throw new BadRequestException("Position is not open");
		}
		BigDecimal ref = queryService.currentPrice(owned);
		if (ref == null || ref.signum() <= 0) {
			throw new BadRequestException("No live price available for " + owned.getSymbol());
		}
		Position closed = executionService.close(owned.getId(), ref, CloseReason.MANUAL)
				.orElseThrow(() -> new BadRequestException("Failed to close position"));
		return toPositionDto(closed);
	}

	@Operation(summary = "Reset the paper account — closes all open positions and restores initial balance")
	@DeleteMapping("/account")
	public PaperAccountResponse reset() {
		User user = currentUser();
		Portfolio portfolio = accountService.getOrCreate(user);
		// Close all open positions at their current market price with reason=RESET.
		for (Position open : queryService.listOpen(user)) {
			BigDecimal ref = queryService.currentPrice(open);
			if (ref == null || ref.signum() <= 0) ref = open.getEntryPrice();
			executionService.close(open.getId(), ref, CloseReason.RESET);
		}
		accountService.reset(portfolio);
		return toAccountDto(queryService.loadAccount(user));
	}

	private Position fetchOwned(UUID id) {
		User user = currentUser();
		return queryService.listAll(user).stream()
				.filter(p -> p.getId().equals(id))
				.min(Comparator.comparing(Position::getCreatedAt))
				.orElseThrow(() -> new ResourceNotFoundException("Position not found: " + id));
	}

	private PaperAccountResponse toAccountDto(PaperTradingQueryService.AccountView view) {
		Portfolio p = view.portfolio();
		return new PaperAccountResponse(
				p.getId(),
				p.getQuoteCurrency(),
				p.getInitialBalance(),
				p.getAvailableBalance(),
				p.getInvested(),
				p.getTotalBalance(),
				view.equity(),
				p.getRealizedPnl(),
				view.unrealizedPnl(),
				p.getTotalFees(),
				p.getTotalTrades(),
				p.getWinningTrades(),
				p.getLosingTrades(),
				view.winRate(),
				p.getCreatedAt());
	}

	private PaperPositionResponse toPositionDto(Position p) {
		BigDecimal current = p.getStatus() == PositionStatus.OPEN
				? queryService.currentPrice(p)
				: p.getExitPrice();
		BigDecimal unrealized = p.getStatus() == PositionStatus.OPEN
				? queryService.unrealized(p)
				: BigDecimal.ZERO;
		BigDecimal unrealizedPct = p.getStatus() == PositionStatus.OPEN
				? queryService.unrealizedPct(p)
				: BigDecimal.ZERO;
		return new PaperPositionResponse(
				p.getId(),
				p.getSignalId(),
				p.getSymbol(),
				p.getSide(),
				p.getSize(),
				p.getEntryPrice(),
				current,
				p.getExitPrice(),
				p.getStopLoss(),
				p.getTakeProfit1(),
				p.getTakeProfit2(),
				p.getTakeProfit3(),
				p.getNotional(),
				p.getEntryFee(),
				p.getExitFee(),
				p.getRealizedPnl(),
				unrealized,
				unrealizedPct,
				p.getStatus(),
				p.getCloseReason(),
				p.getOpenedAt(),
				p.getClosedAt(),
				p.getCreatedAt());
	}

	private User currentUser() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
			throw new BadRequestException("Authentication required");
		}
		return userRepository.findById(principal.getId())
				.orElseThrow(() -> new ResourceNotFoundException("User not found"));
	}

	private static BigDecimal safe(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}
}
