package com.shyblack.cryptosignals.service;

import com.shyblack.cryptosignals.config.SettingsProperties;
import com.shyblack.cryptosignals.dto.settings.ConnectionValidationStatus;
import com.shyblack.cryptosignals.dto.settings.ExchangeCredentialConnectionResponse;
import com.shyblack.cryptosignals.dto.settings.ExchangeCredentialRequest;
import com.shyblack.cryptosignals.dto.settings.ExchangeCredentialView;
import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.exception.BadRequestException;
import com.shyblack.cryptosignals.exception.ResourceNotFoundException;
import com.shyblack.cryptosignals.exchange.ExchangeAccountSnapshot;
import com.shyblack.cryptosignals.exchange.ExchangeAdapterException;
import com.shyblack.cryptosignals.exchange.ExchangeTradingAdapter;
import com.shyblack.cryptosignals.exchange.futures.FuturesExchangeAdapter;
import com.shyblack.cryptosignals.repository.ExchangeCredentialRepository;
import com.shyblack.cryptosignals.repository.UserRepository;
import com.shyblack.cryptosignals.security.ExchangeCredentialEncryptor;
import com.shyblack.cryptosignals.security.UserPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exchange API-credential lifecycle. Credentials are encrypted at rest and
 * every response is masked; raw secrets are never returned. All lookups are
 * scoped to the authenticated principal's id, so no client-supplied user id is
 * ever trusted (IDOR-safe).
 */
@Service
@RequiredArgsConstructor
public class ExchangeCredentialService {

	private final ExchangeCredentialRepository credentialRepository;
	private final UserRepository userRepository;
	private final ExchangeCredentialEncryptor encryptor;
	private final SettingsProperties properties;
	private final ExchangeTradingAdapter spotAdapter;
	private final FuturesExchangeAdapter futuresAdapter;
	private final ExchangeConnectionClassifier classifier;

	@Transactional(readOnly = true)
	public List<ExchangeCredentialView> list(UserPrincipal principal) {
		return credentialRepository.findByUser_Id(principal.getId()).stream()
				.map(this::toView)
				.toList();
	}

	@Transactional
	public ExchangeCredentialView create(UserPrincipal principal, ExchangeCredentialRequest request) {
		User user = currentUser(principal);

		long existing = credentialRepository.countByUser_Id(user.getId());
		if (existing >= properties.maxOpenPositions()) {
			throw new BadRequestException("Maximum of " + properties.maxOpenPositions()
					+ " exchange connections reached");
		}
		if (credentialRepository.findByUser_IdAndExchange(user.getId(), request.exchange()).isPresent()) {
			throw new BadRequestException("An exchange credential for "
					+ request.exchange().name() + " is already connected");
		}

		ExchangeCredential credential = new ExchangeCredential();
		credential.setUser(user);
		credential.setExchange(request.exchange());
		credential.setStatus(ExchangeConnectionStatus.NOT_CONNECTED);
		credential.setApiKey(encryptor.encrypt(request.apiKey()));
		credential.setApiSecret(encryptor.encrypt(request.apiSecret()));
		credential.setLabel(clean(request.label()));
		credential.setDisplayName(clean(request.displayName()));
		return toView(credentialRepository.save(credential));
	}

	/**
	 * Which market scope a connection test validates.
	 *
	 * <p>Both scopes read the same stored {@link ExchangeCredential}; only the signed read differs
	 * ({@code GET /api/v3/account} versus {@code GET /fapi/v2/account}). No duplicate credential
	 * record is created per scope.
	 */
	public enum ValidationScope {
		SPOT,
		FUTURES;

		public static ValidationScope parse(String raw) {
			if (raw == null || raw.isBlank()) {
				return SPOT;
			}
			try {
				return valueOf(raw.trim().toUpperCase());
			} catch (IllegalArgumentException notAScope) {
				throw new BadRequestException(
						"Unknown validation scope '" + raw + "'. Use SPOT or FUTURES.");
			}
		}
	}

	/**
	 * Validates a credential against the exchange with a real signed, read-only request.
	 *
	 * <p>The status is set only when the exchange actually accepts the signed request. Scope-aware
	 * because a Binance credential may be usable on one market and not the other, and validating
	 * only spot would report a futures-only key as broken and a spot-only key as fully working.
	 *
	 * <p>Performs no order placement, cancellation or modification of any kind: only an
	 * authenticated account read through {@code validateCredentials}.
	 *
	 * <p>Failures are classified by {@link ExchangeConnectionClassifier}, which covers every
	 * {@link Throwable} — not only adapter exceptions — and returns an authored message that
	 * contains no credential material and no raw exchange payload.
	 *
	 * <p>When {@code app.live-trading.mode=MOCK} (the default for dev and tests) the configured
	 * adapter is the in-process simulator, so this validates the local path rather than the exchange.
	 */
	@Transactional
	public ExchangeCredentialConnectionResponse testConnection(
			UserPrincipal principal, UUID id, ValidationScope scope) {

		ExchangeCredential credential = requireOwned(principal, id);
		long startedAt = System.nanoTime();

		try {
// Spot and futures expose different snapshot types behind different interfaces, so the
			// two reads are issued explicitly rather than through a shared supertype.
			boolean canTrade = scope == ValidationScope.FUTURES
					? futuresAdapter.validateCredentials(credential).canTrade()
					: spotAdapter.validateCredentials(credential).canTrade();

			credential.setStatus(ExchangeConnectionStatus.CONNECTED);
			credentialRepository.save(credential);

			return new ExchangeCredentialConnectionResponse(
					credential.getId(),
					credential.getExchange(),
					scope.name(),
					true,
					// A read-only key is connected. Saying "failed" would be a lie, and hiding it
					// would let a user believe they can trade.
					canTrade
							? "Connection verified against " + credential.getExchange()
									+ " " + scope.name() + "."
							: "Connection verified against " + credential.getExchange()
									+ " " + scope.name() + ", but the key has no trading "
									+ "permission (read-only).",
					elapsedMs(startedAt),
					Instant.now(),
					ExchangeConnectionStatus.CONNECTED,
					ConnectionValidationStatus.CONNECTED,
					canTrade);

		} catch (Exception failure) {
			long latencyMs = elapsedMs(startedAt);
			ExchangeConnectionClassifier.Classification classified = classifier.classify(failure);

			// Only a credential-attributable failure may mark the stored key FAILED. A timeout
			// or a rate limit says nothing about the key, and overwriting a valid status with
			// FAILED because a laptop lost Wi-Fi would be a false record.
			boolean markFailed = classified.status().isCredentialAttributable();
			if (markFailed) {
				credential.setStatus(ExchangeConnectionStatus.FAILED);
				credentialRepository.save(credential);
			}
			// Reported after any update, so the response agrees with what was persisted.
			ExchangeConnectionStatus persistedStatus = credential.getStatus();

			return new ExchangeCredentialConnectionResponse(
					credential.getId(),
					credential.getExchange(),
					scope.name(),
					false,
					classified.message(),
					latencyMs,
					Instant.now(),
					persistedStatus,
					classified.status(),
					false);
		}
	}

	/** Backwards-compatible overload validating the spot scope, which is the default. */
	@Transactional
	public ExchangeCredentialConnectionResponse testConnection(UserPrincipal principal, UUID id) {
		return testConnection(principal, id, ValidationScope.SPOT);
	}

	private static long elapsedMs(long startedAtNanos) {
		return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
	}

	@Transactional
	public void delete(UserPrincipal principal, UUID id) {
		ExchangeCredential credential = requireOwned(principal, id);
		credentialRepository.delete(credential);
	}

	@Transactional
	public ExchangeCredentialView disconnect(UserPrincipal principal, UUID id) {
		ExchangeCredential credential = requireOwned(principal, id);
		credential.setStatus(ExchangeConnectionStatus.NOT_CONNECTED);
		return toView(credentialRepository.save(credential));
	}

	@Transactional
	public ExchangeCredentialView revoke(UserPrincipal principal, UUID id) {
		ExchangeCredential credential = requireOwned(principal, id);
		credential.setStatus(ExchangeConnectionStatus.REVOKED);
		return toView(credentialRepository.save(credential));
	}

	private ExchangeCredential requireOwned(UserPrincipal principal, UUID id) {
		return credentialRepository.findByIdAndUser_Id(id, principal.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Exchange credential not found: " + id));
	}

	private User currentUser(UserPrincipal principal) {
		if (principal == null) {
			throw new BadRequestException("Authentication required");
		}
		return userRepository.findById(principal.getId())
				.orElseThrow(() -> new ResourceNotFoundException("User not found"));
	}

	/**
	 * Faithful masked view: decrypts the at-rest ciphertext and masks the real
	 * value so the last-four fragment reflects the actual secret.
	 */
	private ExchangeCredentialView toView(ExchangeCredential credential) {
		return new ExchangeCredentialView(
				credential.getId(),
				credential.getExchange(),
				credential.getStatus(),
				credential.getLabel(),
				credential.getDisplayName(),
				encryptor.mask(encryptor.decrypt(credential.getApiKey())),
				encryptor.mask(encryptor.decrypt(credential.getApiSecret())),
				credential.getCreatedAt(),
				credential.getUpdatedAt());
	}

	private static String clean(String value) {
		return value == null ? null : value.isBlank() ? null : value.trim();
	}
}
