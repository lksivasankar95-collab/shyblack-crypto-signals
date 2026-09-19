package com.shyblack.cryptosignals.service.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LiveTradingRiskServiceTest {

	private UserSettingsRepository userSettingsRepository;
	private LiveOrderRepository liveOrderRepository;
	private LiveTradingRiskService risk;

	private User user;
	private LiveTradingAccount account;

	@BeforeEach
	void setUp() {
		userSettingsRepository = mock(UserSettingsRepository.class);
		liveOrderRepository = mock(LiveOrderRepository.class);
		risk = new LiveTradingRiskService(userSettingsRepository, liveOrderRepository);

		user = new User();
		user.setId(UUID.randomUUID());

		account = new LiveTradingAccount();
		account.setUser(user);
		account.setEnabled(true);
		account.setKillSwitchActive(false);
		account.setConnectionStatus(ExchangeConnectionStatus.CONNECTED);
		account.setMaxActivePositions(3);

		UserSettings settings = new UserSettings();
		settings.setLiveTradingAllowed(true);
		when(userSettingsRepository.findByUser_Id(user.getId())).thenReturn(Optional.of(settings));

		// No open entry for the symbol by default.
		when(liveOrderRepository.findByAccountAndSymbolAndPurposeAndStatusIn(
				any(), anyString(), eq(LiveOrderPurpose.ENTRY), any())).thenReturn(List.of());
		// No active orders.
		when(liveOrderRepository.findByAccountAndStatusInOrderByCreatedAtDesc(any(), any()))
				.thenReturn(List.of());
	}

	@Test
	void spotLongSignal_isAccepted() {
		assertThat(risk.check(user, account, signal(TradingMode.SPOT, PositionSide.LONG)))
				.isEqualTo(LiveTradingRiskReason.OK);
	}

	@Test
	void futuresSignal_isRejectedWith_UNSUPPORTED_TRADING_MODE() {
		assertThat(risk.check(user, account, signal(TradingMode.FUTURES, PositionSide.LONG)))
				.isEqualTo(LiveTradingRiskReason.UNSUPPORTED_TRADING_MODE);
	}

	@Test
	void optionsSignal_isRejectedWith_UNSUPPORTED_TRADING_MODE() {
		assertThat(risk.check(user, account, signal(TradingMode.OPTIONS, PositionSide.LONG)))
				.isEqualTo(LiveTradingRiskReason.UNSUPPORTED_TRADING_MODE);
	}

	@Test
	void shortSignal_onSpot_isRejectedWith_UNSUPPORTED_SIDE() {
		assertThat(risk.check(user, account, signal(TradingMode.SPOT, PositionSide.SHORT)))
				.isEqualTo(LiveTradingRiskReason.UNSUPPORTED_SIDE);
	}

	@Test
	void existingOpenEntry_isRejectedWith_EXISTING_POSITION() {
		when(liveOrderRepository.findByAccountAndSymbolAndPurposeAndStatusIn(
				any(), eq("BTCUSDT"), eq(LiveOrderPurpose.ENTRY), any()))
				.thenReturn(List.of(new LiveOrder()));
		assertThat(risk.check(user, account, signal(TradingMode.SPOT, PositionSide.LONG)))
				.isEqualTo(LiveTradingRiskReason.EXISTING_POSITION);
	}

	@Test
	void disabledAccount_TRADING_DISABLED() {
		account.setEnabled(false);
		assertThat(risk.check(user, account, signal(TradingMode.SPOT, PositionSide.LONG)))
				.isEqualTo(LiveTradingRiskReason.TRADING_DISABLED);
	}

	@Test
	void killSwitch_KILL_SWITCH_ACTIVE() {
		account.setKillSwitchActive(true);
		assertThat(risk.check(user, account, signal(TradingMode.SPOT, PositionSide.LONG)))
				.isEqualTo(LiveTradingRiskReason.KILL_SWITCH_ACTIVE);
	}

	@Test
	void disconnected_ACCOUNT_DISCONNECTED() {
		account.setConnectionStatus(ExchangeConnectionStatus.FAILED);
		assertThat(risk.check(user, account, signal(TradingMode.SPOT, PositionSide.LONG)))
				.isEqualTo(LiveTradingRiskReason.ACCOUNT_DISCONNECTED);
	}

	@Test
	void missingStopLoss_INVALID_STOP_LOSS() {
		Signal s = signal(TradingMode.SPOT, PositionSide.LONG);
		s.setStopLoss(null);
		assertThat(risk.check(user, account, s))
				.isEqualTo(LiveTradingRiskReason.INVALID_STOP_LOSS);
	}

	@Test
	void nullSignal_INVALID_STOP_LOSS() {
		assertThat(risk.check(user, account, null))
				.isEqualTo(LiveTradingRiskReason.INVALID_STOP_LOSS);
	}

	@Test
	void hasOpenEntry_returnsTrueWhenActiveEntryExists() {
		when(liveOrderRepository.findByAccountAndSymbolAndPurposeAndStatusIn(
				any(), eq("BTCUSDT"), eq(LiveOrderPurpose.ENTRY), any()))
				.thenReturn(List.of(new LiveOrder()));
		assertThat(risk.hasOpenEntry(account, "BTCUSDT")).isTrue();
	}

	private Signal signal(TradingMode mode, PositionSide side) {
		Signal s = new Signal();
		s.setSymbol("BTCUSDT");
		s.setTradingMode(mode);
		s.setSide(side);
		s.setEntryPrice(new BigDecimal("100"));
		s.setStopLoss(new BigDecimal("95"));
		s.setSuggestedRiskPercent(new BigDecimal("1.00"));
		return s;
	}
}
