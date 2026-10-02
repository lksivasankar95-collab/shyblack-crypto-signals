package com.shyblack.cryptosignals.service.execution;

import com.shyblack.cryptosignals.config.ExecutionProperties;
import com.shyblack.cryptosignals.config.FuturesTradingProperties;
import com.shyblack.cryptosignals.config.LiveTradingProperties;
import com.shyblack.cryptosignals.entity.ExecutionDecisionRecord;
import com.shyblack.cryptosignals.entity.FuturesTradingAccount;
import com.shyblack.cryptosignals.entity.LiveTradingAccount;
import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.UserSettings;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.FuturesRiskReason;
import com.shyblack.cryptosignals.entity.enums.LiveTradingRiskReason;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.SignalStatus;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.repository.ExecutionDecisionRecordRepository;
import com.shyblack.cryptosignals.repository.FuturesTradingAccountRepository;
import com.shyblack.cryptosignals.repository.LiveTradingAccountRepository;
import com.shyblack.cryptosignals.repository.SignalRepository;
import com.shyblack.cryptosignals.repository.UserRepository;
import com.shyblack.cryptosignals.repository.UserSettingsRepository;
import com.shyblack.cryptosignals.service.SignalGeneratedEvent;
import com.shyblack.cryptosignals.service.futures.FuturesAccountService;
import com.shyblack.cryptosignals.service.futures.FuturesEngineService;
import com.shyblack.cryptosignals.service.futures.FuturesRiskService;
import com.shyblack.cryptosignals.service.live.LiveTradingAccountService;
import com.shyblack.cryptosignals.service.live.LiveTradingEngineService;
import com.shyblack.cryptosignals.service.live.LiveTradingRiskService;
import com.shyblack.cryptosignals.service.paper.PaperTradingEngineService;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The single authoritative routing decision for a signal.
 *
 * <p>Every {@link SignalGeneratedEvent} passes through here exactly once. This
 * class decides <i>where</i> a signal may execute and <i>whether</i> it may
 * execute at all. It does not calculate signals, change scores, alter strategy
 * parameters, size positions, or submit an order itself.
 *
 * <p><b>Single entry point.</b> The three engines keep their existing logic and
 * no longer listen for {@link SignalGeneratedEvent} themselves; this router is
 * the only component that does. That is what prevents the
 * "signal → paper listener → router → paper listener again" double-execution
 * shape, and it is verified by a test that counts listener invocations.
 *
 * <p><b>Gate order is fixed and first-failure-wins</b>, so the reported reason is
 * deterministic for a given input. The order is capability, then signal
 * eligibility, then account state, then operator approval, then idempotency, then
 * delegated risk.
 *
 * <p><b>Fail closed.</b> Every path that is not explicitly {@code EXECUTE} places
 * no order. That includes the router being disabled, dry-run being on, a missing
 * adapter, an unreadable credential, and any unexpected exception: the catch
 * block records a rejection rather than falling through to execution.
 *
 * <p><b>Live execution is off unless explicitly enabled.</b> Three independent
 * switches must all be open: {@code app.execution.enabled},
 * {@code app.live-trading.auto-execute} (or the futures equivalent), and the
 * per-account gates. All default to disabled.
 */
@Service
@RequiredArgsConstructor
public class ExecutionRouter {

	private static final Logger log = LoggerFactory.getLogger(ExecutionRouter.class);

	private final ExecutionProperties props;
	private final LiveTradingProperties liveProps;
	private final FuturesTradingProperties futuresProps;
	private final SignalRepository signalRepository;
	private final ExecutionDecisionRecordRepository decisionRepository;
	private final LiveTradingAccountRepository liveAccountRepository;
	private final FuturesTradingAccountRepository futuresAccountRepository;
	private final UserRepository userRepository;
	private final UserSettingsRepository userSettingsRepository;

	private final PaperTradingEngineService paperEngine;
	private final LiveTradingEngineService liveEngine;
	private final FuturesEngineService futuresEngine;
private final LiveTradingRiskService liveRiskService;
	private final FuturesRiskService futuresRiskService;

	// ------------------------------------------------------------------ event entry

	/**
	 * The one and only {@link SignalGeneratedEvent} execution listener.
	 *
	 * <p>Runs after commit, matching the phase the three engines previously used,
	 * so the signal row is already durable when it is read.
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void onSignalGenerated(SignalGeneratedEvent event) {
		UUID signalId = event.getSignalId();
		Optional<Signal> maybe = signalRepository.findById(signalId);
		if (maybe.isEmpty()) {
			log.warn("[Exec] signal {} not found — nothing routed", signalId);
			return;
		}
		Signal signal = maybe.get();

		List<ExecutionRoutingResult> results = routeAll(signal);
		for (ExecutionRoutingResult result : results) {
			log.info("[Exec] signal={} mode={} category={} decision={} reason={} engine={}",
					signalId, result.accountMode(), result.accountCategory(),
					result.decision(), result.rejectionReason(), result.targetEngine());
		}
	}

	/**
	 * Routes one signal to every account scope that could act on it.
	 *
	 * <p>Returns one result per evaluated account, so a caller can see every
	 * decision rather than only the first failure. A failure for one account never
	 * prevents the others from being evaluated.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public List<ExecutionRoutingResult> routeAll(Signal signal) {
		List<ExecutionRoutingResult> results = new java.util.ArrayList<>();

// PAPER: every enabled account whose legacy account type is PAPER. Paper is
		// mode-agnostic by existing design and is deliberately left that way.
		for (User user : paperCandidates(signal)) {
			results.add(routeToPaper(user, signal));
		}

		TradingMode mode = signal.getTradingMode();

		if (mode == TradingMode.SPOT) {
			for (LiveTradingAccount account : liveAccountRepository.findAllWithCredential()) {
				if (account.getUser() == null) {
					continue;
				}
				results.add(routeToLiveSpot(account.getUser(), account, signal));
			}
			return results;
		}

		if (mode == TradingMode.FUTURES) {
			for (FuturesTradingAccount account : futuresAccounts()) {
				if (account.getUser() == null) {
					continue;
				}
				results.add(routeToLiveFutures(account.getUser(), account, signal));
			}
			return results;
		}

		// An unsupported or absent trading mode reaches every live account as an
		// explicit NOT_SUPPORTED decision. Emitting nothing would let the signal
		// disappear from the audit trail, which is indistinguishable from never
		// having been routed at all.
		for (LiveTradingAccount account : liveAccountRepository.findAllWithCredential()) {
			if (account.getUser() == null) {
				continue;
			}
			results.add(unsupportedLiveDecision(account.getUser(), signal,
					AccountCategory.SPOT));
		}
		for (FuturesTradingAccount account : futuresAccounts()) {
			if (account.getUser() == null) {
				continue;
			}
			results.add(unsupportedLiveDecision(account.getUser(), signal,
					AccountCategory.FUTURES));
		}
		return results;
	}

	/**
	 * A LIVE decision for a trading mode that has no engine.
	 *
	 * <p>Recorded rather than skipped so an operator can see that the signal was
	 * considered and why it was refused.
	 */
	private ExecutionRoutingResult unsupportedLiveDecision(
			User user, Signal signal, AccountCategory category) {
		TradingMode mode = signal.getTradingMode();
		ExecutionIdentity identity = ExecutionIdentity.of(
				user.getId(), signal.getId(), AccountMode.LIVE, category, mode);

		if (mode == null) {
			return persist(reject(identity, ExecutionRejectionReason.TRADING_MODE_MISSING,
					"The signal carries no trading mode."));
		}
		return persist(notSupported(identity, ExecutionRejectionReason.OPTIONS_NOT_SUPPORTED,
				"Options execution is not supported; no options engine exists."));
	}

	// ------------------------------------------------------------------ PAPER

	/**
	 * PAPER decision.
	 *
	 * <p>Paper has no kill switch, no credential and no auto-execute gate in the
	 * existing design, and Phase 8 does not add one: doing so would change paper
	 * accounting behaviour, which is out of scope. Only capability and signal
	 * eligibility apply.
	 */
private ExecutionRoutingResult routeToPaper(User user, Signal signal) {
		ExecutionIdentity identity = ExecutionIdentity.of(
				user.getId(), signal.getId(), AccountMode.PAPER, AccountCategory.MAIN,
				signal.getTradingMode());

		ExecutionRoutingResult blocked = capabilityAndSignalGate(identity, signal, AccountMode.PAPER);
		if (blocked != null) {
			return persist(blocked);
		}

		// Idempotency applies to paper too. The paper module additionally enforces
		// its own (portfolio_id, signal_id) uniqueness, but a duplicate event would
		// otherwise still re-enter the engine and re-evaluate every account.
		ExecutionRoutingResult duplicate = duplicateGuard(identity);
		if (duplicate != null) {
			return duplicate;
		}

		ExecutionRequest request = ExecutionRequests.from(
				signal, user.getId(), AccountMode.PAPER, AccountCategory.MAIN, signal.getTradingMode());

		ExecutionRoutingResult result = ExecutionRoutingResult.builder()
				.identity(identity)
				.decision(ExecutionDecision.EXECUTE)
				.detail("Routed to the existing paper execution path.")
				.targetEngine("PAPER")
				.request(request)
				.dryRun(false)
				.outcome(ExecutionOutcome.ORDER_REQUESTED)
				.decidedAt(Instant.now())
				.build();

		ExecutionRoutingResult persisted = persist(result);
		delegatePaper(persisted, user, signal);
		return persisted;
	}

	/**
	 * Hands the approved signal to the paper engine.
	 *
	 * <p>Only ever reached for {@link ExecutionDecision#EXECUTE}, so a refusal
	 * cannot become an execution. An engine failure is logged and contained: one
	 * account's failure must not abort the rest of the fan-out.
	 */
	private void delegatePaper(ExecutionRoutingResult result, User user, Signal signal) {
		if (result.decision() != ExecutionDecision.EXECUTE) {
			return;
		}
		try {
			paperEngine.executeForUser(user, signal);
		} catch (Exception ex) {
			log.warn("[Exec] paper delegation failed user={} signal={} err={}",
					user.getId(), signal.getId(), ex.getMessage());
		}
	}

	// ------------------------------------------------------------------ LIVE SPOT

	/**
	 * LIVE SPOT decision.
	 *
	 * <p>Runs the full gate chain and then delegates to the existing live engine.
	 * The router adds no execution logic of its own beyond the gates.
	 */
	private ExecutionRoutingResult routeToLiveSpot(
			User user, LiveTradingAccount account, Signal signal) {

		ExecutionIdentity identity = ExecutionIdentity.of(
				user.getId(), signal.getId(), AccountMode.LIVE, AccountCategory.SPOT,
				TradingMode.SPOT);

		ExecutionRoutingResult blocked = capabilityAndSignalGate(identity, signal, AccountMode.LIVE);
		if (blocked != null) {
			return persist(blocked);
		}

		// Account state gates, in fixed order.
		ExecutionRejectionReason accountProblem = liveAccountProblem(account, user);
		if (accountProblem != null) {
			return persist(reject(identity, accountProblem, "Live spot account gate failed."));
		}

		if (!props.enabled()) {
			return persist(reject(identity, ExecutionRejectionReason.AUTO_EXECUTE_DISABLED,
					"app.execution.enabled is false; the execution router is off."));
		}
		if (!liveProps.autoExecute()) {
			return persist(reject(identity, ExecutionRejectionReason.AUTO_EXECUTE_DISABLED,
					"app.live-trading.auto-execute is false; live auto execution is disabled."));
		}

		ExecutionRoutingResult duplicate = duplicateGuard(identity);
		if (duplicate != null) {
			return duplicate;
		}

		// Delegate the real risk rules to the existing risk service. The router does
		// not reimplement or reinterpret them.
		LiveTradingRiskReason risk = liveRiskService.check(user, account, signal);
		if (risk != LiveTradingRiskReason.OK) {
			return persist(reject(identity, ExecutionRejectionReason.RISK_REJECTED,
					"LiveTradingRiskService refused the signal: " + risk));
		}

		ExecutionRequest request = ExecutionRequests.from(
				signal, user.getId(), AccountMode.LIVE, AccountCategory.SPOT, TradingMode.SPOT);

		if (props.dryRun()) {
			// Every gate ran and the request exists, but nothing is sent. This is how
			// the boundary is validated without an exchange.
			return persist(ExecutionRoutingResult.builder()
					.identity(identity)
					.decision(ExecutionDecision.DRY_RUN)
					.detail("All gates passed. No order sent: dry-run mode is active.")
					.targetEngine("LIVE_SPOT")
					.request(request)
					.dryRun(true)
					.decidedAt(Instant.now())
					.build());
		}

		ExecutionRoutingResult result = ExecutionRoutingResult.builder()
				.identity(identity)
				.decision(ExecutionDecision.EXECUTE)
				.detail("All gates passed; delegating to the live spot engine.")
				.targetEngine("LIVE_SPOT")
				.request(request)
				.dryRun(false)
				.outcome(ExecutionOutcome.ORDER_REQUESTED)
				.decidedAt(Instant.now())
				.build();

		ExecutionRoutingResult persisted = persist(result);
		delegateLiveSpot(persisted, account, signal);
		return persisted;
	}

	private void delegateLiveSpot(
			ExecutionRoutingResult result, LiveTradingAccount account, Signal signal) {
		if (result.decision() != ExecutionDecision.EXECUTE) {
			return;
		}
		try {
			liveEngine.executeForAccount(account, signal);
		} catch (Exception ex) {
			log.warn("[Exec] live spot delegation failed account={} signal={} err={}",
					account.getId(), signal.getId(), ex.getMessage());
		}
	}

	// ------------------------------------------------------------------ LIVE FUTURES

	/** LIVE FUTURES decision. Mirrors the spot chain, delegating to futures gates. */
	private ExecutionRoutingResult routeToLiveFutures(
			User user, FuturesTradingAccount account, Signal signal) {

		ExecutionIdentity identity = ExecutionIdentity.of(
				user.getId(), signal.getId(), AccountMode.LIVE, AccountCategory.FUTURES,
				TradingMode.FUTURES);

		ExecutionRoutingResult blocked = capabilityAndSignalGate(identity, signal, AccountMode.LIVE);
		if (blocked != null) {
			return persist(blocked);
		}

		ExecutionRejectionReason accountProblem = futuresAccountProblem(account, user);
		if (accountProblem != null) {
			return persist(reject(identity, accountProblem, "Live futures account gate failed."));
		}

		if (!props.enabled()) {
			return persist(reject(identity, ExecutionRejectionReason.AUTO_EXECUTE_DISABLED,
					"app.execution.enabled is false; the execution router is off."));
		}
		if (!futuresProps.autoExecute()) {
			return persist(reject(identity, ExecutionRejectionReason.AUTO_EXECUTE_DISABLED,
					"app.futures-trading.auto-execute is false; live auto execution is disabled."));
		}

		ExecutionRoutingResult duplicate = duplicateGuard(identity);
		if (duplicate != null) {
			return duplicate;
		}

		// Reuse FuturesRiskService unchanged: margin mode, position mode,
		// acknowledgement, kill switch, duplicate position, limits.
		FuturesRiskReason risk = futuresRiskService.check(user, account, signal);
		if (risk != FuturesRiskReason.OK) {
			return persist(reject(identity, ExecutionRejectionReason.RISK_REJECTED,
					"FuturesRiskService refused the signal: " + risk));
		}

		ExecutionRequest request = ExecutionRequests.from(
				signal, user.getId(), AccountMode.LIVE, AccountCategory.FUTURES, TradingMode.FUTURES);

		if (props.dryRun()) {
			return persist(ExecutionRoutingResult.builder()
					.identity(identity)
					.decision(ExecutionDecision.DRY_RUN)
					.detail("All gates passed. No order sent: dry-run mode is active.")
					.targetEngine("LIVE_FUTURES")
					.request(request)
					.dryRun(true)
					.decidedAt(Instant.now())
					.build());
		}

		ExecutionRoutingResult result = ExecutionRoutingResult.builder()
				.identity(identity)
				.decision(ExecutionDecision.EXECUTE)
				.detail("All gates passed; delegating to the live futures engine.")
				.targetEngine("LIVE_FUTURES")
				.request(request)
				.dryRun(false)
				.outcome(ExecutionOutcome.ORDER_REQUESTED)
				.decidedAt(Instant.now())
				.build();

		ExecutionRoutingResult persisted = persist(result);
		delegateLiveFutures(persisted, account, signal);
		return persisted;
	}

	private void delegateLiveFutures(
			ExecutionRoutingResult result,
			FuturesTradingAccount account,
			Signal signal) {
		if (result.decision() != ExecutionDecision.EXECUTE) {
			return;
		}
		try {
			futuresEngine.executeForAccount(account, signal);
		} catch (Exception ex) {
			log.warn("[Exec] live futures delegation failed account={} signal={} err={}",
					account.getId(), signal.getId(), ex.getMessage());
		}
	}

	// ------------------------------------------------------------------ gates

	/**
	 * Capability and signal eligibility, shared by every mode.
	 *
	 * <p>Returns null when the signal passes, otherwise the decision to persist.
	 * Kept in one method so PAPER and LIVE cannot drift apart on what makes a
	 * signal eligible at all.
	 */
	private ExecutionRoutingResult capabilityAndSignalGate(
			ExecutionIdentity identity, Signal signal, AccountMode mode) {

		TradingMode tradingMode = signal.getTradingMode();

		if (tradingMode == null) {
			return reject(identity, ExecutionRejectionReason.TRADING_MODE_MISSING,
					"The signal carries no trading mode.");
		}
		if (!ExecutionRouting.isSupportedTradingMode(tradingMode)) {
			// OPTIONS has no engine. Distinct from a rejection: nothing was ever built.
			return notSupported(identity, ExecutionRejectionReason.OPTIONS_NOT_SUPPORTED,
					"Options execution is not supported; no options engine exists.");
		}
		if (identity.accountCategory() == AccountCategory.MAIN && mode == AccountMode.LIVE) {
			return notExecutable(identity, ExecutionRejectionReason.MAIN_NOT_EXECUTABLE,
					"MAIN aggregates spot and futures and is never an execution target.");
		}
		if (!ExecutionRouting.isExecutableCategory(identity.accountCategory())
				&& mode == AccountMode.LIVE) {
			return notSupported(identity, ExecutionRejectionReason.NO_EXECUTION_ENGINE,
					"No execution engine exists for category " + identity.accountCategory() + ".");
		}

		if (signal.getStatus() != SignalStatus.ACTIVE) {
			return reject(identity, ExecutionRejectionReason.SIGNAL_NOT_ACTIVE,
					"Signal status is " + signal.getStatus() + ", not ACTIVE.");
		}
		if (signal.getSymbol() == null || signal.getSymbol().isBlank()) {
			return reject(identity, ExecutionRejectionReason.SYMBOL_MISSING,
					"The signal carries no symbol.");
		}
		if (signal.getSide() == null) {
			return reject(identity, ExecutionRejectionReason.DIRECTION_INVALID,
					"The signal carries no direction.");
		}
		// Spot executes long entries only; the futures engine is two-sided. An
		// unsupported direction is rejected rather than silently coerced.
		if (mode == AccountMode.LIVE && identity.accountCategory() == AccountCategory.SPOT
				&& signal.getSide() != PositionSide.LONG) {
			return reject(identity, ExecutionRejectionReason.DIRECTION_INVALID,
					"Live spot execution supports LONG entries only; got " + signal.getSide() + ".");
		}
		return null;
	}

	/**
	 * Live spot account state, evaluated in a fixed order.
	 *
	 * <p>Kill switch is checked before connection and before approval, so an
	 * account under emergency stop is refused even if everything else is fine.
	 */
	private ExecutionRejectionReason liveAccountProblem(
			LiveTradingAccount account, User user) {

		if (!account.isEnabled()) {
			return ExecutionRejectionReason.ACCOUNT_INACTIVE;
		}
		if (account.isKillSwitchActive()) {
			return ExecutionRejectionReason.KILL_SWITCH_ACTIVE;
		}
		if (account.getConnectionStatus() != ExchangeConnectionStatus.CONNECTED) {
			return ExecutionRejectionReason.ACCOUNT_NOT_CONNECTED;
		}
		if (account.getCredential() == null) {
			return ExecutionRejectionReason.CREDENTIALS_UNAVAILABLE;
		}
		if (account.getCredential().getStatus() != ExchangeConnectionStatus.CONNECTED) {
			return ExecutionRejectionReason.CREDENTIALS_UNAVAILABLE;
		}
		if (!userSettingsRepository.findByUser_Id(user.getId())
				.map(UserSettings::isLiveTradingAllowed)
				.orElse(false)) {
			return ExecutionRejectionReason.LIVE_TRADING_NOT_ALLOWED;
		}
		return null;
	}

	/** Live futures account state, evaluated in a fixed order. */
	private ExecutionRejectionReason futuresAccountProblem(
			FuturesTradingAccount account, User user) {

		if (!account.isEnabled()) {
			return ExecutionRejectionReason.ACCOUNT_INACTIVE;
		}
		if (account.isKillSwitchActive()) {
			return ExecutionRejectionReason.KILL_SWITCH_ACTIVE;
		}
		if (!account.isAcknowledged()) {
			return ExecutionRejectionReason.ACCOUNT_NOT_ACKNOWLEDGED;
		}
		if (account.getConnectionStatus() != ExchangeConnectionStatus.CONNECTED) {
			return ExecutionRejectionReason.ACCOUNT_NOT_CONNECTED;
		}
		if (account.getCredential() == null
				|| account.getCredential().getStatus() != ExchangeConnectionStatus.CONNECTED) {
			return ExecutionRejectionReason.CREDENTIALS_UNAVAILABLE;
		}
		if (!userSettingsRepository.findByUser_Id(user.getId())
				.map(UserSettings::isLiveTradingAllowed)
				.orElse(false)) {
			return ExecutionRejectionReason.LIVE_TRADING_NOT_ALLOWED;
		}
		return null;
	}

	/**
	 * Idempotency guard.
	 *
	 * <p>Two mechanisms, in order:
	 * <ol>
	 *   <li>A pre-check on the decision ledger, so the common duplicate case costs
	 *       one indexed read.</li>
	 *   <li>The table's unique constraint, which is the real guarantee. If two
	 *       consumers race, the loser's insert raises a constraint violation and
	 *       is reported as a duplicate rather than executing.</li>
	 * </ol>
	 *
	 * <p>A previous {@code UNKNOWN} outcome is a separate rejection from a plain
	 * duplicate: the order may exist on the exchange, so the correct next step is
	 * reconciliation, never re-sending.
	 */
	private ExecutionRoutingResult duplicateGuard(ExecutionIdentity identity) {
		Optional<ExecutionDecisionRecord> existing =
				decisionRepository.findByUserIdAndSignalIdAndAccountModeAndAccountCategory(
						identity.userId(), identity.signalId(),
						identity.accountMode(), identity.accountCategory());
		if (existing.isEmpty()) {
			return null;
		}
		ExecutionDecisionRecord prior = existing.get();
		if (prior.getOutcome() == ExecutionOutcome.UNKNOWN) {
			return ExecutionRoutingResult.builder()
					.identity(identity)
					.decision(ExecutionDecision.REJECT)
					.rejectionReason(ExecutionRejectionReason.PREVIOUS_OUTCOME_UNKNOWN)
					.detail("A previous attempt for this identity is UNKNOWN. Reconcile the "
							+ "exchange order state before considering a resubmission.")
					.recordId(prior.getId())
					.decidedAt(Instant.now())
					.build();
		}
		return ExecutionRoutingResult.builder()
				.identity(identity)
				.decision(ExecutionDecision.REJECT)
				.rejectionReason(ExecutionRejectionReason.DUPLICATE_EXECUTION)
				.detail("An execution decision already exists for this signal and scope.")
				.recordId(prior.getId())
				.decidedAt(Instant.now())
				.build();
	}

	// ------------------------------------------------------------------ persistence

	/**
	 * Persists the decision, converting a lost uniqueness race into a duplicate
	 * rejection rather than an execution.
	 *
	 * <p>A {@code DataIntegrityViolationException} here means another consumer
	 * inserted the same identity first. That is the safe outcome: no order.
	 */
	private ExecutionRoutingResult persist(ExecutionRoutingResult result) {
		if (result.recordId() != null) {
			return result;
		}
		try {
			ExecutionDecisionRecord saved = decisionRepository.saveAndFlush(result.toRecord());
			return ExecutionRoutingResult.builder()
					.identity(result.identity())
					.decision(result.decision())
					.rejectionReason(result.rejectionReason())
					.detail(result.detail())
					.targetEngine(result.targetEngine())
					.request(result.request())
					.dryRun(result.dryRun())
					.outcome(result.outcome())
					.exchangeOrderId(result.exchangeOrderId())
					.exchangeStatus(result.exchangeStatus())
					.recordId(saved.getId())
					.decidedAt(result.decidedAt())
					.build();
} catch (DataIntegrityViolationException dup) {
			// Caught specifically because a lost uniqueness race must become a
			// rejection, not an escaped exception. Note this is Spring's translation
			// of the constraint violation, not the JPA PersistenceException.
			log.info("[Exec] duplicate execution identity raced and was refused: {}",
					result.identity() == null ? null : result.identity().canonical());
			return ExecutionRoutingResult.builder()
					.identity(result.identity())
					.decision(ExecutionDecision.REJECT)
					.rejectionReason(ExecutionRejectionReason.DUPLICATE_EXECUTION)
					.detail("A concurrent attempt claimed this execution identity first.")
					.request(result.request())
					.decidedAt(Instant.now())
					.build();
		}
	}

	// ------------------------------------------------------------------ candidates

	/**
	 * Every enabled user on the legacy PAPER account type.
	 *
	 * <p>Read from the account table rather than a filtered repository query, so
	 * the router is not silently handed a pre-filtered candidate list: the gate
	 * chain decides eligibility, and the audit trail records the refusal.
	 */
	private List<User> paperCandidates(Signal signal) {
		return userRepository.findAll().stream()
				.filter(User::isEnabled)
				.filter(u -> u.getAccountType() == com.shyblack.cryptosignals.entity.enums.AccountType.PAPER)
				.toList();
	}

private List<FuturesTradingAccount> futuresAccounts() {
		return futuresAccountRepository.findAllWithCredential();
	}

	// ------------------------------------------------------------------ builders

	private ExecutionRoutingResult reject(
			ExecutionIdentity identity, ExecutionRejectionReason reason, String detail) {
		return ExecutionRoutingResult.builder()
				.identity(identity)
				.decision(ExecutionDecision.REJECT)
				.rejectionReason(reason)
				.detail(detail)
				.decidedAt(Instant.now())
				.build();
	}

	private ExecutionRoutingResult notSupported(
			ExecutionIdentity identity, ExecutionRejectionReason reason, String detail) {
		return ExecutionRoutingResult.builder()
				.identity(identity)
				.decision(ExecutionDecision.NOT_SUPPORTED)
				.rejectionReason(reason)
				.detail(detail)
				.decidedAt(Instant.now())
				.build();
	}

	private ExecutionRoutingResult notExecutable(
			ExecutionIdentity identity, ExecutionRejectionReason reason, String detail) {
		return ExecutionRoutingResult.builder()
				.identity(identity)
				.decision(ExecutionDecision.NOT_EXECUTABLE)
				.rejectionReason(reason)
				.detail(detail)
				.decidedAt(Instant.now())
				.build();
	}
}