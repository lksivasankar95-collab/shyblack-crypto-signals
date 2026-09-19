package com.shyblack.cryptosignals.service.futures;

import com.shyblack.cryptosignals.config.FuturesTradingProperties;
import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.FuturesTradingAccount;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.UserSettings;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.FuturesMarginMode;
import com.shyblack.cryptosignals.entity.enums.FuturesPositionMode;
import com.shyblack.cryptosignals.exception.BadRequestException;
import com.shyblack.cryptosignals.exception.ResourceNotFoundException;
import com.shyblack.cryptosignals.exchange.ExchangeAdapterException;
import com.shyblack.cryptosignals.exchange.futures.FuturesAccountSnapshot;
import com.shyblack.cryptosignals.exchange.futures.FuturesExchangeAdapter;
import com.shyblack.cryptosignals.repository.ExchangeCredentialRepository;
import com.shyblack.cryptosignals.repository.FuturesTradingAccountRepository;
import com.shyblack.cryptosignals.repository.UserSettingsRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FuturesAccountService {

	private static final Logger log = LoggerFactory.getLogger(FuturesAccountService.class);

	private final FuturesTradingAccountRepository accountRepository;
	private final ExchangeCredentialRepository credentialRepository;
	private final UserSettingsRepository userSettingsRepository;
	private final FuturesExchangeAdapter adapter;
	private final FuturesTradingProperties props;

	@Transactional
	public FuturesTradingAccount connect(User user, ExchangeName exchange) {
		ExchangeCredential credential = credentialRepository
				.findByUser_IdAndExchange(user.getId(), exchange)
				.orElseThrow(() -> new BadRequestException(
						"No " + exchange + " credentials — connect exchange first"));

		FuturesTradingAccount account = accountRepository
				.findByUserAndExchange(user, exchange)
				.orElseGet(() -> {
					FuturesTradingAccount fresh = new FuturesTradingAccount();
					fresh.setUser(user);
					fresh.setExchange(exchange);
					fresh.setCredential(credential);
					fresh.setEnabled(false);
					fresh.setKillSwitchActive(false);
					fresh.setAcknowledged(false);
					fresh.setMarginMode(props.defaultMarginMode());
					fresh.setPositionMode(props.requiredPositionMode());
					fresh.setMaxLeverage(props.maxLeverage());
					fresh.setMaxActivePositions(props.defaultMaxActive());
					fresh.setMaxNotionalPerTrade(props.defaultMaxNotional());
					fresh.setDailyLossLimitPct(props.defaultDailyLossPct());
					return fresh;
				});
		account.setCredential(credential);
		account.setConnectionStatus(ExchangeConnectionStatus.CONNECTING);
		accountRepository.save(account);

		FuturesAccountSnapshot snapshot;
		try {
			snapshot = adapter.validateCredentials(credential);
		} catch (ExchangeAdapterException ex) {
			account.setConnectionStatus(ExchangeConnectionStatus.FAILED);
			account.setLastValidationMessage(safe(ex));
			log.warn("[FutAccount] validate failed user={} err={}", user.getId(), safe(ex));
			return accountRepository.save(account);
		}
		account.setConnectionStatus(snapshot.canTrade()
				? ExchangeConnectionStatus.CONNECTED : ExchangeConnectionStatus.FAILED);
		account.setLastValidatedAt(Instant.now());
		account.setLastValidationMessage(snapshot.canTrade() ? "Connected" : "Trading disabled");
		account.setMarginAsset(snapshot.marginAsset());
		account.setWalletBalance(snapshot.walletBalance());
		account.setAvailableBalance(snapshot.availableBalance());
		account.setMarginBalance(snapshot.marginBalance());
		account.setUsedMargin(snapshot.usedMargin());
		account.setMaintenanceMargin(snapshot.maintenanceMargin());
		account.setUnrealizedPnl(snapshot.unrealizedPnl());
		// Reject unsupported position mode up front.
		if (snapshot.positionMode() != FuturesPositionMode.ONE_WAY) {
			account.setEnabled(false);
			account.setLastValidationMessage("Binance account is in HEDGE mode; switch to ONE_WAY.");
		}
		return accountRepository.save(account);
	}

	@Transactional
	public FuturesTradingAccount acknowledge(User user, boolean flag) {
		FuturesTradingAccount account = require(user);
		account.setAcknowledged(flag);
		return accountRepository.save(account);
	}

	@Transactional
	public FuturesTradingAccount activate(User user) {
		FuturesTradingAccount account = require(user);
		if (account.getConnectionStatus() != ExchangeConnectionStatus.CONNECTED) {
			throw new BadRequestException("Cannot activate — connection not verified");
		}
		if (!account.isAcknowledged()) {
			throw new BadRequestException("Safety acknowledgement is required");
		}
		if (account.getPositionMode() != FuturesPositionMode.ONE_WAY) {
			throw new BadRequestException("Only ONE_WAY position mode is supported");
		}
		if (account.getMarginMode() == FuturesMarginMode.CROSS) {
			throw new BadRequestException("Cross margin is not supported");
		}
		UserSettings settings = userSettingsRepository.findByUser_Id(user.getId())
				.orElseThrow(() -> new BadRequestException("User settings missing"));
		if (!settings.isLiveTradingAllowed()) {
			throw new BadRequestException("Live trading not permitted in user settings");
		}
		account.setEnabled(true);
		account.setKillSwitchActive(false);
		rollSession(account);
		log.info("[FutAccount] ACTIVATED user={} maxLeverage={}",
				user.getId(), account.getMaxLeverage());
		return accountRepository.save(account);
	}

	@Transactional
	public FuturesTradingAccount deactivate(User user) {
		FuturesTradingAccount account = require(user);
		account.setEnabled(false);
		return accountRepository.save(account);
	}

	@Transactional
	public FuturesTradingAccount triggerKillSwitch(User user, String reason) {
		FuturesTradingAccount account = require(user);
		account.setKillSwitchActive(true);
		account.setLastValidationMessage("Kill switch: " + reason);
		log.warn("[FutAccount] KILL SWITCH user={} reason={}", user.getId(), reason);
		return accountRepository.save(account);
	}

	@Transactional
	public FuturesTradingAccount releaseKillSwitch(User user) {
		FuturesTradingAccount account = require(user);
		account.setKillSwitchActive(false);
		return accountRepository.save(account);
	}

	@Transactional
	public FuturesTradingAccount refreshBalance(User user) {
		FuturesTradingAccount account = require(user);
		try {
			FuturesAccountSnapshot snap = adapter.getAccount(account.getCredential());
			account.setWalletBalance(snap.walletBalance());
			account.setAvailableBalance(snap.availableBalance());
			account.setMarginBalance(snap.marginBalance());
			account.setUsedMargin(snap.usedMargin());
			account.setMaintenanceMargin(snap.maintenanceMargin());
			account.setUnrealizedPnl(snap.unrealizedPnl());
			account.setLastValidatedAt(Instant.now());
		} catch (ExchangeAdapterException ex) {
			log.warn("[FutAccount] balance refresh failed user={} err={}", user.getId(), safe(ex));
		}
		rollSession(account);
		return accountRepository.save(account);
	}

	public Optional<FuturesTradingAccount> findForUser(User user) {
		return accountRepository.findFirstByUser(user);
	}

	private FuturesTradingAccount require(User user) {
		return accountRepository.findFirstByUser(user)
				.orElseThrow(() -> new ResourceNotFoundException("Futures account not connected"));
	}

	private void rollSession(FuturesTradingAccount account) {
		LocalDate today = LocalDate.now(ZoneOffset.UTC);
		if (!today.equals(account.getSessionDate())) {
			account.setSessionDate(today);
			account.setSessionStartEquity(account.getWalletBalance());
			account.setRealizedPnlToday(java.math.BigDecimal.ZERO);
		}
	}

	private static String safe(Exception ex) {
		String m = ex.getMessage();
		if (m == null) return "error";
		return m.length() > 200 ? m.substring(0, 200) : m;
	}
}
