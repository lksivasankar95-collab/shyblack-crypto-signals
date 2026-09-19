package com.shyblack.cryptosignals.service.live;

import com.shyblack.cryptosignals.entity.LiveTradingAccount;
import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.UserSettings;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.LiveOrderStatus;
import com.shyblack.cryptosignals.entity.enums.LiveTradingRiskReason;
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
 */
@Service
@RequiredArgsConstructor
public class LiveTradingRiskService {

	private final UserSettingsRepository userSettingsRepository;
	private final LiveOrderRepository liveOrderRepository;

	public LiveTradingRiskReason check(User user, LiveTradingAccount account, Signal signal) {
		if (account == null) return LiveTradingRiskReason.ACCOUNT_NOT_ACTIVATED;
		if (!account.isEnabled()) return LiveTradingRiskReason.TRADING_DISABLED;
		if (account.isKillSwitchActive()) return LiveTradingRiskReason.KILL_SWITCH_ACTIVE;
		if (account.getConnectionStatus() != ExchangeConnectionStatus.CONNECTED) {
			return LiveTradingRiskReason.ACCOUNT_DISCONNECTED;
		}
		if (signal == null || signal.getStopLoss() == null || signal.getStopLoss().signum() <= 0) {
			return LiveTradingRiskReason.INVALID_STOP_LOSS;
		}
		UserSettings settings = userSettingsRepository.findByUser_Id(user.getId()).orElse(null);
		if (settings == null || !settings.isLiveTradingAllowed()) {
			return LiveTradingRiskReason.TRADING_DISABLED;
		}
		List<com.shyblack.cryptosignals.entity.LiveOrder> active = liveOrderRepository
				.findByAccountAndStatusInOrderByCreatedAtDesc(account, List.of(
						LiveOrderStatus.CREATED, LiveOrderStatus.SUBMITTING, LiveOrderStatus.SUBMITTED,
						LiveOrderStatus.ACKNOWLEDGED, LiveOrderStatus.PARTIALLY_FILLED));
		if (active.size() >= account.getMaxActivePositions()) {
			return LiveTradingRiskReason.MAX_POSITIONS_REACHED;
		}
		if (dailyLossExceeded(account)) return LiveTradingRiskReason.DAILY_LOSS_LIMIT;
		return LiveTradingRiskReason.OK;
	}

	private boolean dailyLossExceeded(LiveTradingAccount account) {
		BigDecimal limitPct = account.getDailyLossLimitPct();
		BigDecimal startEquity = account.getSessionStartEquity();
		if (limitPct == null || limitPct.signum() <= 0 || startEquity == null || startEquity.signum() <= 0) {
			return false;
		}
		if (account.getSessionDate() == null || !account.getSessionDate()
				.equals(LocalDate.now(ZoneOffset.UTC))) {
			return false; // session hasn't been rolled yet — first trade of the day gets through
		}
		BigDecimal current = account.getCachedTotalBalance() == null ? startEquity
				: account.getCachedTotalBalance();
		BigDecimal drawdown = startEquity.subtract(current);
		BigDecimal maxLoss = startEquity.multiply(limitPct).movePointLeft(2);
		return drawdown.compareTo(maxLoss) >= 0;
	}
}
