package com.shyblack.cryptosignals.service.live;

import com.shyblack.cryptosignals.entity.LiveOrder;
import com.shyblack.cryptosignals.entity.LiveTradingAccount;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.LiveOrderPurpose;
import com.shyblack.cryptosignals.entity.enums.LiveOrderStatus;
import com.shyblack.cryptosignals.entity.enums.LiveOrderType;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.ProtectionStatus;
import com.shyblack.cryptosignals.exception.BadRequestException;
import com.shyblack.cryptosignals.exception.ResourceNotFoundException;
import com.shyblack.cryptosignals.exchange.ClientOrderIdGenerator;
import com.shyblack.cryptosignals.exchange.ExchangeAdapterException;
import com.shyblack.cryptosignals.exchange.ExchangeTradingAdapter;
import com.shyblack.cryptosignals.exchange.PlaceOrderRequest;
import com.shyblack.cryptosignals.exchange.SymbolRules;
import com.shyblack.cryptosignals.repository.LiveOrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closes a filled SPOT entry by placing a real SELL for the executed
 * quantity. Also cancels any live protective STOP_LOSS for the same entry so
 * we don't leave a dangling order behind.
 *
 * Preserves the same idempotency contract as the entry pipeline: the
 * close-order clientOrderId is deterministic per entry, so double-tapping
 * "Close" cannot produce two SELL orders.
 */
@Service
@RequiredArgsConstructor
public class LiveTradingCloseService {

	private static final Logger log = LoggerFactory.getLogger(LiveTradingCloseService.class);

	private final LiveOrderRepository liveOrderRepository;
	private final LiveTradingExecutionService executionService;
	private final ExchangeTradingAdapter adapter;
	private final ClientOrderIdGenerator idGen;

	public LiveOrder closeEntry(User user, UUID entryOrderId) {
		LiveOrder entry = liveOrderRepository.findById(entryOrderId)
				.orElseThrow(() -> new ResourceNotFoundException("Live order not found: " + entryOrderId));

		if (!entry.getAccount().getUser().getId().equals(user.getId())) {
			throw new ResourceNotFoundException("Live order not found: " + entryOrderId);
		}
		if (entry.getPurpose() != LiveOrderPurpose.ENTRY) {
			throw new BadRequestException("Only ENTRY orders can be closed. Use cancel for pending orders.");
		}
		if (entry.getStatus() != LiveOrderStatus.FILLED
				&& entry.getStatus() != LiveOrderStatus.PARTIALLY_FILLED) {
			throw new BadRequestException("Entry has no executed quantity to close (status="
					+ entry.getStatus() + ")");
		}

		BigDecimal executed = entry.getExecutedQuantity();
		if (executed == null || executed.signum() <= 0) {
			throw new BadRequestException("Entry has no executed quantity to close");
		}

		LiveTradingAccount account = entry.getAccount();

		// Cancel any live sibling STOP_LOSS first so we don't leave a dangling order.
		cancelSiblingStopLoss(account, entry.getId());

		SymbolRules rules;
		try {
			rules = adapter.getSymbolRules(entry.getSymbol());
		} catch (ExchangeAdapterException ex) {
			log.warn("[LiveClose] rules lookup failed for {} err={}", entry.getSymbol(), ex.getMessage());
			throw new BadRequestException("Exchange rules unavailable for " + entry.getSymbol());
		}
		if (rules == null) {
			throw new BadRequestException("No exchange rules for " + entry.getSymbol());
		}

		BigDecimal qty = rules.normalizeQuantity(executed);
		if (qty.signum() <= 0 || !rules.meetsMinQty(qty)) {
			throw new BadRequestException("Executed qty falls below exchange minimums after rounding");
		}

		String clientOrderId = idGen.forSignal(user.getId(), entry.getId(), entry.getSymbol(),
				PositionSide.SHORT, // SELL leg to close the long
				LiveOrderPurpose.MANUAL_CLOSE);

		// Idempotency — if this close intent already exists, return it.
		LiveOrder existing = liveOrderRepository.findByAccountAndClientOrderId(account, clientOrderId).orElse(null);
		if (existing != null) return existing;

		LiveOrder intent = new LiveOrder();
		intent.setAccount(account);
		intent.setSignalId(entry.getSignalId());
		intent.setParentOrderId(entry.getId());
		intent.setClientOrderId(clientOrderId);
		intent.setSymbol(entry.getSymbol());
		// SPOT SELL — represented as SHORT in the side enum because we're selling the base asset.
		intent.setSide(PositionSide.SHORT);
		intent.setType(LiveOrderType.MARKET);
		intent.setPurpose(LiveOrderPurpose.MANUAL_CLOSE);
		intent.setStatus(LiveOrderStatus.CREATED);
		intent.setRequestedQuantity(qty);
		intent.setPrice(entry.getAvgFillPrice());
		intent.setProtectionStatus(ProtectionStatus.NOT_APPLICABLE);

		LiveOrder saved = persistIntent(intent, clientOrderId, account);
		return submitCloseOrder(saved, qty);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	protected LiveOrder persistIntent(LiveOrder intent, String clientOrderId,
			LiveTradingAccount account) {
		try {
			return liveOrderRepository.saveAndFlush(intent);
		} catch (DataIntegrityViolationException dup) {
			return liveOrderRepository.findByAccountAndClientOrderId(account, clientOrderId).orElseThrow();
		}
	}

	private LiveOrder submitCloseOrder(LiveOrder intent, BigDecimal qty) {
		try {
			var result = adapter.placeOrder(intent.getAccount().getCredential(), new PlaceOrderRequest(
					intent.getSymbol(),
					PositionSide.SHORT,
					LiveOrderType.MARKET,
					qty,
					intent.getPrice(),
					null,
					intent.getClientOrderId()));
			return executionService.applyResult(intent.getId(), result);
		} catch (ExchangeAdapterException ex) {
			return executionService.recordFailure(intent.getId(), ex);
		}
	}

	private void cancelSiblingStopLoss(LiveTradingAccount account, UUID entryId) {
		List<LiveOrder> live = liveOrderRepository.findByAccountAndStatusInOrderByCreatedAtDesc(
				account, List.of(LiveOrderStatus.ACKNOWLEDGED, LiveOrderStatus.SUBMITTED,
						LiveOrderStatus.PARTIALLY_FILLED, LiveOrderStatus.SUBMITTING));
		for (LiveOrder o : live) {
			if (o.getPurpose() == LiveOrderPurpose.STOP_LOSS
					&& entryId.equals(o.getParentOrderId())) {
				try {
					executionService.cancel(o.getId(), "manual close — parent " + entryId);
				} catch (Exception ex) {
					log.warn("[LiveClose] failed to cancel sibling SL {} — {}", o.getId(), ex.getMessage());
				}
			}
		}
	}
}
