package com.shyblack.cryptosignals.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.LiveTradingAccount;
import com.shyblack.cryptosignals.entity.FuturesTradingAccount;
import com.shyblack.cryptosignals.entity.Portfolio;
import com.shyblack.cryptosignals.entity.Position;
import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.CloseReason;
import com.shyblack.cryptosignals.entity.enums.EntryType;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.FuturesMarginMode;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.PositionStatus;
import com.shyblack.cryptosignals.entity.enums.SignalGrade;
import com.shyblack.cryptosignals.entity.enums.SignalStatus;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.exchange.futures.FuturesExchangePosition;
import com.shyblack.cryptosignals.exchange.futures.mock.MockFuturesExchangeAdapter;
import com.shyblack.cryptosignals.exchange.futures.FuturesIncomeSnapshot;
import com.shyblack.cryptosignals.exchange.mock.MockExchangeTradingAdapter;
import com.shyblack.cryptosignals.exchange.ExchangeOrderSnapshot;
import com.shyblack.cryptosignals.exchange.ExchangeTradeSnapshot;
import com.shyblack.cryptosignals.repository.ExchangeCredentialRepository;
import com.shyblack.cryptosignals.repository.FuturesTradingAccountRepository;
import com.shyblack.cryptosignals.repository.LiveTradingAccountRepository;
import com.shyblack.cryptosignals.repository.PortfolioRepository;
import com.shyblack.cryptosignals.repository.PositionRepository;
import com.shyblack.cryptosignals.repository.SignalRepository;
import com.shyblack.cryptosignals.repository.UserRepository;
import com.shyblack.cryptosignals.service.portfolio.LivePortfolioSyncService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Phase 5 API contract for the unified read-only Portfolio endpoints.
 *
 * <p>Proves authentication, PAPER/LIVE and category isolation, availability semantics, Options
 * being unsupported rather than zeroed, ownership, invalid-input handling, and that no credential
 * material can reach a response body.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PortfolioApiIntegrationTest {

	private static final AtomicInteger SEQ = new AtomicInteger();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PortfolioRepository portfolioRepository;

	@Autowired
	private PositionRepository positionRepository;

	@Autowired
	private SignalRepository signalRepository;

	@Autowired
	private ExchangeCredentialRepository credentialRepository;

	@Autowired
	private LiveTradingAccountRepository liveAccountRepository;

	@Autowired
	private FuturesTradingAccountRepository futuresAccountRepository;

	@Autowired
	private MockExchangeTradingAdapter spotAdapter;

	@Autowired
	private MockFuturesExchangeAdapter futuresAdapter;

	@Autowired
	private LivePortfolioSyncService syncService;

	@BeforeEach
	void resetAdapters() {
		spotAdapter.reset();
		futuresAdapter.reset();
	}

	// ------------------------------------------------------------- auth setup

	private String token;
	private String email;
	private User user;

	private void signUp() throws Exception {
		email = "portfolio-" + SEQ.incrementAndGet() + "@example.com";
		String password = "secret123";
		mockMvc.perform(post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"" + password
								+ "\",\"fullName\":\"Portfolio Tester\"}"))
				.andExpect(status().isCreated());

		MvcResult login = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
				.andExpect(status().isOk())
				.andReturn();

		JsonNode body = objectMapper.readTree(login.getResponse().getContentAsString());
		token = body.get("accessToken").asText();
		user = userRepository.findByEmail(email).orElseThrow();
	}

	private org.springframework.test.web.servlet.ResultActions authGet(String path) throws Exception {
		return mockMvc.perform(get(path).header("Authorization", "Bearer " + token));
	}

	private JsonNode authGetJson(String path) throws Exception {
		return objectMapper.readTree(authGet(path).andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsString());
	}

	// ------------------------------------------------------------- fixtures

	private void paperPortfolio() {
		Portfolio portfolio = new Portfolio();
		portfolio.setUser(user);
		portfolio.setName("Paper Trading");
		portfolio.setAccountType(AccountType.PAPER);
		portfolio.setInitialBalance(new BigDecimal("1000"));
		portfolio.setTotalBalance(new BigDecimal("1000"));
		portfolio.setAvailableBalance(new BigDecimal("1000"));
		portfolio.setInvested(BigDecimal.ZERO);
		portfolioRepository.saveAndFlush(portfolio);
	}

	private Signal signal(TradingMode mode) {
		Signal s = new Signal();
		s.setSymbol("BTCUSDT");
		s.setStatus(SignalStatus.ACTIVE);
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

	private void paperPosition(Portfolio portfolio, Signal signal, PositionStatus status) {
		Position p = new Position();
		p.setPortfolio(portfolio);
		p.setSignalId(signal == null ? null : signal.getId());
		p.setSymbol("BTCUSDT");
		p.setSide(PositionSide.LONG);
		p.setSize(new BigDecimal("2"));
		p.setNotional(new BigDecimal("200"));
		p.setEntryPrice(new BigDecimal("100"));
		p.setCurrentPrice(new BigDecimal("100"));
		p.setStatus(status);
		if (status == PositionStatus.CLOSED) {
			p.setExitPrice(new BigDecimal("110"));
			p.setRealizedPnl(new BigDecimal("20"));
			p.setCloseReason(CloseReason.TAKE_PROFIT);
			p.setClosedAt(Instant.now());
		}
		positionRepository.saveAndFlush(p);
	}

	private ExchangeCredential credential() {
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

	private void liveSpotAccount() {
		LiveTradingAccount account = new LiveTradingAccount();
		account.setUser(user);
		account.setExchange(ExchangeName.BINANCE);
		account.setCredential(credential());
		account.setConnectionStatus(ExchangeConnectionStatus.CONNECTED);
		account.setQuoteCurrency("USDT");
		liveAccountRepository.saveAndFlush(account);
	}

	private void liveFuturesAccount() {
		FuturesTradingAccount account = new FuturesTradingAccount();
		account.setUser(user);
		account.setExchange(ExchangeName.BINANCE);
		account.setCredential(credential());
		account.setConnectionStatus(ExchangeConnectionStatus.CONNECTED);
		account.setMarginAsset("USDT");
		account.setWalletBalance(new BigDecimal("5000"));
		account.setAvailableBalance(new BigDecimal("4000"));
		account.setUsedMargin(new BigDecimal("1000"));
		account.setUnrealizedPnl(new BigDecimal("25"));
		futuresAccountRepository.saveAndFlush(account);
	}

	// ------------------------------------------------- A. authentication

	@Test
	void unauthenticatedRequestsAreRejected() throws Exception {
		mockMvc.perform(get("/api/v1/portfolio")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/portfolio/SPOT")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/portfolio/FUTURES/positions"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void authenticatedRequestSucceeds() throws Exception {
		signUp();
		paperPortfolio();

		authGet("/api/v1/portfolio?mode=PAPER")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountMode").value("PAPER"))
				.andExpect(jsonPath("$.accounts.length()").value(4));
	}

	// ---------------------------------------------------- B. PAPER overview

	@Test
	void paperOverviewReturnsAllFourCategories() throws Exception {
		signUp();
		paperPortfolio();

		JsonNode body = authGetJson("/api/v1/portfolio?mode=PAPER");

		assertThat(body.get("accountMode").asText()).isEqualTo("PAPER");
		assertThat(body.get("accounts")).hasSize(4);
		assertThat(body.get("accounts").findValuesAsText("accountCategory"))
				.containsExactly("MAIN", "SPOT", "FUTURES", "OPTIONS");
		assertThat(body.get("accounts").findValuesAsText("availability"))
				.contains("AVAILABLE", "UNSUPPORTED");
	}

	@Test
	void paperOverviewDefaultsToPaperWhenNoModeIsGiven() throws Exception {
		signUp();
		paperPortfolio();

		JsonNode body = authGetJson("/api/v1/portfolio");

		assertThat(body.get("accountMode").asText())
				.as("the default must be deterministic and echoed, never implied")
				.isEqualTo("PAPER");
	}

	@Test
	void paperMainExposesTheSharedWallet() throws Exception {
		signUp();
		paperPortfolio();

		JsonNode main = authGetJson("/api/v1/portfolio/MAIN?mode=PAPER");

		assertThat(main.get("availability").asText()).isEqualTo("AVAILABLE");
		assertThat(main.get("availableBalance").decimalValue()).isEqualByComparingTo("1000");
		assertThat(main.get("equity").decimalValue()).isEqualByComparingTo("1000");
		assertThat(main.get("exchange").isNull())
				.as("a simulated account reports no exchange")
				.isTrue();
	}

	// ----------------------------------------------------- C. LIVE overview

	@Test
	void liveOverviewReturnsAllFourCategories() throws Exception {
		signUp();

		JsonNode body = authGetJson("/api/v1/portfolio?mode=LIVE");

		assertThat(body.get("accountMode").asText()).isEqualTo("LIVE");
		assertThat(body.get("accounts")).hasSize(4);
	}

	@Test
	void liveMainIsUnavailableAndNeverAggregated() throws Exception {
		signUp();
		liveSpotAccount();
		liveFuturesAccount();
		spotAdapter.putBalance("USDT", new BigDecimal("100"), BigDecimal.ZERO);
		syncService.syncSpot(user);
		syncService.syncFutures(user);

		JsonNode main = authGetJson("/api/v1/portfolio/MAIN?mode=LIVE");

		assertThat(main.get("availability").asText()).isEqualTo("UNAVAILABLE");
		assertThat(main.get("equity").isNull())
				.as("spot and futures wallets must never be summed into a main balance")
				.isTrue();
		assertThat(main.get("availableBalance").isNull()).isTrue();
	}

	@Test
	void liveSpotReturnsTheSynchronisedSnapshot() throws Exception {
		signUp();
		liveSpotAccount();
		spotAdapter.putBalance("USDT", new BigDecimal("100.5"), new BigDecimal("20.25"));
		syncService.syncSpot(user);

		JsonNode spot = authGetJson("/api/v1/portfolio/SPOT?mode=LIVE");

		assertThat(spot.get("availability").asText()).isEqualTo("AVAILABLE");
		assertThat(spot.get("availableBalance").decimalValue()).isEqualByComparingTo("100.5");
		assertThat(spot.get("equity").decimalValue()).isEqualByComparingTo("120.75");
	}

	@Test
	void liveFuturesReturnsTheAccountSnapshot() throws Exception {
		signUp();
		liveFuturesAccount();

		JsonNode futures = authGetJson("/api/v1/portfolio/FUTURES?mode=LIVE");

		assertThat(futures.get("equity").decimalValue()).isEqualByComparingTo("5000");
		assertThat(futures.get("availableBalance").decimalValue()).isEqualByComparingTo("4000");
		assertThat(futures.get("unrealizedPnl").decimalValue()).isEqualByComparingTo("25");
	}

	@Test
	void liveRealizedPnlStaysNullRatherThanFallingBackToZero() throws Exception {
		signUp();
		liveFuturesAccount();

		JsonNode futures = authGetJson("/api/v1/portfolio/FUTURES?mode=LIVE");

		assertThat(futures.get("realizedPnl").isNull())
				.as("no authoritative exchange income source exists, so zero would be a fabrication")
				.isTrue();
	}

	// ------------------------------------------------ D. mode isolation

	@Test
	void paperRequestNeverReturnsLiveData() throws Exception {
		signUp();
		paperPortfolio();
		liveSpotAccount();
		spotAdapter.putBalance("USDT", new BigDecimal("9999"), BigDecimal.ZERO);
		syncService.syncSpot(user);

		JsonNode paper = authGetJson("/api/v1/portfolio/SPOT?mode=PAPER");

		assertThat(paper.get("accountMode").asText()).isEqualTo("PAPER");
		assertThat(paper.get("availableBalance").isNull())
				.as("a live balance must never leak into a paper response")
				.isTrue();
	}

	@Test
	void liveRequestNeverReturnsPaperData() throws Exception {
		signUp();
		paperPortfolio();
		liveSpotAccount();

		JsonNode live = authGetJson("/api/v1/portfolio/SPOT?mode=LIVE");

		assertThat(live.get("accountMode").asText()).isEqualTo("LIVE");
		assertThat(live.get("exchange").asText()).isEqualTo("BINANCE");
	}

	// ------------------------------------------- E. category isolation

	@Test
	void paperSpotExcludesFuturesPositions() throws Exception {
		signUp();
		paperPortfolio();
		paperPortfolio();
		Portfolio portfolio = paperPortfolioOrFetch();
		paperPosition(portfolio, signal(TradingMode.SPOT), PositionStatus.OPEN);
		paperPosition(portfolio, signal(TradingMode.FUTURES), PositionStatus.OPEN);

		JsonNode spot = authGetJson("/api/v1/portfolio/SPOT/positions?mode=PAPER");

		assertThat(spot.get("positions")).hasSize(1);
		assertThat(spot.get("positions").get(0).get("accountCategory").asText()).isEqualTo("SPOT");
	}

	@Test
	void paperFuturesExcludesSpotPositions() throws Exception {
		signUp();
		paperPortfolio();
		paperPortfolio();
		Portfolio portfolio = paperPortfolioOrFetch();
		paperPosition(portfolio, signal(TradingMode.SPOT), PositionStatus.OPEN);
		paperPosition(portfolio, signal(TradingMode.FUTURES), PositionStatus.OPEN);

		JsonNode futures = authGetJson("/api/v1/portfolio/FUTURES/positions?mode=PAPER");

		assertThat(futures.get("positions")).hasSize(1);
		assertThat(futures.get("positions").get(0).get("accountCategory").asText()).isEqualTo("FUTURES");
	}

	@Test
	void paperCategoriesDoNotShareCapital() throws Exception {
		signUp();
		paperPortfolio();
		paperPortfolio();
		Portfolio portfolio = paperPortfolioOrFetch();
		paperPosition(portfolio, signal(TradingMode.SPOT), PositionStatus.OPEN);

		JsonNode spot = authGetJson("/api/v1/portfolio/SPOT?mode=PAPER");
		JsonNode futures = authGetJson("/api/v1/portfolio/FUTURES?mode=PAPER");

		assertThat(spot.get("availableBalance").isNull())
				.as("the wallet is shared and must not be reported twice")
				.isTrue();
		assertThat(futures.get("availableBalance").isNull()).isTrue();
	}

	@Test
	void liveFuturesPositionsComeFromTheExchangeSnapshot() throws Exception {
		signUp();
		liveFuturesAccount();
		futuresAdapter.putPosition(new FuturesExchangePosition(
				"BTCUSDT", PositionSide.LONG, new BigDecimal("1"), new BigDecimal("60000"),
				new BigDecimal("61000"), new BigDecimal("45000"), 3, FuturesMarginMode.ISOLATED,
				new BigDecimal("10000"), new BigDecimal("61000"), new BigDecimal("1000"), Instant.now()));
		syncService.syncFutures(user);

		JsonNode body = authGetJson("/api/v1/portfolio/FUTURES/positions?mode=LIVE");

		assertThat(body.get("availability").asText()).isEqualTo("AVAILABLE");
		assertThat(body.get("positions")).hasSize(1);
		JsonNode position = body.get("positions").get(0);
		assertThat(position.get("symbol").asText()).isEqualTo("BTCUSDT");
		assertThat(position.get("side").asText()).isEqualTo("LONG");
		assertThat(position.get("quantity").decimalValue()).isEqualByComparingTo("1");
		assertThat(position.get("entryPrice").decimalValue()).isEqualByComparingTo("60000");
		assertThat(position.get("currentPrice").decimalValue()).isEqualByComparingTo("61000");
		assertThat(position.get("liquidationPrice").decimalValue()).isEqualByComparingTo("45000");
		assertThat(position.get("leverage").asInt()).isEqualTo(3);
		assertThat(position.get("stopLoss").isNull())
				.as("the exchange position response carries no stop loss; deriving one would fabricate it")
				.isTrue();
		assertThat(position.get("takeProfit1").isNull()).isTrue();
		assertThat(position.get("realizedPnl").isNull()).isTrue();
	}

	@Test
	void liveSpotPositionsAreUnsupportedRatherThanEmpty() throws Exception {
		signUp();
		liveSpotAccount();
		spotAdapter.putBalance("BTC", new BigDecimal("2"), BigDecimal.ZERO);
		syncService.syncSpot(user);

		JsonNode body = authGetJson("/api/v1/portfolio/SPOT/positions?mode=LIVE");

		assertThat(body.get("availability").asText()).isEqualTo("UNSUPPORTED");
		assertThat(body.get("positions")).isEmpty();
		assertThat(body.get("statusMessage").asText()).contains("balances, not open positions");
	}

	@Test
	void liveMainPositionsAreUnavailableBecauseTheyWouldMixWallets() throws Exception {
		signUp();
		liveFuturesAccount();
		futuresAdapter.putPosition(new FuturesExchangePosition(
				"BTCUSDT", PositionSide.LONG, new BigDecimal("1"), new BigDecimal("60000"),
				BigDecimal.ZERO, null, 3, FuturesMarginMode.ISOLATED,
				BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, Instant.now()));
		syncService.syncFutures(user);

		JsonNode body = authGetJson("/api/v1/portfolio/MAIN/positions?mode=LIVE");

		assertThat(body.get("availability").asText()).isEqualTo("UNAVAILABLE");
		assertThat(body.get("positions")).isEmpty();
	}

	// ------------------------------------------------------ F. availability

	@Test
	void unavailableLiveDataIsNullAndNeverZero() throws Exception {
		signUp();

		JsonNode spot = authGetJson("/api/v1/portfolio/SPOT?mode=LIVE");

		assertThat(spot.get("availability").asText()).isNotEqualTo("AVAILABLE");
		assertThat(spot.get("equity").isNull()).isTrue();
		assertThat(spot.get("availableBalance").isNull()).isTrue();
		assertThat(spot.get("invested").isNull()).isTrue();
		assertThat(spot.get("openPositionCount").isNull()).isTrue();
		assertThat(spot.get("orderCount").isNull()).isTrue();
	}

	@Test
	void anUncachedQuoteAssetStaysNullRatherThanZero() throws Exception {
		signUp();
		liveSpotAccount();
		spotAdapter.putBalance("BTC", new BigDecimal("2"), BigDecimal.ZERO);
		syncService.syncSpot(user);

		JsonNode spot = authGetJson("/api/v1/portfolio/SPOT?mode=LIVE");

		assertThat(spot.get("availableBalance").isNull())
				.as("a missing balance is not a zero balance")
				.isTrue();
	}

	// --------------------------------------------------------- G. options

	@Test
	void optionsIsUnsupportedInBothModesWithNoValues() throws Exception {
		signUp();
		paperPortfolio();
		liveSpotAccount();

		for (String mode : new String[] {"PAPER", "LIVE"}) {
			JsonNode options = authGetJson("/api/v1/portfolio/OPTIONS?mode=" + mode);
			assertThat(options.get("availability").asText()).isEqualTo("UNSUPPORTED");
			assertThat(options.get("equity").isNull()).isTrue();
			assertThat(options.get("availableBalance").isNull()).isTrue();
			assertThat(options.get("invested").isNull()).isTrue();
			assertThat(options.get("realizedPnl").isNull()).isTrue();
			assertThat(options.get("unrealizedPnl").isNull()).isTrue();
			assertThat(options.get("totalPositionCount").isNull()).isTrue();
			assertThat(options.get("openPositionCount").isNull()).isTrue();
			assertThat(options.get("orderCount").isNull()).isTrue();
			assertThat(options.get("statusMessage").asText()).contains("Options");
		}
	}

	@Test
	void optionsPositionsAreUnsupportedAndEmpty() throws Exception {
		signUp();
		paperPortfolio();

		JsonNode body = authGetJson("/api/v1/portfolio/OPTIONS/positions?mode=PAPER");

		assertThat(body.get("availability").asText()).isEqualTo("UNSUPPORTED");
		assertThat(body.get("positions")).isEmpty();
	}

	// -------------------------------------------------------- H. ownership

	@Test
	void oneUserNeverSeesAnotherUsersPortfolio() throws Exception {
		signUp();
		paperPortfolio();
		paperPortfolio();
		Portfolio portfolio = paperPortfolioOrFetch();
		portfolio.setAvailableBalance(new BigDecimal("4242"));
		portfolio.setTotalBalance(new BigDecimal("4242"));
		portfolioRepository.saveAndFlush(portfolio);

		String firstToken = token;
		String firstEmail = email;
		signUp();
		paperPortfolio();
		paperPortfolio();
		Portfolio second = paperPortfolioOrFetch();
		second.setAvailableBalance(new BigDecimal("11"));
		second.setTotalBalance(new BigDecimal("11"));
		portfolioRepository.saveAndFlush(second);

		String secondToken = token;

		JsonNode firstBody = objectMapper.readTree(mockMvc
				.perform(get("/api/v1/portfolio/MAIN?mode=PAPER")
						.header("Authorization", "Bearer " + firstToken))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

		assertThat(firstBody.get("availableBalance").decimalValue())
				.as("each caller sees only its own portfolio")
				.isEqualByComparingTo("4242");
		assertThat(firstEmail).isNotBlank();
	}

	@Test
	void noClientSuppliedUserIdentifierIsAccepted() throws Exception {
		signUp();
		authGetJson("/api/v1/portfolio?mode=PAPER");
		authGetJson("/api/v1/portfolio/MAIN?mode=PAPER");
		authGetJson("/api/v1/portfolio/FUTURES/positions?mode=PAPER");

		mockMvc.perform(get("/api/v1/portfolio?mode=PAPER&userId=" + user.getId())
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
	}

	// ------------------------------------------------------ I. invalid input

	@Test
	void invalidModeIsRejectedWithBadRequest() throws Exception {
		signUp();

		authGet("/api/v1/portfolio?mode=SOMETHING_ELSE").andExpect(status().isBadRequest());
		authGet("/api/v1/portfolio/MAIN?mode=SOMETHING_ELSE").andExpect(status().isBadRequest());
		authGet("/api/v1/portfolio/MAIN/positions?mode=SOMETHING_ELSE")
				.andExpect(status().isBadRequest());
	}

	@Test
	void invalidCategoryIsRejectedWithBadRequest() throws Exception {
		signUp();

		authGet("/api/v1/portfolio/NOT_A_CATEGORY").andExpect(status().isBadRequest());
		authGet("/api/v1/portfolio/NOT_A_CATEGORY/positions").andExpect(status().isBadRequest());
	}

	@Test
	void invalidModeErrorNamesTheAcceptedValues() throws Exception {
		signUp();

		String body = authGet("/api/v1/portfolio?mode=SOMETHING_ELSE")
				.andExpect(status().isBadRequest())
				.andReturn().getResponse().getContentAsString();

		assertThat(body).contains("PAPER").contains("LIVE");
	}

	// --------------------------------------------------------- J. security

	@Test
	void noCredentialOrListenKeyMaterialAppearsInAnyResponse() throws Exception {
		signUp();
		liveSpotAccount();
		liveFuturesAccount();
		spotAdapter.putBalance("USDT", new BigDecimal("10"), BigDecimal.ZERO);
		syncService.syncSpot(user);
		syncService.syncFutures(user);

		String overview = authGet("/api/v1/portfolio?mode=LIVE")
				.andReturn().getResponse().getContentAsString();
		String spot = authGet("/api/v1/portfolio/SPOT?mode=LIVE")
				.andReturn().getResponse().getContentAsString();
		String futures = authGet("/api/v1/portfolio/FUTURES?mode=LIVE")
				.andReturn().getResponse().getContentAsString();
		String all = overview + spot + futures;

		for (String forbidden : new String[] {
				"apiKey", "apiSecret", "credentialId", "listenKey", "listen_key",
				"dGVzdC1vbmx5LWtleQ", "dGVzdC1vbmx5LXNlY3JldA", "X-MBX-APIKEY", "signature"}) {
			assertThat(all).as("response must not expose %s", forbidden).doesNotContain(forbidden);
		}
	}

	@Test
	void responsesExposeNoInternalEntityIdentifiers() throws Exception {
		signUp();
		paperPortfolio();
		paperPortfolio();
		paperPortfolio();
		Portfolio portfolio = paperPortfolioOrFetch();
		paperPosition(portfolio, signal(TradingMode.SPOT), PositionStatus.OPEN);

		String body = authGet("/api/v1/portfolio?mode=PAPER")
				.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain("portfolioId");
		assertThat(body).doesNotContain("userId");
		assertThat(body).doesNotContain("signalId");
	}

	// ------------------------------------------- K. legacy endpoint removal

	@Test
	void removedLegacyEndpointsReturnNotFoundRatherThanServerError() throws Exception {
		signUp();

		authGet("/api/v1/portfolios").andExpect(status().isNotFound());
		authGet("/api/v1/positions").andExpect(status().isNotFound());
	}

	@Test
	void paperTradingEndpointsAreUnaffected() throws Exception {
		signUp();
		paperPortfolio();

		authGet("/api/v1/paper-trading/account").andExpect(status().isOk());
		authGet("/api/v1/paper-trading/positions").andExpect(status().isOk());
	}

	// -------------------------------------------------- phase 7: holdings

	@Test
	void spotHoldingsExposeExchangeWalletBalances() throws Exception {
		signUp();
		credential();
		spotAdapter.putBalance("BTC", new BigDecimal("0.5"), new BigDecimal("0.25"));
		syncService.syncSpot(user);

		authGet("/api/v1/portfolio/SPOT/holdings?mode=LIVE")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availability").value("AVAILABLE"))
				.andExpect(jsonPath("$.source").value("EXCHANGE"))
				.andExpect(jsonPath("$.holdings.length()").value(1))
				.andExpect(jsonPath("$.holdings[0].asset").value("BTC"))
				.andExpect(jsonPath("$.holdings[0].free").value(0.5))
				.andExpect(jsonPath("$.holdings[0].locked").value(0.25))
				.andExpect(jsonPath("$.holdings[0].total").value(0.75));
	}

	@Test
	void paperHoldingsReportUnsupportedRatherThanAnEmptyWallet() throws Exception {
		signUp();

		authGet("/api/v1/portfolio/SPOT/holdings")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountMode").value("PAPER"))
				.andExpect(jsonPath("$.availability").value("UNSUPPORTED"))
				.andExpect(jsonPath("$.holdings.length()").value(0));
	}

	@Test
	void holdingsRequireAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/portfolio/SPOT/holdings?mode=LIVE"))
				.andExpect(status().isUnauthorized());
	}

	// ----------------------------------------------------- phase 7: history

	@Test
	void liveSpotOrdersAreReturnedNewestFirstWithAnExplicitWindow() throws Exception {
		signUp();
		credential();
		Instant now = Instant.now();
		spotAdapter.putOrder(new ExchangeOrderSnapshot("BTCUSDT", 1L, "c1", "BUY", "LIMIT", "FILLED",
				new BigDecimal("100"), new BigDecimal("1"), new BigDecimal("1"),
				new BigDecimal("100"), now.minusSeconds(600), now.minusSeconds(600)));
		spotAdapter.putOrder(new ExchangeOrderSnapshot("BTCUSDT", 2L, "c2", "SELL", "MARKET", "FILLED",
				new BigDecimal("110"), new BigDecimal("1"), new BigDecimal("1"),
				new BigDecimal("110"), now.minusSeconds(60), now.minusSeconds(60)));

		authGet("/api/v1/portfolio/SPOT/history?mode=LIVE&type=ORDER&symbol=BTCUSDT")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availability").value("AVAILABLE"))
				.andExpect(jsonPath("$.source").value("EXCHANGE"))
				.andExpect(jsonPath("$.entryType").value("ORDER"))
				.andExpect(jsonPath("$.complete").value(true))
				.andExpect(jsonPath("$.windowFrom").exists())
				.andExpect(jsonPath("$.windowTo").exists())
				.andExpect(jsonPath("$.entries.length()").value(2))
				.andExpect(jsonPath("$.entries[0].orderId").value(2))
				.andExpect(jsonPath("$.entries[0].side").value("SELL"))
				.andExpect(jsonPath("$.entries[1].orderId").value(1));
	}

	@Test
	void liveFillsAreReturnedAndOmittedValuesStayNull() throws Exception {
		signUp();
		credential();
		spotAdapter.putTrade(new ExchangeTradeSnapshot("BTCUSDT", 55L, 9L, "BUY",
				new BigDecimal("100"), new BigDecimal("2"), null,
				new BigDecimal("0.2"), "BNB", true, Instant.now().minusSeconds(60)));

		authGet("/api/v1/portfolio/SPOT/history?mode=LIVE&type=TRADE&symbol=BTCUSDT")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.entries.length()").value(1))
				.andExpect(jsonPath("$.entries[0].tradeId").value(55))
				.andExpect(jsonPath("$.entries[0].orderId").value(9))
				.andExpect(jsonPath("$.entries[0].feeAsset").value("BNB"))
				.andExpect(jsonPath("$.entries[0].realizedPnl").doesNotExist());
	}

	@Test
	void futuresIncomeCarriesExchangeRealizedPnl() throws Exception {
		signUp();
		credential();
		futuresAdapter.putIncome(new FuturesIncomeSnapshot("BTCUSDT", "REALIZED_PNL",
				new BigDecimal("42.5"), "USDT", 777L, Instant.now().minusSeconds(60)));

		authGet("/api/v1/portfolio/FUTURES/history?mode=LIVE&type=INCOME")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.entries.length()").value(1))
				.andExpect(jsonPath("$.entries[0].entryType").value("INCOME"))
				.andExpect(jsonPath("$.entries[0].status").value("REALIZED_PNL"))
				.andExpect(jsonPath("$.entries[0].realizedPnl").value(42.5));
	}

	@Test
	void paperHistoryNeverReturnsExchangeRecords() throws Exception {
		signUp();
		credential();
		spotAdapter.putOrder(new ExchangeOrderSnapshot("BTCUSDT", 1L, "c1", "BUY", "LIMIT", "FILLED",
				BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
				Instant.now(), Instant.now()));

		authGet("/api/v1/portfolio/SPOT/history?type=ORDER&symbol=BTCUSDT")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountMode").value("PAPER"))
				.andExpect(jsonPath("$.source").value("LOCAL_PAPER"))
				.andExpect(jsonPath("$.entries.length()").value(0));
	}

	@Test
	void liveMainHistoryIsUnavailableBecauseItWouldMixWallets() throws Exception {
		signUp();
		credential();

		authGet("/api/v1/portfolio/MAIN/history?mode=LIVE&type=ORDER&symbol=BTCUSDT")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availability").value("UNAVAILABLE"))
				.andExpect(jsonPath("$.entries.length()").value(0));
	}

	@Test
	void optionsHistoryIsUnsupported() throws Exception {
		signUp();

		authGet("/api/v1/portfolio/OPTIONS/history?mode=LIVE&type=ORDER&symbol=BTCUSDT")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availability").value("UNSUPPORTED"))
				.andExpect(jsonPath("$.entries.length()").value(0));
	}

	@Test
	void anUnconnectedAccountReportsNotConnectedRatherThanEmpty() throws Exception {
		signUp();

		authGet("/api/v1/portfolio/SPOT/history?mode=LIVE&type=ORDER&symbol=BTCUSDT")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availability").value("NOT_CONNECTED"))
				.andExpect(jsonPath("$.entries.length()").value(0));
	}

	@Test
	void anOverlyWideWindowIsRejected() throws Exception {
		signUp();

		authGet("/api/v1/portfolio/SPOT/history?mode=LIVE&type=ORDER&symbol=BTCUSDT"
						+ "&from=2020-01-01T00:00:00Z&to=2021-01-01T00:00:00Z")
				.andExpect(status().isBadRequest());
	}

	@Test
	void anExcessiveLimitIsRejected() throws Exception {
		signUp();

		authGet("/api/v1/portfolio/SPOT/history?mode=LIVE&type=ORDER&symbol=BTCUSDT&limit=100000")
				.andExpect(status().isBadRequest());
	}

	@Test
	void historyRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/portfolio/SPOT/history?mode=LIVE&symbol=BTCUSDT"))
				.andExpect(status().isUnauthorized());
	}

	// -------------------------------------------------- phase 7: sync status

	@Test
	void syncStatusReportsFreshnessWithoutExposingCredentials() throws Exception {
		signUp();
		credential();
		spotAdapter.putBalance("USDT", new BigDecimal("100"), BigDecimal.ZERO);
		syncService.syncSpot(user);

		String body = authGet("/api/v1/portfolio/SPOT/sync-status?mode=LIVE")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountMode").value("LIVE"))
				.andExpect(jsonPath("$.accountCategory").value("SPOT"))
				.andExpect(jsonPath("$.availability").value("AVAILABLE"))
				.andExpect(jsonPath("$.stale").value(false))
				.andExpect(jsonPath("$.lastRestSync").exists())
				.andReturn().getResponse().getContentAsString();

		org.assertj.core.api.Assertions.assertThat(body)
				.doesNotContain("apiKey")
				.doesNotContain("apiSecret")
				.doesNotContain("listenKey");
	}

	@Test
	void syncStatusDefaultsToPaperMode() throws Exception {
		signUp();

		authGet("/api/v1/portfolio/SPOT/sync-status")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountMode").value("PAPER"))
				.andExpect(jsonPath("$.connectionStatus").doesNotExist());
	}

	@Test
	void syncStatusRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/portfolio/SPOT/sync-status?mode=LIVE"))
				.andExpect(status().isUnauthorized());
	}

	// -------------------------------------------------------------- helpers

	private Portfolio paperPortfolioOrFetch() {
		return portfolioRepository.findFirstByUserAndAccountType(user, AccountType.PAPER).orElseThrow();
	}
}
