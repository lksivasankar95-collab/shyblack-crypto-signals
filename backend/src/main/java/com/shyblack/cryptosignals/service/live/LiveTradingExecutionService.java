package com.shyblack.cryptosignals.service.live;

import com.shyblack.cryptosignals.entity.LiveOrder;
import com.shyblack.cryptosignals.entity.LiveOrderLifecycleEvent;
import com.shyblack.cryptosignals.entity.LiveTradingAccount;
import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.enums.LiveOrderLifecycleEventType;
import com.shyblack.cryptosignals.entity.enums.LiveOrderPurpose;
import com.shyblack.cryptosignals.entity.enums.LiveOrderStatus;
import com.shyblack.cryptosignals.entity.enums.LiveOrderType;
import com.shyblack.cryptosignals.exchange.ClientOrderIdGenerator;
import com.shyblack.cryptosignals.exchange.ExchangeAdapterException;
import com.shyblack.cryptosignals.exchange.ExchangeOrderResult;
import com.shyblack.cryptosignals.exchange.ExchangeTradingAdapter;
import com.shyblack.cryptosignals.exchange.PlaceOrderRequest;
import com.shyblack.cryptosignals.exchange.SymbolRules;
import com.shyblack.cryptosignals.repository.LiveOrderLifecycleEventRepository;
import com.shyblack.cryptosignals.repository.LiveOrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only path that submits real exchange orders. Transaction boundaries are
 * deliberately narrow: we commit local intent BEFORE calling the exchange, so
 * an exchange HTTP call is never held inside a DB transaction. If the
 * exchange call throws, the persisted intent is still there and reconciliation
 * (or an operator) can recover.
 *
 * Idempotency: the deterministic clientOrderId + unique constraint means the
 * same signal can never produce two exchange orders even under duplicate
 * events, restarts or retries.
 */
@Service
@RequiredArgsConstructor
public class LiveTradingExecutionService {

	private static final Logger log = LoggerFactory.getLogger(LiveTradingExecutionService.class);

	private final LiveOrderRepository liveOrderRepository;
	private final LiveOrderLifecycleEventRepository lifecycleRepository;
	private final ExchangeTradingAdapter adapter;
	private final ClientOrderIdGenerator idGen;

	/** Persist the local intent atomically. Committed before any exchange HTTP call. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public LiveOrder createIntent(LiveTradingAccount account, Signal signal,
			SymbolRules rules, BigDecimal quantity, BigDecimal referencePrice,
			LiveOrderPurpose purpose, LiveOrderType type, UUID parentOrderId,
			BigDecimal stopPrice) {

		String clientOrderId = idGen.forSignal(account.getUser().getId(),
				signal != null ? signal.getId() : parentOrderId,
				rules.symbol(), signal != null ? signal.getSide() : com.shyblack.cryptosignals.entity.enums.PositionSide.LONG,
				purpose);

		Optional<LiveOrder> existing = liveOrderRepository.findByAccountAndClientOrderId(account, clientOrderId);
		if (existing.isPresent()) return existing.get();

		LiveOrder order = new LiveOrder();
		order.setAccount(account);
		order.setSignalId(signal != null ? signal.getId() : null);
		order.setParentOrderId(parentOrderId);
		order.setClientOrderId(clientOrderId);
		order.setSymbol(rules.symbol());
		order.setSide(signal != null ? signal.getSide()
				: com.shyblack.cryptosignals.entity.enums.PositionSide.LONG);
		order.setType(type);
		order.setPurpose(purpose);
		order.setStatus(LiveOrderStatus.CREATED);
		order.setRequestedQuantity(quantity);
		order.setPrice(referencePrice);
		order.setStopPrice(stopPrice);

		try {
			order = liveOrderRepository.saveAndFlush(order);
		} catch (DataIntegrityViolationException dup) {
			return liveOrderRepository.findByAccountAndClientOrderId(account, clientOrderId).orElseThrow();
		}
		writeEvent(order, LiveOrderLifecycleEventType.INTENT_CREATED, "clientOrderId=" + clientOrderId);
		return order;
	}

	/**
	 * Submit the intent to the exchange and persist the response. Runs OUTSIDE
	 * the caller's transaction and uses REQUIRES_NEW when updating so the
	 * exchange HTTP call happens without holding any DB lock.
	 */
	public LiveOrder submit(LiveOrder intent) {
		markSubmitting(intent.getId());
		ExchangeOrderResult result;
		try {
			result = adapter.placeOrder(intent.getAccount().getCredential(), new PlaceOrderRequest(
					intent.getSymbol(),
					intent.getSide(),
					intent.getType(),
					intent.getRequestedQuantity(),
					intent.getPrice(),
					intent.getStopPrice(),
					intent.getClientOrderId()));
		} catch (ExchangeAdapterException ex) {
			return recordFailure(intent.getId(), ex);
		}
		return applyResult(intent.getId(), result);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	protected void markSubmitting(UUID id) {
		liveOrderRepository.findByIdForUpdate(id).ifPresent(order -> {
			if (order.getStatus() == LiveOrderStatus.CREATED) {
				order.setStatus(LiveOrderStatus.SUBMITTING);
				order.setSubmittedAt(Instant.now());
				liveOrderRepository.save(order);
				writeEvent(order, LiveOrderLifecycleEventType.SUBMITTED, null);
			}
		});
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	protected LiveOrder applyResult(UUID id, ExchangeOrderResult result) {
		LiveOrder order = liveOrderRepository.findByIdForUpdate(id)
				.orElseThrow(() -> new IllegalStateException("Order missing: " + id));
		order.setExchangeOrderId(result.exchangeOrderId());
		order.setStatus(result.status());
		order.setExecutedQuantity(nonNull(result.executedQuantity()));
		order.setCumulativeQuoteQty(nonNull(result.cumulativeQuoteQty()));
		order.setAvgFillPrice(result.avgFillPrice());
		order.setFees(nonNull(result.fee()));
		order.setFeeAsset(result.feeAsset());
		if (order.getStatus() == LiveOrderStatus.FILLED
				|| order.getStatus() == LiveOrderStatus.PARTIALLY_FILLED) {
			order.setLastFillAt(Instant.now());
		}
		if (isTerminal(order.getStatus())) order.setCompletedAt(Instant.now());
		order = liveOrderRepository.save(order);
		writeEvent(order, LiveOrderLifecycleEventType.ACKNOWLEDGED,
				"exchangeOrderId=" + result.exchangeOrderId() + " status=" + result.status());
		if (order.getStatus() == LiveOrderStatus.FILLED
				|| order.getStatus() == LiveOrderStatus.PARTIALLY_FILLED) {
			writeEvent(order, LiveOrderLifecycleEventType.FILL,
					"executed=" + result.executedQuantity() + " avg=" + result.avgFillPrice()
							+ " fee=" + result.fee() + " " + result.feeAsset());
		}
		log.info("[LiveExec] order={} status={} executed={} avg={} fee={} {}",
				order.getId(), order.getStatus(), order.getExecutedQuantity(),
				order.getAvgFillPrice(), order.getFees(), order.getFeeAsset());
		return order;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	protected LiveOrder recordFailure(UUID id, ExchangeAdapterException ex) {
		LiveOrder order = liveOrderRepository.findByIdForUpdate(id).orElseThrow();
		// If the exchange might have accepted the order but we couldn't parse the
		// response, mark UNKNOWN so reconciliation can query by clientOrderId.
		LiveOrderStatus status = ex.exchangeCode() != null && ex.exchangeCode() == -1013
				? LiveOrderStatus.REJECTED
				: (ex.retryable() ? LiveOrderStatus.UNKNOWN : LiveOrderStatus.FAILED);
		order.setStatus(status);
		order.setRejectReason(safe(ex.getMessage()));
		liveOrderRepository.save(order);
		writeEvent(order, status == LiveOrderStatus.REJECTED
				? LiveOrderLifecycleEventType.REJECTED
				: LiveOrderLifecycleEventType.FAILED,
				"code=" + ex.exchangeCode() + " http=" + ex.httpStatus() + " " + safe(ex.getMessage()));
		log.warn("[LiveExec] submit failed order={} status={} reason={}", id, status, safe(ex.getMessage()));
		return order;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public LiveOrder cancel(UUID orderId, String reasonLabel) {
		LiveOrder order = liveOrderRepository.findByIdForUpdate(orderId)
				.orElseThrow(() -> new IllegalStateException("Order not found"));
		if (isTerminal(order.getStatus())) return order;
		order.setStatus(LiveOrderStatus.CANCEL_REQUESTED);
		liveOrderRepository.save(order);
		writeEvent(order, LiveOrderLifecycleEventType.CANCEL_REQUESTED, reasonLabel);
		try {
			ExchangeOrderResult result = adapter.cancelOrder(
					order.getAccount().getCredential(), order.getSymbol(), order.getClientOrderId());
			if (result != null) {
				order.setStatus(result.status());
				order.setCompletedAt(Instant.now());
				liveOrderRepository.save(order);
				writeEvent(order, LiveOrderLifecycleEventType.CANCELLED,
						"exchange status=" + result.status());
			}
		} catch (ExchangeAdapterException ex) {
			writeEvent(order, LiveOrderLifecycleEventType.FAILED, "cancel error " + safe(ex.getMessage()));
			log.warn("[LiveExec] cancel failed order={} err={}", orderId, safe(ex.getMessage()));
		}
		return order;
	}

	// ------------------------------------------------------------------

	private void writeEvent(LiveOrder order, LiveOrderLifecycleEventType type, String message) {
		LiveOrderLifecycleEvent event = new LiveOrderLifecycleEvent();
		event.setOrder(order);
		event.setEventType(type);
		event.setStatusSnapshot(order.getStatus());
		event.setQuantity(order.getExecutedQuantity());
		event.setPrice(order.getAvgFillPrice());
		event.setFee(order.getFees());
		event.setFeeAsset(order.getFeeAsset());
		event.setMessage(message);
		lifecycleRepository.save(event);
	}

	private static boolean isTerminal(LiveOrderStatus status) {
		return switch (status) {
			case FILLED, CANCELLED, REJECTED, EXPIRED, FAILED -> true;
			default -> false;
		};
	}

	private static BigDecimal nonNull(BigDecimal v) {
		return v == null ? BigDecimal.ZERO : v;
	}

	private static String safe(String v) {
		if (v == null) return null;
		return v.length() > 480 ? v.substring(0, 480) : v;
	}
}
