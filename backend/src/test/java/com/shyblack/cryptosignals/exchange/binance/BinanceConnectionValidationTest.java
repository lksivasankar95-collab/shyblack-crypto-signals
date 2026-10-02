package com.shyblack.cryptosignals.exchange.binance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shyblack.cryptosignals.config.FuturesTradingProperties;
import com.shyblack.cryptosignals.config.LiveTradingProperties;
import com.shyblack.cryptosignals.config.SettingsProperties;
import com.shyblack.cryptosignals.dto.settings.ConnectionValidationStatus;
import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.Role;
import com.shyblack.cryptosignals.exchange.ExchangeTradingAdapter;
import com.shyblack.cryptosignals.exchange.binance.BinanceLiveTradingAdapter;
import com.shyblack.cryptosignals.exchange.futures.FuturesExchangeAdapter;
import com.shyblack.cryptosignals.exchange.futures.binance.BinanceFuturesLiveAdapter;
import com.shyblack.cryptosignals.repository.ExchangeCredentialRepository;
import com.shyblack.cryptosignals.repository.UserRepository;
import com.shyblack.cryptosignals.security.ExchangeCredentialEncryptor;
import com.shyblack.cryptosignals.security.UserPrincipal;
import com.shyblack.cryptosignals.service.ExchangeConnectionClassifier;
import com.shyblack.cryptosignals.service.ExchangeCredentialService;
import com.shyblack.cryptosignals.service.ExchangeCredentialService.ValidationScope;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Phase 9 follow-up — real credential validation, proven against a loopback exchange.
 *
 * <p>Assembles the production {@link ExchangeCredentialService} with the <b>real</b> Binance spot
 * and futures adapters pointed at a scripted loopback server. The service performs a genuine
 * authenticated account read: URL construction, query encoding, HMAC-SHA256, the API key header
 * and JSON parsing are all exercised for real.
 *
 * <p>No request can reach Binance. Key material is generated per test and is throwaway.
 */
class BinanceConnectionValidationTest {

	private static final String TEST_SEED = "connection-test-seed-never-used-in-production";
	private static final String TEST_API_KEY = "connection-test-api-key";
	private static final String TEST_API_SECRET = "connection-test-api-secret";

	private static final String SPOT_ACCOUNT_OK =
			"{\"canTrade\":true,\"balances\":[{\"asset\":\"USDT\",\"free\":\"100\",\"locked\":\"5\"}]}";
	private static final String FUTURES_ACCOUNT_OK =
			"{\"totalWalletBalance\":\"1000\",\"availableBalance\":\"900\","
					+ "\"totalMarginBalance\":\"1000\",\"totalInitialMargin\":\"100\","
					+ "\"totalMaintMargin\":\"10\",\"totalUnrealizedProfit\":\"0\",\"canTrade\":true}";

	private FakeExchangeHttp exchange;
	private ExchangeCredentialService service;
	private ExchangeCredentialRepository credentialRepository;
	private ExchangeCredentialEncryptor encryptor;
	private User owner;

	@BeforeEach
	void setUp() throws Exception {
		exchange = new FakeExchangeHttp();
		encryptor = new ExchangeCredentialEncryptor(
				new SettingsProperties(2.0, 20.0, 4, TEST_SEED, null, null, null));

		ExchangeTradingAdapter spotAdapter = new BinanceLiveTradingAdapter(
				new LiveTradingProperties(LiveTradingProperties.Mode.EXCHANGE,
						exchange.baseUrl(), exchange.baseUrl(), exchange.baseUrl(),
						5000, new BigDecimal("200"), 3, new BigDecimal("5"), false),
				encryptor);

		FuturesExchangeAdapter futuresAdapter = new BinanceFuturesLiveAdapter(
				new FuturesTradingProperties(FuturesTradingProperties.Mode.EXCHANGE,
						exchange.baseUrl(), exchange.baseUrl(), 5000, 3,
						com.shyblack.cryptosignals.entity.enums.FuturesMarginMode.ISOLATED,
						com.shyblack.cryptosignals.entity.enums.FuturesPositionMode.ONE_WAY,
						new BigDecimal("200"), 2, new BigDecimal("5"), new BigDecimal("0.30"),
						new BigDecimal("15.00"), false),
				encryptor);

		credentialRepository = Mockito.mock(ExchangeCredentialRepository.class);
		UserRepository userRepository = Mockito.mock(UserRepository.class);
		Mockito.when(credentialRepository.save(Mockito.any(ExchangeCredential.class)))
				.thenAnswer(inv -> inv.getArgument(0));

		service = new ExchangeCredentialService(
				credentialRepository,
				userRepository,
				encryptor,
				new SettingsProperties(2.0, 20.0, 4, TEST_SEED, null, null, null),
				spotAdapter,
				futuresAdapter,
				new ExchangeConnectionClassifier());

		owner = new User();
		owner.setId(UUID.randomUUID());
		owner.setEmail("conn-test@example.com");
		owner.setRole(Role.USER);
	}

	@AfterEach
	void tearDown() {
		exchange.close();
	}

	// ------------------------------------------------------------------ helpers

	private ExchangeCredential storedCredential() {
		ExchangeCredential credential = new ExchangeCredential();
		credential.setId(UUID.randomUUID());
		credential.setUser(owner);
		credential.setExchange(ExchangeName.BINANCE);
		credential.setStatus(com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus.NOT_CONNECTED);
		credential.setApiKey(encryptor.encrypt(TEST_API_KEY));
		credential.setApiSecret(encryptor.encrypt(TEST_API_SECRET));
		Mockito.when(credentialRepository.findByIdAndUser_Id(credential.getId(), owner.getId()))
				.thenReturn(Optional.of(credential));
		return credential;
	}

	private UserPrincipal principal() {
		return new UserPrincipal(owner);
	}

	private ExchangeConnectionClassifier.Classification validate(ValidationScope scope) {
		storedCredential();
		return new ExchangeConnectionClassifier().classify(captureFailure(scope));
	}

	private Throwable captureFailure(ValidationScope scope) {
		try {
			service.testConnection(principal(), lastCredentialId, scope);
			return null;
		} catch (RuntimeException ex) {
			return ex;
		}
	}

	private UUID lastCredentialId;

	// ================================== 1. valid credentials, real authenticated read

	@Test
	void validSpotCredentialsProduceARealAuthenticatedAccountRead() {
		exchange.on("/api/v3/account", SPOT_ACCOUNT_OK);
		ExchangeCredential credential = storedCredential();

		var response = service.testConnection(principal(), credential.getId(), ValidationScope.SPOT);

		assertThat(response.ok()).isTrue();
		assertThat(response.validationStatus()).isEqualTo(ConnectionValidationStatus.CONNECTED);
		assertThat(response.scope()).isEqualTo("SPOT");
		assertThat(response.canTrade()).isTrue();

		// The request is a real signed account read.
		assertThat(exchange.lastRequest().method()).isEqualTo("GET");
		assertThat(exchange.lastRequest().path()).isEqualTo("/api/v3/account");
		assertThat(exchange.header("X-MBX-APIKEY")).isEqualTo(TEST_API_KEY);
		assertThat(exchange.paramOrNull("timestamp")).isNotNull();
		assertThat(exchange.paramOrNull("recvWindow")).isEqualTo("5000");
	}

	@Test
	void theRequestSignatureIsHmacOfTheExactQueryString() {
		exchange.on("/api/v3/account", SPOT_ACCOUNT_OK);
		ExchangeCredential credential = storedCredential();

		service.testConnection(principal(), credential.getId(), ValidationScope.SPOT);

		String raw = exchange.lastRequest().rawQuery();
		String signature = exchange.param("signature");
		String signedPayload = raw.substring(0, raw.length() - ("&signature=" + signature).length());
		assertThat(signature)
				.isEqualTo(BinanceSignatureUtil.hmacSha256Hex(TEST_API_SECRET, signedPayload))
				.matches("^[0-9a-f]{64}$");
	}

	@Test
	void theTimestampIsCurrentWallClockMillis() {
		exchange.on("/api/v3/account", SPOT_ACCOUNT_OK);
		ExchangeCredential credential = storedCredential();

		long before = System.currentTimeMillis();
		service.testConnection(principal(), credential.getId(), ValidationScope.SPOT);
		long after = System.currentTimeMillis();

		assertThat(Long.parseLong(exchange.param("timestamp")))
				.isBetween(before - 1000, after + 1000);
	}

	// ==================================== 2/3. invalid key and invalid secret/signature

	@Test
	void anInvalidApiKeyIsReportedAsInvalidCredentials() {
		exchange.on("/api/v3/account", 401,
				"{\"code\":-2015,\"msg\":\"Invalid API-key, IP, or permissions for action.\"}");
		ExchangeCredential credential = storedCredential();

		var response = service.testConnection(principal(), credential.getId(), ValidationScope.SPOT);

		assertThat(response.ok()).isFalse();
		assertThat(response.validationStatus())
				.isEqualTo(ConnectionValidationStatus.INVALID_CREDENTIALS);
	}

	@Test
	void anInvalidSignatureIsDistinguishedFromAnInvalidKey() {
		// -1022 is Binance's "signature for this request is not valid", which in practice
		// means the API secret is wrong rather than the key.
		exchange.on("/api/v3/account", 401,
				"{\"code\":-1022,\"msg\":\"Signature for this request is not valid.\"}");
		ExchangeCredential credential = storedCredential();

		var response = service.testConnection(principal(), credential.getId(), ValidationScope.SPOT);

		assertThat(response.validationStatus())
				.isEqualTo(ConnectionValidationStatus.INVALID_SIGNATURE);
		assertThat(response.ok()).isFalse();
	}

	@Test
	void aWrongSecretProducesAFreshSignatureThatBinanceRejects() {
		exchange.on("/api/v3/account", 401,
				"{\"code\":-1022,\"msg\":\"Signature for this request is not valid.\"}");

		// A different secret must produce a different signature over the same query.
		ExchangeCredential other = new ExchangeCredential();
		other.setId(UUID.randomUUID());
		other.setUser(owner);
		other.setExchange(ExchangeName.BINANCE);
		other.setApiKey(encryptor.encrypt(TEST_API_KEY));
		other.setApiSecret(encryptor.encrypt("a-completely-different-secret"));
		Mockito.when(credentialRepository.findByIdAndUser_Id(other.getId(), owner.getId()))
				.thenReturn(Optional.of(other));

		service.testConnection(principal(), other.getId(), ValidationScope.SPOT);

		assertThat(exchange.param("signature"))
				.isNotEqualTo(BinanceSignatureUtil.hmacSha256Hex(TEST_API_SECRET, "x"));
		assertThat(exchange.header("X-MBX-APIKEY"))
				.as("the key travels in the header and is never signed into the query")
				.isEqualTo(TEST_API_KEY);
	}

	// ==================================== 4/5. deterministic mapping of 4xx and 5xx

	@Test
	void aTimestampErrorIsReportedDistinctly() {
		exchange.on("/api/v3/account", 400,
				"{\"code\":-1021,\"msg\":\"Timestamp for this request was 1000ms ahead.\"}");
		ExchangeCredential credential = storedCredential();

		var response = service.testConnection(principal(), credential.getId(), ValidationScope.SPOT);

		assertThat(response.validationStatus())
				.isEqualTo(ConnectionValidationStatus.TIMESTAMP_ERROR);
	}

	@Test
	void aRateLimitIsReportedDistinctly() {
		exchange.on("/api/v3/account", 429, "{\"code\":-1003,\"msg\":\"Too many requests.\"}");
		ExchangeCredential credential = storedCredential();

		assertThat(service.testConnection(principal(), credential.getId(), ValidationScope.SPOT)
				.validationStatus())
				.isEqualTo(ConnectionValidationStatus.RATE_LIMITED);
	}

	@Test
	void anIpBanIsReportedAsRateLimited() {
		exchange.on("/api/v3/account", 418, "{\"code\":-1003,\"msg\":\"IP banned.\"}");
		ExchangeCredential credential = storedCredential();

		assertThat(service.testConnection(principal(), credential.getId(), ValidationScope.SPOT)
				.validationStatus())
				.isEqualTo(ConnectionValidationStatus.RATE_LIMITED);
	}

	@Test
	void anUnrecognisedClientErrorIsMappedToAClientError() {
		exchange.on("/api/v3/account", 400, "{\"code\":-1121,\"msg\":\"Invalid symbol.\"}");
		ExchangeCredential credential = storedCredential();

		assertThat(service.testConnection(principal(), credential.getId(), ValidationScope.SPOT)
				.validationStatus())
				.isEqualTo(ConnectionValidationStatus.BINANCE_CLIENT_ERROR);
	}

	@Test
	void aServerErrorIsReportedAsABinanceApiError() {
		exchange.on("/api/v3/account", 503, "{\"code\":-1001,\"msg\":\"Internal error.\"}");
		ExchangeCredential credential = storedCredential();

		assertThat(service.testConnection(principal(), credential.getId(), ValidationScope.SPOT)
				.validationStatus())
				.isEqualTo(ConnectionValidationStatus.BINANCE_API_ERROR);
	}

	// ==================================== 6. timeout and network failure

	@Test
	void aTimeoutIsReportedAsTimeoutAndDoesNotMarkTheKeyFailed() {
		exchange.onSlow("/api/v3/account", java.util.concurrent.TimeUnit.SECONDS.toMillis(30));
		ExchangeCredential credential = storedCredential();

		var response = service.testConnection(principal(), credential.getId(), ValidationScope.SPOT);

		assertThat(response.validationStatus()).isEqualTo(ConnectionValidationStatus.TIMEOUT);
		assertThat(response.ok()).isFalse();
		assertThat(credential.getStatus())
				.as("a timeout says nothing about the key, so a previously "
						+ "valid status must not be overwritten")
				.isEqualTo(com.shyblack.cryptosignals.entity.enums
						.ExchangeConnectionStatus.NOT_CONNECTED);
		Mockito.verify(credentialRepository, Mockito.never())
				.save(Mockito.argThat(c -> c.getStatus() ==
						com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus.FAILED));
	}

	@Test
	void aMalformedResponseIsClassifiedRatherThanEscapingAsAServerError() {
		// Previously a non-JSON body produced a JsonSyntaxException that escaped the service
		// entirely and became an HTTP 500.
		exchange.on("/api/v3/account", "<html>gateway error</html>");
		ExchangeCredential credential = storedCredential();

		var response = service.testConnection(principal(), credential.getId(), ValidationScope.SPOT);

		assertThat(response.validationStatus()).isEqualTo(ConnectionValidationStatus.UNKNOWN_ERROR);
		assertThat(response.ok()).isFalse();
	}

	@Test
	void anAccountResponseWithoutBalancesIsClassifiedNotCrashed() {
		exchange.on("/api/v3/account", "{\"canTrade\":true}");
		ExchangeCredential credential = storedCredential();

		var response = service.testConnection(principal(), credential.getId(), ValidationScope.SPOT);

		assertThat(response.ok()).isFalse();
		assertThat(response.validationStatus()).isNotNull();
	}

	// ==================================== 11/12. security: no secret anywhere

	@Test
	void noSecretAppearsInTheResponseForAnyFailureClass() {
		record Case(String body, int status) { }
		java.util.List<Case> cases = java.util.List.of(
				new Case("{\"code\":-2015,\"msg\":\"Invalid API-key.\"}", 401),
				new Case("{\"code\":-1022,\"msg\":\"Signature not valid.\"}", 401),
				new Case("{\"code\":-1021,\"msg\":\"Timestamp too far ahead.\"}", 400),
				new Case("{\"code\":-1003,\"msg\":\"Too many requests.\"}", 429),
				new Case("{\"code\":-1001,\"msg\":\"Internal error.\"}", 503),
				new Case("<html>gateway</html>", 200));

		for (Case testCase : cases) {
			FakeExchangeHttp local = exchange;
			local.on("/api/v3/account", testCase.status(), testCase.body());
			ExchangeCredential credential = storedCredential();

			var response = service.testConnection(
					principal(), credential.getId(), ValidationScope.SPOT);

			String rendered = response.toString();
			String computedSignature = exchange.lastRequest().query().get("signature");
			assertThat(rendered)
					.as("body %s must not leak credential material", testCase.body())
					.doesNotContain(TEST_API_KEY)
					.doesNotContain(TEST_API_SECRET)
					.doesNotContain("X-MBX-APIKEY")
					// The computed HMAC must never appear. The literal status name
					// INVALID_SIGNATURE is a machine-readable value, not a leak.
					.doesNotContain("recvWindow")
					.doesNotContain("timestamp=");
			if (computedSignature != null) {
				assertThat(rendered)
						.as("the computed HMAC must never reach the client")
						.doesNotContain(computedSignature);
			}
		}
	}

	@Test
	void theRawExchangeBodyIsNeverEchoedToTheClient() {
		exchange.on("/api/v3/account", 400,
				"{\"code\":-1121,\"msg\":\"secret-ish upstream detail\"}");
		ExchangeCredential credential = storedCredential();

		var response = service.testConnection(principal(), credential.getId(), ValidationScope.SPOT);

		assertThat(response.message())
				.as("messages are authored, not forwarded from the exchange")
				.doesNotContain("secret-ish upstream detail")
				.doesNotContain("-1121")
				.doesNotContain("400 Bad Request");
	}

	@Test
	void thePersistedCredentialStillHoldsOnlyCiphertext() {
		exchange.on("/api/v3/account", SPOT_ACCOUNT_OK);
		ExchangeCredential credential = storedCredential();

		service.testConnection(principal(), credential.getId(), ValidationScope.SPOT);

		assertThat(credential.getApiKey())
				.as("the entity keeps ciphertext, never the plaintext key")
				.isNotEqualTo(TEST_API_KEY);
		assertThat(credential.getApiSecret()).isNotEqualTo(TEST_API_SECRET);
	}

	// ==================================== 13/14. futures scope, read-only

	@Test
	void futuresValidationUsesTheRealFuturesAccountEndpoint() {
		exchange.on("/fapi/v2/account", FUTURES_ACCOUNT_OK);
		exchange.on("/fapi/v1/positionSide/dual", "{\"dualSidePosition\":false}");
		ExchangeCredential credential = storedCredential();

		var response = service.testConnection(principal(), credential.getId(), ValidationScope.FUTURES);

		assertThat(response.ok()).isTrue();
		assertThat(response.scope()).isEqualTo("FUTURES");
		assertThat(response.validationStatus()).isEqualTo(ConnectionValidationStatus.CONNECTED);
		assertThat(exchange.paths()).contains("/fapi/v2/account");
		assertThat(exchange.header("X-MBX-APIKEY")).isEqualTo(TEST_API_KEY);
	}

	@Test
	void bothScopesReuseTheSameStoredCredential() {
		exchange.on("/api/v3/account", SPOT_ACCOUNT_OK);
		exchange.on("/fapi/v2/account", FUTURES_ACCOUNT_OK);
		exchange.on("/fapi/v1/positionSide/dual", "{\"dualSidePosition\":false}");
		ExchangeCredential credential = storedCredential();

		service.testConnection(principal(), credential.getId(), ValidationScope.SPOT);
		service.testConnection(principal(), credential.getId(), ValidationScope.FUTURES);

		// Both scopes save the status of the SAME row. No second credential is created.
		var saved = Mockito.mockingDetails(credentialRepository).getInvocations().stream()
				.filter(i -> i.getMethod().getName().equals("save"))
				.map(i -> (ExchangeCredential) i.getArgument(0))
				.toList();
		assertThat(saved).isNotEmpty();
		assertThat(saved).allSatisfy(row -> assertThat(row.getId()).isEqualTo(credential.getId()));
		assertThat(saved).extracting(ExchangeCredential::getExchange)
				.as("one credential serves both scopes; no duplicate per market")
				.containsOnly(ExchangeName.BINANCE);
	}

	@Test
	void noOrderEndpointIsEverCalledInEitherScope() {
		exchange.on("/api/v3/account", SPOT_ACCOUNT_OK);
		exchange.on("/fapi/v2/account", FUTURES_ACCOUNT_OK);
		exchange.on("/fapi/v1/positionSide/dual", "{\"dualSidePosition\":false}");
		ExchangeCredential credential = storedCredential();

		service.testConnection(principal(), credential.getId(), ValidationScope.SPOT);
		service.testConnection(principal(), credential.getId(), ValidationScope.FUTURES);

		// The exact set of endpoints a connection test may touch. Anything that
		// mutates account or order state is absent by construction, not by convention.
		// positionSide/dual is a read-only position-mode probe.
		assertThat(exchange.paths()).containsExactlyInAnyOrder(
				"/api/v3/account",
				"/fapi/v2/account",
				"/fapi/v1/positionSide/dual");
	}

	@Test
	void futuresValidationReportsAFailureWithoutFallingBackToTheSpotEndpoint() {
		exchange.on("/fapi/v2/account", 401,
				"{\"code\":-2015,\"msg\":\"Invalid API-key, IP, or permissions for action.\"}");
		ExchangeCredential credential = storedCredential();

		var response = service.testConnection(principal(), credential.getId(), ValidationScope.FUTURES);

		assertThat(response.ok()).isFalse();
		assertThat(response.validationStatus())
				.isEqualTo(ConnectionValidationStatus.INVALID_CREDENTIALS);
		assertThat(exchange.paths())
				.doesNotContain("/api/v3/account");
	}

	// ==================================== classifier unit coverage

	@Test
	void theClassifierRejectsAnUnknownScopeBeforeAnyNetworkCall() {
		ExchangeCredential credential = storedCredential();

		assertThatThrownBy(() -> service.testConnection(
				principal(), credential.getId(), ValidationScope.parse("PERPETUAL")))
				.hasMessageContaining("Unknown validation scope");
		assertThat(exchange.requestCount())
				.as("an invalid request must never reach the exchange")
				.isZero();
	}

	@Test
	void scopeParsingDefaultsToSpotAndAcceptsEitherCase() {
		assertThat(ValidationScope.parse(null)).isEqualTo(ValidationScope.SPOT);
		assertThat(ValidationScope.parse("")).isEqualTo(ValidationScope.SPOT);
		assertThat(ValidationScope.parse("futures")).isEqualTo(ValidationScope.FUTURES);
		assertThat(ValidationScope.parse("SPOT")).isEqualTo(ValidationScope.SPOT);
	}

	@Test
	void aReadOnlyKeyIsConnectedAndSaysSo() {
		exchange.on("/api/v3/account",
				"{\"canTrade\":false,\"balances\":[{\"asset\":\"USDT\",\"free\":\"1\",\"locked\":\"0\"}]}");
		ExchangeCredential credential = storedCredential();

		var response = service.testConnection(principal(), credential.getId(), ValidationScope.SPOT);

		assertThat(response.ok())
				.as("a read-only key is a working connection, not a failure")
				.isTrue();
		assertThat(response.canTrade()).isFalse();
		assertThat(response.message()).contains("read-only");
	}
}