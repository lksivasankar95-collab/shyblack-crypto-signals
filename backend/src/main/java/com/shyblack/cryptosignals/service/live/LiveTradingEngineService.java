package com.shyblack.cryptosignals.service.live;

import com.shyblack.cryptosignals.entity.LiveOrder;
import com.shyblack.cryptosignals.entity.LiveTradingAccount;
import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.LiveOrderPurpose;
import com.shyblack.cryptosignals.entity.enums.LiveOrderStatus;
import com.shyblack.cryptosignals.entity.enums.LiveOrderType;
import com.shyblack.cryptosignals.entity.enums.LiveTradingRiskReason;
import com.shyblack.cryptosignals.entity.enums.ProtectionStatus;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.repository.LiveOrderRepository;
import com.shyblack.cryptosignals.exchange.ExchangeTradingAdapter;
import com.shyblack.cryptosignals.exchange.SymbolRules;
import com.shyblack.cryptosignals.market.MarketBook;
import com.shyblack.cryptosignals.repository.SignalRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the signal → live-order pipeline for ONE already-gated account.
 *
 * Since Phase 8 this engine no longer listens for {@code SignalGeneratedEvent}
 * and performs no fan-out: {@code service.execution.ExecutionRouter} is the
 * single authoritative routing decision and calls {@link #executeForAccount}
 * only after mode, category, account state, operator approval, idempotency and
 * the risk service have all passed.
 *
 * Given one approved account and a SPOT signal:
 *
 *   1. Re-check risk (defence in depth at the point of submission)
 *   2. Fetch exchange symbol rules
 *   3. Size the trade
 *   4. Create local intent (committed)
 *   5. Submit exchange MARKET buy
 *   6. On fill, create protective SL (STOP_LOSS_LIMIT)
 *
 * Step 6 fires only on a status the exchange actually reported as filled or
 * partially filled. A submitted or acknowledged order is never treated as
 * filled.
 *
 * Nothing here touches paper trading — the two engines live in separate
 * packages by design.
 */
@Service
@RequiredArgsConstructor
public class LiveTradingEngineService {

	private static final Logger log = LoggerFactory.getLogger(LiveTradingEngineService.class);

	private final LiveTradingRiskService riskService;
	private final LiveTradingSizingService sizingService;
	private final LiveTradingExecutionService executionService;
	private final ExchangeTradingAdapter adapter;
	private final MarketBook marketBook;
	private final LiveOrderRepository liveOrderRepository;

	/**
	 * Entry point for one specific live spot account.
	 *
	 * <p>Since Phase 8 this engine no longer listens for {@code SignalGeneratedEvent}
	 * itself. {@code service.execution.ExecutionRouter} is the single authoritative
	 * routing decision; it has already resolved the mode, category, account state,
	 * operator approval and idempotency, and it calls this method only when every
	 * gate passed. The engine therefore performs no fan-out and no mode gating of
	 * its own, while the execution behaviour below is unchanged.
	 *
	 * <p>The defensive risk re-check below is deliberately retained. The router
	 * already called the same risk service, but re-checking at the point of
	 * submission means a change between the two calls still cannot place an order.
	 */
	public void executeForAccount(LiveTradingAccount account, Signal signal) {
		try {
			processForAccount(account, signal);
		} catch (Exception ex) {
			log.warn("[Live] execution failed account={} signal={} err={}",
					account.getId(), signal.getId(), ex.getMessage());
		}
	}

	private void processForAccount(LiveTradingAccount account, Signal signal) {
		User user = account.getUser();
		// This live engine executes the SPOT strategy universe only; mode is a
		// system/strategy concern, not a user preference.
		if (signal.getTradingMode() != TradingMode.SPOT) return;

		LiveTradingRiskReason risk = riskService.check(user, account, signal);
		if (risk != LiveTradingRiskReason.OK) {
			log.info("[Live] signal={} account={} BLOCKED reason={}",
					signal.getId(), account.getId(), risk);
			return;
		}

		SymbolRules rules;
		try {
			rules = adapter.getSymbolRules(signal.getSymbol());
		} catch (Exception ex) {
			log.warn("[Live] no rules for {} — {}", signal.getSymbol(), ex.getMessage());
			return;
		}
		if (rules == null) return;

		BigDecimal referencePrice = liveReferencePrice(signal);
		if (referencePrice == null) return;

		BigDecimal available = account.getCachedAvailableBalance() == null
				? BigDecimal.ZERO
				: account.getCachedAvailableBalance();
		LiveTradingSizingService.Sizing sizing =
				sizingService.size(account, signal, rules, available, referencePrice);
		if (!sizing.ok()) {
			log.info("[Live] sizing rejected signal={} account={} reason={}",
					signal.getId(), account.getId(), sizing.reason());
			return;
		}

		LiveOrder entryIntent = executionService.createIntent(
				account, signal, rules, sizing.quantity(), referencePrice,
				LiveOrderPurpose.ENTRY, LiveOrderType.MARKET, null, null);
		LiveOrder submitted = executionService.submit(entryIntent);

		if (submitted.getStatus() == LiveOrderStatus.FILLED
				|| submitted.getStatus() == LiveOrderStatus.PARTIALLY_FILLED) {
			markProtection(submitted, ProtectionStatus.PENDING);
			placeProtectiveStop(account, signal, rules, submitted);
		}
	}

	private void placeProtectiveStop(LiveTradingAccount account, Signal signal,
			SymbolRules rules, LiveOrder entry) {
		BigDecimal stopPrice = rules.normalizePrice(signal.getStopLoss());
		BigDecimal limitPrice = rules.normalizePrice(
				signal.getStopLoss().multiply(new BigDecimal("0.995")));
		BigDecimal qty = entry.getExecutedQuantity();
		if (qty == null || qty.signum() <= 0) {
			markProtection(entry, ProtectionStatus.PROTECTION_FAILED);
			log.warn("[Live] SL skipped — entry {} has no executed quantity", entry.getId());
			return;
		}

		LiveOrder submitted;
		try {
			LiveOrder intent = executionService.createIntent(
					account, signal, rules, qty, limitPrice,
					LiveOrderPurpose.STOP_LOSS,
					LiveOrderType.STOP_LOSS_LIMIT,
					entry.getId(),
					stopPrice);
			submitted = executionService.submit(intent);
		} catch (Exception ex) {
			markProtection(entry, ProtectionStatus.PROTECTION_FAILED);
			log.error("[Live] SL PLACEMENT FAILED for entry={} err={} — position is UNPROTECTED",
					entry.getId(), ex.getMessage());
			return;
		}

		boolean protectedOk = switch (submitted.getStatus()) {
			case ACKNOWLEDGED, SUBMITTED, PARTIALLY_FILLED, FILLED -> true;
			default -> false;
		};
		markProtection(entry, protectedOk
				? ProtectionStatus.PROTECTED
				: ProtectionStatus.PROTECTION_FAILED);
		if (!protectedOk) {
			log.error("[Live] SL placement returned non-live status={} for entry={} — UNPROTECTED",
					submitted.getStatus(), entry.getId());
		} else {
			log.info("[Live] SL placed for entry={} qty={} stop={} limit={}",
					entry.getId(), qty, stopPrice, limitPrice);
		}
	}

	private void markProtection(LiveOrder entry, ProtectionStatus status) {
		liveOrderRepository.findByIdForUpdate(entry.getId()).ifPresent(fresh -> {
			fresh.setProtectionStatus(status);
			liveOrderRepository.save(fresh);
		});
	}

	/**
	 * The reference price used to size a live order.
	 *
	 * <p>Resolved on the signal's own market. This previously read {@code spotTickers} regardless of
	 * the signal's market, so a futures signal was sized against the spot price for the same symbol —
	 * a real quantity error, since a perpetual trades at a different price than its underlying pair.
	 * Falling back to {@code signal.entryPrice} keeps the behaviour safe when the market has no live
	 * quote, which is a value already recorded on the signal rather than a substituted one.
	 */
	private BigDecimal liveReferencePrice(Signal signal) {
		if (signal.getTradingMode() == null) return null;
		if (signal.getTradingMode().name().equals("OPTIONS")) return null;
		if (adapter.exchange() == ExchangeName.BINANCE) {
			// Prefer MarketBook (already streaming) so we never call an extra REST endpoint
			// just to size a trade. Fall back to signal.entryPrice.
			return marketBook.ticker(signal.getTradingMode(), signal.getSymbol())
					.map(t -> t.price())
					.orElseGet(signal::getEntryPrice);
		}
		return signal.getEntryPrice();
	}

	// Suppress: connection status field kept for future symmetry with disconnect flows.
	@SuppressWarnings("unused")
	private static boolean isConnected(LiveTradingAccount account) {
		return account.getConnectionStatus() == ExchangeConnectionStatus.CONNECTED;
	}
}
