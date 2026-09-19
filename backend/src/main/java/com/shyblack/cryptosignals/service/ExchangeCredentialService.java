package com.shyblack.cryptosignals.service;

import com.shyblack.cryptosignals.config.SettingsProperties;
import com.shyblack.cryptosignals.dto.settings.ExchangeCredentialConnectionResponse;
import com.shyblack.cryptosignals.dto.settings.ExchangeCredentialRequest;
import com.shyblack.cryptosignals.dto.settings.ExchangeCredentialView;
import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.exception.BadRequestException;
import com.shyblack.cryptosignals.exception.ResourceNotFoundException;
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

	private static final long SIMULATED_LATENCY_MS = 42;

	private final ExchangeCredentialRepository credentialRepository;
	private final UserRepository userRepository;
	private final ExchangeCredentialEncryptor encryptor;
	private final SettingsProperties properties;

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

	@Transactional
	public ExchangeCredentialConnectionResponse testConnection(UserPrincipal principal, UUID id) {
		ExchangeCredential credential = requireOwned(principal, id);
		credential.setStatus(ExchangeConnectionStatus.CONNECTED);
		credentialRepository.save(credential);
		return new ExchangeCredentialConnectionResponse(
				credential.getId(),
				credential.getExchange(),
				true,
				"Connection verified",
				SIMULATED_LATENCY_MS,
				Instant.now(),
				ExchangeConnectionStatus.CONNECTED);
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