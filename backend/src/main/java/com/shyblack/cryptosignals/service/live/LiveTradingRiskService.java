package com.shyblack.cryptosignals.service.live;

import com.shyblack.cryptosignals.entity.LiveOrder;
import com.shyblack.cryptosignals.entity.LiveTradingAccount;
import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.UserSettings;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.LiveOrderPurpose;
import com.shyblack.cryptosignals.entity.enums.LiveOrderStatus;
import com.shyblack.cryptosignals.entity.enums.LiveTradingRiskReason;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.repository.LiveOrderRepository;
import com.shyblack.cryptosignals.repository.UserSettingsRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Pre-order risk gate. Every {@code check(...)} evaluates a stack of
 * deterministic rules; the first failure short-circuits with an explicit
 * reason so callers can audit exactly why a signal was blocked.
 *
 * This implementation is SPOT-only. FUTURES / OPTIONS signals are refused at
 * the top of the stack, and SPOT signals with side != LONG are refused
 * before they can hit the exchange adapter — SPOT has no naked short.
 */
@Service
@RequiredArgsConstructor
public class LiveTradingRiskService {

	private static final List<LiveOrderStatus> ACTIVE_STATUSES = List.of(
			LiveOrderStatus.CREATED, LiveOrderStatus.SUBMITTING, LiveOrderStatus.SUBMITTED,
			LiveOrderStatus.ACKNOWLEDGED, LiveOrderStatus.PARTIALLY_FILLED,
			LiveOrderStatus.FILLED); // FILLED entries count as open positions until closed

	private final UserSettingsRepository userSettingsRepository;
	private final LiveOrderRepository liveOrderRepository;

	public LiveTradingRiskReason check(User user, LiveTradingAccount account, Signal signal) {
		if (account == null) return LiveTradingRiskReason.ACCOUNT_NOT_ACTIVATED;
		if (!account.isEnabled()) return LiveTradingRiskReason.TRADING_DISABLED;
		if (account.isKillSwitchActive()) return LiveTradingRiskReason.KILL_SWITCH_ACTIVE;
		if (account.getConnectionStatus() != ExchangeConnectionStatus.CONNECTED) {
			return LiveTradingRiskReason.ACCOUNT_DISCONNECTED;
		}
		if (signal == null) return LiveTradingRiskReason.INVALID_STOP_LOSS;
		// SPOT ONLY — refuse anything else before we can build an order.
		if (signal.getTradingMode() != TradingMode.SPOT) {
			return LiveTradingRiskReason.UNSUPPORTED_TRADING_MODE;
		}
		// SPOT does not permit naked shorts. Entries must be LONG.
		if (signal.getSide() != PositionSide.LONG) {
			return LiveTradingRiskReason.UNSUPPORTED_SIDE;
		}
		if (signal.getStopLoss() == null || signal.getStopLoss().signum() <= 0) {
			return LiveTradingRiskReason.INVALID_STOP_LOSS;
		}
		UserSettings settings = userSettingsRepository.findByUser_Id(user.getId()).orElse(null);
		if (settings == null || !settings.isLiveTradingAllowed()) {
			return LiveTradingRiskReason.TRADING_DISABLED;
		}

		// Refuse to stack another BUY on top of an existing open entry for the same symbol.
		if (hasOpenEntry(account, signal.getSymbol())) {
			return LiveTradingRiskReason.EXISTING_POSITION;
		}

		List<LiveOrder> active = liveOrderRepository
				.findByAccountAndStatusInOrderByCreatedAtDesc(account, List.of(
						LiveOrderStatus.CREATED, LiveOrderStatus.SUBMITTING, LiveOrderStatus.SUBMITTED,
						LiveOrderStatus.ACKNOWLEDGED, LiveOrderStatus.PARTIALLY_FILLED));
		if (active.size() >= account.getMaxActivePositions()) {
			return LiveTradingRiskReason.MAX_POSITIONS_REACHED;
		}
		if (dailyLossExceeded(account)) return LiveTradingRiskReason.DAILY_LOSS_LIMIT;
		return LiveTradingRiskReason.OK;
	}

	public boolean hasOpenEntry(LiveTradingAccount account, String symbol) {
		return !liveOrderRepository.findByAccountAndSymbolAndPurposeAndStatusIn(
				account, symbol, LiveOrderPurpose.ENTRY, ACTIVE_STATUSES).isEmpty();
	}

	private boolean dailyLossExceeded(LiveTradingAccount account) {
		BigDecimal limitPct = account.getDailyLossLimitPct();
		BigDecimal startEquity = account.getSessionStartEquity();
		if (limitPct == null || limitPct.signum() <= 0 || startEquity == null || startEquity.signum() <= 0) {
			return false;
		}
		if (account.getSessionDate() == null || !account.getSessionDate()
				.equals(LocalDate.now(ZoneOffset.UTC))) {
			return false;
		}
		BigDecimal current = account.getCachedTotalBalance() == null ? startEquity
				: account.getCachedTotalBalance();
		BigDecimal drawdown = startEquity.subtract(current);
		BigDecimal maxLoss = startEquity.multiply(limitPct).movePointLeft(2);
		return drawdown.compareTo(maxLoss) >= 0;
	}
}
