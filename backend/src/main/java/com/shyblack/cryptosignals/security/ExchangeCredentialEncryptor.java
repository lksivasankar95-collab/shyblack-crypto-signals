package com.shyblack.cryptosignals.security;

import com.shyblack.cryptosignals.config.SettingsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Spring component wrapping {@link AesGcmEncryptor} for exchange API
 * credentials, bound to the {@code app.settings.encryption-secret-key} seed.
 */
@Component
@RequiredArgsConstructor
public class ExchangeCredentialEncryptor {

	private final SettingsProperties properties;

	public String encrypt(String plaintext) {
		return instance().encrypt(plaintext);
	}

	public String decrypt(String encoded) {
		return instance().decrypt(encoded);
	}

	public String mask(String raw) {
		return AesGcmEncryptor.mask(raw);
	}

	private AesGcmEncryptor instance() {
		return new AesGcmEncryptor(properties.encryptionSecretKey());
	}
}