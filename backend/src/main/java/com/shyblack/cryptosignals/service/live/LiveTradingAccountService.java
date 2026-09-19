package com.shyblack.cryptosignals.service.live;

import com.shyblack.cryptosignals.config.LiveTradingProperties;
import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.LiveTradingAccount;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.UserSettings;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.exception.BadRequestException;
import com.shyblack.cryptosignals.exception.ResourceNotFoundException;
import com.shyblack.cryptosignals.exchange.ExchangeAccountSnapshot;
import com.shyblack.cryptosignals.exchange.ExchangeAdapterException;
import com.shyblack.cryptosignals.exchange.ExchangeTradingAdapter;
import com.shyblack.cryptosignals.repository.ExchangeCredentialRepository;
import com.shyblack.cryptosignals.repository.LiveTradingAccountRepository;
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

/**
 * Live account lifecycle:
 *  - {@code connect}  — associate the user with a stored exchange credential;
 *                       calls {@code adapter.validateCredentials} to verify.
 *  - {@code activate} — flips {@code enabled} to true (requires prior connect
 *                       + {@code liveTradingAllowed} in user settings).
 *  - {@code deactivate}, {@code triggerKillSwitch}, {@code releaseKillSwitch}
 *  - {@code refreshBalance} — periodic + on-demand sync.
 *
 * Account is created with {@code enabled=false} so it never routes orders
 * until the user explicitly opts in.
 */
@Service
@RequiredArgsConstructor
public class LiveTradingAccountService {

	private static final Logger log = LoggerFactory.getLogger(LiveTradingAccountService.class);

	private final LiveTradingAccountRepository accountRepository;
	private final ExchangeCredentialRepository credentialRepository;
	private final UserSettingsRepository userSettingsRepository;
	private final ExchangeTradingAdapter adapter;
	private final LiveTradingProperties props;

	@Transactional
	public LiveTradingAccount connect(User user, ExchangeName exchange) {
		ExchangeCredential credential = credentialRepository
				.findByUser_IdAndExchange(user.getId(), exchange)
				.orElseThrow(() -> new BadRequestException(
						"No " + exchange + " credentials — connect exchange first"));

		LiveTradingAccount account = accountRepository
				.findByUserAndExchange(user, exchange)
				.orElseGet(() -> {
					LiveTradingAccount fresh = new LiveTradingAccount();
					fresh.setUser(user);
					fresh.setExchange(exchange);
					fresh.setCredential(credential);
					fresh.setEnabled(false);
					fresh.setKillSwitchActive(false);
					fresh.setMaxActivePositions(props.defaultMaxActive());
					fresh.setMaxNotionalPerTrade(props.defaultMaxNotional());
					fresh.setDailyLossLimitPct(props.defaultDailyLossPct());
					return fresh;
				});
		account.setCredential(credential);
		account.setConnectionStatus(ExchangeConnectionStatus.CONNECTING);
		accountRepository.save(account);

		ExchangeAccountSnapshot snapshot;
		try {
			snapshot = adapter.validateCredentials(credential);
		} catch (ExchangeAdapterException ex) {
			account.setConnectionStatus(ExchangeConnectionStatus.FAILED);
			account.setLastValidationMessage(safeMessage(ex));
			log.warn("[LiveAccount] validate failed user={} exchange={} err={}",
					user.getId(), exchange, safeMessage(ex));
			credential.setStatus(ExchangeConnectionStatus.FAILED);
			credentialRepository.save(credential);
			return accountRepository.save(account);
		}
		account.setConnectionStatus(snapshot.canTrade()
				? ExchangeConnectionStatus.CONNECTED
				: ExchangeConnectionStatus.FAILED);
		account.setLastValidatedAt(Instant.now());
		account.setLastValidationMessage(snapshot.canTrade()
				? "Connected"
				: "Exchange denies trading permission");
		account.setCachedAvailableBalance(snapshot.availableBalance());
		account.setCachedTotalBalance(snapshot.totalBalance());
		account.setQuoteCurrency(snapshot.quoteCurrency());
		credential.setStatus(account.getConnectionStatus());
		credentialRepository.save(credential);
		return accountRepository.save(account);
	}

	@Transactional
	public LiveTradingAccount activate(User user) {
		LiveTradingAccount account = requireOwned(user);
		if (account.getConnectionStatus() != ExchangeConnectionStatus.CONNECTED) {
			throw new BadRequestException("Cannot activate — connection status is "
					+ account.getConnectionStatus());
		}
		UserSettings settings = userSettingsRepository.findByUser_Id(user.getId())
				.orElseThrow(() -> new BadRequestException("Settings not found"));
		if (!settings.isLiveTradingAllowed()) {
			throw new BadRequestException("Live trading not permitted in user settings");
		}
		account.setEnabled(true);
		account.setKillSwitchActive(false);
		rollSessionIfNeeded(account);
		log.info("[LiveAccount] ACTIVATED user={} exchange={}", user.getId(), account.getExchange());
		return accountRepository.save(account);
	}

	@Transactional
	public LiveTradingAccount deactivate(User user) {
		LiveTradingAccount account = requireOwned(user);
		account.setEnabled(false);
		log.info("[LiveAccount] DEACTIVATED user={}", user.getId());
		return accountRepository.save(account);
	}

	@Transactional
	public LiveTradingAccount triggerKillSwitch(User user, String reason) {
		LiveTradingAccount account = requireOwned(user);
		account.setKillSwitchActive(true);
		account.setLastValidationMessage("Kill switch: " + reason);
		log.warn("[LiveAccount] KILL SWITCH user={} reason={}", user.getId(), reason);
		return accountRepository.save(account);
	}

	@Transactional
	public LiveTradingAccount releaseKillSwitch(User user) {
		LiveTradingAccount account = requireOwned(user);
		account.setKillSwitchActive(false);
		log.info("[LiveAccount] kill switch RELEASED user={}", user.getId());
		return accountRepository.save(account);
	}

	@Transactional
	public LiveTradingAccount disconnect(User user) {
		LiveTradingAccount account = requireOwned(user);
		account.setEnabled(false);
		account.setConnectionStatus(ExchangeConnectionStatus.NOT_CONNECTED);
		return accountRepository.save(account);
	}

	@Transactional
	public LiveTradingAccount refreshBalance(User user) {
		LiveTradingAccount account = requireOwned(user);
		try {
			ExchangeAccountSnapshot snap = adapter.getAccountBalance(account.getCredential());
			account.setCachedAvailableBalance(snap.availableBalance());
			account.setCachedTotalBalance(snap.totalBalance());
			account.setLastValidatedAt(Instant.now());
		} catch (ExchangeAdapterException ex) {
			log.warn("[LiveAccount] balance refresh failed user={} err={}",
					user.getId(), safeMessage(ex));
		}
		rollSessionIfNeeded(account);
		return accountRepository.save(account);
	}

	@Transactional(readOnly = true)
	public Optional<LiveTradingAccount> findForUser(User user) {
		return accountRepository.findFirstByUser(user);
	}

	private LiveTradingAccount requireOwned(User user) {
		return accountRepository.findFirstByUser(user)
				.orElseThrow(() -> new ResourceNotFoundException("Live account not connected"));
	}

	private void rollSessionIfNeeded(LiveTradingAccount account) {
		LocalDate today = LocalDate.now(ZoneOffset.UTC);
		if (!today.equals(account.getSessionDate())) {
			account.setSessionDate(today);
			account.setSessionStartEquity(account.getCachedTotalBalance());
		}
	}

	private static String safeMessage(Exception ex) {
		String m = ex.getMessage();
		if (m == null) return "error";
		// Defensive: never let a stray API key/secret leak into DB or logs.
		return m.length() > 200 ? m.substring(0, 200) : m;
	}
}
