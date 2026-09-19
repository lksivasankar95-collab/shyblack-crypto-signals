package com.shyblack.cryptosignals.service.futures;

import com.shyblack.cryptosignals.entity.FuturesOrder;
import com.shyblack.cryptosignals.entity.FuturesOrderLifecycleEvent;
import com.shyblack.cryptosignals.entity.FuturesTradingAccount;
import com.shyblack.cryptosignals.entity.enums.FuturesLifecycleEventType;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderPurpose;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderStatus;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderType;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.exchange.ExchangeAdapterException;
import com.shyblack.cryptosignals.exchange.futures.FuturesExchangeAdapter;
import com.shyblack.cryptosignals.exchange.futures.FuturesOrderResult;
import com.shyblack.cryptosignals.exchange.futures.PlaceFuturesOrderRequest;
import com.shyblack.cryptosignals.repository.FuturesOrderLifecycleEventRepository;
import com.shyblack.cryptosignals.repository.FuturesOrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only path that submits real Futures orders. Same idempotency + narrow
 * transaction contract as the SPOT execution service — DB commit BEFORE the
 * Binance HTTP call; the HTTP call is never inside a transaction.
 */
@Service
@RequiredArgsConstructor
public class FuturesExecutionService {

	private static final Logger log = LoggerFactory.getLogger(FuturesExecutionService.class);

	private final FuturesOrderRepository futuresOrderRepository;
	private final FuturesOrderLifecycleEventRepository lifecycleRepository;
	private final FuturesExchangeAdapter adapter;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public FuturesOrder createIntent(FuturesTradingAccount account, UUID signalId, UUID parentOrderId,
			String clientOrderId, String symbol, PositionSide exchangeSide, PositionSide positionSide,
			FuturesOrderType type, FuturesOrderPurpose purpose, boolean reduceOnly, int leverage,
			BigDecimal quantity, BigDecimal price, BigDecimal stopPrice) {

		return futuresOrderRepository.findByAccountAndClientOrderId(account, clientOrderId)
				.orElseGet(() -> {
					FuturesOrder order = new FuturesOrder();
					order.setAccount(account);
					order.setSignalId(signalId);
					order.setParentOrderId(parentOrderId);
					order.setClientOrderId(clientOrderId);
					order.setSymbol(symbol);
					order.setSide(exchangeSide);
					order.setPositionSide(positionSide);
					order.setType(type);
					order.setPurpose(purpose);
					order.setReduceOnly(reduceOnly);
					order.setLeverage(leverage);
					order.setRequestedQuantity(quantity);
					order.setPrice(price);
					order.setStopPrice(stopPrice);
					order.setStatus(FuturesOrderStatus.CREATED);
					try {
						FuturesOrder saved = futuresOrderRepository.saveAndFlush(order);
						writeEvent(saved, FuturesLifecycleEventType.INTENT_CREATED,
								"clientOrderId=" + clientOrderId);
						return saved;
					} catch (DataIntegrityViolationException dup) {
						return futuresOrderRepository
								.findByAccountAndClientOrderId(account, clientOrderId).orElseThrow();
					}
				});
	}

	/** Submit an intent to Binance. Runs outside any DB transaction. */
	public FuturesOrder submit(FuturesOrder intent) {
		markSubmitting(intent.getId());
		FuturesOrderResult result;
		try {
			result = adapter.placeOrder(intent.getAccount().getCredential(),
					new PlaceFuturesOrderRequest(
							intent.getSymbol(),
							intent.getSide(),
							intent.getPositionSide(),
							intent.getType(),
							intent.isReduceOnly(),
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
	public FuturesOrder markSubmitting(UUID id) {
		return futuresOrderRepository.findByIdForUpdate(id).map(order -> {
			if (order.getStatus() == FuturesOrderStatus.CREATED) {
				order.setStatus(FuturesOrderStatus.SUBMITTING);
				order.setSubmittedAt(Instant.now());
				FuturesOrder saved = futuresOrderRepository.save(order);
				writeEvent(saved, FuturesLifecycleEventType.SUBMITTED, null);
				return saved;
			}
			return order;
		}).orElse(null);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public FuturesOrder applyResult(UUID id, FuturesOrderResult result) {
		FuturesOrder order = futuresOrderRepository.findByIdForUpdate(id).orElseThrow();
		order.setExchangeOrderId(result.exchangeOrderId());
		order.setStatus(result.status());
		order.setExecutedQuantity(nonNull(result.executedQuantity()));
		order.setCumulativeQuoteQty(nonNull(result.cumulativeQuoteQty()));
		if (result.avgFillPrice() != null) order.setAvgFillPrice(result.avgFillPrice());
		if (result.fee() != null && result.fee().signum() > 0) order.setFees(result.fee());
		if (result.feeAsset() != null) order.setFeeAsset(result.feeAsset());
		if (order.getStatus() == FuturesOrderStatus.FILLED
				|| order.getStatus() == FuturesOrderStatus.PARTIALLY_FILLED) {
			order.setLastFillAt(Instant.now());
		}
		if (isTerminal(order.getStatus())) order.setCompletedAt(Instant.now());
		FuturesOrder saved = futuresOrderRepository.save(order);
		writeEvent(saved, FuturesLifecycleEventType.ACKNOWLEDGED,
				"exchangeOrderId=" + result.exchangeOrderId() + " status=" + result.status());
		if (saved.getStatus() == FuturesOrderStatus.FILLED
				|| saved.getStatus() == FuturesOrderStatus.PARTIALLY_FILLED) {
			writeEvent(saved, FuturesLifecycleEventType.FILL,
					"executed=" + result.executedQuantity() + " avg=" + result.avgFillPrice()
							+ " fee=" + result.fee() + " " + result.feeAsset());
		}
		return saved;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public FuturesOrder recordFailure(UUID id, ExchangeAdapterException ex) {
		FuturesOrder order = futuresOrderRepository.findByIdForUpdate(id).orElseThrow();
		FuturesOrderStatus status = ex.retryable()
				? FuturesOrderStatus.UNKNOWN
				: FuturesOrderStatus.FAILED;
		order.setStatus(status);
		order.setRejectReason(safe(ex.getMessage()));
		FuturesOrder saved = futuresOrderRepository.save(order);
		writeEvent(saved, status == FuturesOrderStatus.UNKNOWN
						? FuturesLifecycleEventType.FAILED
						: FuturesLifecycleEventType.REJECTED,
				"code=" + ex.exchangeCode() + " http=" + ex.httpStatus());
		return saved;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public FuturesOrder cancel(UUID id, String reasonLabel) {
		FuturesOrder order = futuresOrderRepository.findByIdForUpdate(id).orElseThrow();
		if (isTerminal(order.getStatus())) return order;
		order.setStatus(FuturesOrderStatus.CANCEL_REQUESTED);
		futuresOrderRepository.save(order);
		writeEvent(order, FuturesLifecycleEventType.CANCEL_REQUESTED, reasonLabel);
		try {
			FuturesOrderResult result = adapter.cancelOrder(
					order.getAccount().getCredential(), order.getSymbol(), order.getClientOrderId());
			if (result != null) {
				order.setStatus(result.status());
				order.setCompletedAt(Instant.now());
				futuresOrderRepository.save(order);
				writeEvent(order, FuturesLifecycleEventType.CANCELLED,
						"exchange status=" + result.status());
			}
		} catch (ExchangeAdapterException ex) {
			log.warn("[FutExec] cancel failed order={} err={}", id, safe(ex.getMessage()));
			writeEvent(order, FuturesLifecycleEventType.FAILED, "cancel error " + safe(ex.getMessage()));
		}
		return order;
	}

	// ------------------------------------------------------------------

	private void writeEvent(FuturesOrder order, FuturesLifecycleEventType type, String message) {
		FuturesOrderLifecycleEvent event = new FuturesOrderLifecycleEvent();
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

	private static boolean isTerminal(FuturesOrderStatus status) {
		return switch (status) {
			case FILLED, CANCELLED, REJECTED, EXPIRED, FAILED -> true;
			default -> false;
		};
	}

	private static BigDecimal nonNull(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
	private static String safe(String v) {
		if (v == null) return null;
		return v.length() > 480 ? v.substring(0, 480) : v;
	}
}
