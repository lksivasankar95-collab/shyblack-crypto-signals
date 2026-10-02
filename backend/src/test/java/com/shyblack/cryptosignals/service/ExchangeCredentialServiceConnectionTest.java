package com.shyblack.cryptosignals.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shyblack.cryptosignals.config.SettingsProperties;
import com.shyblack.cryptosignals.dto.settings.ExchangeCredentialConnectionResponse;
import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.Role;
import com.shyblack.cryptosignals.exception.ResourceNotFoundException;
import com.shyblack.cryptosignals.exchange.ExchangeAccountSnapshot;
import com.shyblack.cryptosignals.exchange.ExchangeAdapterException;
import com.shyblack.cryptosignals.exchange.ExchangeAssetBalance;
import com.shyblack.cryptosignals.exchange.ExchangeBalances;
import com.shyblack.cryptosignals.exchange.ExchangeOrderResult;
import com.shyblack.cryptosignals.exchange.ExchangeTradingAdapter;
import com.shyblack.cryptosignals.exchange.PlaceOrderRequest;
import com.shyblack.cryptosignals.exchange.SymbolRules;
import com.shyblack.cryptosignals.repository.ExchangeCredentialRepository;
import com.shyblack.cryptosignals.repository.UserRepository;
import com.shyblack.cryptosignals.security.ExchangeCredentialEncryptor;
import com.shyblack.cryptosignals.security.UserPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Phase 3 connection validation.
 *
 * <p>Previously {@code testConnection} returned a hardcoded success after a simulated delay without
 * contacting anything, so a credential was reported CONNECTED merely because it existed in the
 * database. These tests pin the real behaviour: the status follows an actual authenticated
 * read-only exchange call, and no order is ever placed.
 */
class ExchangeCredentialServiceConnectionTest {

	private static final AtomicInteger SEQ = new AtomicInteger();

	private ExchangeCredentialRepository credentialRepository;
	private UserRepository userRepository;
	private ExchangeCredentialEncryptor encryptor;
	private ExchangeTradingAdapter adapter;
	private com.shyblack.cryptosignals.exchange.futures.FuturesExchangeAdapter futuresAdapter;
	private ExchangeCredentialService service;
	private User owner;

	@BeforeEach
	void setUp() {
		credentialRepository = mock(ExchangeCredentialRepository.class);
		userRepository = mock(UserRepository.class);
		encryptor = new ExchangeCredentialEncryptor(new SettingsProperties(
				0, 0, 0, "phase3-test-only-encryption-seed", null, null, null));
		adapter = mock(ExchangeTradingAdapter.class);
		futuresAdapter = mock(com.shyblack.cryptosignals.exchange.futures.FuturesExchangeAdapter.class);
		service = new ExchangeCredentialService(
				credentialRepository,
				userRepository,
				encryptor,
				new SettingsProperties(0, 0, 0, "phase3-test-only-encryption-seed", null, null, null),
				adapter,
				futuresAdapter,
				new ExchangeConnectionClassifier());

		owner = new User();
		owner.setEmail("conn-" + SEQ.incrementAndGet() + "@example.com");
		owner.setFullName("Connection Tester");
		owner.setRole(Role.USER);
	}

	private UserPrincipal principal() {
		// The principal only needs the user id, which mirrors how the security context supplies it.
		UUID id = UUID.randomUUID();
		User stub = new User();
		stub.setId(id);
		stub.setEmail(owner.getEmail());
		stub.setRole(Role.USER);
		return new UserPrincipal(stub);
	}

	private ExchangeCredential storedCredential(ExchangeConnectionStatus status) {
		ExchangeCredential credential = new ExchangeCredential();
		credential.setId(UUID.randomUUID());
		credential.setUser(owner);
		credential.setExchange(ExchangeName.BINANCE);
		credential.setStatus(status);
		credential.setApiKey(encryptor.encrypt("test-api-key"));
		credential.setApiSecret(encryptor.encrypt("test-api-secret"));
		return credential;
	}

	// ----------------------------------------------------- valid credentials

	@Test
	void validCredentialsAreConfirmedByARealAuthenticatedRead() {
		ExchangeCredential credential = storedCredential(ExchangeConnectionStatus.NOT_CONNECTED);
		UserPrincipal principal = principal();
		when(credentialRepository.findByIdAndUser_Id(credential.getId(), principal.getId()))
				.thenReturn(Optional.of(credential));
		when(credentialRepository.save(any(ExchangeCredential.class)))
				.thenAnswer(inv -> inv.getArgument(0));
		when(adapter.exchange()).thenReturn(ExchangeName.BINANCE);
		when(adapter.validateCredentials(credential)).thenReturn(new ExchangeAccountSnapshot(
				"USDT", new BigDecimal("100"), new BigDecimal("100"), true, Instant.now()));

		ExchangeCredentialConnectionResponse response =
				service.testConnection(principal, credential.getId());

		assertThat(response.ok()).isTrue();
		assertThat(response.status()).isEqualTo(ExchangeConnectionStatus.CONNECTED);
		assertThat(response.message()).contains("BINANCE");
		verify(adapter).validateCredentials(credential);
		assertThat(credential.getStatus()).isEqualTo(ExchangeConnectionStatus.CONNECTED);
	}

	@Test
	void aConnectedButReadOnlyKeyIsReportedHonestly() {
		ExchangeCredential credential = storedCredential(ExchangeConnectionStatus.NOT_CONNECTED);
		UserPrincipal principal = principal();
		when(credentialRepository.findByIdAndUser_Id(credential.getId(), principal.getId()))
				.thenReturn(Optional.of(credential));
		when(credentialRepository.save(any(ExchangeCredential.class)))
				.thenAnswer(inv -> inv.getArgument(0));
		when(adapter.exchange()).thenReturn(ExchangeName.BINANCE);
		when(adapter.validateCredentials(credential)).thenReturn(new ExchangeAccountSnapshot(
				"USDT", new BigDecimal("100"), new BigDecimal("100"), false, Instant.now()));

		ExchangeCredentialConnectionResponse response =
				service.testConnection(principal, credential.getId());

		assertThat(response.ok()).isTrue();
		assertThat(response.message()).contains("read-only");
	}

	// ---------------------------------------------------- invalid credentials

	@Test
	void invalidCredentialsAreRecordedAsFailedNotConnected() {
		ExchangeCredential credential = storedCredential(ExchangeConnectionStatus.NOT_CONNECTED);
		UserPrincipal principal = principal();
		when(credentialRepository.findByIdAndUser_Id(credential.getId(), principal.getId()))
				.thenReturn(Optional.of(credential));
		when(credentialRepository.save(any(ExchangeCredential.class)))
				.thenAnswer(inv -> inv.getArgument(0));
		when(adapter.exchange()).thenReturn(ExchangeName.BINANCE);
		when(adapter.validateCredentials(credential)).thenThrow(new ExchangeAdapterException(
				"Binance error 401", null, false, 401, -2015));

		ExchangeCredentialConnectionResponse response =
				service.testConnection(principal, credential.getId());

		assertThat(response.ok())
				.as("a rejected credential must never be reported as connected")
				.isFalse();
		assertThat(response.status()).isEqualTo(ExchangeConnectionStatus.FAILED);
		assertThat(credential.getStatus()).isEqualTo(ExchangeConnectionStatus.FAILED);
	}

	@Test
	void failureDiagnosticNeverContainsCredentialMaterial() {
		ExchangeCredential credential = storedCredential(ExchangeConnectionStatus.NOT_CONNECTED);
		UserPrincipal principal = principal();
		when(credentialRepository.findByIdAndUser_Id(credential.getId(), principal.getId()))
				.thenReturn(Optional.of(credential));
		when(credentialRepository.save(any(ExchangeCredential.class)))
				.thenAnswer(inv -> inv.getArgument(0));
		when(adapter.exchange()).thenReturn(ExchangeName.BINANCE);
		when(adapter.validateCredentials(credential)).thenThrow(new ExchangeAdapterException(
				"Binance error 401", null, false, 401, -2015));

		ExchangeCredentialConnectionResponse response =
				service.testConnection(principal, credential.getId());

		assertThat(response.message())
				.doesNotContain("test-api-key")
				.doesNotContain("test-api-secret");
	}

	// ---------------------------------------------------------------- ownership

	@Test
	void aCredentialBelongingToAnotherUserIsNotFound() {
		UserPrincipal principal = principal();
		when(credentialRepository.findByIdAndUser_Id(any(UUID.class), any(UUID.class)))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.testConnection(principal, UUID.randomUUID()))
				.isInstanceOf(ResourceNotFoundException.class);
		verifyNoInteractions(adapter);
	}

	// --------------------------------------------------------- read-only proof

	@Test
	void connectionValidationPlacesNoOrder() {
		ExchangeCredential credential = storedCredential(ExchangeConnectionStatus.NOT_CONNECTED);
		UserPrincipal principal = principal();
		when(credentialRepository.findByIdAndUser_Id(credential.getId(), principal.getId()))
				.thenReturn(Optional.of(credential));
		when(credentialRepository.save(any(ExchangeCredential.class)))
				.thenAnswer(inv -> inv.getArgument(0));
		when(adapter.exchange()).thenReturn(ExchangeName.BINANCE);
		when(adapter.validateCredentials(credential)).thenReturn(new ExchangeAccountSnapshot(
				"USDT", BigDecimal.ONE, BigDecimal.ONE, true, Instant.now()));

		service.testConnection(principal, credential.getId());

		verify(adapter, org.mockito.Mockito.never())
				.placeOrder(any(), any(PlaceOrderRequest.class));
		verify(adapter, org.mockito.Mockito.never())
				.cancelOrder(any(), any(String.class), any(String.class));
		verify(adapter, org.mockito.Mockito.never()).getOrder(any(), any(String.class), any(String.class));
	}

	@Test
	void noHardcodedLatencyRemains() {
		ExchangeCredential credential = storedCredential(ExchangeConnectionStatus.NOT_CONNECTED);
		UserPrincipal principal = principal();
		when(credentialRepository.findByIdAndUser_Id(credential.getId(), principal.getId()))
				.thenReturn(Optional.of(credential));
		when(credentialRepository.save(any(ExchangeCredential.class)))
				.thenAnswer(inv -> inv.getArgument(0));
		when(adapter.exchange()).thenReturn(ExchangeName.BINANCE);
		when(adapter.validateCredentials(credential)).thenReturn(new ExchangeAccountSnapshot(
				"USDT", BigDecimal.ONE, BigDecimal.ONE, true, Instant.now()));

		ExchangeCredentialConnectionResponse response =
				service.testConnection(principal, credential.getId());

		assertThat(response.latencyMs())
				.as("latency is now measured, not the previous simulated 42 ms")
				.isNotEqualTo(42L);
	}

	// --------------------------------------------------------- value objects

	@Test
	void assetBalancePreservesExplicitZeroAndAbsentComponents() {
		ExchangeAssetBalance zero = new ExchangeAssetBalance("USDT", BigDecimal.ZERO, BigDecimal.ZERO);
		ExchangeAssetBalance partial = new ExchangeAssetBalance("BTC", null, BigDecimal.TEN);

		assertThat(zero.total()).isEqualByComparingTo("0");
		assertThat(partial.total()).as("an unknown component yields no total").isNull();
	}

	@Test
	void balancesDistinguishAbsentFromZero() {
		ExchangeBalances balances = new ExchangeBalances(
				Map.of("USDT", new ExchangeAssetBalance("USDT", BigDecimal.ZERO, BigDecimal.ZERO)),
				true, Instant.now());

		assertThat(balances.find("USDT")).isPresent();
		assertThat(balances.free("USDT")).isEqualByComparingTo("0");
		assertThat(balances.find("BTC")).isEmpty();
		assertThat(balances.free("BTC")).isNull();
	}

	@Test
	void savedCredentialStatusIsPersisted() {
		ExchangeCredential credential = storedCredential(ExchangeConnectionStatus.NOT_CONNECTED);
		UserPrincipal principal = principal();
		when(credentialRepository.findByIdAndUser_Id(credential.getId(), principal.getId()))
				.thenReturn(Optional.of(credential));
		when(credentialRepository.save(any(ExchangeCredential.class)))
				.thenAnswer(inv -> inv.getArgument(0));
		when(adapter.exchange()).thenReturn(ExchangeName.BINANCE);
		when(adapter.validateCredentials(credential)).thenReturn(new ExchangeAccountSnapshot(
				"USDT", BigDecimal.ONE, BigDecimal.ONE, true, Instant.now()));

		service.testConnection(principal, credential.getId());

		ArgumentCaptor<ExchangeCredential> captor = ArgumentCaptor.forClass(ExchangeCredential.class);
		verify(credentialRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo(ExchangeConnectionStatus.CONNECTED);
	}
}