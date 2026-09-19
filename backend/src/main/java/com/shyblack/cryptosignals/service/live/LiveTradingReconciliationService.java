package com.shyblack.cryptosignals.service.live;

import com.shyblack.cryptosignals.entity.LiveOrder;
import com.shyblack.cryptosignals.entity.LiveOrderLifecycleEvent;
import com.shyblack.cryptosignals.entity.LiveTradingAccount;
import com.shyblack.cryptosignals.entity.enums.LiveOrderLifecycleEventType;
import com.shyblack.cryptosignals.entity.enums.LiveOrderStatus;
import com.shyblack.cryptosignals.exchange.ExchangeAdapterException;
import com.shyblack.cryptosignals.exchange.ExchangeOrderResult;
import com.shyblack.cryptosignals.exchange.ExchangeTradingAdapter;
import com.shyblack.cryptosignals.repository.LiveOrderLifecycleEventRepository;
import com.shyblack.cryptosignals.repository.LiveOrderRepository;
import com.shyblack.cryptosignals.repository.LiveTradingAccountRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bridges the gap between our optimistic local state and what the exchange
 * actually did. Runs every 30 s (configurable) and after every app restart,
 * refreshing every non-terminal live order from the exchange.
 *
 * Also refreshes account balances so risk decisions stay grounded in reality.
 *
 * Rules: this service NEVER creates new orders. It only OBSERVES + UPDATES
 * local state. Every mutation writes an audit event.
 */
@Service
@RequiredArgsConstructor
public class LiveTradingReconciliationService {

	private static final Logger log = LoggerFactory.getLogger(LiveTradingReconciliationService.class);

	private final LiveOrderRepository orderRepository;
	private final LiveOrderLifecycleEventRepository lifecycleRepository;
	private final LiveTradingAccountRepository accountRepository;
	private final ExchangeTradingAdapter adapter;

	@Scheduled(fixedDelayString = "${app.live-trading.reconcile-interval-ms:30000}")
	public void reconcileOpenOrders() {
		List<LiveOrder> pending = orderRepository.findByStatusIn(List.of(
				LiveOrderStatus.SUBMITTING,
				LiveOrderStatus.SUBMITTED,
				LiveOrderStatus.ACKNOWLEDGED,
				LiveOrderStatus.PARTIALLY_FILLED,
				LiveOrderStatus.CANCEL_REQUESTED,
				LiveOrderStatus.UNKNOWN,
				LiveOrderStatus.RECONCILING));
		for (LiveOrder order : pending) {
			try {
				reconcileOne(order);
			} catch (Exception ex) {
				log.warn("[Reconciler] failed order={} err={}", order.getId(), ex.getMessage());
			}
		}
	}

	@Scheduled(fixedDelayString = "${app.live-trading.balance-refresh-ms:60000}")
	public void refreshBalances() {
		for (LiveTradingAccount account : accountRepository.findByEnabledTrueAndKillSwitchActiveFalse()) {
			try {
				var snap = adapter.getAccountBalance(account.getCredential());
				updateBalance(account.getId(), snap.availableBalance(), snap.totalBalance());
			} catch (ExchangeAdapterException ex) {
				log.debug("[Reconciler] balance refresh skipped account={} err={}",
						account.getId(), ex.getMessage());
			}
		}
	}

	@Transactional
	protected void updateBalance(java.util.UUID accountId, BigDecimal available, BigDecimal total) {
		accountRepository.findByIdForUpdate(accountId).ifPresent(a -> {
			a.setCachedAvailableBalance(available);
			a.setCachedTotalBalance(total);
			a.setLastValidatedAt(Instant.now());
			accountRepository.save(a);
		});
	}

	@Transactional
	protected void reconcileOne(LiveOrder localSnapshot) {
		ExchangeOrderResult remote;
		try {
			remote = adapter.getOrder(
					localSnapshot.getAccount().getCredential(),
					localSnapshot.getSymbol(),
					localSnapshot.getClientOrderId());
		} catch (ExchangeAdapterException ex) {
			return; // transient — try again next tick
		}
		if (remote == null) return;

		LiveOrder order = orderRepository.findByIdForUpdate(localSnapshot.getId())
				.orElse(null);
		if (order == null) return;
		if (order.getStatus() == remote.status()
				&& sameQty(order.getExecutedQuantity(), remote.executedQuantity())) {
			return;
		}
		LiveOrderStatus prev = order.getStatus();
		order.setStatus(remote.status());
		order.setExchangeOrderId(remote.exchangeOrderId());
		order.setExecutedQuantity(nonNull(remote.executedQuantity()));
		order.setCumulativeQuoteQty(nonNull(remote.cumulativeQuoteQty()));
		if (remote.avgFillPrice() != null) order.setAvgFillPrice(remote.avgFillPrice());
		if (remote.fee() != null) order.setFees(remote.fee());
		if (remote.feeAsset() != null) order.setFeeAsset(remote.feeAsset());
		if (order.getStatus() == LiveOrderStatus.FILLED
				|| order.getStatus() == LiveOrderStatus.PARTIALLY_FILLED) {
			order.setLastFillAt(Instant.now());
		}
		if (isTerminal(order.getStatus())) order.setCompletedAt(Instant.now());
		orderRepository.save(order);

		LiveOrderLifecycleEvent event = new LiveOrderLifecycleEvent();
		event.setOrder(order);
		event.setEventType(LiveOrderLifecycleEventType.RECONCILED);
		event.setStatusSnapshot(order.getStatus());
		event.setQuantity(order.getExecutedQuantity());
		event.setPrice(order.getAvgFillPrice());
		event.setFee(order.getFees());
		event.setFeeAsset(order.getFeeAsset());
		event.setMessage("reconciled " + prev + " -> " + order.getStatus());
		lifecycleRepository.save(event);
		log.info("[Reconciler] order={} {} -> {} executed={}", order.getId(),
				prev, order.getStatus(), order.getExecutedQuantity());
	}

	private static boolean isTerminal(LiveOrderStatus status) {
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

	private static BigDecimal nonNull(BigDecimal v) {
		return v == null ? BigDecimal.ZERO : v;
	}
}
