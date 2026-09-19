package com.shyblack.cryptosignals.service.futures;

import com.shyblack.cryptosignals.config.FuturesTradingProperties;
import com.shyblack.cryptosignals.entity.FuturesOrder;
import com.shyblack.cryptosignals.entity.FuturesPosition;
import com.shyblack.cryptosignals.entity.FuturesTradingAccount;
import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.enums.FuturesLifecycleEventType;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderPurpose;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderStatus;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderType;
import com.shyblack.cryptosignals.entity.enums.FuturesPositionStatus;
import com.shyblack.cryptosignals.entity.enums.FuturesProtectionStatus;
import com.shyblack.cryptosignals.entity.enums.FuturesRiskReason;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.SignalStatus;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.exchange.SymbolRules;
import com.shyblack.cryptosignals.exchange.futures.FuturesExchangeAdapter;
import com.shyblack.cryptosignals.market.MarketBook;
import com.shyblack.cryptosignals.repository.FuturesOrderLifecycleEventRepository;
import com.shyblack.cryptosignals.repository.FuturesPositionRepository;
import com.shyblack.cryptosignals.repository.FuturesTradingAccountRepository;
import com.shyblack.cryptosignals.repository.SignalRepository;
import com.shyblack.cryptosignals.entity.FuturesOrderLifecycleEvent;
import com.shyblack.cryptosignals.service.SignalGeneratedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Orchestrates FUTURES signal → live-order pipeline.
 *
 *   SPOT signals are ignored — the SPOT engine handles those.
 *   FUTURES signals reach this listener via TradingMode.FUTURES.
 *
 * Flow per user account:
 *   1. Risk gate (Futures-specific).
 *   2. Leverage check.
 *   3. Fetch symbol rules.
 *   4. Sizing (risk-based, leverage-aware for margin).
 *   5. Liquidation safety.
 *   6. setLeverage + setMarginMode on the exchange.
 *   7. Create local intent (committed).
 *   8. Submit MARKET BUY (LONG) or MARKET SELL (SHORT).
 *   9. On fill: create FuturesPosition row + submit protective STOP_MARKET
 *      REDUCE_ONLY (SELL for LONG, BUY for SHORT). ProtectionStatus flips
 *      to PROTECTED or PROTECTION_FAILED.
 */
@Service
@RequiredArgsConstructor
public class FuturesEngineService {

	private static final Logger log = LoggerFactory.getLogger(FuturesEngineService.class);

	private final FuturesTradingProperties props;
	private final FuturesTradingAccountRepository accountRepository;
	private final SignalRepository signalRepository;
	private final FuturesRiskService riskService;
	private final FuturesTradingSizingService sizingService;
	private final FuturesLiquidationService liquidationService;
	private final FuturesExecutionService executionService;
	private final FuturesExchangeAdapter adapter;
	private final MarketBook marketBook;
	private final FuturesClientOrderIdGenerator idGen;
	private final FuturesPositionRepository positionRepository;
	private final FuturesOrderLifecycleEventRepository lifecycleRepository;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onSignalGenerated(SignalGeneratedEvent event) {
		if (!props.autoExecute()) return;
		Optional<Signal> maybe = signalRepository.findById(event.getSignalId());
		if (maybe.isEmpty()) return;
		Signal signal = maybe.get();
		if (signal.getStatus() != SignalStatus.ACTIVE) return;
		if (signal.getTradingMode() != TradingMode.FUTURES) return; // SPOT handled elsewhere

		for (FuturesTradingAccount account : accountRepository.findByEnabledTrueAndKillSwitchActiveFalse()) {
			try { processForAccount(account, signal); }
			catch (Exception ex) {
				log.warn("[FutEngine] fan-out failed account={} signal={} err={}",
						account.getId(), signal.getId(), ex.getMessage());
			}
		}
	}

	private void processForAccount(FuturesTradingAccount account, Signal signal) {
		FuturesRiskReason risk = riskService.check(account.getUser(), account, signal);
		if (risk != FuturesRiskReason.OK) {
			log.info("[FutEngine] signal={} account={} BLOCKED reason={}",
					signal.getId(), account.getId(), risk);
			return;
		}
		int leverage = Math.min(account.getMaxLeverage(), props.maxLeverage());
		FuturesRiskReason leverageCheck = riskService.checkLeverage(account, leverage);
		if (leverageCheck != FuturesRiskReason.OK) {
			log.info("[FutEngine] leverage rejected account={} lev={} reason={}",
					account.getId(), leverage, leverageCheck);
			return;
		}

		SymbolRules rules;
		try { rules = adapter.getSymbolRules(signal.getSymbol()); }
		catch (Exception ex) {
			log.warn("[FutEngine] rules lookup failed {} — {}", signal.getSymbol(), ex.getMessage());
			return;
		}
		if (rules == null) return;

		BigDecimal referencePrice = referencePrice(signal);
		if (referencePrice == null) return;

		BigDecimal available = account.getAvailableBalance() == null
				? BigDecimal.ZERO : account.getAvailableBalance();
		FuturesTradingSizingService.Sizing sizing =
				sizingService.size(account, signal, rules, available, referencePrice, leverage);
		if (!sizing.ok()) {
			log.info("[FutEngine] sizing rejected signal={} account={} reason={}",
					signal.getId(), account.getId(), sizing.reason());
			return;
		}

		FuturesLiquidationService.Assessment liq = liquidationService.assess(
				signal.getSide(), referencePrice, signal.getStopLoss(), leverage);
		if (!liq.safe()) {
			log.info("[FutEngine] liquidation rejected signal={} reason={}", signal.getId(), liq.reason());
			return;
		}

		// Configure exchange (idempotent — mock ignores, testnet accepts / returns known-error).
		try {
			adapter.setLeverage(account.getCredential(), signal.getSymbol(), leverage);
			adapter.setMarginMode(account.getCredential(), signal.getSymbol(), account.getMarginMode());
		} catch (Exception ex) {
			log.warn("[FutEngine] failed to configure leverage/margin — {}", ex.getMessage());
			return;
		}

		PositionSide positionSide = signal.getSide();
		PositionSide exchangeSide = positionSide; // BUY for LONG, SELL for SHORT (mapped in adapter)

		String clientOrderId = idGen.forSignal(account.getUser().getId(), signal.getId(),
				signal.getSymbol(), positionSide, FuturesOrderPurpose.ENTRY);

		FuturesOrder entry = executionService.createIntent(
				account, signal.getId(), null, clientOrderId, signal.getSymbol(),
				exchangeSide, positionSide, FuturesOrderType.MARKET,
				FuturesOrderPurpose.ENTRY, false, leverage,
				sizing.quantity(), referencePrice, null);
		FuturesOrder submitted = executionService.submit(entry);
		if (submitted.getStatus() != FuturesOrderStatus.FILLED
				&& submitted.getStatus() != FuturesOrderStatus.PARTIALLY_FILLED) {
			return;
		}

		FuturesPosition position = openPositionRow(account, signal, submitted,
				sizing, liq.liquidationPrice(), leverage);
		placeProtectiveStop(account, signal, rules, submitted, position, leverage);
	}

	@Transactional
	protected FuturesPosition openPositionRow(FuturesTradingAccount account, Signal signal,
			FuturesOrder entryOrder, FuturesTradingSizingService.Sizing sizing,
			BigDecimal liquidationPrice, int leverage) {
		FuturesPosition pos = new FuturesPosition();
		pos.setAccount(account);
		pos.setSignalId(signal.getId());
		pos.setEntryOrderId(entryOrder.getId());
		pos.setSymbol(signal.getSymbol());
		pos.setPositionSide(signal.getSide());
		pos.setMarginMode(account.getMarginMode());
		pos.setLeverage(leverage);
		pos.setQuantity(entryOrder.getExecutedQuantity());
		pos.setEntryPrice(entryOrder.getAvgFillPrice() != null
				? entryOrder.getAvgFillPrice() : signal.getEntryPrice());
		pos.setStopLoss(signal.getStopLoss());
		pos.setTakeProfit(signal.getTargetPrice());
		pos.setInitialMargin(sizing.initialMargin());
		pos.setLiquidationPrice(liquidationPrice);
		pos.setStatus(FuturesPositionStatus.OPEN);
		pos.setProtectionStatus(FuturesProtectionStatus.PENDING);
		pos.setOpenedAt(Instant.now());
		pos.setTradingFees(entryOrder.getFees() == null ? BigDecimal.ZERO : entryOrder.getFees());
		FuturesPosition saved = positionRepository.save(pos);
		writePositionEvent(saved.getId(), FuturesLifecycleEventType.POSITION_OPENED,
				"qty=" + entryOrder.getExecutedQuantity() + " lev=" + leverage);
		return saved;
	}

	private void placeProtectiveStop(FuturesTradingAccount account, Signal signal,
			SymbolRules rules, FuturesOrder entry, FuturesPosition position, int leverage) {
		BigDecimal qty = entry.getExecutedQuantity();
		if (qty == null || qty.signum() <= 0) {
			markProtection(position.getId(), FuturesProtectionStatus.PROTECTION_FAILED,
					"entry has no executed quantity");
			return;
		}
		BigDecimal stop = rules.normalizePrice(signal.getStopLoss());

		PositionSide protectiveExchangeSide =
				signal.getSide() == PositionSide.LONG ? PositionSide.SHORT : PositionSide.LONG;

		String clientOrderId = idGen.forSignal(account.getUser().getId(), signal.getId(),
				signal.getSymbol(), signal.getSide(), FuturesOrderPurpose.STOP_LOSS);

		FuturesOrder intent = executionService.createIntent(
				account, signal.getId(), entry.getId(), clientOrderId,
				signal.getSymbol(), protectiveExchangeSide, signal.getSide(),
				FuturesOrderType.STOP_MARKET, FuturesOrderPurpose.STOP_LOSS,
				true, leverage, qty, null, stop);

		FuturesOrder submitted;
		try {
			submitted = executionService.submit(intent);
		} catch (Exception ex) {
			markProtection(position.getId(), FuturesProtectionStatus.PROTECTION_FAILED,
					"submit failed: " + ex.getMessage());
			log.error("[FutEngine] SL PLACEMENT FAILED position={} — UNPROTECTED", position.getId());
			return;
		}

		boolean live = switch (submitted.getStatus()) {
			case ACKNOWLEDGED, SUBMITTED, PARTIALLY_FILLED, FILLED -> true;
			default -> false;
		};
		markProtection(position.getId(),
				live ? FuturesProtectionStatus.PROTECTED : FuturesProtectionStatus.PROTECTION_FAILED,
				live ? "protected qty=" + qty : "protection failed status=" + submitted.getStatus());
		if (live) {
			// Store SL order id on the position for later cancellation on manual close.
			positionRepository.findByIdForUpdate(position.getId()).ifPresent(p -> {
				p.setStopOrderId(submitted.getId());
				positionRepository.save(p);
			});
		}
	}

	@Transactional
	protected void markProtection(UUID positionId, FuturesProtectionStatus status, String note) {
		positionRepository.findByIdForUpdate(positionId).ifPresent(p -> {
			p.setProtectionStatus(status);
			positionRepository.save(p);
		});
		writePositionEvent(positionId,
				status == FuturesProtectionStatus.PROTECTED
						? FuturesLifecycleEventType.PROTECTION_PLACED
						: FuturesLifecycleEventType.PROTECTION_FAILED,
				note);
	}

	private void writePositionEvent(UUID positionId, FuturesLifecycleEventType type, String message) {
		FuturesOrderLifecycleEvent event = new FuturesOrderLifecycleEvent();
		event.setPositionId(positionId);
		event.setEventType(type);
		event.setMessage(message);
		lifecycleRepository.save(event);
	}

	private BigDecimal referencePrice(Signal signal) {
		return marketBook.futuresTickers().get(signal.getSymbol())
				.map(t -> t.price())
				.orElseGet(signal::getEntryPrice);
	}

	// Suppress unused-list warning kept for future symmetry.
	@SuppressWarnings("unused")
	private static List<FuturesOrderPurpose> reserved() { return List.of(); }
}
