package com.shyblack.cryptosignals.service.futures;

import com.shyblack.cryptosignals.config.FuturesTradingProperties;
import com.shyblack.cryptosignals.entity.FuturesOrder;
import com.shyblack.cryptosignals.entity.FuturesPosition;
import com.shyblack.cryptosignals.entity.FuturesTradingAccount;
import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.UserSettings;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.FuturesMarginMode;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderPurpose;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderStatus;
import com.shyblack.cryptosignals.entity.enums.FuturesPositionMode;
import com.shyblack.cryptosignals.entity.enums.FuturesPositionStatus;
import com.shyblack.cryptosignals.entity.enums.FuturesRiskReason;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.repository.FuturesOrderRepository;
import com.shyblack.cryptosignals.repository.FuturesPositionRepository;
import com.shyblack.cryptosignals.repository.UserSettingsRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Futures pre-order risk gate. Independent from SPOT — every non-OK reason
 * is Futures-specific and covered by {@code FuturesRiskServiceTest}.
 */
@Service
@RequiredArgsConstructor
public class FuturesRiskService {

	private final UserSettingsRepository userSettingsRepository;
	private final FuturesOrderRepository futuresOrderRepository;
	private final FuturesPositionRepository futuresPositionRepository;
	private final FuturesTradingProperties props;

	public FuturesRiskReason check(User user, FuturesTradingAccount account, Signal signal) {
		if (account == null) return FuturesRiskReason.ACCOUNT_NOT_ACTIVE;
		if (!account.isEnabled()) return FuturesRiskReason.FUTURES_DISABLED;
		if (!account.isAcknowledged()) return FuturesRiskReason.ACKNOWLEDGEMENT_REQUIRED;
		if (account.isKillSwitchActive()) return FuturesRiskReason.KILL_SWITCH_ENABLED;
		if (account.getConnectionStatus() != ExchangeConnectionStatus.CONNECTED) {
			return FuturesRiskReason.ACCOUNT_NOT_ACTIVE;
		}
		if (signal == null) return FuturesRiskReason.INVALID_SIGNAL;
		if (signal.getTradingMode() != TradingMode.FUTURES) return FuturesRiskReason.INVALID_SIGNAL;
		if (signal.getSide() != PositionSide.LONG && signal.getSide() != PositionSide.SHORT) {
			return FuturesRiskReason.INVALID_SIGNAL;
		}
		if (signal.getStopLoss() == null || signal.getStopLoss().signum() <= 0) {
			return FuturesRiskReason.STOP_LOSS_REQUIRED;
		}
		if (account.getMarginMode() == FuturesMarginMode.CROSS) {
			return FuturesRiskReason.UNSUPPORTED_MARGIN_MODE;
		}
		if (account.getPositionMode() != FuturesPositionMode.ONE_WAY) {
			return FuturesRiskReason.UNSUPPORTED_POSITION_MODE;
		}

		UserSettings settings = userSettingsRepository.findByUser_Id(user.getId()).orElse(null);
		if (settings == null || !settings.isLiveTradingAllowed()) {
			return FuturesRiskReason.FUTURES_DISABLED;
		}

		// Refuse to stack a same-side position on the same symbol.
		Optional<FuturesPosition> existingSameSide = futuresPositionRepository
				.findByAccountAndSymbolAndPositionSideAndStatus(
						account, signal.getSymbol(), signal.getSide(), FuturesPositionStatus.OPEN);
		if (existingSameSide.isPresent()) return FuturesRiskReason.POSITION_ALREADY_EXISTS;

		// Refuse to open a hedge (opposite side) unless HEDGE mode is enabled — never in this drop.
		Optional<FuturesPosition> oppositeSide = futuresPositionRepository
				.findByAccountAndSymbolAndPositionSideAndStatus(
						account, signal.getSymbol(),
						signal.getSide() == PositionSide.LONG ? PositionSide.SHORT : PositionSide.LONG,
						FuturesPositionStatus.OPEN);
		if (oppositeSide.isPresent()) return FuturesRiskReason.POSITION_ALREADY_EXISTS;

		List<FuturesOrder> active = futuresOrderRepository
				.findByAccountAndStatusInOrderByCreatedAtDesc(account, List.of(
						FuturesOrderStatus.CREATED, FuturesOrderStatus.SUBMITTING,
						FuturesOrderStatus.SUBMITTED, FuturesOrderStatus.ACKNOWLEDGED,
						FuturesOrderStatus.PARTIALLY_FILLED))
				.stream()
				.filter(o -> o.getPurpose() == FuturesOrderPurpose.ENTRY)
				.toList();
		if (active.size() >= account.getMaxActivePositions()) {
			return FuturesRiskReason.MAX_POSITIONS_REACHED;
		}
		if (dailyLossExceeded(account)) return FuturesRiskReason.DAILY_LOSS_LIMIT;
		return FuturesRiskReason.OK;
	}

	/** Separate leverage check so callers can validate a specific requested value. */
	public FuturesRiskReason checkLeverage(FuturesTradingAccount account, int requested) {
		if (requested <= 0) return FuturesRiskReason.LEVERAGE_EXCEEDED;
		int cap = Math.min(account.getMaxLeverage(), props.maxLeverage());
		if (requested > cap) return FuturesRiskReason.LEVERAGE_EXCEEDED;
		return FuturesRiskReason.OK;
	}

	private boolean dailyLossExceeded(FuturesTradingAccount account) {
		BigDecimal limitPct = account.getDailyLossLimitPct();
		BigDecimal startEquity = account.getSessionStartEquity();
		if (limitPct == null || limitPct.signum() <= 0
				|| startEquity == null || startEquity.signum() <= 0) return false;
		if (account.getSessionDate() == null
				|| !account.getSessionDate().equals(LocalDate.now(ZoneOffset.UTC))) return false;
		BigDecimal loss = account.getRealizedPnlToday() == null
				? BigDecimal.ZERO : account.getRealizedPnlToday().abs();
		if (account.getRealizedPnlToday() != null && account.getRealizedPnlToday().signum() >= 0) return false;
		BigDecimal maxLoss = startEquity.multiply(limitPct).movePointLeft(2);
		return loss.compareTo(maxLoss) >= 0;
	}
}
