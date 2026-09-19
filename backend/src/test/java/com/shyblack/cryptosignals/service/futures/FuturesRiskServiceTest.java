package com.shyblack.cryptosignals.service.futures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shyblack.cryptosignals.config.FuturesTradingProperties;
import com.shyblack.cryptosignals.entity.FuturesPosition;
import com.shyblack.cryptosignals.entity.FuturesTradingAccount;
import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.UserSettings;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.FuturesMarginMode;
import com.shyblack.cryptosignals.entity.enums.FuturesPositionMode;
import com.shyblack.cryptosignals.entity.enums.FuturesPositionStatus;
import com.shyblack.cryptosignals.entity.enums.FuturesRiskReason;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.repository.FuturesOrderRepository;
import com.shyblack.cryptosignals.repository.FuturesPositionRepository;
import com.shyblack.cryptosignals.repository.UserSettingsRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FuturesRiskServiceTest {

	private UserSettingsRepository userSettingsRepository;
	private FuturesOrderRepository orderRepository;
	private FuturesPositionRepository positionRepository;
	private FuturesRiskService risk;

	private User user;
	private FuturesTradingAccount account;

	@BeforeEach
	void setUp() {
		userSettingsRepository = mock(UserSettingsRepository.class);
		orderRepository = mock(FuturesOrderRepository.class);
		positionRepository = mock(FuturesPositionRepository.class);

		FuturesTradingProperties props = new FuturesTradingProperties(
				FuturesTradingProperties.Mode.MOCK,
				"https://testnet", "wss://testnet", 5000, 3,
				FuturesMarginMode.ISOLATED, FuturesPositionMode.ONE_WAY,
				new BigDecimal("200.00"), 2, new BigDecimal("5.00"),
				new BigDecimal("0.30"), new BigDecimal("15.00"), false);
		risk = new FuturesRiskService(userSettingsRepository, orderRepository, positionRepository, props);

		user = new User();
		user.setId(UUID.randomUUID());

		account = new FuturesTradingAccount();
		account.setUser(user);
		account.setEnabled(true);
		account.setAcknowledged(true);
		account.setKillSwitchActive(false);
		account.setConnectionStatus(ExchangeConnectionStatus.CONNECTED);
		account.setMarginMode(FuturesMarginMode.ISOLATED);
		account.setPositionMode(FuturesPositionMode.ONE_WAY);
		account.setMaxLeverage(3);
		account.setMaxActivePositions(2);

		UserSettings settings = new UserSettings();
		settings.setLiveTradingAllowed(true);
		when(userSettingsRepository.findByUser_Id(user.getId())).thenReturn(Optional.of(settings));
		when(positionRepository.findByAccountAndSymbolAndPositionSideAndStatus(any(), anyString(), any(), any()))
				.thenReturn(Optional.empty());
		when(orderRepository.findByAccountAndStatusInOrderByCreatedAtDesc(any(), any()))
				.thenReturn(List.of());
	}

	@Test
	void futuresLongSignal_isAccepted() {
		assertThat(risk.check(user, account, futuresSignal(PositionSide.LONG)))
				.isEqualTo(FuturesRiskReason.OK);
	}

	@Test
	void futuresShortSignal_isAccepted() {
		assertThat(risk.check(user, account, futuresSignal(PositionSide.SHORT)))
				.isEqualTo(FuturesRiskReason.OK);
	}

	@Test
	void spotSignal_isRejected_INVALID_SIGNAL() {
		Signal s = futuresSignal(PositionSide.LONG);
		s.setTradingMode(TradingMode.SPOT);
		assertThat(risk.check(user, account, s)).isEqualTo(FuturesRiskReason.INVALID_SIGNAL);
	}

	@Test
	void disabledAccount_FUTURES_DISABLED() {
		account.setEnabled(false);
		assertThat(risk.check(user, account, futuresSignal(PositionSide.LONG)))
				.isEqualTo(FuturesRiskReason.FUTURES_DISABLED);
	}

	@Test
	void missingAck_ACKNOWLEDGEMENT_REQUIRED() {
		account.setAcknowledged(false);
		assertThat(risk.check(user, account, futuresSignal(PositionSide.LONG)))
				.isEqualTo(FuturesRiskReason.ACKNOWLEDGEMENT_REQUIRED);
	}

	@Test
	void killSwitch_KILL_SWITCH_ENABLED() {
		account.setKillSwitchActive(true);
		assertThat(risk.check(user, account, futuresSignal(PositionSide.LONG)))
				.isEqualTo(FuturesRiskReason.KILL_SWITCH_ENABLED);
	}

	@Test
	void crossMargin_UNSUPPORTED_MARGIN_MODE() {
		account.setMarginMode(FuturesMarginMode.CROSS);
		assertThat(risk.check(user, account, futuresSignal(PositionSide.LONG)))
				.isEqualTo(FuturesRiskReason.UNSUPPORTED_MARGIN_MODE);
	}

	@Test
	void hedgeMode_UNSUPPORTED_POSITION_MODE() {
		account.setPositionMode(FuturesPositionMode.HEDGE);
		assertThat(risk.check(user, account, futuresSignal(PositionSide.LONG)))
				.isEqualTo(FuturesRiskReason.UNSUPPORTED_POSITION_MODE);
	}

	@Test
	void openSameSidePosition_POSITION_ALREADY_EXISTS() {
		when(positionRepository.findByAccountAndSymbolAndPositionSideAndStatus(
				any(), eq("BTCUSDT"), eq(PositionSide.LONG), eq(FuturesPositionStatus.OPEN)))
				.thenReturn(Optional.of(new FuturesPosition()));
		assertThat(risk.check(user, account, futuresSignal(PositionSide.LONG)))
				.isEqualTo(FuturesRiskReason.POSITION_ALREADY_EXISTS);
	}

	@Test
	void oppositeSidePosition_alsoRejected() {
		when(positionRepository.findByAccountAndSymbolAndPositionSideAndStatus(
				any(), eq("BTCUSDT"), eq(PositionSide.SHORT), eq(FuturesPositionStatus.OPEN)))
				.thenReturn(Optional.of(new FuturesPosition()));
		assertThat(risk.check(user, account, futuresSignal(PositionSide.LONG)))
				.isEqualTo(FuturesRiskReason.POSITION_ALREADY_EXISTS);
	}

	@Test
	void missingStopLoss_STOP_LOSS_REQUIRED() {
		Signal s = futuresSignal(PositionSide.LONG);
		s.setStopLoss(null);
		assertThat(risk.check(user, account, s)).isEqualTo(FuturesRiskReason.STOP_LOSS_REQUIRED);
	}

	@Test
	void leverage_beyondCap_isRejected() {
		assertThat(risk.checkLeverage(account, 10)).isEqualTo(FuturesRiskReason.LEVERAGE_EXCEEDED);
		assertThat(risk.checkLeverage(account, 0)).isEqualTo(FuturesRiskReason.LEVERAGE_EXCEEDED);
		assertThat(risk.checkLeverage(account, 3)).isEqualTo(FuturesRiskReason.OK);
	}

	private Signal futuresSignal(PositionSide side) {
		Signal s = new Signal();
		s.setSymbol("BTCUSDT");
		s.setTradingMode(TradingMode.FUTURES);
		s.setSide(side);
		s.setEntryPrice(new BigDecimal("50000"));
		s.setStopLoss(side == PositionSide.LONG
				? new BigDecimal("49000") : new BigDecimal("51000"));
		s.setSuggestedRiskPercent(new BigDecimal("1.00"));
		return s;
	}
}
