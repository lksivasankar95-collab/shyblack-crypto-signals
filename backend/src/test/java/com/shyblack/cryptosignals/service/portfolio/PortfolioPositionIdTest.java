package com.shyblack.cryptosignals.service.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.dto.portfolio.PortfolioPositionView;
import com.shyblack.cryptosignals.dto.portfolio.PortfolioPositionsResponse;
import com.shyblack.cryptosignals.entity.FuturesPosition;
import com.shyblack.cryptosignals.entity.FuturesTradingAccount;
import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.Portfolio;
import com.shyblack.cryptosignals.entity.PortfolioExchangePosition;
import com.shyblack.cryptosignals.entity.Position;
import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.FuturesPositionStatus;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.PositionStatus;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.repository.FuturesPositionRepository;
import com.shyblack.cryptosignals.repository.FuturesTradingAccountRepository;
import com.shyblack.cryptosignals.repository.PortfolioExchangePositionRepository;
import com.shyblack.cryptosignals.repository.PortfolioRepository;
import com.shyblack.cryptosignals.repository.PositionRepository;
import com.shyblack.cryptosignals.repository.SignalRepository;
import com.shyblack.cryptosignals.repository.UserRepository;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * The stable identifier published on every {@link PortfolioPositionView} row.
 *
 * <p>Close and stop/target repositioning must be able to name exactly one position.
 * A list index cannot do that — it shifts as positions open and close — so each row
 * publishes the key of the record its own scope's action endpoints actually address.
 *
 * <p>The case that matters is LIVE FUTURES. The read model reports the exchange's
 * position snapshot, while the live actions address a locally tracked
 * {@code FuturesPosition}. Those are different rows: publishing the snapshot key
 * would hand the client an identifier that fails when used. This pins that the two
 * are never conflated, and that an unaddressable row reports no id rather than a
 * broken one.
 */
@SpringBootTest
@ActiveProfiles("test")
class PortfolioPositionIdTest {

	private static final AtomicInteger SEQ = new AtomicInteger();

	@Autowired private PortfolioAccountReadService readService;
	@Autowired private UserRepository userRepository;
	@Autowired private PortfolioRepository portfolioRepository;
	@Autowired private PositionRepository positionRepository;
	@Autowired private SignalRepository signalRepository;
	@Autowired private FuturesTradingAccountRepository futuresAccountRepository;
	@Autowired private FuturesPositionRepository futuresPositionRepository;
	@Autowired private PortfolioExchangePositionRepository exchangePositionRepository;
	@Autowired private com.shyblack.cryptosignals.repository.ExchangeCredentialRepository credentialRepository;

	// ── Contract ──────────────────────────────────────────────────

	@Test
	void theRowPublishesANullableIdentifierField() {
		var component = java.util.Arrays.stream(PortfolioPositionView.class.getRecordComponents())
				.filter(c -> c.getName().equals("positionId"))
				.findFirst();
		assertThat(component).as("a position row must publish a stable key").isPresent();
		assertThat(component.get().getType()).isEqualTo(String.class);
	}

	// ── PAPER: the Position key ───────────────────────────────────

	@Test
	void paperRowPublishesThePositionPrimaryKey() {
		User user = user();
		Portfolio portfolio = paperPortfolio(user);
		Position position = paperPosition(portfolio, PositionSide.LONG, PositionStatus.OPEN);

		PortfolioPositionView view = onlyPosition(
				readService.listPositions(user, AccountMode.PAPER, AccountCategory.SPOT));

		assertThat(view.positionId())
				.as("the paper close/reposition endpoints address the Position key")
				.isEqualTo(position.getId().toString());
	}

	@Test
	void twoPaperRowsOnOneSymbolStayDistinguishable() {
		User user = user();
		Portfolio portfolio = paperPortfolio(user);
		Position longLeg = paperPosition(portfolio, PositionSide.LONG, PositionStatus.OPEN);
		Position shortLeg = paperPosition(portfolio, PositionSide.SHORT, PositionStatus.OPEN);

		var response = readService.listPositions(user, AccountMode.PAPER, AccountCategory.SPOT);

		assertThat(response.positions()).hasSize(2);
		assertThat(response.positions())
				.extracting(PortfolioPositionView::positionId)
				.containsExactlyInAnyOrder(longLeg.getId().toString(), shortLeg.getId().toString())
				.doesNotHaveDuplicates();
	}

	// ── LIVE FUTURES: the actionable key, never the snapshot key ──

	@Test
	void liveFuturesResolvesTheTrackedPositionNotTheSnapshotKey() {
		User user = user();
		FuturesTradingAccount account = futuresAccount(user);
		PortfolioExchangePosition snapshot = exchangePosition(user, "ETHUSDT", "LONG",
				new BigDecimal("1"));
		FuturesPosition tracked = futuresPosition(account, "ETHUSDT",
				PositionSide.LONG, FuturesPositionStatus.OPEN);

		PortfolioPositionView view = onlyPosition(
				readService.listPositions(user, AccountMode.LIVE, AccountCategory.FUTURES));

		assertThat(view.positionId())
				.as("must be the FuturesPosition key the live close endpoint accepts")
				.isEqualTo(tracked.getId().toString());
		assertThat(view.positionId())
				.as("never the exchange snapshot's own key")
				.isNotEqualTo(snapshot.getId().toString());
	}

	@Test
	void liveFuturesWithNoTrackedPositionPublishesNoIdentifier() {
		User user = user();
		futuresAccount(user);
		exchangePosition(user, "SOLUSDT", "LONG", new BigDecimal("1"));

		PortfolioPositionView view = onlyPosition(
				readService.listPositions(user, AccountMode.LIVE, AccountCategory.FUTURES));

		assertThat(view.positionId())
				.as("an id that would fail when used is worse than none")
				.isNull();
	}

	@Test
	void liveFuturesIgnoresAClosedTrackedPosition() {
		User user = user();
		FuturesTradingAccount account = futuresAccount(user);
		exchangePosition(user, "XRPUSDT", "LONG", new BigDecimal("1"));
		futuresPosition(account, "XRPUSDT", PositionSide.LONG, FuturesPositionStatus.CLOSED);

		assertThat(onlyPosition(
				readService.listPositions(user, AccountMode.LIVE, AccountCategory.FUTURES))
				.positionId())
				.as("a closed record is not something to close again")
				.isNull();
	}

	// ── Harness ───────────────────────────────────────────────────

	private static PortfolioPositionView onlyPosition(PortfolioPositionsResponse response) {
		assertThat(response.positions())
				.as("fixture should produce exactly one position row")
				.hasSize(1);
		return response.positions().get(0);
	}

	private User user() {
		User u = new User();
		u.setEmail("position-id-" + SEQ.incrementAndGet() + "@example.com");
		u.setFullName("Position Id Tester");
		u.setAccountType(AccountType.PAPER);
		return userRepository.saveAndFlush(u);
	}

	private Portfolio paperPortfolio(User user) {
		Portfolio p = new Portfolio();
		p.setUser(user);
		p.setName("Paper Trading");
		p.setAccountType(AccountType.PAPER);
		p.setQuoteCurrency("USDT");
		p.setInitialBalance(new BigDecimal("10000"));
		p.setTotalBalance(new BigDecimal("10000"));
		p.setAvailableBalance(new BigDecimal("10000"));
		p.setInvested(BigDecimal.ZERO);
		return portfolioRepository.saveAndFlush(p);
	}

	private Position paperPosition(Portfolio portfolio, PositionSide side, PositionStatus status) {
		Signal signal = new Signal();
		signal.setSymbol("BTCUSDT");
		signal.setTradingMode(TradingMode.SPOT);
		signal.setStatus(com.shyblack.cryptosignals.entity.enums.SignalStatus.ACTIVE);
		signal.setSide(side);
		signal.setConfidence(80);
		signal.setEntryPrice(new BigDecimal("100"));
		signal.setTargetPrice(new BigDecimal("120"));
		signal.setStopLoss(new BigDecimal("90"));
		signal.setSuggestedRiskPercent(new BigDecimal("2.00"));
		signal.setEntryType(com.shyblack.cryptosignals.entity.enums.EntryType.PRE_BREAKOUT);
		signal.setSignalGrade(com.shyblack.cryptosignals.entity.enums.SignalGrade.BUY);
		signal = signalRepository.saveAndFlush(signal);

		Position p = new Position();
		p.setPortfolio(portfolio);
		p.setSignalId(signal.getId());
		p.setSymbol("BTCUSDT");
		p.setSide(side);
		p.setStatus(status);
		p.setSize(new BigDecimal("1"));
		p.setEntryPrice(new BigDecimal("100"));
		p.setNotional(new BigDecimal("100"));
		return positionRepository.saveAndFlush(p);
	}

	private FuturesTradingAccount futuresAccount(User user) {
		ExchangeCredential cred = new ExchangeCredential();
		cred.setUser(user);
		cred.setExchange(com.shyblack.cryptosignals.entity.enums.ExchangeName.BINANCE);
		cred.setApiKey("dGVzdC1vbmx5LWtleQ==");
		cred.setApiSecret("dGVzdC1vbmx5LXNlY3JldA==");
		cred = credentialRepository.saveAndFlush(cred);

		FuturesTradingAccount a = new FuturesTradingAccount();
		a.setUser(user);
		a.setCredential(cred);
		a.setExchange(com.shyblack.cryptosignals.entity.enums.ExchangeName.BINANCE);
		a.setMarginAsset("USDT");
		a.setWalletBalance(new BigDecimal("10000"));
		return futuresAccountRepository.saveAndFlush(a);
	}

	private PortfolioExchangePosition exchangePosition(User user, String symbol,
			String positionSide, BigDecimal amount) {
		PortfolioExchangePosition p = new PortfolioExchangePosition();
		p.setUser(user);
		p.setExchange(com.shyblack.cryptosignals.entity.enums.ExchangeName.BINANCE);
		p.setSymbol(symbol);
		p.setPositionSide(PositionSide.valueOf(positionSide));
		p.setPositionAmount(amount);
		p.setEntryPrice(new BigDecimal("100"));
		p.setMarkPrice(new BigDecimal("105"));
		p.setNotional(new BigDecimal("105"));
		p.setFetchedAt(java.time.Instant.now());
		return exchangePositionRepository.saveAndFlush(p);
	}

	private FuturesPosition futuresPosition(FuturesTradingAccount account, String symbol,
			PositionSide side, FuturesPositionStatus status) {
		FuturesPosition p = new FuturesPosition();
		p.setAccount(account);
		p.setSymbol(symbol);
		p.setPositionSide(side);
		p.setStatus(status);
		p.setQuantity(new BigDecimal("1"));
		p.setEntryPrice(new BigDecimal("100"));
		p.setInitialMargin(new BigDecimal("10"));
		return futuresPositionRepository.saveAndFlush(p);
	}
}
