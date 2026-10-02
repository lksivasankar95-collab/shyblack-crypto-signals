package com.shyblack.cryptosignals.service.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.dto.portfolio.PortfolioClosedPositionView;
import com.shyblack.cryptosignals.dto.portfolio.PortfolioClosedPositionsResponse;
import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountAvailability;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.PositionStatus;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.exchange.futures.FuturesExchangeAdapter;
import com.shyblack.cryptosignals.exchange.futures.FuturesIncomeSnapshot;
import com.shyblack.cryptosignals.exchange.futures.FuturesOrderResult;
import com.shyblack.cryptosignals.exchange.futures.FuturesTradeSnapshot;
import com.shyblack.cryptosignals.exchange.futures.mock.MockFuturesExchangeAdapter;
import com.shyblack.cryptosignals.entity.Position;
import com.shyblack.cryptosignals.repository.ExchangeCredentialRepository;
import com.shyblack.cryptosignals.repository.PortfolioRepository;
import com.shyblack.cryptosignals.repository.PositionRepository;
import com.shyblack.cryptosignals.repository.SignalRepository;
import com.shyblack.cryptosignals.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Closed positions for one account scope.
 *
 * <p>The property under test is not "does it produce a row" but "does it refuse to invent
 * one". Every assertion below is about a field that must stay null rather than become a
 * zero, a guess, or a locally recomputed figure.
 */
@SpringBootTest
@TestPropertySource(properties = {
		"app.live-trading.mode=MOCK",
		"app.futures-trading.mode=MOCK",
		"spring.task.scheduling.enabled=false"
})
class PortfolioClosedPositionServiceTest {

	@Autowired
	private PortfolioClosedPositionService service;

	@Autowired
	private MockFuturesExchangeAdapter futuresAdapter;

	@Autowired
	private ExchangeCredentialRepository credentialRepository;

	@Autowired
	private PositionRepository positionRepository;

	@Autowired
	private SignalRepository signalRepository;

	@Autowired
	private PortfolioRepository portfolioRepository;

	@Autowired
	private UserRepository userRepository;

	private User user;
	private HistoryWindow window;

	@BeforeEach
	void setUp() {
		user = userRepository.save(newUser(AccountType.LIVE));

		ExchangeCredential credential = new ExchangeCredential();
		credential.setUser(user);
		credential.setExchange(ExchangeName.BINANCE);
		credential.setApiKey("enc:key");
		credential.setApiSecret("enc:secret");
		credentialRepository.save(credential);

		// The mock adapter is a shared singleton across the whole application context, so its
		// seeded fills accumulate across test methods. Clearing them is what keeps each
		// reconstruction assertion about only the fills this test declared.
		futuresAdapter.clearSeeded();

		// A bounded window, exactly as the controller resolves one.
		window = HistoryWindow.resolve(
				java.time.Instant.now().minusSeconds(86400),
				java.time.Instant.now(),
				200);
	}

	// ------------------------------------------------------- A. scope refusal

	@Nested
	@DisplayName("A. scope isolation")
	class ScopeIsolation {

		@Test
		@DisplayName("live spot has no closed-position record, because spot has no leveraged position lifecycle")
		void liveSpotIsUnsupported() {
			PortfolioClosedPositionsResponse response =
					service.closedPositions(user, AccountMode.LIVE, AccountCategory.SPOT, window, null);

			assertThat(response.availability()).isEqualTo(AccountAvailability.UNSUPPORTED);
			assertThat(response.positions()).isEmpty();
			assertThat(response.statusMessage()).contains("wallet assets rather than leveraged positions");
		}

		@Test
		@DisplayName("options is unsupported in both account modes")
		void optionsIsUnsupported() {
			for (AccountMode mode : AccountMode.values()) {
				PortfolioClosedPositionsResponse response =
						service.closedPositions(user, mode, AccountCategory.OPTIONS, window, null);

				assertThat(response.availability()).isEqualTo(AccountAvailability.UNSUPPORTED);
				assertThat(response.positions()).isEmpty();
			}
		}

		@Test
		@DisplayName("MAIN refuses to serve, because it would merge spot and futures records")
		void mainRefusesToMerge() {
			PortfolioClosedPositionsResponse response =
					service.closedPositions(user, AccountMode.LIVE, AccountCategory.MAIN, window, null);

			assertThat(response.positions()).isEmpty();
			assertThat(response.statusMessage()).contains("would mix the spot and futures");
		}
	}

	// ------------------------------------------------ B. reconstruction

	@Nested
	@DisplayName("B. live futures reconstruction from real fills")
	class Reconstruction {

		@Test
		@DisplayName("a full round trip yields entry, exit, quantity and exchange-reported realized P&L")
		void fullRoundTrip() {
			java.time.Instant open = java.time.Instant.now().minusSeconds(7200);
			java.time.Instant close = java.time.Instant.now().minusSeconds(3600);

			futuresAdapter.putTrade(trade(1L, 101L, "BUY", "LONG", "60000", "0.5", null, open));
			futuresAdapter.putTrade(trade(2L, 102L, "SELL", "LONG", "62000", "0.5", "1000", close));

			PortfolioClosedPositionsResponse response =
					service.closedPositions(user, AccountMode.LIVE, AccountCategory.FUTURES, window, null);

			assertThat(response.availability()).isEqualTo(AccountAvailability.AVAILABLE);
			assertThat(response.positions()).hasSize(1);
			assertThat(response.partial())
					.as("both sides of the round trip were observable")
					.isFalse();

			PortfolioClosedPositionView position = response.positions().get(0);
			assertThat(position.symbol()).isEqualTo("BTCUSDT");
			assertThat(position.side()).isEqualTo("LONG");
			assertThat(position.entryPrice()).isEqualByComparingTo("60000");
			assertThat(position.exitPrice()).isEqualByComparingTo("62000");
			assertThat(position.quantity()).isEqualByComparingTo("0.5");
			// Realized P&L is the exchange's own per-fill value, not entry minus exit.
			assertThat(position.realizedPnl()).isEqualByComparingTo("1000");
			assertThat(position.openedAt()).isEqualTo(open);
			assertThat(position.closedAt()).isEqualTo(close);
			assertThat(position.duration()).isNotNull();
			assertThat(position.orderIds()).containsExactly(101L, 102L);
			assertThat(position.tradeIds()).containsExactly(1L, 2L);
		}

		@Test
		@DisplayName("funding is always null, because a funding-fee record has no position attribution")
		void fundingIsNeverAttributedToAPosition() {
			java.time.Instant open = java.time.Instant.now().minusSeconds(7200);
			java.time.Instant close = java.time.Instant.now().minusSeconds(3600);

			futuresAdapter.putTrade(trade(1L, 101L, "BUY", "LONG", "60000", "0.5", null, open));
			futuresAdapter.putTrade(trade(2L, 102L, "SELL", "LONG", "62000", "0.5", "1000", close));
			// A funding income record exists in the same window. It must not be pulled into
			// the position record, because the exchange provides no such linkage.
			futuresAdapter.putIncome(new FuturesIncomeSnapshot(
					"BTCUSDT", "FUNDING_FEE", new java.math.BigDecimal("-3.5"),
					"USDT", 9001L, close));

			PortfolioClosedPositionView position = service
					.closedPositions(user, AccountMode.LIVE, AccountCategory.FUTURES, window, null)
					.positions().get(0);

			assertThat(position.funding())
					.as("funding has no position attribution upstream")
					.isNull();
		}

		@Test
		@DisplayName("a round trip whose opening fill was never observed leaves the entry price unknown")
		void unobservableOpeningLeavesEntryPriceUnknown() {
			java.time.Instant base = java.time.Instant.now().minusSeconds(900);

			// Open a long and flip through zero into a short. The flip is a single exchange
			// fill that both closes the long and opens the short, so splitting its quantity and
			// P&L across two records would be a guess. The short that the flip opened
			// therefore has no observable opening fill, and its entry price stays unknown.
			futuresAdapter.putTrade(trade(1L, 101L, "BUY", "BOTH", "60000", "1", null, base));
			futuresAdapter.putTrade(trade(2L, 102L, "SELL", "BOTH", "61000", "3", "1000",
					base.plusSeconds(60)));
			futuresAdapter.putTrade(trade(3L, 103L, "BUY", "BOTH", "59000", "2", "400",
					base.plusSeconds(120)));

			PortfolioClosedPositionsResponse response =
					service.closedPositions(user, AccountMode.LIVE, AccountCategory.FUTURES, window, null);

			assertThat(response.positions()).hasSize(2);

			// Newest close first, so the short (closed later) leads.
			PortfolioClosedPositionView shortPosition = response.positions().get(0);
			assertThat(shortPosition.side()).isEqualTo("SHORT");
			assertThat(shortPosition.entryPrice())
					.as("an opening fill that was never observed must stay unknown, not be guessed")
					.isNull();
			// The closing side of that short was fully observed, so it is still reported.
			assertThat(shortPosition.exitPrice()).isEqualByComparingTo("59000");

			PortfolioClosedPositionView longPosition = response.positions().get(1);
			assertThat(longPosition.side()).isEqualTo("LONG");
			assertThat(longPosition.entryPrice())
					.as("the long's opening fill was observed")
					.isEqualByComparingTo("60000");
			assertThat(longPosition.realizedPnl()).isEqualByComparingTo("1000");

			assertThat(response.partial())
					.as("the caller must be told the reconstruction is incomplete")
					.isTrue();
		}

		@Test
		@DisplayName("a lone reducing fill is not reported as a closed position")
		void loneReducingFillIsNotAClosedPosition() {
			// A single sell could be opening a short rather than closing a long. Reporting it as a
			// closed position would be a guess, so nothing is emitted.
			futuresAdapter.putTrade(trade(7L, 107L, "SELL", "BOTH", "62000", "0.5", null,
					java.time.Instant.now().minusSeconds(600)));

			PortfolioClosedPositionsResponse response =
					service.closedPositions(user, AccountMode.LIVE, AccountCategory.FUTURES, window, null);

			assertThat(response.positions())
					.as("an unpaired fill is not evidence of a completed round trip")
					.isEmpty();
		}

		@Test
		@DisplayName("an unreported commission leaves fees unknown rather than zero")
		void unreportedCommissionLeavesFeesUnknown() {
			java.time.Instant open = java.time.Instant.now().minusSeconds(7200);
			java.time.Instant close = java.time.Instant.now().minusSeconds(3600);

			futuresAdapter.putTrade(trade(1L, 101L, "BUY", "LONG", "60000", "0.5", null, open));
			// The closing fill reports no commission at all.
			futuresAdapter.putTrade(trade(2L, 102L, "SELL", "LONG", "62000", "0.5", "1000", close));

			PortfolioClosedPositionView position = service
					.closedPositions(user, AccountMode.LIVE, AccountCategory.FUTURES, window, null)
					.positions().get(0);

			assertThat(position.fees())
					.as("a partial fee sum would understate the cost; zero would claim no fee was charged")
					.isNull();
		}

		@Test
		@DisplayName("an unreported realized P&L leaves the total unknown rather than zero")
		void unreportedRealizedPnlLeavesTotalUnknown() {
			java.time.Instant open = java.time.Instant.now().minusSeconds(7200);
			java.time.Instant close = java.time.Instant.now().minusSeconds(3600);

			futuresAdapter.putTrade(trade(1L, 101L, "BUY", "LONG", "60000", "0.5", null, open));
			futuresAdapter.putTrade(trade(2L, 102L, "SELL", "LONG", "62000", "0.5", null, close));

			PortfolioClosedPositionView position = service
					.closedPositions(user, AccountMode.LIVE, AccountCategory.FUTURES, window, null)
					.positions().get(0);

			assertThat(position.realizedPnl())
					.as("zero would be indistinguishable from a genuinely break-even close")
					.isNull();
		}

		@Test
		@DisplayName("a position still open at the end of the window is not reported as closed")
		void openPositionIsNotReported() {
			java.time.Instant open = java.time.Instant.now().minusSeconds(3600);
			futuresAdapter.putTrade(trade(1L, 101L, "BUY", "LONG", "60000", "0.5", null, open));

			PortfolioClosedPositionsResponse response =
					service.closedPositions(user, AccountMode.LIVE, AccountCategory.FUTURES, window, null);

			assertThat(response.positions())
					.as("an open position is not a closed position")
					.isEmpty();
		}

		@Test
		@DisplayName("short positions are reconstructed as SHORT, not as a sign convention")
		void shortRoundTripIsLabelledShort() {
			java.time.Instant open = java.time.Instant.now().minusSeconds(7200);
			java.time.Instant close = java.time.Instant.now().minusSeconds(3600);

			futuresAdapter.putTrade(trade(1L, 201L, "SELL", "SHORT", "60000", "0.5", null, open));
			futuresAdapter.putTrade(trade(2L, 202L, "BUY", "SHORT", "58000", "0.5", "1000", close));

			PortfolioClosedPositionView position = service
					.closedPositions(user, AccountMode.LIVE, AccountCategory.FUTURES, window, null)
					.positions().get(0);

			assertThat(position.side()).isEqualTo("SHORT");
			assertThat(position.entryPrice()).isEqualByComparingTo("60000");
			assertThat(position.exitPrice()).isEqualByComparingTo("58000");
		}

		@Test
		@DisplayName("a flip that crosses zero closes one round trip and never fabricates a second")
		void flipClosesOneRoundTrip() {
			java.time.Instant base = java.time.Instant.now().minusSeconds(7200);

			// Long 1, then sell 3: the long is closed and exposure flips short by 2.
			futuresAdapter.putTrade(trade(1L, 301L, "BUY", "BOTH", "60000", "1", null, base));
			futuresAdapter.putTrade(trade(2L, 302L, "SELL", "BOTH", "61000", "3", "1000", base.plusSeconds(60)));

			PortfolioClosedPositionsResponse response =
					service.closedPositions(user, AccountMode.LIVE, AccountCategory.FUTURES, window, null);

			// Exactly one closed round trip. The flip is not split into a second record with
			// guessed quantities and a split P&L.
			assertThat(response.positions()).hasSize(1);
			assertThat(response.positions().get(0).realizedPnl()).isEqualByComparingTo("1000");
		}

		@Test
		@DisplayName("a missing credential reports NOT_CONNECTED and substitutes no simulated data")
		void missingCredentialNeverFallsBackToPaper() {
			credentialRepository.deleteAll();

			PortfolioClosedPositionsResponse response =
					service.closedPositions(user, AccountMode.LIVE, AccountCategory.FUTURES, window, null);

			assertThat(response.availability()).isEqualTo(AccountAvailability.NOT_CONNECTED);
			assertThat(response.positions()).isEmpty();
			assertThat(response.statusMessage()).contains("never substituted");
		}
	}

	// ------------------------------------------------------------ C. paper

	@Nested
	@DisplayName("C. paper closed positions come from the paper engine's own records")
	class Paper {

		@Test
		@DisplayName("a closed paper position is reported from its recorded prices and timestamps")
		void closedPaperPosition() {
			User paperUser = userRepository.save(newUser(AccountType.PAPER));

			UUID futuresSignal = seedSignal(paperUser, "BTCUSDT", TradingMode.FUTURES);

			com.shyblack.cryptosignals.entity.Portfolio portfolio =
					new com.shyblack.cryptosignals.entity.Portfolio();
			portfolio.setUser(paperUser);
			portfolio.setName("paper");
			portfolio.setAccountType(AccountType.PAPER);
			portfolio.setQuoteCurrency("USDT");
			portfolio.setInitialBalance(new java.math.BigDecimal("10000"));
			portfolio.setTotalBalance(new java.math.BigDecimal("10000"));
			portfolio.setAvailableBalance(new java.math.BigDecimal("10000"));
			portfolio.setInvested(java.math.BigDecimal.ZERO);
			portfolio.setRealizedPnl(new java.math.BigDecimal("500"));
			portfolio.setTotalFees(java.math.BigDecimal.ONE);

			Position position = new Position();
			// saveAndFlush, not save: the position references this portfolio, and an unflushed parent
			// makes Hibernate reject the child as a transient reference.
			position.setPortfolio(portfolioRepository.saveAndFlush(portfolio));
			position.setSignalId(futuresSignal);
			position.setSymbol("BTCUSDT");
			position.setSide(PositionSide.LONG);
			position.setSize(new java.math.BigDecimal("1"));
			position.setOriginalSize(new java.math.BigDecimal("1"));
			position.setRemainingSize(java.math.BigDecimal.ZERO);
			position.setEntryPrice(new java.math.BigDecimal("60000"));
			position.setExitPrice(new java.math.BigDecimal("62000"));
			position.setAverageExitPrice(new java.math.BigDecimal("62000"));
			position.setRealizedPnl(new java.math.BigDecimal("500"));
			position.setEntryFee(new java.math.BigDecimal("0.6"));
			position.setExitFee(new java.math.BigDecimal("0.6"));
			position.setStatus(PositionStatus.CLOSED);
			position.setOpenedAt(java.time.Instant.now().minusSeconds(7200));
			position.setClosedAt(java.time.Instant.now().minusSeconds(3600));
			positionRepository.save(position);

			PortfolioClosedPositionsResponse response = service.closedPositions(
					paperUser, AccountMode.PAPER, AccountCategory.FUTURES, window, null);

			assertThat(response.availability()).isEqualTo(AccountAvailability.AVAILABLE);
			assertThat(response.positions()).hasSize(1);

			PortfolioClosedPositionView closed = response.positions().get(0);
			assertThat(closed.entryPrice()).isEqualByComparingTo("60000");
			// The engine's recorded average exit price is preferred over the last exit price.
			assertThat(closed.exitPrice()).isEqualByComparingTo("62000");
			assertThat(closed.realizedPnl()).isEqualByComparingTo("500");
			assertThat(closed.fees()).isEqualByComparingTo("1.2");
			assertThat(closed.duration()).isNotNull();
			// A simulated round trip has no exchange identifiers, and says so.
			assertThat(closed.orderIds()).isEmpty();
			assertThat(closed.tradeIds()).isEmpty();
			assertThat(response.statusMessage()).contains("no exchange order or trade identifiers");
			// Funding is never attributed to a simulated position either.
			assertThat(closed.funding()).isNull();
		}

		@Test
		@DisplayName("an open paper position is never reported as closed")
		void openPaperPositionIsNotClosed() {
			User paperUser = userRepository.save(newUser(AccountType.PAPER));

			com.shyblack.cryptosignals.entity.Portfolio portfolio =
					new com.shyblack.cryptosignals.entity.Portfolio();
			portfolio.setUser(paperUser);
			portfolio.setName("paper");
			portfolio.setAccountType(AccountType.PAPER);
			portfolio.setQuoteCurrency("USDT");
			portfolio.setInitialBalance(new java.math.BigDecimal("10000"));
			portfolio.setTotalBalance(new java.math.BigDecimal("10000"));
			portfolio.setAvailableBalance(new java.math.BigDecimal("5000"));
			portfolio.setInvested(new java.math.BigDecimal("5000"));
			portfolio.setRealizedPnl(java.math.BigDecimal.ZERO);
			portfolio.setTotalFees(java.math.BigDecimal.ZERO);

			UUID futuresSignal = seedSignal(paperUser, "ETHUSDT", TradingMode.FUTURES);

			Position position = new Position();
			// saveAndFlush, not save: the position references this portfolio, and an unflushed
			// parent makes Hibernate reject the child as a transient reference.
			position.setPortfolio(portfolioRepository.saveAndFlush(portfolio));
			position.setSignalId(futuresSignal);
			position.setSymbol("ETHUSDT");
			position.setSide(PositionSide.LONG);
			position.setSize(new java.math.BigDecimal("2"));
			position.setOriginalSize(new java.math.BigDecimal("2"));
			position.setRemainingSize(new java.math.BigDecimal("2"));
			position.setEntryPrice(new java.math.BigDecimal("3000"));
			position.setStatus(PositionStatus.OPEN);
			position.setOpenedAt(java.time.Instant.now().minusSeconds(600));
			positionRepository.save(position);

			PortfolioClosedPositionsResponse response = service.closedPositions(
					paperUser, AccountMode.PAPER, AccountCategory.FUTURES, window, null);

			assertThat(response.positions()).isEmpty();
		}
	}

	// ----------------------------------------------------------- helpers

	/**
	 * A persisted user. Every mandatory column is populated, because the schema enforces
	 * them and a null here would fail the fixture rather than the behaviour under test.
	 */
	private static User newUser(AccountType accountType) {
		User candidate = new User();
		candidate.setEmail("closed-" + System.nanoTime() + "@example.test");
		candidate.setFullName("Closed Position Test");
		candidate.setPasswordHash("hash");
		candidate.setAccountType(accountType);
		return candidate;
	}

	/**
	 * A market-scoped signal, because a paper position is attributed to a market category
	 * only through the signal it originated from.
	 */
	private UUID seedSignal(User owner, String symbol, TradingMode mode) {
		Signal signal = new Signal();
		signal.setCreatedBy(owner);
		signal.setSymbol(symbol);
		signal.setSide(PositionSide.LONG);
		signal.setTradingMode(mode);
		signal.setStrategyId(UUID.randomUUID());
		signal.setScore(80);
		// The schema enforces these, so the fixture would fail on a null rather than on the
		// behaviour under test.
		signal.setConfidence(80);
		signal.setEntryPrice(new java.math.BigDecimal("100"));
		signal.setTargetPrice(new java.math.BigDecimal("120"));
		signal.setStopLoss(new java.math.BigDecimal("90"));
		return signalRepository.save(signal).getId();
	}

	private static FuturesTradeSnapshot trade(
			long tradeId,
			long orderId,
			String side,
			String positionSide,
			String price,
			String quantity,
			String realizedPnl,
			java.time.Instant time) {
		return new FuturesTradeSnapshot(
				"BTCUSDT",
				tradeId,
				orderId,
				side,
				positionSide,
				new java.math.BigDecimal(price),
				new java.math.BigDecimal(quantity),
				null,
				null,
				null,
				realizedPnl == null ? null : new java.math.BigDecimal(realizedPnl),
				"USDT",
				null,
				time);
	}
}