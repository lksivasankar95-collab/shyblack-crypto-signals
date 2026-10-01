package com.shyblack.cryptosignals.service.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.PortfolioAccountConnection;
import com.shyblack.cryptosignals.entity.PortfolioExchangeBalance;
import com.shyblack.cryptosignals.entity.PortfolioExchangeEventApplied;
import com.shyblack.cryptosignals.entity.PortfolioExchangePosition;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountAvailability;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.FuturesMarginMode;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.repository.ExchangeCredentialRepository;
import com.shyblack.cryptosignals.repository.PortfolioAccountConnectionRepository;
import com.shyblack.cryptosignals.repository.PortfolioExchangeBalanceRepository;
import com.shyblack.cryptosignals.repository.PortfolioExchangeEventAppliedRepository;
import com.shyblack.cryptosignals.repository.PortfolioExchangePositionRepository;
import com.shyblack.cryptosignals.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Phase 4 event correctness, exercised through raw payloads with no socket involved.
 *
 * <p>Covers spot balance application, futures account/position application in both directions, flat
 * position handling, duplicate suppression, out-of-order rejection, unknown and malformed payloads,
 * Paper isolation, Options isolation, multi-user isolation, and secret hygiene.
 */
@SpringBootTest
@ActiveProfiles("test")
class LiveUserStreamEventProcessorTest {

	private static final AtomicInteger SEQ = new AtomicInteger();

	@Autowired
	private LiveUserStreamEventProcessor processor;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ExchangeCredentialRepository credentialRepository;

	@Autowired
	private PortfolioExchangeBalanceRepository balanceRepository;

	@Autowired
	private PortfolioExchangePositionRepository positionRepository;

	@Autowired
	private PortfolioAccountConnectionRepository connectionRepository;

	@Autowired
	private PortfolioExchangeEventAppliedRepository eventRepository;

	@BeforeEach
	void clearLedger() {
		// The ledger is append-only; clearing keeps assertions about row counts deterministic.
		eventRepository.deleteAll();
	}

	private User user(AccountType accountType) {
		User u = new User();
		u.setEmail("stream-" + SEQ.incrementAndGet() + "@example.com");
		u.setFullName("Stream Tester");
		u.setAccountType(accountType);
		return userRepository.saveAndFlush(u);
	}

	private void credential(User u) {
		ExchangeCredential c = new ExchangeCredential();
		c.setUser(u);
		c.setExchange(ExchangeName.BINANCE);
		c.setApiKey("dGVzdC1vbmx5LWtleQ==");
		c.setApiSecret("dGVzdC1vbmx5LXNlY3JldA==");
		credentialRepository.saveAndFlush(c);
	}

	private long now() {
		return Instant.now().toEpochMilli();
	}

	private String spotBalanceEvent(long eventTime, String asset, String free, String locked) {
		return """
				{"e":"outboundAccountPosition","E":%d,"u":%d,
				 "B":[{"a":"%s","f":"%s","l":"%s"}]}"""
				.formatted(eventTime, eventTime, asset, free, locked);
	}

	private String futuresAccountEvent(long transactionTime, String positions) {
		return """
				{"e":"ACCOUNT_UPDATE","E":%d,"T":%d,
				 "a":{"m":"ORDER_TRADE_UPDATE","B":[],"P":%s}}"""
				.formatted(transactionTime, transactionTime, positions);
	}

	// ------------------------------------------------------ B. spot balances

	@Test
	void spotBalanceUpdateIsAppliedToTheReadSnapshot() {
		User u = user(AccountType.LIVE);
		credential(u);
		long t = now();

		var outcome = processor.process(u, AccountCategory.SPOT,
				spotBalanceEvent(t, "USDT", "150.5", "20.25"));

		assertThat(outcome).isEqualTo(LiveUserStreamEventProcessor.Outcome.APPLIED);
		PortfolioExchangeBalance usdt = balanceRepository
				.findByUserAndExchangeAndAsset(u, ExchangeName.BINANCE, "USDT").orElseThrow();
		assertThat(usdt.getFree()).isEqualByComparingTo("150.5");
		assertThat(usdt.getLocked()).isEqualByComparingTo("20.25");
		assertThat(usdt.total()).isEqualByComparingTo("170.75");
	}

	@Test
	void spotUpdateCreatesTheScopeConnectionRowAndMarksItAvailable() {
		User u = user(AccountType.LIVE);
		credential(u);

		processor.process(u, AccountCategory.SPOT, spotBalanceEvent(now(), "USDT", "1", "0"));

		PortfolioAccountConnection connection = connectionRepository
				.findByUserAndAccountModeAndAccountCategory(u, AccountMode.LIVE, AccountCategory.SPOT)
				.orElseThrow();
		assertThat(connection.getAvailability()).isEqualTo(AccountAvailability.AVAILABLE);
		assertThat(connection.getConnectionStatus()).isEqualTo(ExchangeConnectionStatus.CONNECTED);
		assertThat(connection.getLastEventAt()).as("the stream high-water mark is recorded").isNotNull();
		assertThat(connection.getLastSyncedAt())
				.as("a stream event must never masquerade as a REST snapshot")
				.isNull();
	}

	@Test
	void spotBalanceIsNeverTreatedAsAPosition() {
		User u = user(AccountType.LIVE);
		credential(u);

		processor.process(u, AccountCategory.SPOT, spotBalanceEvent(now(), "BTC", "2", "0"));

		assertThat(positionRepository.findByUserAndExchangeOrderBySymbolAsc(u, ExchangeName.BINANCE))
				.as("spot has no position concept")
				.isEmpty();
	}

	@Test
	void spotExecutionReportIsAcceptedWithoutInventingOrderHistory() {
		User u = user(AccountType.LIVE);
		credential(u);
		String event = """
				{"e":"executionReport","E":%d,"s":"BTCUSDT","c":"SB-x","S":"BUY","o":"MARKET",
				 "f":"GTC","q":"1","p":"0","X":"FILLED","i":4242,"L":"0","n":"0","T":%d}"""
				.formatted(now(), now());

		var outcome = processor.process(u, AccountCategory.SPOT, event);

		assertThat(outcome).isEqualTo(LiveUserStreamEventProcessor.Outcome.APPLIED);
		assertThat(balanceRepository.findByUserAndExchange(u, ExchangeName.BINANCE))
				.as("an order event carries no balance, so none may be invented")
				.isEmpty();
	}

	@Test
	void spotDepositRequiresRestBecauseFreeAndLockedAreUnknown() {
		User u = user(AccountType.LIVE);
		credential(u);
		String event = """
				{"e":"balanceUpdate","E":%d,"a":"BTC","d":"1.00000000","T":%d}""".formatted(now(), now());

		var outcome = processor.process(u, AccountCategory.SPOT, event);

		assertThat(outcome).isEqualTo(LiveUserStreamEventProcessor.Outcome.RECONCILE_REQUIRED);
		assertThat(balanceRepository.findByUserAndExchange(u, ExchangeName.BINANCE)).isEmpty();
		PortfolioAccountConnection connection = connectionRepository
				.findByUserAndAccountModeAndAccountCategory(u, AccountMode.LIVE, AccountCategory.SPOT)
				.orElseThrow();
		assertThat(connection.getAvailability()).isEqualTo(AccountAvailability.STALE);
	}

	// -------------------------------------------------- C/D. futures account

	@Test
	void futuresAccountUpdateAppliesALongPosition() {
		User u = user(AccountType.LIVE);
		credential(u);
		long t = now();

		var outcome = processor.process(u, AccountCategory.FUTURES,
				futuresAccountEvent(t, "[{\"s\":\"BTCUSDT\",\"pa\":\"0.5\",\"ep\":\"60000\","
						+ "\"up\":\"500.25\",\"mt\":\"isolated\",\"iw\":\"10000\",\"ps\":\"BOTH\"}]"));

		assertThat(outcome).isEqualTo(LiveUserStreamEventProcessor.Outcome.APPLIED);
		PortfolioExchangePosition position = positionRepository
				.findByUserAndExchangeOrderBySymbolAsc(u, ExchangeName.BINANCE).get(0);
		assertThat(position.getSymbol()).isEqualTo("BTCUSDT");
		assertThat(position.getPositionSide()).isEqualTo(PositionSide.LONG);
		assertThat(position.getPositionAmount()).isEqualByComparingTo("0.5");
		assertThat(position.getEntryPrice()).isEqualByComparingTo("60000");
		assertThat(position.getUnrealizedProfit()).isEqualByComparingTo("500.25");
		assertThat(position.getIsolatedMargin()).isEqualByComparingTo("10000");
		assertThat(position.getMarginMode()).isEqualTo(FuturesMarginMode.ISOLATED);
	}

	@Test
	void futuresAccountUpdateAppliesAShortPosition() {
		User u = user(AccountType.LIVE);
		credential(u);

		processor.process(u, AccountCategory.FUTURES,
				futuresAccountEvent(now(), "[{\"s\":\"ETHUSDT\",\"pa\":\"-2\",\"ep\":\"3000\","
						+ "\"up\":\"-100\",\"mt\":\"cross\",\"iw\":\"0\"}]"));

		PortfolioExchangePosition position = positionRepository
				.findByUserAndExchangeOrderBySymbolAsc(u, ExchangeName.BINANCE).get(0);
		assertThat(position.getPositionSide()).isEqualTo(PositionSide.SHORT);
		assertThat(position.getPositionAmount()).isEqualByComparingTo("-2");
		assertThat(position.getMarginMode()).isEqualTo(FuturesMarginMode.CROSS);
	}

	@Test
	void futuresAccountUpdateHandlesAFlatPositionAsClosed() {
		User u = user(AccountType.LIVE);
		credential(u);
		processor.process(u, AccountCategory.FUTURES,
				futuresAccountEvent(now(), "[{\"s\":\"BTCUSDT\",\"pa\":\"0.5\",\"ep\":\"60000\"}]"));
		assertThat(positionRepository.findByUserAndExchangeOrderBySymbolAsc(u, ExchangeName.BINANCE))
				.hasSize(1);

		processor.process(u, AccountCategory.FUTURES,
				futuresAccountEvent(now() + 1000, "[{\"s\":\"BTCUSDT\",\"pa\":\"0\",\"ep\":\"0\"}]"));

		assertThat(positionRepository.findByUserAndExchangeOrderBySymbolAsc(u, ExchangeName.BINANCE))
				.as("flat on the exchange means closed, so the row must disappear")
				.isEmpty();
	}

	@Test
	void futuresUpdateNeverInventsMarkPriceOrLiquidationPrice() {
		User u = user(AccountType.LIVE);
		credential(u);

		processor.process(u, AccountCategory.FUTURES,
				futuresAccountEvent(now(), "[{\"s\":\"BTCUSDT\",\"pa\":\"0.5\",\"ep\":\"60000\"}]"));

		PortfolioExchangePosition position = positionRepository
				.findByUserAndExchangeOrderBySymbolAsc(u, ExchangeName.BINANCE).get(0);
		assertThat(position.getMarkPrice())
				.as("ACCOUNT_UPDATE carries no mark price; REST reconciliation supplies it")
				.isNull();
		assertThat(position.getLiquidationPrice()).isNull();
		assertThat(position.getLeverage()).isNull();
	}

	@Test
	void futuresAccountUpdateWithNoPositionsIsStillApplied() {
		User u = user(AccountType.LIVE);
		credential(u);

		var outcome = processor.process(u, AccountCategory.FUTURES,
				"{\"e\":\"ACCOUNT_UPDATE\",\"E\":%d,\"T\":%d,\"a\":{\"m\":\"ORDER_TRADE_UPDATE\","
						+ "\"B\":[{\"a\":\"USDT\",\"wb\":\"1000\",\"cw\":\"600\",\"bc\":\"-12.5\"}],\"P\":[]}}"
						.formatted(now(), now()));

		assertThat(outcome).isEqualTo(LiveUserStreamEventProcessor.Outcome.APPLIED);
		assertThat(positionRepository.findByUserAndExchangeOrderBySymbolAsc(u, ExchangeName.BINANCE))
				.isEmpty();
	}

	@Test
	void futuresOrderTradeUpdateIsAcceptedWithoutInventingHistory() {
		User u = user(AccountType.LIVE);
		credential(u);
		String event = """
				{"e":"ORDER_TRADE_UPDATE","E":%d,"T":%d,
				 "o":{"s":"BTCUSDT","S":"SELL","o\":\"TRAILING_STOP_MARKET","f":"GTC","q":"1",
				      "p":"0","X":"NEW","i":99,"t":-1,"si":0,"ss":0}}"""
				.formatted(now(), now());

		assertThat(processor.process(u, AccountCategory.FUTURES, event))
				.isEqualTo(LiveUserStreamEventProcessor.Outcome.APPLIED);
		assertThat(positionRepository.findByUserAndExchangeOrderBySymbolAsc(u, ExchangeName.BINANCE))
				.isEmpty();
	}

	// ------------------------------------------------------ E. idempotency

	@Test
	void duplicateEventIsAppliedExactlyOnce() {
		User u = user(AccountType.LIVE);
		credential(u);
		long t = now();
		String event = spotBalanceEvent(t, "USDT", "150.5", "20.25");

		assertThat(processor.process(u, AccountCategory.SPOT, event))
				.isEqualTo(LiveUserStreamEventProcessor.Outcome.APPLIED);
		assertThat(processor.process(u, AccountCategory.SPOT, event))
				.as("a byte-identical replay must be suppressed")
				.isEqualTo(LiveUserStreamEventProcessor.Outcome.DUPLICATE);
		assertThat(processor.process(u, AccountCategory.SPOT, event))
				.isEqualTo(LiveUserStreamEventProcessor.Outcome.DUPLICATE);

		assertThat(balanceRepository.findByUserAndExchange(u, ExchangeName.BINANCE))
				.as("no duplicated rows")
				.hasSize(1);
		assertThat(eventRepository.countByUserAndAccountCategory(u, AccountCategory.SPOT))
				.as("one ledger entry for one logical event")
				.isEqualTo(1);
		PortfolioExchangeBalance usdt = balanceRepository
				.findByUserAndExchangeAndAsset(u, ExchangeName.BINANCE, "USDT").orElseThrow();
		assertThat(usdt.getFree())
				.as("a replay must not double the balance")
				.isEqualByComparingTo("150.5");
	}

	@Test
	void duplicateFuturesEventDoesNotDoubleThePosition() {
		User u = user(AccountType.LIVE);
		credential(u);
		String event = futuresAccountEvent(now(),
				"[{\"s\":\"BTCUSDT\",\"pa\":\"0.5\",\"ep\":\"60000\",\"up\":\"10\"}]");

		processor.process(u, AccountCategory.FUTURES, event);
		processor.process(u, AccountCategory.FUTURES, event);

		assertThat(positionRepository.findByUserAndExchangeOrderBySymbolAsc(u, ExchangeName.BINANCE))
				.hasSize(1);
		assertThat(positionRepository.findByUserAndExchangeOrderBySymbolAsc(u, ExchangeName.BINANCE).get(0)
				.getPositionAmount()).isEqualByComparingTo("0.5");
	}

	@Test
	void duplicateSuppressionIsScopedPerUser() {
		User a = user(AccountType.LIVE);
		User b = user(AccountType.LIVE);
		credential(a);
		credential(b);
		String event = spotBalanceEvent(now(), "USDT", "10", "0");

		assertThat(processor.process(a, AccountCategory.SPOT, event))
				.isEqualTo(LiveUserStreamEventProcessor.Outcome.APPLIED);
		assertThat(processor.process(b, AccountCategory.SPOT, event))
				.as("the same payload for another user is not a duplicate")
				.isEqualTo(LiveUserStreamEventProcessor.Outcome.APPLIED);
	}

	@Test
	void duplicateSuppressionIsScopedPerCategory() {
		User u = user(AccountType.LIVE);
		credential(u);
		long t = now();
		String event = spotBalanceEvent(t, "USDT", "10", "0");

		assertThat(processor.process(u, AccountCategory.SPOT, event))
				.isEqualTo(LiveUserStreamEventProcessor.Outcome.APPLIED);
		assertThat(processor.process(u, AccountCategory.FUTURES, event))
				.as("an event type valid for spot is unknown to futures")
				.isEqualTo(LiveUserStreamEventProcessor.Outcome.UNKNOWN_EVENT);
	}

	@Test
	void suppressionSurvivesAReloadBecauseItIsPersisted() {
		User u = user(AccountType.LIVE);
		credential(u);
		String event = futuresAccountEvent(now(), "[{\"s\":\"BTCUSDT\",\"pa\":\"1\",\"ep\":\"60000\"}]");

		processor.process(u, AccountCategory.FUTURES, event);

		List<PortfolioExchangeEventApplied> ledger =
				eventRepository.findByUserAndAccountCategory(u, AccountCategory.FUTURES);
		assertThat(ledger).hasSize(1);
		assertThat(ledger.get(0).getEventIdentity())
				.as("identity is built from exchange fields, not a generated UUID")
				.startsWith("T=")
				.contains("m=ORDER_TRADE_UPDATE");
		assertThat(ledger.get(0).getEventTime()).isNotNull();
	}

	// ---------------------------------------------- F. ordering / staleness

	@Test
	void outOfOrderEventIsRejected() {
		User u = user(AccountType.LIVE);
		credential(u);
		long t = now();
		processor.process(u, AccountCategory.SPOT, spotBalanceEvent(t, "USDT", "100", "0"));
		processor.process(u, AccountCategory.SPOT, spotBalanceEvent(t + 5000, "USDT", "200", "0"));

		var outcome = processor.process(u, AccountCategory.SPOT, spotBalanceEvent(t, "USDT", "999", "0"));

		assertThat(outcome).isEqualTo(LiveUserStreamEventProcessor.Outcome.STALE_REJECTED);
		PortfolioExchangeBalance usdt = balanceRepository
				.findByUserAndExchangeAndAsset(u, ExchangeName.BINANCE, "USDT").orElseThrow();
		assertThat(usdt.getFree())
				.as("a stale event must never overwrite newer state")
				.isEqualByComparingTo("200");
	}

	@Test
	void outOfOrderFuturesEventIsRejected() {
		User u = user(AccountType.LIVE);
		credential(u);
		long t = now();
		processor.process(u, AccountCategory.FUTURES,
				futuresAccountEvent(t, "[{\"s\":\"BTCUSDT\",\"pa\":\"1\",\"ep\":\"60000\"}]"));
		processor.process(u, AccountCategory.FUTURES,
				futuresAccountEvent(t + 1000, "[{\"s\":\"BTCUSDT\",\"pa\":\"2\",\"ep\":\"60000\"}]"));

		assertThat(processor.process(u, AccountCategory.FUTURES,
				futuresAccountEvent(t, "[{\"s\":\"BTCUSDT\",\"pa\":\"9\",\"ep\":\"60000\"}]")))
				.isEqualTo(LiveUserStreamEventProcessor.Outcome.STALE_REJECTED);
		assertThat(positionRepository.findByUserAndExchangeOrderBySymbolAsc(u, ExchangeName.BINANCE).get(0)
				.getPositionAmount()).isEqualByComparingTo("2");
	}

	@Test
	void anIdleJumpIsNotTreatedAsAGap() {
		User u = user(AccountType.LIVE);
		credential(u);
		// An account idle for an hour and then trading again produces exactly this shape: two events
		// an hour apart, both in the past. Treating the jump as a missed event would fabricate a gap
		// and needlessly discard a real update.
		long anHourAgo = now() - 3_600_000;
		processor.process(u, AccountCategory.SPOT, spotBalanceEvent(anHourAgo, "USDT", "10", "0"));

		var outcome = processor.process(u, AccountCategory.SPOT, spotBalanceEvent(now(), "USDT", "11", "0"));

		assertThat(outcome).isEqualTo(LiveUserStreamEventProcessor.Outcome.APPLIED);
		assertThat(balanceRepository.findByUserAndExchangeAndAsset(u, ExchangeName.BINANCE, "USDT")
				.orElseThrow().getFree()).isEqualByComparingTo("11");
	}

	@Test
	void aPayloadDatedFarInTheFutureIsRejected() {
		User u = user(AccountType.LIVE);
		credential(u);
		long absurd = Instant.now().plusSeconds(86_400).toEpochMilli();

		assertThat(processor.process(u, AccountCategory.SPOT, spotBalanceEvent(absurd, "USDT", "1", "0")))
				.isEqualTo(LiveUserStreamEventProcessor.Outcome.MALFORMED);
		assertThat(balanceRepository.findByUserAndExchange(u, ExchangeName.BINANCE)).isEmpty();
	}

	// ------------------------------------------------ J/K. unknown/malformed

	@Test
	void unknownEventTypeDoesNotCrashAndIsIgnored() {
		User u = user(AccountType.LIVE);
		credential(u);

		var outcome = processor.process(u, AccountCategory.SPOT,
				"{\"e\":\"listStatus\",\"E\":%d,\"u\":1}".formatted(now()));

		assertThat(outcome).isEqualTo(LiveUserStreamEventProcessor.Outcome.UNKNOWN_EVENT);
		assertThat(balanceRepository.findByUserAndExchange(u, ExchangeName.BINANCE)).isEmpty();
	}

	@Test
	void futuresEventTypeIsUnknownToTheSpotStream() {
		User u = user(AccountType.LIVE);
		credential(u);

		assertThat(processor.process(u, AccountCategory.SPOT,
				"{\"e\":\"ACCOUNT_UPDATE\",\"E\":%d,\"T\":%d,\"a\":{}}".formatted(now(), now())))
				.isEqualTo(LiveUserStreamEventProcessor.Outcome.UNKNOWN_EVENT);
	}

	@Test
	void malformedPayloadsAreRejectedSafely() {
		User u = user(AccountType.LIVE);
		credential(u);

		assertThat(processor.process(u, AccountCategory.SPOT, "not json at all"))
				.isEqualTo(LiveUserStreamEventProcessor.Outcome.MALFORMED);
		assertThat(processor.process(u, AccountCategory.SPOT, "[1,2,3]"))
				.isEqualTo(LiveUserStreamEventProcessor.Outcome.MALFORMED);
		assertThat(processor.process(u, AccountCategory.SPOT, "{\"noEventType\":true}"))
				.isEqualTo(LiveUserStreamEventProcessor.Outcome.MALFORMED);
		assertThat(processor.process(u, AccountCategory.SPOT, ""))
				.isEqualTo(LiveUserStreamEventProcessor.Outcome.MALFORMED);
		assertThat(processor.process(u, AccountCategory.SPOT, null))
				.isEqualTo(LiveUserStreamEventProcessor.Outcome.MALFORMED);
	}

	@Test
	void aBalanceEventWithoutTheBalanceArrayRequiresReconciliation() {
		User u = user(AccountType.LIVE);
		credential(u);

		assertThat(processor.process(u, AccountCategory.SPOT,
				"{\"e\":\"outboundAccountPosition\",\"E\":%d,\"u\":1}".formatted(now())))
				.isEqualTo(LiveUserStreamEventProcessor.Outcome.RECONCILE_REQUIRED);
	}

	@Test
	void nonNumericBalanceFieldsAreIgnoredRatherThanCoerced() {
		User u = user(AccountType.LIVE);
		credential(u);

		processor.process(u, AccountCategory.SPOT,
				"{\"e\":\"outboundAccountPosition\",\"E\":%d,\"u\":1,"
						+ "\"B\":[{\"a\":\"USDT\",\"f\":\"abc\",\"l\":\"0\"}]}".formatted(now()));

		PortfolioExchangeBalance usdt = balanceRepository
				.findByUserAndExchangeAndAsset(u, ExchangeName.BINANCE, "USDT").orElseThrow();
		assertThat(usdt.getFree())
				.as("an unusable value is unknown, never zero")
				.isNull();
	}

	// ------------------------------------------------ M/N. scope boundaries

	@Test
	void paperUserIsRejectedAndNoExchangeRowIsWritten() {
		User paper = user(AccountType.PAPER);

		assertThat(processor.process(paper, AccountCategory.SPOT, spotBalanceEvent(now(), "USDT", "5", "0")))
				.as("a simulated account must never gain exchange state")
				.isEqualTo(LiveUserStreamEventProcessor.Outcome.SCOPE_REJECTED);
		assertThat(balanceRepository.findByUserAndExchange(paper, ExchangeName.BINANCE)).isEmpty();
		assertThat(connectionRepository.findByUserAndAccountMode(paper, AccountMode.LIVE)).isEmpty();
	}

	@Test
	void optionsAndMainScopesAreRejected() {
		User u = user(AccountType.LIVE);
		credential(u);

		assertThat(processor.process(u, AccountCategory.OPTIONS, "{\"e\":\"ACCOUNT_UPDATE\"}"))
				.isEqualTo(LiveUserStreamEventProcessor.Outcome.SCOPE_REJECTED);
		assertThat(processor.process(u, AccountCategory.MAIN, "{\"e\":\"ACCOUNT_UPDATE\"}"))
				.isEqualTo(LiveUserStreamEventProcessor.Outcome.SCOPE_REJECTED);
		assertThat(processor.process(null, AccountCategory.SPOT, "{}"))
				.isEqualTo(LiveUserStreamEventProcessor.Outcome.SCOPE_REJECTED);
		assertThat(processor.process(u, null, "{}"))
				.isEqualTo(LiveUserStreamEventProcessor.Outcome.SCOPE_REJECTED);
	}

	// ----------------------------------------------- L. multi-user isolation

	@Test
	void oneUsersEventNeverUpdatesAnotherUsersSnapshot() {
		User a = user(AccountType.LIVE);
		User b = user(AccountType.LIVE);
		credential(a);
		credential(b);

		processor.process(a, AccountCategory.SPOT, spotBalanceEvent(now(), "USDT", "111", "0"));

		assertThat(balanceRepository.findByUserAndExchange(b, ExchangeName.BINANCE)).isEmpty();
		assertThat(balanceRepository.findByUserAndExchange(a, ExchangeName.BINANCE)).hasSize(1);
		assertThat(balanceRepository.findByUserAndExchangeAndAsset(b, ExchangeName.BINANCE, "USDT"))
				.isEmpty();
	}

	@Test
	void oneUsersStaleOrderingNeverAffectsAnotherUsersScope() {
		User a = user(AccountType.LIVE);
		User b = user(AccountType.LIVE);
		credential(a);
		credential(b);
		long t = now();

		processor.process(a, AccountCategory.SPOT, spotBalanceEvent(t, "USDT", "1", "0"));
		// B has a much newer high-water mark; A's newer-looking event must still be evaluated alone.
		processor.process(b, AccountCategory.SPOT, spotBalanceEvent(t + 60_000, "USDT", "2", "0"));
		assertThat(processor.process(a, AccountCategory.SPOT, spotBalanceEvent(t + 30_000, "USDT", "3", "0")))
				.isEqualTo(LiveUserStreamEventProcessor.Outcome.APPLIED);
	}

	@Test
	void connectionStatusIsTrackedIndependentlyPerUserAndScope() {
		User a = user(AccountType.LIVE);
		User b = user(AccountType.LIVE);
		credential(a);
		credential(b);

		processor.process(a, AccountCategory.SPOT, spotBalanceEvent(now(), "USDT", "1", "0"));
		processor.process(b, AccountCategory.FUTURES,
				futuresAccountEvent(now(), "[{\"s\":\"BTCUSDT\",\"pa\":\"1\",\"ep\":\"1\"}]"));

		assertThat(connectionRepository.findByUserAndAccountModeAndAccountCategory(
				a, AccountMode.LIVE, AccountCategory.SPOT)).isPresent();
		assertThat(connectionRepository.findByUserAndAccountModeAndAccountCategory(
				a, AccountMode.LIVE, AccountCategory.FUTURES)).isEmpty();
		assertThat(connectionRepository.findByUserAndAccountModeAndAccountCategory(
				b, AccountMode.LIVE, AccountCategory.FUTURES)).isPresent();
		assertThat(connectionRepository.findByUserAndAccountModeAndAccountCategory(
				b, AccountMode.LIVE, AccountCategory.SPOT)).isEmpty();
	}

	// -------------------------------------------------------------- Q. secrets

	@Test
	void noExchangeRowEverStoresCredentialMaterial() {
		User u = user(AccountType.LIVE);
		credential(u);
		processor.process(u, AccountCategory.SPOT, spotBalanceEvent(now(), "USDT", "1", "0"));

		assertThat(java.util.Arrays.stream(PortfolioExchangeBalance.class.getDeclaredFields())
				.map(java.lang.reflect.Field::getName))
				.noneMatch(n -> n.toLowerCase().contains("key")
						|| n.toLowerCase().contains("secret")
						|| n.toLowerCase().contains("credential"));
		assertThat(java.util.Arrays.stream(PortfolioExchangePosition.class.getDeclaredFields())
				.map(java.lang.reflect.Field::getName))
				.noneMatch(n -> n.toLowerCase().contains("key")
						|| n.toLowerCase().contains("secret")
						|| n.toLowerCase().contains("credential"));
		assertThat(java.util.Arrays.stream(PortfolioExchangeEventApplied.class.getDeclaredFields())
				.map(java.lang.reflect.Field::getName))
				.noneMatch(n -> n.toLowerCase().contains("key")
						|| n.toLowerCase().contains("secret")
						|| n.toLowerCase().contains("credential"));
	}
}