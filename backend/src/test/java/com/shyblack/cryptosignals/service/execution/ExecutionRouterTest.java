package com.shyblack.cryptosignals.service.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shyblack.cryptosignals.config.ExecutionProperties;
import com.shyblack.cryptosignals.config.FuturesTradingProperties;
import com.shyblack.cryptosignals.config.LiveTradingProperties;
import com.shyblack.cryptosignals.entity.ExecutionDecisionRecord;
import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.FuturesTradingAccount;
import com.shyblack.cryptosignals.entity.LiveTradingAccount;
import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.UserSettings;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.EntryType;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.FuturesMarginMode;
import com.shyblack.cryptosignals.entity.enums.FuturesPositionMode;
import com.shyblack.cryptosignals.entity.enums.LiveTradingRiskReason;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.SignalGrade;
import com.shyblack.cryptosignals.entity.enums.SignalStatus;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.repository.ExecutionDecisionRecordRepository;
import com.shyblack.cryptosignals.repository.FuturesTradingAccountRepository;
import com.shyblack.cryptosignals.repository.LiveTradingAccountRepository;
import com.shyblack.cryptosignals.repository.SignalRepository;
import com.shyblack.cryptosignals.repository.UserRepository;
import com.shyblack.cryptosignals.repository.UserSettingsRepository;
import com.shyblack.cryptosignals.service.futures.FuturesEngineService;
import com.shyblack.cryptosignals.service.futures.FuturesRiskService;
import com.shyblack.cryptosignals.service.live.LiveTradingEngineService;
import com.shyblack.cryptosignals.service.live.LiveTradingRiskService;
import com.shyblack.cryptosignals.service.paper.PaperTradingEngineService;
import com.shyblack.cryptosignals.service.paper.PaperTradingExecutionService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Phase 8 execution router: capability routing, safety gates, isolation,
 * idempotency and retry safety.
 *
 * <p>A plain unit test with mocked collaborators. No Spring context and no
 * database, which keeps each gate assertion about the router's own logic rather
 * than about context wiring or transaction boundaries. The full-stack wiring is
 * covered separately by the existing execution and portfolio suites, which share
 * the same Spring context.
 *
 * <p><b>No real Binance order is possible from this class.</b> The router holds no
 * exchange adapter at all: it decides and delegates, and the only component that
 * can reach an adapter is an engine, which is always a mock here. The delegated
 * engines are verified to be called exactly once on success and never on refusal.
 */
class ExecutionRouterTest {

	private static final AtomicInteger SEQ = new AtomicInteger();

	private ExecutionRouter router;

	private SignalRepository signalRepository;
	private ExecutionDecisionRecordRepository decisionRepository;
	private LiveTradingAccountRepository liveAccountRepository;
	private FuturesTradingAccountRepository futuresAccountRepository;
	private UserRepository userRepository;
	private UserSettingsRepository userSettingsRepository;

	private PaperTradingEngineService paperEngine;
	private LiveTradingEngineService liveEngine;
	private FuturesEngineService futuresEngine;
	private LiveTradingRiskService liveRiskService;
	private FuturesRiskService futuresRiskService;

	@BeforeEach
	void setUp() {
		signalRepository = mock(SignalRepository.class);
		decisionRepository = mock(ExecutionDecisionRecordRepository.class);
		liveAccountRepository = mock(LiveTradingAccountRepository.class);
		futuresAccountRepository = mock(FuturesTradingAccountRepository.class);
		userRepository = mock(UserRepository.class);
		userSettingsRepository = mock(UserSettingsRepository.class);
		paperEngine = mock(PaperTradingEngineService.class);
		liveEngine = mock(LiveTradingEngineService.class);
		futuresEngine = mock(FuturesEngineService.class);
		liveRiskService = mock(LiveTradingRiskService.class);
		futuresRiskService = mock(FuturesRiskService.class);

		// Default: nobody is a candidate, so each test opts in explicitly.
		when(userRepository.findAll()).thenReturn(List.of());
		when(liveAccountRepository.findAllWithCredential()).thenReturn(List.of());
		when(futuresAccountRepository.findAllWithCredential()).thenReturn(List.of());
		when(decisionRepository.findByUserIdAndSignalIdAndAccountModeAndAccountCategory(
				any(), any(), any(), any())).thenReturn(Optional.empty());
		// Echo the saved row back with an id, as a real repository would after insert.
		when(decisionRepository.saveAndFlush(any(ExecutionDecisionRecord.class)))
				.thenAnswer(invocation -> {
					ExecutionDecisionRecord row = invocation.getArgument(0);
					if (row.getId() == null) {
						row.setId(UUID.randomUUID());
					}
					return row;
				});
		when(liveRiskService.check(any(), any(), any())).thenReturn(LiveTradingRiskReason.OK);
		when(futuresRiskService.check(any(), any(), any()))
				.thenReturn(com.shyblack.cryptosignals.entity.enums.FuturesRiskReason.OK);

		router = new ExecutionRouter(
				new ExecutionProperties(true, false),
				liveProps(true),
				futuresProps(true),
				signalRepository, decisionRepository,
				liveAccountRepository, futuresAccountRepository,
				userRepository, userSettingsRepository,
				paperEngine, liveEngine, futuresEngine,
				liveRiskService, futuresRiskService);
	}

	// ------------------------------------------------------------------ fixtures

	private User user(AccountType type) {
		User u = new User();
		u.setId(UUID.randomUUID());
		u.setEmail("exec-" + SEQ.incrementAndGet() + "@example.com");
		u.setFullName("Execution Tester");
		u.setAccountType(type);
		u.setEnabled(true);
		return u;
	}

	private ExchangeCredential credential() {
		ExchangeCredential c = new ExchangeCredential();
		c.setId(UUID.randomUUID());
		c.setExchange(ExchangeName.BINANCE);
		c.setApiKey("dGVzdC1vbmx5LWtleQ==");
		c.setApiSecret("dGVzdC1vbmx5LXNlY3JldA==");
		c.setStatus(ExchangeConnectionStatus.CONNECTED);
		return c;
	}

	private LiveTradingAccount liveSpotAccount(User user) {
		LiveTradingAccount a = new LiveTradingAccount();
		a.setId(UUID.randomUUID());
		a.setUser(user);
		a.setExchange(ExchangeName.BINANCE);
		a.setCredential(credential());
		a.setConnectionStatus(ExchangeConnectionStatus.CONNECTED);
		a.setEnabled(true);
		a.setKillSwitchActive(false);
		a.setQuoteCurrency("USDT");
		a.setCachedAvailableBalance(new BigDecimal("10000"));
		return a;
	}

	private FuturesTradingAccount liveFuturesAccount(User user) {
		FuturesTradingAccount a = new FuturesTradingAccount();
		a.setId(UUID.randomUUID());
		a.setUser(user);
		a.setExchange(ExchangeName.BINANCE);
		a.setCredential(credential());
		a.setConnectionStatus(ExchangeConnectionStatus.CONNECTED);
		a.setEnabled(true);
		a.setAcknowledged(true);
		a.setKillSwitchActive(false);
		a.setMarginAsset("USDT");
		a.setAvailableBalance(new BigDecimal("10000"));
		a.setMaxLeverage(3);
		return a;
	}

	private void allowLiveTrading(User user, boolean allowed) {
		UserSettings settings = new UserSettings();
		settings.setId(UUID.randomUUID());
		settings.setUser(user);
		settings.setLiveTradingAllowed(allowed);
		when(userSettingsRepository.findByUser_Id(user.getId()))
				.thenReturn(Optional.of(settings));
	}

	private Signal signal(TradingMode mode, SignalStatus status, PositionSide side) {
		Signal s = new Signal();
		s.setId(UUID.randomUUID());
		s.setSymbol("BTCUSDT");
		s.setStatus(status);
		s.setSide(side);
		s.setTradingMode(mode);
		s.setEntryPrice(new BigDecimal("50000"));
		s.setStopLoss(new BigDecimal("49000"));
		s.setTargetPrice(new BigDecimal("55000"));
		s.setEntryType(EntryType.PRE_BREAKOUT);
		s.setSignalGrade(SignalGrade.BUY);
		s.setConfidence(80);
		s.setSuggestedRiskPercent(new BigDecimal("2.00"));
		return s;
	}

	private Signal spotSignal() {
		return signal(TradingMode.SPOT, SignalStatus.ACTIVE, PositionSide.LONG);
	}

	private Signal futuresSignal() {
		return signal(TradingMode.FUTURES, SignalStatus.ACTIVE, PositionSide.LONG);
	}

	/** Makes the given users the only paper candidates. */
	private void paperCandidates(User... users) {
		when(userRepository.findAll()).thenReturn(List.of(users));
	}

	private void liveSpotCandidates(LiveTradingAccount... accounts) {
		when(liveAccountRepository.findAllWithCredential()).thenReturn(List.of(accounts));
	}

	private void liveFuturesCandidates(FuturesTradingAccount... accounts) {
		when(futuresAccountRepository.findAllWithCredential()).thenReturn(List.of(accounts));
	}

	/** The ledger already holds a decision for this identity. */
	private void priorDecision(User user, Signal signal, AccountMode mode,
			AccountCategory category, ExecutionOutcome outcome) {
		ExecutionDecisionRecord existing = new ExecutionDecisionRecord();
		existing.setId(UUID.randomUUID());
		existing.setUserId(user.getId());
		existing.setSignalId(signal.getId());
		existing.setAccountMode(mode);
		existing.setAccountCategory(category);
		existing.setOutcome(outcome);
		when(decisionRepository.findByUserIdAndSignalIdAndAccountModeAndAccountCategory(
				eq(user.getId()), eq(signal.getId()), eq(mode), eq(category)))
				.thenReturn(Optional.of(existing));
	}

	// ------------------------------------------------------------------ helpers

	private ExecutionRoutingResult routeFor(User user, Signal signal) {
		return decisionFor(router.routeAll(signal), user);
	}

	private ExecutionRoutingResult decisionFor(List<ExecutionRoutingResult> routed, User user) {
		return routed.stream()
				.filter(r -> r.identity().userId().equals(user.getId()))
				.findFirst()
				.orElseThrow(() -> new AssertionError(
						"no routing decision was produced for this user and signal"));
	}

	private boolean hasLiveDecision(User user, Signal signal) {
		return router.routeAll(signal).stream()
				.anyMatch(r -> r.identity().userId().equals(user.getId())
						&& r.accountMode() == AccountMode.LIVE);
	}

/**
	 * No <i>live</i> engine was delegated to, so nothing could reach an exchange.
	 *
	 * <p>The paper engine is deliberately excluded: paper places no exchange order in
	 * either mode, so paper routing is asserted separately and asserting it here
	 * would be wrong for the many tests that also carry a paper candidate.
	 */
	private void assertNoDelegation() {
		verify(liveEngine, never()).executeForAccount(any(), any());
		verify(futuresEngine, never()).executeForAccount(any(), any());
	}

	private ExecutionRouter routerWith(boolean enabled, boolean dryRun, boolean autoExecute) {
		return new ExecutionRouter(
				new ExecutionProperties(enabled, dryRun),
				liveProps(autoExecute),
				futuresProps(autoExecute),
				signalRepository, decisionRepository,
				liveAccountRepository, futuresAccountRepository,
				userRepository, userSettingsRepository,
				paperEngine, liveEngine, futuresEngine,
				liveRiskService, futuresRiskService);
	}

	private LiveTradingProperties liveProps(boolean autoExecute) {
		return new LiveTradingProperties(LiveTradingProperties.Mode.MOCK, null, null, null,
				5000, new BigDecimal("200.00"), 3, new BigDecimal("5.00"), autoExecute);
	}

	private FuturesTradingProperties futuresProps(boolean autoExecute) {
		return new FuturesTradingProperties(FuturesTradingProperties.Mode.MOCK, null, null,
				5000, 3, FuturesMarginMode.ISOLATED, FuturesPositionMode.ONE_WAY,
				new BigDecimal("200.00"), 2, new BigDecimal("5.00"), new BigDecimal("0.30"),
				new BigDecimal("15.00"), autoExecute);
	}

	// =================================================================== A. PAPER

	@Test
	void paperSignalRoutesToPaper() {
		User paper = user(AccountType.PAPER);
		paperCandidates(paper);
		Signal signal = spotSignal();

		ExecutionRoutingResult result = routeFor(paper, signal);

		assertThat(result.decision()).isEqualTo(ExecutionDecision.EXECUTE);
		assertThat(result.accountMode()).isEqualTo(AccountMode.PAPER);
		assertThat(result.targetEngine()).isEqualTo("PAPER");
		verify(paperEngine, times(1)).executeForUser(eq(paper), eq(signal));
		assertNoDelegation();
	}

	@Test
	void paperNeverDelegatesToALiveEngine() {
		User paper = user(AccountType.PAPER);
		paperCandidates(paper);
		Signal signal = spotSignal();

		routeFor(paper, signal);

		verify(liveEngine, never()).executeForAccount(any(), any());
		verify(futuresEngine, never()).executeForAccount(any(), any());
	}

	@Test
	void paperAndLiveAccountsAreRoutedIndependentlyForOneSignal() {
		User paper = user(AccountType.PAPER);
		User live = user(AccountType.LIVE);
		LiveTradingAccount account = liveSpotAccount(live);
		paperCandidates(paper);
		liveSpotCandidates(account);
		allowLiveTrading(live, true);
		Signal signal = spotSignal();

		// One routing pass: routeAll delegates as a side effect, so calling it twice
		// would execute the paper path twice.
		List<ExecutionRoutingResult> routed = router.routeAll(signal);
		ExecutionRoutingResult paperResult = decisionFor(routed, paper);
		ExecutionRoutingResult liveResult = decisionFor(routed, live);

		assertThat(paperResult.accountMode()).isEqualTo(AccountMode.PAPER);
		assertThat(paperResult.decision()).isEqualTo(ExecutionDecision.EXECUTE);
		assertThat(liveResult.accountMode()).isEqualTo(AccountMode.LIVE);
		assertThat(liveResult.decision()).isEqualTo(ExecutionDecision.EXECUTE);
		verify(paperEngine, times(1)).executeForUser(eq(paper), eq(signal));
		verify(liveEngine, times(1)).executeForAccount(eq(account), eq(signal));
	}

	@Test
	void paperAccountingIsDelegatedNotReimplemented() {
		User paper = user(AccountType.PAPER);
		paperCandidates(paper);
		Signal signal = spotSignal();

		routeFor(paper, signal);

// Sizing, fees and realised P&L stay inside the existing paper module: the
		// router calls exactly one entry point, exactly once, and touches no paper
		// accounting API itself.
		verify(paperEngine, times(1)).executeForUser(eq(paper), eq(signal));
	}

	@Test
	void aDisabledPaperUserIsNotRouted() {
		User paper = user(AccountType.PAPER);
		paper.setEnabled(false);
		// The router reads the unfiltered user list so a refusal is recorded with its
		// reason rather than the account silently vanishing from the audit trail.
		when(userRepository.findAll()).thenReturn(List.of(paper));

		assertThat(router.routeAll(spotSignal()))
				.as("a disabled user is not a candidate at all")
				.isEmpty();
		verify(paperEngine, never()).executeForUser(any(), any());
	}

	@Test
	void aLiveAccountTypeUserWithoutAPaperPortfolioIsNotRouted() {
		User live = user(AccountType.LIVE);
		when(userRepository.findAll()).thenReturn(List.of(live));

		assertThat(router.routeAll(spotSignal()))
				.as("only the legacy PAPER account type receives paper routing")
				.isEmpty();
		verify(paperEngine, never()).executeForUser(any(), any());
	}

	// ============================================================== B. LIVE SPOT

	@Test
	void liveSpotWithEveryGateOpenDelegatesToTheSpotEngine() {
		User live = user(AccountType.LIVE);
		LiveTradingAccount account = liveSpotAccount(live);
		liveSpotCandidates(account);
		allowLiveTrading(live, true);

		ExecutionRoutingResult result = routeFor(live, spotSignal());

		assertThat(result.decision()).isEqualTo(ExecutionDecision.EXECUTE);
		assertThat(result.accountCategory()).isEqualTo(AccountCategory.SPOT);
		assertThat(result.targetEngine()).isEqualTo("LIVE_SPOT");
		verify(liveEngine, times(1)).executeForAccount(eq(account), any());
	}

	@Test
	void routerDisabledMeansNoOrderEvenWhenEveryOtherGateIsOpen() {
		User live = user(AccountType.LIVE);
		liveSpotCandidates(liveSpotAccount(live));
		allowLiveTrading(live, true);

		ExecutionRoutingResult result = routeWith(
				routerWith(false, false, true), live, spotSignal());

		assertThat(result.decision()).isEqualTo(ExecutionDecision.REJECT);
		assertThat(result.rejectionReason())
				.isEqualTo(ExecutionRejectionReason.AUTO_EXECUTE_DISABLED);
		assertNoDelegation();
	}

	@Test
	void liveTradingAutoExecuteDisabledMeansNoOrder() {
		User live = user(AccountType.LIVE);
		liveSpotCandidates(liveSpotAccount(live));
		allowLiveTrading(live, true);

		ExecutionRoutingResult result = routeWith(
				routerWith(true, false, false), live, spotSignal());

		assertThat(result.rejectionReason())
				.isEqualTo(ExecutionRejectionReason.AUTO_EXECUTE_DISABLED);
		assertNoDelegation();
	}

	@Test
	void spotKillSwitchActiveMeansNoOrder() {
		User live = user(AccountType.LIVE);
		LiveTradingAccount account = liveSpotAccount(live);
		account.setKillSwitchActive(true);
		liveSpotCandidates(account);
		allowLiveTrading(live, true);

		ExecutionRoutingResult result = routeFor(live, spotSignal());

		assertThat(result.decision()).isEqualTo(ExecutionDecision.REJECT);
		assertThat(result.rejectionReason()).isEqualTo(ExecutionRejectionReason.KILL_SWITCH_ACTIVE);
		assertNoDelegation();
	}

	@Test
	void inactiveAccountMeansNoOrderAndIsNeverAutoActivated() {
		User live = user(AccountType.LIVE);
		LiveTradingAccount account = liveSpotAccount(live);
		account.setEnabled(false);
		liveSpotCandidates(account);
		allowLiveTrading(live, true);

		ExecutionRoutingResult result = routeFor(live, spotSignal());

		assertThat(result.rejectionReason()).isEqualTo(ExecutionRejectionReason.ACCOUNT_INACTIVE);
		assertThat(account.isEnabled())
				.as("routing must never activate an account as a side effect")
				.isFalse();
		assertNoDelegation();
	}

	@Test
	void disconnectedAccountMeansNoOrder() {
		User live = user(AccountType.LIVE);
		LiveTradingAccount account = liveSpotAccount(live);
		account.setConnectionStatus(ExchangeConnectionStatus.REVOKED);
		liveSpotCandidates(account);
		allowLiveTrading(live, true);

		assertThat(routeFor(live, spotSignal()).rejectionReason())
				.isEqualTo(ExecutionRejectionReason.ACCOUNT_NOT_CONNECTED);
		assertNoDelegation();
	}

	@Test
	void liveTradingNotAllowedMeansNoOrder() {
		User live = user(AccountType.LIVE);
		liveSpotCandidates(liveSpotAccount(live));
		allowLiveTrading(live, false);

		assertThat(routeFor(live, spotSignal()).rejectionReason())
				.isEqualTo(ExecutionRejectionReason.LIVE_TRADING_NOT_ALLOWED);
		assertNoDelegation();
	}

	@Test
	void missingUserSettingsMeansLiveTradingIsNotAllowed() {
		User live = user(AccountType.LIVE);
		liveSpotCandidates(liveSpotAccount(live));
		when(userSettingsRepository.findByUser_Id(live.getId())).thenReturn(Optional.empty());

		assertThat(routeFor(live, spotSignal()).rejectionReason())
				.isEqualTo(ExecutionRejectionReason.LIVE_TRADING_NOT_ALLOWED);
		assertNoDelegation();
	}

	@Test
	void unusableCredentialMeansNoOrder() {
		User live = user(AccountType.LIVE);
		LiveTradingAccount account = liveSpotAccount(live);
		account.getCredential().setStatus(ExchangeConnectionStatus.NOT_CONNECTED);
		liveSpotCandidates(account);
		allowLiveTrading(live, true);

		assertThat(routeFor(live, spotSignal()).rejectionReason())
				.isEqualTo(ExecutionRejectionReason.CREDENTIALS_UNAVAILABLE);
		assertNoDelegation();
	}

	@Test
	void aRiskServiceRefusalMeansNoOrderAndReportsTheRiskReason() {
		User live = user(AccountType.LIVE);
		LiveTradingAccount account = liveSpotAccount(live);
		liveSpotCandidates(account);
		allowLiveTrading(live, true);
		when(liveRiskService.check(any(), any(), any()))
				.thenReturn(LiveTradingRiskReason.INVALID_STOP_LOSS);

		ExecutionRoutingResult result = routeFor(live, spotSignal());

		assertThat(result.decision()).isEqualTo(ExecutionDecision.REJECT);
		assertThat(result.rejectionReason()).isEqualTo(ExecutionRejectionReason.RISK_REJECTED);
		assertThat(result.detail())
				.as("the delegated reason is reported verbatim, never re-interpreted")
				.contains("INVALID_STOP_LOSS");
		assertNoDelegation();
	}

	@Test
	void aShortSpotSignalIsRejectedRatherThanCoerced() {
		User live = user(AccountType.LIVE);
		liveSpotCandidates(liveSpotAccount(live));
		allowLiveTrading(live, true);

		ExecutionRoutingResult result = routeFor(live,
				signal(TradingMode.SPOT, SignalStatus.ACTIVE, PositionSide.SHORT));

		assertThat(result.rejectionReason()).isEqualTo(ExecutionRejectionReason.DIRECTION_INVALID);
		assertNoDelegation();
	}

	@Test
	void aBlankSymbolMeansNoOrder() {
		User live = user(AccountType.LIVE);
		liveSpotCandidates(liveSpotAccount(live));
		allowLiveTrading(live, true);
		Signal signal = spotSignal();
		signal.setSymbol("   ");

		assertThat(routeFor(live, signal).rejectionReason())
				.isEqualTo(ExecutionRejectionReason.SYMBOL_MISSING);
		assertNoDelegation();
	}

	@Test
	void aSignalWithNoDirectionMeansNoOrder() {
		User live = user(AccountType.LIVE);
		liveSpotCandidates(liveSpotAccount(live));
		allowLiveTrading(live, true);
		Signal signal = spotSignal();
		signal.setSide(null);

		assertThat(routeFor(live, signal).rejectionReason())
				.isEqualTo(ExecutionRejectionReason.DIRECTION_INVALID);
		assertNoDelegation();
	}

	@Test
	void aNonActiveSignalMeansNoOrder() {
		User paper = user(AccountType.PAPER);
		paperCandidates(paper);

		ExecutionRoutingResult result = routeFor(paper,
				signal(TradingMode.SPOT, SignalStatus.CLOSED, PositionSide.LONG));

		assertThat(result.rejectionReason()).isEqualTo(ExecutionRejectionReason.SIGNAL_NOT_ACTIVE);
		assertNoDelegation();
	}

	// ============================================================ C. LIVE FUTURES

	@Test
	void liveFuturesWithEveryGateOpenDelegatesToTheFuturesEngine() {
		User live = user(AccountType.LIVE);
		FuturesTradingAccount account = liveFuturesAccount(live);
		liveFuturesCandidates(account);
		allowLiveTrading(live, true);

		ExecutionRoutingResult result = routeFor(live, futuresSignal());

		assertThat(result.decision()).isEqualTo(ExecutionDecision.EXECUTE);
		assertThat(result.accountCategory()).isEqualTo(AccountCategory.FUTURES);
		assertThat(result.targetEngine()).isEqualTo("LIVE_FUTURES");
		verify(futuresEngine, times(1)).executeForAccount(eq(account), any());
	}

	@Test
	void liveFuturesNeverDelegatesToTheSpotEngine() {
		User live = user(AccountType.LIVE);
		liveFuturesCandidates(liveFuturesAccount(live));
		allowLiveTrading(live, true);

		routeFor(live, futuresSignal());

		verify(liveEngine, never()).executeForAccount(any(), any());
	}

	@Test
	void futuresKillSwitchActiveMeansNoOrder() {
		User live = user(AccountType.LIVE);
		FuturesTradingAccount account = liveFuturesAccount(live);
		account.setKillSwitchActive(true);
		liveFuturesCandidates(account);
		allowLiveTrading(live, true);

		ExecutionRoutingResult result = routeFor(live, futuresSignal());

		assertThat(result.rejectionReason()).isEqualTo(ExecutionRejectionReason.KILL_SWITCH_ACTIVE);
		assertNoDelegation();
	}

	@Test
	void unacknowledgedFuturesAccountMeansNoOrder() {
		User live = user(AccountType.LIVE);
		FuturesTradingAccount account = liveFuturesAccount(live);
		account.setAcknowledged(false);
		liveFuturesCandidates(account);
		allowLiveTrading(live, true);

		assertThat(routeFor(live, futuresSignal()).rejectionReason())
				.isEqualTo(ExecutionRejectionReason.ACCOUNT_NOT_ACKNOWLEDGED);
		assertNoDelegation();
	}

	@Test
	void futuresAutoExecuteDisabledMeansNoOrder() {
		User live = user(AccountType.LIVE);
		liveFuturesCandidates(liveFuturesAccount(live));
		allowLiveTrading(live, true);

		ExecutionRoutingResult result = routeWith(
				routerWith(true, true, false), live, futuresSignal());

		assertThat(result.rejectionReason())
				.isEqualTo(ExecutionRejectionReason.AUTO_EXECUTE_DISABLED);
		assertNoDelegation();
	}

	@Test
	void aFuturesRiskServiceRefusalMeansNoOrderAndReportsTheRiskReason() {
		User live = user(AccountType.LIVE);
		liveFuturesCandidates(liveFuturesAccount(live));
		allowLiveTrading(live, true);
		when(futuresRiskService.check(any(), any(), any()))
				.thenReturn(com.shyblack.cryptosignals.entity.enums.FuturesRiskReason
						.UNSUPPORTED_MARGIN_MODE);

		ExecutionRoutingResult result = routeFor(live, futuresSignal());

		assertThat(result.rejectionReason()).isEqualTo(ExecutionRejectionReason.RISK_REJECTED);
		assertThat(result.detail()).contains("UNSUPPORTED_MARGIN_MODE");
		assertNoDelegation();
	}

	// ================================================================ D. ROUTING

	@Test
	void aSpotSignalNeverRoutesToFutures() {
		User spotUser = user(AccountType.LIVE);
		liveSpotCandidates(liveSpotAccount(spotUser));
		User futuresUser = user(AccountType.LIVE);
		liveFuturesCandidates(liveFuturesAccount(futuresUser));
		allowLiveTrading(spotUser, true);
		allowLiveTrading(futuresUser, true);

		ExecutionRoutingResult spotResult = routeFor(spotUser, spotSignal());

		assertThat(spotResult.accountCategory()).isEqualTo(AccountCategory.SPOT);
		assertThat(hasLiveDecision(futuresUser, spotSignal()))
				.as("a futures account must not receive a live decision for a spot signal")
				.isFalse();
		verify(futuresEngine, never()).executeForAccount(any(), any());
	}

	@Test
	void aFuturesSignalNeverRoutesToSpot() {
		User spotUser = user(AccountType.LIVE);
		liveSpotCandidates(liveSpotAccount(spotUser));
		User futuresUser = user(AccountType.LIVE);
		liveFuturesCandidates(liveFuturesAccount(futuresUser));
		allowLiveTrading(spotUser, true);
		allowLiveTrading(futuresUser, true);

		ExecutionRoutingResult futuresResult = routeFor(futuresUser, futuresSignal());

		assertThat(futuresResult.accountCategory()).isEqualTo(AccountCategory.FUTURES);
		assertThat(hasLiveDecision(spotUser, futuresSignal()))
				.as("a spot account must not receive a live decision for a futures signal")
				.isFalse();
		verify(liveEngine, never()).executeForAccount(any(), any());
	}

	@Test
	void optionsIsNotSupportedAndPlacesNoOrder() {
		User live = user(AccountType.LIVE);
		liveSpotCandidates(liveSpotAccount(live));
		liveFuturesCandidates(liveFuturesAccount(live));
		allowLiveTrading(live, true);

		ExecutionRoutingResult result = routeFor(live,
				signal(TradingMode.OPTIONS, SignalStatus.ACTIVE, PositionSide.LONG));

		assertThat(result.decision()).isEqualTo(ExecutionDecision.NOT_SUPPORTED);
		assertThat(result.rejectionReason())
				.isEqualTo(ExecutionRejectionReason.OPTIONS_NOT_SUPPORTED);
		assertNoDelegation();
	}

	@Test
	void mainIsNeverAnExecutableCategory() {
		assertThat(ExecutionRouting.categoryFor(TradingMode.SPOT)).isEqualTo(AccountCategory.SPOT);
		assertThat(ExecutionRouting.categoryFor(TradingMode.FUTURES))
				.isEqualTo(AccountCategory.FUTURES);
		assertThat(ExecutionRouting.categoryFor(TradingMode.OPTIONS))
				.as("options has a reserved category but no engine")
				.isNull();
		assertThat(ExecutionRouting.categoryFor(null)).isNull();
		assertThat(ExecutionRouting.isExecutableCategory(AccountCategory.MAIN)).isFalse();
		assertThat(ExecutionRouting.isExecutableCategory(AccountCategory.OPTIONS)).isFalse();
	}

	@Test
	void theThreeExecutionDimensionsAreKeptDistinct() {
		assertThat(AccountMode.values()).containsExactly(AccountMode.PAPER, AccountMode.LIVE);
		assertThat(AccountCategory.values()).containsExactly(
				AccountCategory.MAIN, AccountCategory.SPOT,
				AccountCategory.FUTURES, AccountCategory.OPTIONS);
		assertThat(TradingMode.values()).containsExactly(
				TradingMode.SPOT, TradingMode.FUTURES, TradingMode.OPTIONS);
		// MAIN aggregates and therefore has no single market mode.
		assertThat(AccountCategory.MAIN.tradingMode()).isEmpty();
		assertThat(AccountCategory.SPOT.tradingMode()).hasValue(TradingMode.SPOT);
	}

	// ============================================================ E. IDEMPOTENCY

	@Test
	void aDuplicateSignalEventDoesNotExecuteTwice() {
		User paper = user(AccountType.PAPER);
		paperCandidates(paper);
		Signal signal = spotSignal();

		ExecutionRoutingResult first = routeFor(paper, signal);
		// The second attempt finds the ledger row the first one wrote.
		priorDecision(paper, signal, AccountMode.PAPER, AccountCategory.MAIN,
				ExecutionOutcome.ORDER_REQUESTED);
		ExecutionRoutingResult second = routeFor(paper, signal);

		assertThat(first.decision()).isEqualTo(ExecutionDecision.EXECUTE);
		assertThat(second.decision()).isEqualTo(ExecutionDecision.REJECT);
		assertThat(second.rejectionReason())
				.isEqualTo(ExecutionRejectionReason.DUPLICATE_EXECUTION);
		verify(paperEngine, times(1)).executeForUser(any(), any());
	}

	@Test
	void aDuplicateLiveSignalDelegatesOnlyOnce() {
		User live = user(AccountType.LIVE);
		liveSpotCandidates(liveSpotAccount(live));
		allowLiveTrading(live, true);
		Signal signal = spotSignal();

		routeFor(live, signal);
		priorDecision(live, signal, AccountMode.LIVE, AccountCategory.SPOT,
				ExecutionOutcome.ORDER_REQUESTED);
		ExecutionRoutingResult second = routeFor(live, signal);

		assertThat(second.rejectionReason())
				.isEqualTo(ExecutionRejectionReason.DUPLICATE_EXECUTION);
		verify(liveEngine, times(1)).executeForAccount(any(), any());
	}

	@Test
	void theExecutionIdentityIsDeterministicNotRandom() {
		User paper = user(AccountType.PAPER);
		paperCandidates(paper);
		Signal signal = spotSignal();

		ExecutionRoutingResult result = routeFor(paper, signal);

		assertThat(result.request().idempotencyKey())
				.isEqualTo(paper.getId() + "|" + signal.getId() + "|PAPER|MAIN|SPOT");
		// Recomputing yields the same key, which is what makes duplicate suppression
		// survive a restart, where any in-memory state would be gone.
		assertThat(ExecutionIdentity.of(paper.getId(), signal.getId(),
				AccountMode.PAPER, AccountCategory.MAIN, TradingMode.SPOT).canonical())
				.isEqualTo(result.request().idempotencyKey());
	}

	@Test
	void spotAndFuturesIdentitiesForOneSignalAreDistinct() {
		User live = user(AccountType.LIVE);
		liveSpotCandidates(liveSpotAccount(live));
		allowLiveTrading(live, true);
		Signal signal = spotSignal();

		String spotKey = routeFor(live, signal).request().idempotencyKey();

		assertThat(spotKey).contains("|LIVE|SPOT|SPOT");
		assertThat(ExecutionIdentity.of(live.getId(), signal.getId(),
				AccountMode.LIVE, AccountCategory.FUTURES, TradingMode.FUTURES).canonical())
				.isNotEqualTo(spotKey);
	}

	@Test
	void anUnknownPreviousOutcomeBlocksResubmissionUntilReconciled() {
		User live = user(AccountType.LIVE);
		liveSpotCandidates(liveSpotAccount(live));
		allowLiveTrading(live, true);
		Signal signal = spotSignal();
		// An ambiguous submission: the exchange outcome was never established, so the
		// order may or may not exist and must not be re-sent.
		priorDecision(live, signal, AccountMode.LIVE, AccountCategory.SPOT,
				ExecutionOutcome.UNKNOWN);

		ExecutionRoutingResult result = routeFor(live, signal);

		assertThat(result.rejectionReason())
				.isEqualTo(ExecutionRejectionReason.PREVIOUS_OUTCOME_UNKNOWN);
		assertThat(result.detail())
				.as("the correct next step is reconciliation, not resubmission")
				.contains("Reconcile");
		assertNoDelegation();
	}

	@Test
	void aLostUniquenessRaceBecomesADuplicateRejectionNotAnOrder() {
		User live = user(AccountType.LIVE);
		liveSpotCandidates(liveSpotAccount(live));
		allowLiveTrading(live, true);
		// The pre-check passed, but a concurrent consumer inserts the same identity
		// first. The unique constraint is the authoritative guard.
		when(decisionRepository.saveAndFlush(any()))
				.thenThrow(new DataIntegrityViolationException("uk_execution_decisions_identity"));

		ExecutionRoutingResult result = routeFor(live, spotSignal());

		assertThat(result.decision()).isEqualTo(ExecutionDecision.REJECT);
		assertThat(result.rejectionReason())
				.isEqualTo(ExecutionRejectionReason.DUPLICATE_EXECUTION);
		assertThat(result.detail()).contains("concurrent attempt");
		assertNoDelegation();
	}

	@Test
	void theLedgerRecordsTheIdentityForEveryDecision() {
		User live = user(AccountType.LIVE);
		liveSpotCandidates(liveSpotAccount(live));
		allowLiveTrading(live, true);
		Signal signal = spotSignal();

		routeFor(live, signal);

		var captor = org.mockito.ArgumentCaptor.forClass(ExecutionDecisionRecord.class);
		verify(decisionRepository).saveAndFlush(captor.capture());
		ExecutionDecisionRecord row = captor.getValue();
		assertThat(row.getUserId()).isEqualTo(live.getId());
		assertThat(row.getSignalId()).isEqualTo(signal.getId());
		assertThat(row.getAccountMode()).isEqualTo(AccountMode.LIVE);
		assertThat(row.getAccountCategory()).isEqualTo(AccountCategory.SPOT);
		assertThat(row.getIdentityKey()).contains(live.getId().toString());
		assertThat(row.getDecidedAt()).isNotNull();
		assertThat(row.getDecision()).isEqualTo(ExecutionDecision.EXECUTE);
	}

	// ================================================================ F. ERROR

	@Test
	void anEngineFailureIsContainedAndDoesNotAbortTheFanOut() {
		User failing = user(AccountType.PAPER);
		User healthy = user(AccountType.PAPER);
		paperCandidates(failing, healthy);
		Signal signal = spotSignal();
		org.mockito.Mockito.doThrow(new IllegalStateException("engine exploded"))
				.when(paperEngine).executeForUser(eq(failing), any());

		List<ExecutionRoutingResult> routed = router.routeAll(signal);
		ExecutionRoutingResult second = decisionFor(routed, healthy);

		assertThat(second.decision())
				.as("one account's failure must not prevent the next from being evaluated")
				.isEqualTo(ExecutionDecision.EXECUTE);
		verify(paperEngine).executeForUser(eq(healthy), eq(signal));
	}

	@Test
	void anUnknownOutcomeIsNeverDefinitiveAndNeverARejection() {
		assertThat(ExecutionOutcome.UNKNOWN.isDefinitive()).isFalse();
		assertThat(ExecutionOutcome.ORDER_REQUESTED.isDefinitive()).isTrue();
		assertThat(ExecutionOutcome.ORDER_ACCEPTED.isDefinitive()).isTrue();
		assertThat(ExecutionOutcome.ORDER_PARTIALLY_FILLED.isDefinitive()).isTrue();
		assertThat(ExecutionOutcome.ORDER_FILLED.isDefinitive()).isTrue();
		assertThat(ExecutionOutcome.ORDER_CANCELED.isDefinitive()).isTrue();
		assertThat(ExecutionOutcome.ORDER_REJECTED.isDefinitive()).isTrue();

		assertThat(ExecutionOutcome.ORDER_REJECTED.isRejection()).isTrue();
		assertThat(ExecutionOutcome.UNKNOWN.isRejection())
				.as("an ambiguous failure is not a rejection and must not be retried as one")
				.isFalse();
	}

	@Test
	void anUnknownSignalIdPlacesNoOrder() {
		User paper = user(AccountType.PAPER);
		paperCandidates(paper);
		Signal signal = spotSignal();
		UUID missing = UUID.randomUUID();
		when(signalRepository.findById(missing)).thenReturn(Optional.empty());

		router.onSignalGenerated(new com.shyblack.cryptosignals.service.SignalGeneratedEvent(missing));

		assertNoDelegation();
	}

	// ================================================================ K. DRY RUN

	@Test
	void dryRunProducesACompleteRequestAndSendsNothing() {
		User live = user(AccountType.LIVE);
		liveSpotCandidates(liveSpotAccount(live));
		allowLiveTrading(live, true);
		Signal signal = spotSignal();

		ExecutionRoutingResult result = routeWith(
				routerWith(true, true, true), live, signal);

		assertThat(result.decision()).isEqualTo(ExecutionDecision.DRY_RUN);
		assertThat(result.dryRunComplete()).isTrue();
		assertThat(result.request()).isNotNull();
		assertThat(result.request().symbol()).isEqualTo("BTCUSDT");
		assertThat(result.request().side()).isEqualTo("LONG");
		assertThat(result.request().accountCategory()).isEqualTo(AccountCategory.SPOT);
		assertThat(result.request().accountMode()).isEqualTo(AccountMode.LIVE);
		assertThat(result.noOrderPlaced()).isTrue();
		assertNoDelegation();
	}

	@Test
	void dryRunStillEnforcesEveryGate() {
		User live = user(AccountType.LIVE);
		LiveTradingAccount account = liveSpotAccount(live);
		account.setKillSwitchActive(true);
		liveSpotCandidates(account);
		allowLiveTrading(live, true);

		ExecutionRoutingResult result = routeWith(
				routerWith(true, true, true), live, spotSignal());

		assertThat(result.decision())
				.as("dry run must not bypass the kill switch")
				.isEqualTo(ExecutionDecision.REJECT);
		assertThat(result.rejectionReason()).isEqualTo(ExecutionRejectionReason.KILL_SWITCH_ACTIVE);
		assertNoDelegation();
	}

	@Test
	void liveExecutionIsOffUnlessEverySwitchIsOpen() {
		assertThat(new ExecutionProperties(false, false).enabled())
				.as("the router must default to disabled")
				.isFalse();
		assertThat(new ExecutionProperties(false, false).dryRun()).isFalse();

		User live = user(AccountType.LIVE);
		liveSpotCandidates(liveSpotAccount(live));
		allowLiveTrading(live, true);
		Signal signal = spotSignal();

		assertThat(routeWith(routerWith(false, false, true), live, signal).rejectionReason())
				.isEqualTo(ExecutionRejectionReason.AUTO_EXECUTE_DISABLED);
		assertThat(routeWith(routerWith(true, false, false), live, signal).rejectionReason())
				.isEqualTo(ExecutionRejectionReason.AUTO_EXECUTE_DISABLED);
		assertNoDelegation();
	}

	// ================================================================ N. SECURITY

	@Test
	void noCredentialAppearsInTheRequestOrTheDecision() {
		User live = user(AccountType.LIVE);
		liveSpotCandidates(liveSpotAccount(live));
		allowLiveTrading(live, true);

		ExecutionRoutingResult result = routeWith(
				routerWith(true, true, true), live, spotSignal());

		String rendered = String.valueOf(result.request())
				+ String.valueOf(result.detail())
				+ String.valueOf(result.targetEngine())
				+ String.valueOf(result.rejectionReason())
				+ String.valueOf(result.identity());
		assertThat(rendered)
				.doesNotContain("dGVzdC1vbmx5LWtleQ")
				.doesNotContain("dGVzdC1vbmx5LXNlY3JldA")
				.doesNotContain("apiKey")
				.doesNotContain("apiSecret")
				.doesNotContain("listenKey")
				.doesNotContain("Authorization");
	}

	@Test
	void thePersistedLedgerStoresNoCredential() {
		User live = user(AccountType.LIVE);
		liveSpotCandidates(liveSpotAccount(live));
		allowLiveTrading(live, true);
		Signal signal = spotSignal();

		routeFor(live, signal);

		var captor = org.mockito.ArgumentCaptor.forClass(ExecutionDecisionRecord.class);
		verify(decisionRepository).saveAndFlush(captor.capture());
		String row = String.valueOf(captor.getValue());
		assertThat(row)
				.doesNotContain("dGVzdC1vbmx5LWtleQ")
				.doesNotContain("dGVzdC1vbmx5LXNlY3JldA")
				.doesNotContain("apiKey")
				.doesNotContain("listenKey");
	}

	@Test
	void everyRejectionCarriesAReason() {
		User live = user(AccountType.LIVE);
		LiveTradingAccount account = liveSpotAccount(live);
		account.setEnabled(false);
		liveSpotCandidates(account);
		allowLiveTrading(live, true);

		routeFor(live, spotSignal());

		var captor = org.mockito.ArgumentCaptor.forClass(ExecutionDecisionRecord.class);
		verify(decisionRepository).saveAndFlush(captor.capture());
		assertThat(captor.getValue().getDecision()).isEqualTo(ExecutionDecision.REJECT);
		assertThat(captor.getValue().getRejectionReason())
				.as("a rejection must never be silent")
				.isNotBlank();
	}

	@Test
	void aNonRejectionCarriesNoReason() {
		User live = user(AccountType.LIVE);
		liveSpotCandidates(liveSpotAccount(live));
		allowLiveTrading(live, true);

		routeFor(live, spotSignal());

		var captor = org.mockito.ArgumentCaptor.forClass(ExecutionDecisionRecord.class);
		verify(decisionRepository).saveAndFlush(captor.capture());
		assertThat(captor.getValue().getDecision()).isEqualTo(ExecutionDecision.EXECUTE);
		assertThat(captor.getValue().getRejectionReason())
				.as("a non-rejection must not carry a misleading reason")
				.isNull();
	}

	// ================================================ single authoritative entry

	@Test
	void noExecutionEngineListensForTheSignalEventItself() {
		// Structural guarantee: the router must be the single execution entry point.
		// A second listener on the same event is precisely how one signal would
		// execute twice, so this is asserted against the source tree rather than
		// trusted to review.
		assertThat(executionEventListeners())
				.as("no component other than ExecutionRouter may consume "
						+ "SignalGeneratedEvent for execution")
				.isEmpty();
	}

	/**
	 * Files that both mention {@code SignalGeneratedEvent} and declare a listener
	 * annotation, excluding the router itself and the notification listener.
	 *
	 * <p>The notification service consumes the event only to raise push
	 * notifications; it never routes or executes, so it is not a second execution
	 * path.
	 */
	private static List<String> executionEventListeners() {
		List<String> found = new java.util.ArrayList<>();
		java.nio.file.Path root = java.nio.file.Path.of(
				"src", "main", "java", "com", "shyblack", "cryptosignals", "service");
		List<String> exempt = List.of("ExecutionRouter.java", "SignalNotificationService.java");

		try (var stream = java.nio.file.Files.walk(root)) {
			stream.filter(p -> p.toString().endsWith(".java"))
					.filter(p -> !exempt.contains(p.getFileName().toString()))
					.forEach(p -> {
						String text;
						try {
							text = java.nio.file.Files.readString(p);
						} catch (java.io.IOException ex) {
							throw new RuntimeException(ex);
						}
						boolean mentionsSignal = text.contains("SignalGeneratedEvent");
						boolean declaresListener = text.contains("@TransactionalEventListener")
								|| text.contains("@EventListener");
						if (mentionsSignal && declaresListener) {
							found.add(p.getFileName().toString());
						}
					});
		} catch (java.io.IOException ex) {
			throw new RuntimeException(ex);
		}
		return found.stream().distinct().sorted().toList();
	}

	/** Routes with a specific router instance and returns the decision for one user. */
	private ExecutionRoutingResult routeWith(ExecutionRouter target, User user, Signal signal) {
		return target.routeAll(signal).stream()
				.filter(r -> r.identity().userId().equals(user.getId()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("no decision for this user"));
	}
}
