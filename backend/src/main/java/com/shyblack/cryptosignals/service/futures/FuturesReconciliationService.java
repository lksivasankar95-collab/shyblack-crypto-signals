package com.shyblack.cryptosignals.service.futures;

import com.shyblack.cryptosignals.entity.FuturesOrder;
import com.shyblack.cryptosignals.entity.FuturesOrderLifecycleEvent;
import com.shyblack.cryptosignals.entity.FuturesTradingAccount;
import com.shyblack.cryptosignals.entity.enums.FuturesLifecycleEventType;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderStatus;
import com.shyblack.cryptosignals.exchange.ExchangeAdapterException;
import com.shyblack.cryptosignals.exchange.futures.FuturesAccountSnapshot;
import com.shyblack.cryptosignals.exchange.futures.FuturesExchangeAdapter;
import com.shyblack.cryptosignals.exchange.futures.FuturesOrderResult;
import com.shyblack.cryptosignals.repository.FuturesOrderLifecycleEventRepository;
import com.shyblack.cryptosignals.repository.FuturesOrderRepository;
import com.shyblack.cryptosignals.repository.FuturesTradingAccountRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Futures REST reconciliation — periodically syncs local order state and
 * account balances against Binance Futures. Never creates or cancels
 * orders on the exchange. Never overrides local intent silently: every
 * change writes a RECONCILED event.
 */
@Service
@RequiredArgsConstructor
public class FuturesReconciliationService {

	private static final Logger log = LoggerFactory.getLogger(FuturesReconciliationService.class);

	private final FuturesOrderRepository orderRepository;
	private final FuturesOrderLifecycleEventRepository lifecycleRepository;
	private final FuturesTradingAccountRepository accountRepository;
	private final FuturesExchangeAdapter adapter;

	@Scheduled(fixedDelayString = "${app.futures-trading.reconcile-interval-ms:30000}")
	public void reconcileOpenOrders() {
		List<FuturesOrder> pending = orderRepository.findByStatusIn(List.of(
				FuturesOrderStatus.SUBMITTING, FuturesOrderStatus.SUBMITTED,
				FuturesOrderStatus.ACKNOWLEDGED, FuturesOrderStatus.PARTIALLY_FILLED,
				FuturesOrderStatus.CANCEL_REQUESTED, FuturesOrderStatus.UNKNOWN,
				FuturesOrderStatus.RECONCILING));
		for (FuturesOrder order : pending) {
			try { reconcileOne(order); }
			catch (Exception ex) {
				log.warn("[FutReconciler] order={} err={}", order.getId(), ex.getMessage());
			}
		}
	}

	@Scheduled(fixedDelayString = "${app.futures-trading.balance-refresh-ms:60000}")
	public void refreshBalances() {
		for (FuturesTradingAccount account : accountRepository.findByEnabledTrueAndKillSwitchActiveFalse()) {
			try {
				FuturesAccountSnapshot snap = adapter.getAccount(account.getCredential());
				updateBalance(account.getId(), snap);
			} catch (ExchangeAdapterException ex) {
				log.debug("[FutReconciler] balance skipped account={} err={}",
						account.getId(), ex.getMessage());
			}
		}
	}

	@Transactional
	protected void updateBalance(UUID accountId, FuturesAccountSnapshot snap) {
		accountRepository.findByIdForUpdate(accountId).ifPresent(a -> {
			a.setWalletBalance(snap.walletBalance());
			a.setAvailableBalance(snap.availableBalance());
			a.setMarginBalance(snap.marginBalance());
			a.setUsedMargin(snap.usedMargin());
			a.setMaintenanceMargin(snap.maintenanceMargin());
			a.setUnrealizedPnl(snap.unrealizedPnl());
			a.setLastValidatedAt(Instant.now());
			accountRepository.save(a);
		});
	}

	@Transactional
	protected void reconcileOne(FuturesOrder snapshot) {
		FuturesOrderResult remote;
		try {
			remote = adapter.getOrder(snapshot.getAccount().getCredential(),
					snapshot.getSymbol(), snapshot.getClientOrderId());
		} catch (ExchangeAdapterException ex) {
			return;
		}
		if (remote == null) return;
		FuturesOrder order = orderRepository.findByIdForUpdate(snapshot.getId()).orElse(null);
		if (order == null) return;
		if (order.getStatus() == remote.status()
				&& sameQty(order.getExecutedQuantity(), remote.executedQuantity())) return;

		FuturesOrderStatus prev = order.getStatus();
		order.setStatus(remote.status());
		order.setExchangeOrderId(remote.exchangeOrderId());
		order.setExecutedQuantity(nonNull(remote.executedQuantity()));
		order.setCumulativeQuoteQty(nonNull(remote.cumulativeQuoteQty()));
		if (remote.avgFillPrice() != null) order.setAvgFillPrice(remote.avgFillPrice());
		if (remote.fee() != null && remote.fee().signum() > 0) order.setFees(remote.fee());
		if (remote.feeAsset() != null) order.setFeeAsset(remote.feeAsset());
		if (order.getStatus() == FuturesOrderStatus.FILLED
				|| order.getStatus() == FuturesOrderStatus.PARTIALLY_FILLED) {
			order.setLastFillAt(Instant.now());
		}
		if (isTerminal(order.getStatus())) order.setCompletedAt(Instant.now());
		orderRepository.save(order);

		FuturesOrderLifecycleEvent event = new FuturesOrderLifecycleEvent();
		event.setOrder(order);
		event.setEventType(FuturesLifecycleEventType.RECONCILED);
		event.setStatusSnapshot(order.getStatus());
		event.setQuantity(order.getExecutedQuantity());
		event.setPrice(order.getAvgFillPrice());
		event.setFee(order.getFees());
		event.setFeeAsset(order.getFeeAsset());
		event.setMessage("reconciled " + prev + " -> " + order.getStatus());
		lifecycleRepository.save(event);
		log.info("[FutReconciler] order={} {} -> {} executed={}", order.getId(),
				prev, order.getStatus(), order.getExecutedQuantity());
	}

	private static boolean isTerminal(FuturesOrderStatus status) {
		return switch (status) {
			case FILLED, CANCELLED, REJECTED, EXPIRED, FAILED -> true;
			default -> false;
		};
	}

	private static boolean sameQty(BigDecimal a, BigDecimal b) {
		BigDecimal x = a == null ? BigDecimal.ZERO : a;
		BigDecimal y = b == null ? BigDecimal.ZERO : b;
		return x.compareTo(y) == 0;
	}

	private static BigDecimal nonNull(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
