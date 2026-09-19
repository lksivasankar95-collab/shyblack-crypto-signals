package com.shyblack.cryptosignals.service.futures;

import com.shyblack.cryptosignals.entity.FuturesOrder;
import com.shyblack.cryptosignals.entity.FuturesPosition;
import com.shyblack.cryptosignals.entity.FuturesTradingAccount;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderPurpose;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderStatus;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderType;
import com.shyblack.cryptosignals.entity.enums.FuturesPositionStatus;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.exception.BadRequestException;
import com.shyblack.cryptosignals.exception.ResourceNotFoundException;
import com.shyblack.cryptosignals.exchange.SymbolRules;
import com.shyblack.cryptosignals.exchange.futures.FuturesExchangeAdapter;
import com.shyblack.cryptosignals.repository.FuturesOrderRepository;
import com.shyblack.cryptosignals.repository.FuturesPositionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manual close for a FUTURES position via a REDUCE_ONLY market order.
 *
 *   LONG  close → SELL REDUCE_ONLY
 *   SHORT close → BUY  REDUCE_ONLY
 *
 * Also cancels the protective STOP_MARKET so no dangling order remains.
 * Deterministic clientOrderId makes the close idempotent.
 */
@Service
@RequiredArgsConstructor
public class FuturesCloseService {

	private static final Logger log = LoggerFactory.getLogger(FuturesCloseService.class);

	private final FuturesPositionRepository positionRepository;
	private final FuturesOrderRepository orderRepository;
	private final FuturesExecutionService executionService;
	private final FuturesExchangeAdapter adapter;
	private final FuturesClientOrderIdGenerator idGen;

	public FuturesOrder closePosition(User user, UUID positionId) {
		FuturesPosition position = positionRepository.findById(positionId)
				.orElseThrow(() -> new ResourceNotFoundException("Position not found: " + positionId));
		if (!position.getAccount().getUser().getId().equals(user.getId())) {
			throw new ResourceNotFoundException("Position not found: " + positionId); // IDOR-safe
		}
		if (position.getStatus() != FuturesPositionStatus.OPEN) {
			throw new BadRequestException("Position is not open (status=" + position.getStatus() + ")");
		}
		BigDecimal qty = position.getQuantity();
		if (qty == null || qty.signum() <= 0) {
			throw new BadRequestException("Position has zero quantity");
		}

		FuturesTradingAccount account = position.getAccount();

		// Cancel protective SL first — no dangling order.
		if (position.getStopOrderId() != null) {
			try { executionService.cancel(position.getStopOrderId(), "manual close position=" + positionId); }
			catch (Exception ex) {
				log.warn("[FutClose] SL cancel failed position={} err={}", positionId, ex.getMessage());
			}
		}

		SymbolRules rules = adapter.getSymbolRules(position.getSymbol());
		if (rules == null) throw new BadRequestException("No exchange rules for " + position.getSymbol());
		BigDecimal normalizedQty = rules.normalizeQuantity(qty);
		if (normalizedQty.signum() <= 0 || !rules.meetsMinQty(normalizedQty)) {
			throw new BadRequestException("Position qty falls below exchange minimum after rounding");
		}

		// REDUCE_ONLY market — LONG close is SELL, SHORT close is BUY.
		PositionSide exchangeSide = position.getPositionSide() == PositionSide.LONG
				? PositionSide.SHORT : PositionSide.LONG;

		String clientOrderId = idGen.forManualClose(user.getId(), position.getId(), position.getPositionSide());
		FuturesOrder intent = executionService.createIntent(
				account, position.getSignalId(), position.getEntryOrderId(), clientOrderId,
				position.getSymbol(), exchangeSide, position.getPositionSide(),
				FuturesOrderType.MARKET, FuturesOrderPurpose.MANUAL_CLOSE,
				true, position.getLeverage(), normalizedQty, null, null);
		FuturesOrder submitted = executionService.submit(intent);

		if (submitted.getStatus() == FuturesOrderStatus.FILLED
				|| submitted.getStatus() == FuturesOrderStatus.PARTIALLY_FILLED) {
			markPositionClosed(position.getId(), submitted);
		}
		return submitted;
	}

	@Transactional
	protected void markPositionClosed(UUID positionId, FuturesOrder closeOrder) {
		positionRepository.findByIdForUpdate(positionId).ifPresent(p -> {
			BigDecimal exit = closeOrder.getAvgFillPrice();
			BigDecimal grossPnl = grossPnl(p.getPositionSide(), p.getEntryPrice(), exit, closeOrder.getExecutedQuantity());
			BigDecimal fees = p.getTradingFees().add(closeOrder.getFees() == null ? BigDecimal.ZERO : closeOrder.getFees());
			BigDecimal net = grossPnl.subtract(fees);
			p.setExitPrice(exit);
			p.setRealizedPnl(net);
			p.setTradingFees(fees);
			p.setStatus(FuturesPositionStatus.CLOSED);
			p.setClosedAt(Instant.now());
			positionRepository.save(p);
		});
	}

	private static BigDecimal grossPnl(PositionSide side, BigDecimal entry, BigDecimal exit, BigDecimal qty) {
		if (entry == null || exit == null || qty == null) return BigDecimal.ZERO;
		BigDecimal diff = side == PositionSide.LONG
				? exit.subtract(entry)
				: entry.subtract(exit);
		return diff.multiply(qty);
	}
}
