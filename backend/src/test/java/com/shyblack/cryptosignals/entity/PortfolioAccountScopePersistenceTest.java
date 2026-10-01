package com.shyblack.cryptosignals.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shyblack.cryptosignals.entity.enums.AccountAvailability;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.repository.PortfolioAccountConnectionRepository;
import com.shyblack.cryptosignals.repository.PortfolioRepository;
import com.shyblack.cryptosignals.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * Proves the Phase 1 account-scope model against a real JPA provider: that legacy rows with SQL
 * NULL scope columns still load, that every one of the eight
 * {@link AccountMode} x {@link AccountCategory} scopes is independently persistable, and that the
 * scope key keeps one row per scope per user.
 */
@SpringBootTest
@ActiveProfiles("test")
class PortfolioAccountScopePersistenceTest {

	private static final AtomicInteger EMAIL_SEQ = new AtomicInteger();

	@Autowired
	private PortfolioRepository portfolioRepository;

	@Autowired
	private PortfolioAccountConnectionRepository connectionRepository;

	@Autowired
	private UserRepository userRepository;

	private User newUser() {
		User user = new User();
		user.setEmail("scope-" + EMAIL_SEQ.incrementAndGet() + "@example.com");
		user.setFullName("Scope Tester");
		return userRepository.saveAndFlush(user);
	}

	private Portfolio paperPortfolio(User user) {
		Portfolio portfolio = new Portfolio();
		portfolio.setUser(user);
		portfolio.setName("Paper Trading");
		portfolio.setAccountType(AccountType.PAPER);
		return portfolio;
	}

	@Test
	void legacyPortfolioRowWithNullScopeColumnsLoadsUnchanged() {
		Portfolio saved = portfolioRepository.saveAndFlush(paperPortfolio(newUser()));

		Portfolio reloaded = portfolioRepository.findById(saved.getId()).orElseThrow();

		assertThat(reloaded.getId()).isEqualTo(saved.getId());
		assertThat(reloaded.getAccountType()).isEqualTo(AccountType.PAPER);
		assertThat(reloaded.getAccountCategory())
				.as("a legacy row has SQL NULL here and must stay NULL, never be backfilled")
				.isNull();
		assertThat(reloaded.getExchange())
				.as("a simulated account has no exchange and must never be defaulted to one")
				.isNull();
		assertThat(reloaded.getName()).isEqualTo("Paper Trading");
		assertThat(reloaded.getQuoteCurrency()).isEqualTo("USDT");
	}

	@Test
	void paperPortfolioBalancesAreUntouchedByAddingScopeColumns() {
		Portfolio portfolio = paperPortfolio(newUser());
		portfolio.setInitialBalance(new BigDecimal("1000.00"));
		portfolio.setTotalBalance(new BigDecimal("1000.00"));
		portfolio.setAvailableBalance(new BigDecimal("750.00"));
		portfolio.setInvested(new BigDecimal("250.00"));

		Portfolio saved = portfolioRepository.saveAndFlush(portfolio);
		Portfolio reloaded = portfolioRepository.findById(saved.getId()).orElseThrow();

		assertThat(reloaded.getInitialBalance()).isEqualByComparingTo("1000.00");
		assertThat(reloaded.getTotalBalance()).isEqualByComparingTo("1000.00");
		assertThat(reloaded.getAvailableBalance()).isEqualByComparingTo("750.00");
		assertThat(reloaded.getInvested()).isEqualByComparingTo("250.00");
		assertThat(reloaded.getRealizedPnl()).isEqualByComparingTo("0");
		assertThat(reloaded.getTotalFees()).isEqualByComparingTo("0");
	}

	@Test
	void portfolioPersistsCategoryAndExchangeWhenPresent() {
		Portfolio portfolio = paperPortfolio(newUser());
		portfolio.setAccountCategory(AccountCategory.SPOT);
		portfolio.setExchange(ExchangeName.BINANCE);

		Portfolio reloaded =
				portfolioRepository.findById(portfolioRepository.saveAndFlush(portfolio).getId())
						.orElseThrow();

		assertThat(reloaded.getAccountCategory()).isEqualTo(AccountCategory.SPOT);
		assertThat(reloaded.getExchange()).isEqualTo(ExchangeName.BINANCE);
	}

	@Test
	void existingPaperLookupStillResolvesTheSinglePaperRow() {
		User user = newUser();
		Portfolio portfolio = paperPortfolio(user);
		portfolioRepository.saveAndFlush(portfolio);

		assertThat(portfolioRepository.findFirstByUserAndAccountType(user, AccountType.PAPER))
				.as("D2: paper keeps exactly one physical portfolio row; the category split is a "
						+ "query-layer partition and must not require extra rows")
				.isPresent();
	}

	@Test
	void allEightAccountScopesAreIndependentlyPersistable() {
		User user = newUser();

		List<PortfolioAccountConnection> saved = new ArrayList<>();
		for (AccountMode mode : AccountMode.values()) {
			for (AccountCategory category : AccountCategory.values()) {
				PortfolioAccountConnection connection = new PortfolioAccountConnection();
				connection.setUser(user);
				connection.setAccountMode(mode);
				connection.setAccountCategory(category);
				connection.setExchange(mode.isLive() ? ExchangeName.BINANCE : null);
				connection.setConnectionStatus(ExchangeConnectionStatus.NOT_CONNECTED);
				connection.setAvailability(
						category == AccountCategory.OPTIONS
								? AccountAvailability.UNSUPPORTED
								: AccountAvailability.NOT_CONNECTED);
				saved.add(connectionRepository.saveAndFlush(connection));
			}
		}

		assertThat(saved).hasSize(8);
		assertThat(saved).extracting(PortfolioAccountConnection::getId).doesNotHaveDuplicates();
		assertThat(connectionRepository.findByUserAndAccountMode(user, AccountMode.PAPER))
				.as("PAPER must expose all four categories")
				.hasSize(4);
		assertThat(connectionRepository.findByUserAndAccountMode(user, AccountMode.LIVE))
				.as("LIVE must expose all four categories")
				.hasSize(4);
		assertThat(connectionRepository.findByUser(user)).hasSize(8);
	}

	@Test
	void paperScopeStoresNoExchangeAndStaysNotConnected() {
		User user = newUser();
		PortfolioAccountConnection connection = new PortfolioAccountConnection();
		connection.setUser(user);
		connection.setAccountMode(AccountMode.PAPER);
		connection.setAccountCategory(AccountCategory.MAIN);
		connectionRepository.saveAndFlush(connection);

		PortfolioAccountConnection reloaded =
				connectionRepository
						.findByUserAndAccountModeAndAccountCategory(
								user, AccountMode.PAPER, AccountCategory.MAIN)
						.orElseThrow();

		assertThat(reloaded.getExchange()).isNull();
		assertThat(reloaded.getConnectionStatus()).isEqualTo(ExchangeConnectionStatus.NOT_CONNECTED);
		assertThat(reloaded.getAvailability()).isEqualTo(AccountAvailability.NOT_CONNECTED);
		assertThat(reloaded.getLastSyncedAt())
				.as("no synchronization has happened yet; null is not a zero timestamp")
				.isNull();
	}

	@Test
	void liveScopeRecordsExchangeAndSynchronizationState() {
		User user = newUser();
		PortfolioAccountConnection connection = new PortfolioAccountConnection();
		connection.setUser(user);
		connection.setAccountMode(AccountMode.LIVE);
		connection.setAccountCategory(AccountCategory.FUTURES);
		connection.setExchange(ExchangeName.BINANCE);
		connection.setConnectionStatus(ExchangeConnectionStatus.CONNECTED);
		connection.setAvailability(AccountAvailability.AVAILABLE);
		connection.setLastSyncedAt(Instant.parse("2026-10-01T00:00:00Z"));
		connection.setLastSyncMessage("snapshot ok");
		connectionRepository.saveAndFlush(connection);

		PortfolioAccountConnection reloaded =
				connectionRepository
						.findByUserAndAccountModeAndAccountCategory(
								user, AccountMode.LIVE, AccountCategory.FUTURES)
						.orElseThrow();

		assertThat(reloaded.getExchange()).isEqualTo(ExchangeName.BINANCE);
		assertThat(reloaded.getAvailability()).isEqualTo(AccountAvailability.AVAILABLE);
		assertThat(reloaded.getLastSyncedAt()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
		assertThat(reloaded.getLastSyncMessage()).isEqualTo("snapshot ok");
	}

	@Test
	void scopeKeyRejectsASecondRowForTheSameUserModeAndCategory() {
		User user = newUser();
		PortfolioAccountConnection first = new PortfolioAccountConnection();
		first.setUser(user);
		first.setAccountMode(AccountMode.LIVE);
		first.setAccountCategory(AccountCategory.SPOT);
		first.setExchange(ExchangeName.BINANCE);
		connectionRepository.saveAndFlush(first);

		PortfolioAccountConnection duplicate = new PortfolioAccountConnection();
		duplicate.setUser(user);
		duplicate.setAccountMode(AccountMode.LIVE);
		duplicate.setAccountCategory(AccountCategory.SPOT);
		duplicate.setExchange(ExchangeName.BINANCE);

		assertThatThrownBy(() -> connectionRepository.saveAndFlush(duplicate))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void oneUserAndOneExchangeMayHoldBothASpotAndAFuturesScope() {
		User user = newUser();
		for (AccountCategory category :
				new AccountCategory[] {AccountCategory.SPOT, AccountCategory.FUTURES}) {
			PortfolioAccountConnection connection = new PortfolioAccountConnection();
			connection.setUser(user);
			connection.setAccountMode(AccountMode.LIVE);
			connection.setAccountCategory(category);
			connection.setExchange(ExchangeName.BINANCE);
			connectionRepository.saveAndFlush(connection);
		}

		assertThat(connectionRepository.findByUserAndAccountMode(user, AccountMode.LIVE)).hasSize(2);
	}

	@Test
	void twoUsersMayEachHoldTheSameScope() {
		PortfolioAccountConnection first = new PortfolioAccountConnection();
		first.setUser(newUser());
		first.setAccountMode(AccountMode.PAPER);
		first.setAccountCategory(AccountCategory.SPOT);
		first.setExchange(null);
		connectionRepository.saveAndFlush(first);

		PortfolioAccountConnection second = new PortfolioAccountConnection();
		second.setUser(newUser());
		second.setAccountMode(AccountMode.PAPER);
		second.setAccountCategory(AccountCategory.SPOT);
		second.setExchange(null);
		connectionRepository.saveAndFlush(second);

		assertThat(connectionRepository.count()).isGreaterThanOrEqualTo(2);
	}
}