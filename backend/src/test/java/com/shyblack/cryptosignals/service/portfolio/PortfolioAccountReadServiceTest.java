package com.shyblack.cryptosignals.service.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shyblack.cryptosignals.dto.portfolio.PortfolioAccountView;
import com.shyblack.cryptosignals.entity.FuturesPosition;
import com.shyblack.cryptosignals.entity.LiveTradingAccount;
import com.shyblack.cryptosignals.entity.LiveOrder;
import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.FuturesTradingAccount;
import com.shyblack.cryptosignals.entity.Portfolio;
import com.shyblack.cryptosignals.entity.PortfolioAccountConnection;
import com.shyblack.cryptosignals.entity.Position;
import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountAvailability;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.CloseReason;
import com.shyblack.cryptosignals.entity.enums.EntryType;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.FuturesPositionStatus;
import com.shyblack.cryptosignals.entity.enums.LiveOrderPurpose;
import com.shyblack.cryptosignals.entity.enums.LiveOrderStatus;
import com.shyblack.cryptosignals.entity.enums.LiveOrderType;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.PositionStatus;
import com.shyblack.cryptosignals.entity.enums.SignalGrade;
import com.shyblack.cryptosignals.entity.enums.SignalStatus;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.repository.ExchangeCredentialRepository;
import com.shyblack.cryptosignals.repository.FuturesOrderRepository;
import com.shyblack.cryptosignals.repository.FuturesPositionRepository;
import com.shyblack.cryptosignals.repository.FuturesTradingAccountRepository;
import com.shyblack.cryptosignals.repository.LiveOrderRepository;
import com.shyblack.cryptosignals.repository.LiveTradingAccountRepository;
import com.shyblack.cryptosignals.repository.PortfolioAccountConnectionRepository;
import com.shyblack.cryptosignals.repository.PortfolioRepository;
import com.shyblack.cryptosignals.repository.PositionRepository;
import com.shyblack.cryptosignals.repository.SignalRepository;
import com.shyblack.cryptosignals.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Behaviour of the unified Portfolio read model across every {@code AccountMode x
 * AccountCategory} scope.
 *
 * <p>Covers the required scenarios: A PAPER MAIN, B/C PAPER SPOT/FUTURES partitioning, D
 * PAPER/LIVE isolation, E OPTIONS, F missing LIVE data, G ownership, H MAIN aggregation, I
 * connection status, J legacy compatibility, K scope independence.
 */
@SpringBootTest
@ActiveProfiles("test")
class PortfolioAccountReadServiceTest {

	private static final AtomicInteger SEQ = new AtomicInteger();

	@Autowired
	private PortfolioAccountReadService readService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PortfolioRepository portfolioRepository;

	@Autowired
	private PositionRepository positionRepository;

	@Autowired
	private SignalRepository signalRepository;

	@Autowired
	private PortfolioAccountConnectionRepository connectionRepository;

	@Autowired
	private LiveTradingAccountRepository liveAccountRepository;

	@Autowired
	private LiveOrderRepository liveOrderRepository;

	@Autowired
	private FuturesTradingAccountRepository futuresAccountRepository;

	@Autowired
	private FuturesPositionRepository futuresPositionRepository;

	@Autowired
	private FuturesOrderRepository futuresOrderRepository;

	@Autowired
	private ExchangeCredentialRepository credentialRepository;

	// ------------------------------------------------------------- fixtures

	private User user() {
		User u = new User();
		u.setEmail("read-model-" + SEQ.incrementAndGet() + "@example.com");
		u.setFullName("Read Model Tester");
		return userRepository.saveAndFlush(u);
	}

	private Signal signal(TradingMode mode) {
		Signal s = new Signal();
		s.setSymbol("BTCUSDT");
		s.setStatus(SignalStatus.CLOSED);
		s.setSide(PositionSide.LONG);
		s.setConfidence(80);
		s.setEntryPrice(new BigDecimal("100"));
		s.setTargetPrice(new BigDecimal("120"));
		s.setStopLoss(new BigDecimal("90"));
		s.setSuggestedRiskPercent(new BigDecimal("2.00"));
		s.setTradingMode(mode);
		s.setEntryType(EntryType.PRE_BREAKOUT);
		s.setSignalGrade(SignalGrade.BUY);
		return signalRepository.saveAndFlush(s);
	}

	/** Creates a paper position through the persisted model only; no engine is invoked. */
	private Position position(Portfolio portfolio, Signal signal, PositionStatus status) {
		Position p = new Position();
		p.setPortfolio(portfolio);
		p.setSignalId(signal == null ? null : signal.getId());
		p.setSymbol("BTCUSDT");
		p.setSide(PositionSide.LONG);
		p.setSize(new BigDecimal("1"));
		p.setNotional(new BigDecimal("100"));
		p.setEntryPrice(new BigDecimal("100"));
		p.setCurrentPrice(new BigDecimal("100"));
		p.setStatus(status);
		if (status == PositionStatus.CLOSED) {
			p.setExitPrice(new BigDecimal("110"));
			p.setRealizedPnl(new BigDecimal("10"));
			p.setCloseReason(CloseReason.TAKE_PROFIT);
			p.setClosedAt(Instant.now());
		}
		return positionRepository.saveAndFlush(p);
	}

	/**
	 * Test-only credential row. Both live and futures accounts declare a NOT NULL credential
	 * association, so an account cannot exist without one. The values are opaque ciphertext and
	 * the read model never decrypts them.
	 *
	 * <p>Idempotent per user: {@code exchange_credentials} is unique on {@code (user, exchange)},
	 * and one Binance credential legitimately backs both the spot and the futures account.
	 */
	private ExchangeCredential credential(User user) {
		return credentialRepository.findByUser_IdAndExchange(user.getId(), ExchangeName.BINANCE)
				.orElseGet(() -> {
					ExchangeCredential c = new ExchangeCredential();
					c.setUser(user);
					c.setExchange(ExchangeName.BINANCE);
					c.setApiKey("dGVzdC1vbmx5LWtleQ==");
					c.setApiSecret("dGVzdC1vbmx5LXNlY3JldA==");
					return credentialRepository.saveAndFlush(c);
				});
	}

	private Portfolio paperPortfolio(User user) {
		Portfolio p = new Portfolio();
		p.setUser(user);
		p.setName("Paper Trading");
		p.setAccountType(AccountType.PAPER);
		p.setInitialBalance(new BigDecimal("1000"));
		p.setTotalBalance(new BigDecimal("1000"));
		p.setAvailableBalance(new BigDecimal("800"));
		p.setInvested(new BigDecimal("200"));
		p.setRealizedPnl(new BigDecimal("25"));
		return portfolioRepository.saveAndFlush(p);
	}

	// ------------------------------------------- A. PAPER MAIN + J. legacy

	@Test
	void paperMainExposesTheSharedWalletAndAggregatesEveryPosition() {
		User u = user();
		Portfolio portfolio = paperPortfolio(u);
		Portfolio legacy = portfolioRepository.findById(portfolio.getId()).orElseThrow();
		assertThat(legacy.getAccountCategory())
				.as("J: a legacy row is stored with SQL NULL category")
				.isNull();

		position(legacy, signal(TradingMode.SPOT), PositionStatus.OPEN);
		position(legacy, signal(TradingMode.FUTURES), PositionStatus.OPEN);
		position(legacy, signal(TradingMode.SPOT), PositionStatus.CLOSED);

		PortfolioAccountView main = readService.getAccount(u, AccountMode.PAPER, AccountCategory.MAIN);

		assertThat(main.accountMode()).isEqualTo(AccountMode.PAPER);
		assertThat(main.accountCategory())
				.as("A/J: NULL category is presented as MAIN without rewriting the row")
				.isEqualTo(AccountCategory.MAIN);
		assertThat(main.availability()).isEqualTo(AccountAvailability.AVAILABLE);
		assertThat(main.availableBalance()).isEqualByComparingTo("800");
		assertThat(main.invested()).isEqualByComparingTo("200");
		assertThat(main.realizedPnl()).isEqualByComparingTo("25");
		assertThat(main.quoteCurrency()).isEqualTo("USDT");
		assertThat(main.exchange()).as("a simulated account has no exchange").isNull();
		assertThat(main.totalPositionCount()).isEqualTo(3);
		assertThat(main.openPositionCount()).isEqualTo(2);
		assertThat(main.orderCount()).as("paper has no exchange order store").isNull();

		Portfolio afterRead = portfolioRepository.findById(portfolio.getId()).orElseThrow();
		assertThat(afterRead.getAccountCategory())
				.as("J: reading must not mutate or backfill the legacy row")
				.isNull();
		assertThat(afterRead.getAvailableBalance()).isEqualByComparingTo("800");
		assertThat(afterRead.getInvested()).isEqualByComparingTo("200");
	}

	@Test
	void paperMainEquityUsesTheVerifiedPaperFormula() {
		User u = user();
		Portfolio portfolio = paperPortfolio(u);
		// available 800 + invested 200 + unrealized 0 (mark == entry) = 1000
		position(portfolio, signal(TradingMode.SPOT), PositionStatus.OPEN);

		PortfolioAccountView main = readService.getAccount(u, AccountMode.PAPER, AccountCategory.MAIN);

		assertThat(main.unrealizedPnl()).isEqualByComparingTo("0");
		assertThat(main.equity()).isEqualByComparingTo("1000");
	}

	// ------------------------------------- B/C. PAPER SPOT vs FUTURES split

	@Test
	void paperSpotIncludesOnlySpotSignalPositions() {
		User u = user();
		Portfolio portfolio = paperPortfolio(u);
		position(portfolio, signal(TradingMode.SPOT), PositionStatus.OPEN);
		position(portfolio, signal(TradingMode.FUTURES), PositionStatus.OPEN);
		position(portfolio, signal(TradingMode.FUTURES), PositionStatus.CLOSED);

		PortfolioAccountView spot = readService.getAccount(u, AccountMode.PAPER, AccountCategory.SPOT);

		assertThat(spot.accountCategory()).isEqualTo(AccountCategory.SPOT);
		assertThat(spot.openPositionCount()).isEqualTo(1);
		assertThat(spot.totalPositionCount()).isEqualTo(1);
		assertThat(spot.invested()).isEqualByComparingTo("100");
		assertThat(spot.realizedPnl()).isEqualByComparingTo("0");
	}

	@Test
	void paperFuturesIncludesOnlyFuturesSignalPositions() {
		User u = user();
		Portfolio portfolio = paperPortfolio(u);
		position(portfolio, signal(TradingMode.SPOT), PositionStatus.OPEN);
		position(portfolio, signal(TradingMode.FUTURES), PositionStatus.OPEN);
		position(portfolio, signal(TradingMode.FUTURES), PositionStatus.CLOSED);

		PortfolioAccountView futures = readService.getAccount(u, AccountMode.PAPER, AccountCategory.FUTURES);

		assertThat(futures.accountCategory()).isEqualTo(AccountCategory.FUTURES);
		assertThat(futures.openPositionCount()).isEqualTo(1);
		assertThat(futures.totalPositionCount()).isEqualTo(2);
		assertThat(futures.realizedPnl()).isEqualByComparingTo("10");
	}

	@Test
	void paperCategoriesNeverShareCapitalBecauseTheBalanceIsWithheld() {
		User u = user();
		Portfolio portfolio = paperPortfolio(u);
		position(portfolio, signal(TradingMode.SPOT), PositionStatus.OPEN);
		position(portfolio, signal(TradingMode.FUTURES), PositionStatus.OPEN);

		PortfolioAccountView spot = readService.getAccount(u, AccountMode.PAPER, AccountCategory.SPOT);
		PortfolioAccountView futures = readService.getAccount(u, AccountMode.PAPER, AccountCategory.FUTURES);
		PortfolioAccountView main = readService.getAccount(u, AccountMode.PAPER, AccountCategory.MAIN);

		assertThat(spot.availableBalance()).as("H: no duplicated capital").isNull();
		assertThat(spot.equity()).isNull();
		assertThat(futures.availableBalance()).isNull();
		assertThat(futures.equity()).isNull();
		assertThat(main.availableBalance()).isEqualByComparingTo("800");
		assertThat(spot.statusMessage()).isNotBlank();
		assertThat(futures.statusMessage()).isNotBlank();
	}

	@Test
	void positionWithoutSignalStaysOutOfEveryCategoryButRemainsInMain() {
		User u = user();
		Portfolio portfolio = paperPortfolio(u);
		position(portfolio, null, PositionStatus.OPEN);

		assertThat(readService.getAccount(u, AccountMode.PAPER, AccountCategory.SPOT).totalPositionCount())
				.as("an uncategorisable position must not be assigned to a market scope")
				.isZero();
		assertThat(readService.getAccount(u, AccountMode.PAPER, AccountCategory.FUTURES).totalPositionCount())
				.isZero();
		assertThat(readService.getAccount(u, AccountMode.PAPER, AccountCategory.MAIN).totalPositionCount())
				.as("MAIN shows the whole account")
				.isEqualTo(1);
	}

	// ------------------------------------------------- E. OPTIONS hard stop

	@Test
	void paperOptionsIsUnsupportedAndFabricatesNothing() {
		User u = user();
		paperPortfolio(u);

		PortfolioAccountView options = readService.getAccount(u, AccountMode.PAPER, AccountCategory.OPTIONS);

		assertThat(options.availability()).isEqualTo(AccountAvailability.UNSUPPORTED);
		assertThat(options.equity()).isNull();
		assertThat(options.availableBalance()).isNull();
		assertThat(options.invested()).isNull();
		assertThat(options.realizedPnl()).isNull();
		assertThat(options.unrealizedPnl()).isNull();
		assertThat(options.totalPositionCount()).as("no fabricated zero count").isNull();
		assertThat(options.openPositionCount()).isNull();
		assertThat(options.orderCount()).isNull();
		assertThat(options.statusMessage()).contains("Options");
	}

	@Test
	void liveOptionsIsUnsupportedAndFabricatesNothing() {
		User u = user();

		PortfolioAccountView options = readService.getAccount(u, AccountMode.LIVE, AccountCategory.OPTIONS);

		assertThat(options.availability()).isEqualTo(AccountAvailability.UNSUPPORTED);
		assertThat(options.equity()).isNull();
		assertThat(options.totalPositionCount()).isNull();
		assertThat(options.orderCount()).isNull();
	}

	// ------------------------------------------ F. missing / partial LIVE

	@Test
	void liveSpotWithoutAnAccountReportsNotConnectedAndNoZeroes() {
		User u = user();

		PortfolioAccountView spot = readService.getAccount(u, AccountMode.LIVE, AccountCategory.SPOT);

		assertThat(spot.availability()).isEqualTo(AccountAvailability.NOT_CONNECTED);
		assertThat(spot.equity()).isNull();
		assertThat(spot.availableBalance()).isNull();
		assertThat(spot.invested()).isNull();
		assertThat(spot.unrealizedPnl()).isNull();
		assertThat(spot.realizedPnl()).isNull();
		assertThat(spot.openPositionCount()).isNull();
		assertThat(spot.totalPositionCount()).isNull();
		assertThat(spot.orderCount()).isNull();
		assertThat(spot.connectionStatus()).isNull();
	}

	@Test
	void liveFuturesWithoutAnAccountReportsNotConnectedAndNoZeroes() {
		User u = user();

		PortfolioAccountView futures = readService.getAccount(u, AccountMode.LIVE, AccountCategory.FUTURES);

		assertThat(futures.availability()).isEqualTo(AccountAvailability.NOT_CONNECTED);
		assertThat(futures.equity()).isNull();
		assertThat(futures.unrealizedPnl()).isNull();
		assertThat(futures.openPositionCount()).isNull();
	}

	@Test
	void liveSpotWithUncachedBalancesKeepsThemNullRatherThanZero() {
		User u = user();
		LiveTradingAccount account = new LiveTradingAccount();
		account.setUser(u);
		account.setExchange(ExchangeName.BINANCE);
		account.setCredential(credential(u));
		account.setConnectionStatus(ExchangeConnectionStatus.CONNECTED);
		account.setQuoteCurrency("USDT");
		// cached balances deliberately left null, as a never-reconciled account would be
		liveAccountRepository.saveAndFlush(account);

		PortfolioAccountView spot = readService.getAccount(u, AccountMode.LIVE, AccountCategory.SPOT);

		assertThat(spot.availability())
				.as("no authoritative snapshot exists yet")
				.isEqualTo(AccountAvailability.UNAVAILABLE);
		assertThat(spot.equity()).as("F: unknown balance stays null").isNull();
		assertThat(spot.availableBalance()).isNull();
		assertThat(spot.invested()).as("spot has no invested source").isNull();
		assertThat(spot.unrealizedPnl()).as("spot has no authoritative unrealized source").isNull();
		assertThat(spot.realizedPnl()).as("spot has no authoritative realized source").isNull();
	}

	@Test
	void liveSpotReportsCachedBalancesWhenPresent() {
		User u = user();
		LiveTradingAccount account = new LiveTradingAccount();
		account.setUser(u);
		account.setExchange(ExchangeName.BINANCE);
		account.setCredential(credential(u));
		account.setConnectionStatus(ExchangeConnectionStatus.CONNECTED);
		account.setQuoteCurrency("USDT");
		account.setCachedAvailableBalance(new BigDecimal("500"));
		account.setCachedTotalBalance(new BigDecimal("750"));
		account.setLastValidatedAt(Instant.now());
		liveAccountRepository.saveAndFlush(account);

		PortfolioAccountView spot = readService.getAccount(u, AccountMode.LIVE, AccountCategory.SPOT);

		assertThat(spot.availability())
				.as("cached account fields alone are not an authoritative portfolio snapshot")
				.isEqualTo(AccountAvailability.UNAVAILABLE);
		assertThat(spot.availableBalance()).isNull();
		assertThat(spot.equity()).isNull();
	}

	@Test
	void liveFuturesWithAnOutdatedSynchronizationIsReportedStale() {
		User u = user();
		FuturesTradingAccount account = new FuturesTradingAccount();
		account.setUser(u);
		account.setExchange(ExchangeName.BINANCE);
		account.setCredential(credential(u));
		account.setConnectionStatus(ExchangeConnectionStatus.CONNECTED);
		account.setWalletBalance(new BigDecimal("900"));
		account.setLastValidatedAt(Instant.now().minus(PortfolioAccountReadService.STALE_AFTER).minusSeconds(60));
		futuresAccountRepository.saveAndFlush(account);

		PortfolioAccountConnection connection = new PortfolioAccountConnection();
		connection.setUser(u);
		connection.setAccountMode(AccountMode.LIVE);
		connection.setAccountCategory(AccountCategory.FUTURES);
		connection.setExchange(ExchangeName.BINANCE);
		connection.setConnectionStatus(ExchangeConnectionStatus.CONNECTED);
		connection.setAvailability(AccountAvailability.AVAILABLE);
		connection.setLastSyncedAt(Instant.now().minus(PortfolioAccountReadService.STALE_AFTER).minusSeconds(60));
		connectionRepository.saveAndFlush(connection);

		PortfolioAccountView futures = readService.getAccount(u, AccountMode.LIVE, AccountCategory.FUTURES);

		assertThat(futures.availability()).isEqualTo(AccountAvailability.STALE);
		assertThat(futures.equity())
				.as("stale values are still shown, never discarded or zeroed")
				.isEqualByComparingTo("900");
	}

	@Test
	void liveFuturesReportsOnlyFieldsTheAccountActuallyStores() {
		User u = user();
		FuturesTradingAccount account = new FuturesTradingAccount();
		account.setUser(u);
		account.setExchange(ExchangeName.BINANCE);
		account.setCredential(credential(u));
		account.setConnectionStatus(ExchangeConnectionStatus.CONNECTED);
		account.setMarginAsset("USDT");
		account.setWalletBalance(new BigDecimal("900"));
		account.setAvailableBalance(new BigDecimal("650"));
		account.setUsedMargin(new BigDecimal("250"));
		account.setUnrealizedPnl(new BigDecimal("-12.5"));
		account.setLastValidatedAt(Instant.now());
		futuresAccountRepository.saveAndFlush(account);

		PortfolioAccountView futures = readService.getAccount(u, AccountMode.LIVE, AccountCategory.FUTURES);

		assertThat(futures.availability()).isEqualTo(AccountAvailability.AVAILABLE);
		assertThat(futures.equity()).isEqualByComparingTo("900");
		assertThat(futures.availableBalance()).isEqualByComparingTo("650");
		assertThat(futures.invested()).isEqualByComparingTo("250");
		assertThat(futures.unrealizedPnl()).isEqualByComparingTo("-12.5");
		assertThat(futures.realizedPnl())
				.as("J: no authoritative exchange income source, so local P&L is never substituted")
				.isNull();
		assertThat(futures.openPositionCount())
				.as("open positions now come from the exchange snapshot, not the local shadow")
				.isZero();
		assertThat(futures.quoteCurrency()).isEqualTo("USDT");
	}

	@Test
	void liveFuturesRealizedPnlIsNullEvenWithLocalClosedPositions() {
		User u = user();
		FuturesTradingAccount account = new FuturesTradingAccount();
		account.setUser(u);
		account.setExchange(ExchangeName.BINANCE);
		account.setCredential(credential(u));
		account.setConnectionStatus(ExchangeConnectionStatus.CONNECTED);
		account = futuresAccountRepository.saveAndFlush(account);

		futuresPositionRepository.saveAndFlush(
				futuresPosition(u, account, "BTCUSDT", FuturesPositionStatus.CLOSED, new BigDecimal("5")));
		futuresPositionRepository.saveAndFlush(
				futuresPosition(u, account, "ETHUSDT", FuturesPositionStatus.CLOSED, new BigDecimal("-2")));

		PortfolioAccountView futures = readService.getAccount(u, AccountMode.LIVE, AccountCategory.FUTURES);

		assertThat(futures.realizedPnl())
				.as("J: local closed-position P&L is never substituted for exchange realized P&L")
				.isNull();
	}

	@Test
	void paperCategoryInvestedIsNullWhenAnOpenPositionHasUnknownNotional() {
		User u = user();
		Portfolio portfolio = paperPortfolio(u);
		Position known = position(portfolio, signal(TradingMode.SPOT), PositionStatus.OPEN);
		Position unknown = position(portfolio, signal(TradingMode.SPOT), PositionStatus.OPEN);
		unknown.setNotional(null);
		positionRepository.saveAndFlush(unknown);
		assertThat(known.getNotional()).isNotNull();

		PortfolioAccountView spot = readService.getAccount(u, AccountMode.PAPER, AccountCategory.SPOT);

		assertThat(spot.invested())
				.as("F: a partial sum would misstate the total, so the figure stays unknown")
				.isNull();
		assertThat(spot.openPositionCount()).isEqualTo(2);
	}

	@Test
	void liveFuturesOrdersAndPositionsAreCountedFromTheirOwnStore() {
		User u = user();
		FuturesTradingAccount account = new FuturesTradingAccount();
		account.setUser(u);
		account.setExchange(ExchangeName.BINANCE);
		account.setCredential(credential(u));
		account.setConnectionStatus(ExchangeConnectionStatus.CONNECTED);
		account = futuresAccountRepository.saveAndFlush(account);

		futuresPositionRepository.saveAndFlush(futuresPosition(u, account, "BTCUSDT", new BigDecimal("3")));

		PortfolioAccountView futures = readService.getAccount(u, AccountMode.LIVE, AccountCategory.FUTURES);

		assertThat(futures.openPositionCount())
				.as("a locally recorded position is not an exchange position")
				.isZero();
		assertThat(futures.totalPositionCount())
				.as("a lifetime position total needs exchange history, which is not integrated")
				.isNull();
		assertThat(futures.orderCount()).isEqualTo(0);
	}

	@Test
	void liveSpotCountsOrdersWithoutInventingPositions() {
		User u = user();
		LiveTradingAccount account = new LiveTradingAccount();
		account.setUser(u);
		account.setExchange(ExchangeName.BINANCE);
		account.setCredential(credential(u));
		account.setConnectionStatus(ExchangeConnectionStatus.CONNECTED);
		account = liveAccountRepository.saveAndFlush(account);

		liveOrderRepository.saveAndFlush(liveOrder(account, LiveOrderPurpose.ENTRY, LiveOrderStatus.FILLED));
		liveOrderRepository.saveAndFlush(liveOrder(account, LiveOrderPurpose.STOP_LOSS, LiveOrderStatus.ACKNOWLEDGED));
		liveOrderRepository.saveAndFlush(liveOrder(account, LiveOrderPurpose.ENTRY, LiveOrderStatus.REJECTED));

		PortfolioAccountView spot = readService.getAccount(u, AccountMode.LIVE, AccountCategory.SPOT);

		assertThat(spot.orderCount()).isEqualTo(3);
		assertThat(spot.openPositionCount())
				.as("spot has no exchange position concept; a balance is never read as a position")
				.isNull();
		assertThat(spot.totalPositionCount()).isNull();
	}

	// ------------------------------------------------- D. PAPER/LIVE split

	@Test
	void liveScopesNeverExposePaperBalancesAndViceVersa() {
		User u = user();
		Portfolio portfolio = paperPortfolio(u);
		position(portfolio, signal(TradingMode.SPOT), PositionStatus.OPEN);

		PortfolioAccountView paperMain = readService.getAccount(u, AccountMode.PAPER, AccountCategory.MAIN);
		PortfolioAccountView paperSpot = readService.getAccount(u, AccountMode.PAPER, AccountCategory.SPOT);
		PortfolioAccountView liveSpot = readService.getAccount(u, AccountMode.LIVE, AccountCategory.SPOT);
		PortfolioAccountView liveMain = readService.getAccount(u, AccountMode.LIVE, AccountCategory.MAIN);

		assertThat(paperMain.availableBalance()).isEqualByComparingTo("800");
		assertThat(liveSpot.availableBalance())
				.as("D: an unconnected live scope must not inherit the paper balance")
				.isNull();
		assertThat(liveMain.availableBalance()).isNull();
		assertThat(paperSpot.orderCount()).as("paper has no exchange orders").isNull();
		assertThat(liveSpot.totalPositionCount())
				.as("D: paper positions must not appear in live")
				.isNull();
		assertThat(liveSpot.exchange()).isEqualTo(ExchangeName.BINANCE);
		assertThat(paperSpot.exchange()).isNull();
	}

	@Test
	void paperIsUnaffectedByAnExistingLiveSpotAccount() {
		User u = user();
		Portfolio portfolio = paperPortfolio(u);
		position(portfolio, signal(TradingMode.SPOT), PositionStatus.OPEN);

		LiveTradingAccount account = new LiveTradingAccount();
		account.setUser(u);
		account.setExchange(ExchangeName.BINANCE);
		account.setCredential(credential(u));
		account.setConnectionStatus(ExchangeConnectionStatus.CONNECTED);
		account.setCachedAvailableBalance(new BigDecimal("12345"));
		liveAccountRepository.saveAndFlush(account);

		PortfolioAccountView paperMain = readService.getAccount(u, AccountMode.PAPER, AccountCategory.MAIN);

		assertThat(paperMain.availableBalance())
				.as("D: a live balance must never leak into paper")
				.isEqualByComparingTo("800");
		assertThat(paperMain.equity()).isEqualByComparingTo("1000");
	}

	// ------------------------------------------------ H. LIVE MAIN derived

	@Test
	void liveMainIsUnavailableAndInventsNoWallet() {
		User u = user();
		LiveTradingAccount spot = new LiveTradingAccount();
		spot.setUser(u);
		spot.setExchange(ExchangeName.BINANCE);
		spot.setCredential(credential(u));
		spot.setConnectionStatus(ExchangeConnectionStatus.CONNECTED);
		spot.setCachedTotalBalance(new BigDecimal("750"));
		liveAccountRepository.saveAndFlush(spot);

		FuturesTradingAccount futures = new FuturesTradingAccount();
		futures.setUser(u);
		futures.setExchange(ExchangeName.BINANCE);
		futures.setCredential(credential(u));
		futures.setConnectionStatus(ExchangeConnectionStatus.CONNECTED);
		futures.setWalletBalance(new BigDecimal("900"));
		futuresAccountRepository.saveAndFlush(futures);

		PortfolioAccountView main = readService.getAccount(u, AccountMode.LIVE, AccountCategory.MAIN);

		assertThat(main.availability()).isEqualTo(AccountAvailability.UNAVAILABLE);
		assertThat(main.equity())
				.as("H: spot and futures wallets must not be summed into a fake main balance")
				.isNull();
		assertThat(main.availableBalance()).isNull();
		assertThat(main.invested()).isNull();
		assertThat(main.totalPositionCount()).isNull();
		assertThat(main.statusMessage()).contains("no single main-wallet balance");
	}

	// ------------------------------------------------------- G. ownership

	@Test
	void oneUserCanNeverSeeAnotherUsersAccount() {
		User owner = user();
		Portfolio ownerPortfolio = paperPortfolio(owner);
		position(ownerPortfolio, signal(TradingMode.SPOT), PositionStatus.OPEN);
		ownerPortfolio.setAvailableBalance(new BigDecimal("4242"));
		portfolioRepository.saveAndFlush(ownerPortfolio);

		User intruder = user();
		paperPortfolio(intruder);

		PortfolioAccountView foreignMain = readService.getAccount(intruder, AccountMode.PAPER, AccountCategory.MAIN);

		assertThat(foreignMain.availableBalance())
				.as("G: the intruder's own wallet is returned, never the owner's")
				.isEqualByComparingTo("800");
		assertThat(foreignMain.totalPositionCount()).isZero();
	}

	@Test
	void nullArgumentsAreRejectedRatherThanDefaulted() {
		User u = user();

		assertThatThrownBy(() -> readService.getAccount(null, AccountMode.PAPER, AccountCategory.MAIN))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> readService.getAccount(u, null, AccountCategory.MAIN))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> readService.getAccount(u, AccountMode.PAPER, null))
				.isInstanceOf(NullPointerException.class);
	}

	// ------------------------------------------- I. connection/status scope

	@Test
	void connectionRecordDrivesStatusForItsOwnScopeOnly() {
		User u = user();
		paperPortfolio(u);

		PortfolioAccountConnection spotConnection = new PortfolioAccountConnection();
		spotConnection.setUser(u);
		spotConnection.setAccountMode(AccountMode.PAPER);
		spotConnection.setAccountCategory(AccountCategory.SPOT);
		spotConnection.setConnectionStatus(ExchangeConnectionStatus.CONNECTED);
		spotConnection.setAvailability(AccountAvailability.SYNCING);
		spotConnection.setLastSyncedAt(Instant.now());
		spotConnection.setLastSyncMessage("paper spot sync");
		connectionRepository.saveAndFlush(spotConnection);

		PortfolioAccountView spot = readService.getAccount(u, AccountMode.PAPER, AccountCategory.SPOT);
		PortfolioAccountView futures = readService.getAccount(u, AccountMode.PAPER, AccountCategory.FUTURES);

		assertThat(spot.connectionStatus()).isEqualTo(ExchangeConnectionStatus.CONNECTED);
		assertThat(spot.statusMessage()).isEqualTo("paper spot sync");
		assertThat(spot.lastSyncedAt()).isNotNull();
		assertThat(futures.connectionStatus())
				.as("I: a connection record must not leak across scopes")
				.isNull();
		assertThat(futures.statusMessage())
				.as("futures still reports its own shared-wallet reason")
				.isEqualTo(PortfolioAccountReadService.PAPER_SHARED_WALLET_MESSAGE);
	}

	@Test
	void connectionRecordCannotFakeAvailableWhileTheAccountIsDisconnected() {
		User u = user();
		LiveTradingAccount account = new LiveTradingAccount();
		account.setUser(u);
		account.setExchange(ExchangeName.BINANCE);
		account.setCredential(credential(u));
		account.setConnectionStatus(ExchangeConnectionStatus.FAILED);
		account.setCachedAvailableBalance(new BigDecimal("10"));
		liveAccountRepository.saveAndFlush(account);

		PortfolioAccountConnection connection = new PortfolioAccountConnection();
		connection.setUser(u);
		connection.setAccountMode(AccountMode.LIVE);
		connection.setAccountCategory(AccountCategory.SPOT);
		connection.setConnectionStatus(ExchangeConnectionStatus.CONNECTED);
		connection.setAvailability(AccountAvailability.AVAILABLE);
		connectionRepository.saveAndFlush(connection);

		PortfolioAccountView spot = readService.getAccount(u, AccountMode.LIVE, AccountCategory.SPOT);

		assertThat(spot.availability())
				.as("a stale record must not override the account's real state")
				.isEqualTo(AccountAvailability.ERROR);
	}

	@Test
	void readModelNeverReadsOrExposesCredentials() {
		User u = user();
		PortfolioAccountConnection connection = new PortfolioAccountConnection();
		connection.setUser(u);
		connection.setAccountMode(AccountMode.LIVE);
		connection.setAccountCategory(AccountCategory.SPOT);
		connection.setExchange(ExchangeName.BINANCE);
		connection.setConnectionStatus(ExchangeConnectionStatus.NOT_CONNECTED);
		connectionRepository.saveAndFlush(connection);

		readService.getAccount(u, AccountMode.LIVE, AccountCategory.SPOT);

		assertThat(PortfolioAccountConnection.class.getDeclaredFields())
				.as("I: credentials stay on ExchangeCredential only")
				.noneMatch(f -> f.getName().toLowerCase().contains("credential")
						|| f.getName().toLowerCase().contains("secret")
						|| f.getName().toLowerCase().contains("apikey"));
	}

	// --------------------------------------- K. overview / scope integrity

	@Test
	void overviewReturnsTheFourCategoriesOfExactlyOneMode() {
		User u = user();
		Portfolio portfolio = paperPortfolio(u);
		position(portfolio, signal(TradingMode.SPOT), PositionStatus.OPEN);

		var paperOverview = readService.getOverview(u, AccountMode.PAPER);
		var liveOverview = readService.getOverview(u, AccountMode.LIVE);

		assertThat(paperOverview).extracting(PortfolioAccountView::accountCategory)
				.containsExactly(
						AccountCategory.MAIN,
						AccountCategory.SPOT,
						AccountCategory.FUTURES,
						AccountCategory.OPTIONS);
		assertThat(paperOverview).extracting(PortfolioAccountView::accountMode)
				.as("K: an overview never mixes modes")
				.containsOnly(AccountMode.PAPER);
		assertThat(liveOverview).extracting(PortfolioAccountView::accountMode)
				.containsOnly(AccountMode.LIVE);
		assertThat(paperOverview).allSatisfy(v ->
				assertThat(v.accountMode()).isEqualTo(AccountMode.PAPER));
		assertThat(liveOverview).allSatisfy(v ->
				assertThat(v.accountMode()).isEqualTo(AccountMode.LIVE));
	}

	@Test
	void unavailableScopesCarryNoNumericFieldAtAll() {
		User u = user();

		for (AccountMode mode : AccountMode.values()) {
			for (AccountCategory category : new AccountCategory[] {AccountCategory.OPTIONS}) {
				PortfolioAccountView view = readService.getAccount(u, mode, category);
				assertThat(view.isUnavailable()).isTrue();
				assertThat(view.equity()).isNull();
				assertThat(view.availableBalance()).isNull();
				assertThat(view.invested()).isNull();
				assertThat(view.realizedPnl()).isNull();
				assertThat(view.unrealizedPnl()).isNull();
				assertThat(view.totalPositionCount()).isNull();
				assertThat(view.openPositionCount()).isNull();
				assertThat(view.orderCount()).isNull();
				assertThat(view.statusMessage()).isNotBlank();
			}
		}
	}

	@Test
	void liveMainIsUnavailableEvenWithNoAccountAtAll() {
		User u = user();

		PortfolioAccountView main = readService.getAccount(u, AccountMode.LIVE, AccountCategory.MAIN);

		assertThat(main.availability()).isEqualTo(AccountAvailability.UNAVAILABLE);
		assertThat(main.equity()).isNull();
	}

	// ------------------------------------------------------------ helpers

	private FuturesPosition futuresPosition(User user, FuturesTradingAccount account, String symbol, BigDecimal realized) {
		return futuresPosition(user, account, symbol, FuturesPositionStatus.OPEN, realized);
	}

	private FuturesPosition futuresPosition(
			User user,
			FuturesTradingAccount account,
			String symbol,
			FuturesPositionStatus status,
			BigDecimal realized) {
		FuturesPosition p = new FuturesPosition();
		p.setAccount(account);
		p.setSymbol(symbol);
		p.setPositionSide(PositionSide.LONG);
		p.setQuantity(new BigDecimal("1"));
		p.setStatus(status);
		if (status == FuturesPositionStatus.CLOSED) {
			p.setClosedAt(Instant.now());
		}
		if (realized != null) {
			p.setRealizedPnl(realized);
		}
		return p;
	}

	private LiveOrder liveOrder(LiveTradingAccount account, LiveOrderPurpose purpose, LiveOrderStatus status) {
		LiveOrder o = new LiveOrder();
		o.setAccount(account);
		o.setClientOrderId("SB-test-" + SEQ.incrementAndGet());
		o.setSymbol("BTCUSDT");
		o.setSide(PositionSide.LONG);
		o.setType(LiveOrderType.MARKET);
		o.setPurpose(purpose);
		o.setStatus(status);
		o.setRequestedQuantity(new BigDecimal("1"));
		return o;
	}
}
