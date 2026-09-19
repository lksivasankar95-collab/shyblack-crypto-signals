package com.shyblack.cryptosignals.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shyblack.cryptosignals.config.SettingsProperties;
import com.shyblack.cryptosignals.entity.enums.PositionSizingMode;
import com.shyblack.cryptosignals.entity.enums.QuoteCurrency;
import com.shyblack.cryptosignals.entity.enums.RiskProfile;
import com.shyblack.cryptosignals.security.AesGcmEncryptor;
import com.shyblack.cryptosignals.security.ExchangeCredentialEncryptor;
import org.junit.jupiter.api.Test;

class AesGcmEncryptorTest {

	private static final String SEED = "unit-test-encryption-seed";

	private final AesGcmEncryptor encryptor = new AesGcmEncryptor(SEED);

	@Test
	void encryptDecryptRoundTripsValues() {
		String[] secrets = {
				"super-secret-api-key",
				"a-very-long-secret-that-contains-symbols-!@#$%^&*()_+-=[]{}|;':,./<>?",
				"",
				"x"
		};
		for (String secret : secrets) {
			String ciphertext = encryptor.encrypt(secret);
			assertThat(encryptor.decrypt(ciphertext)).isEqualTo(secret);
		}
	}

	@Test
	void ciphertextIsBase64AndNeverContainsPlaintext() {
		String plaintext = "the-raw-secret-7890";
		String ciphertext = encryptor.encrypt(plaintext);

		assertThat(ciphertext).isNotEqualTo(plaintext).doesNotContain(plaintext);
		assertThat(ciphertext).matches("^[A-Za-z0-9+/]+={0,2}$");
	}

	@Test
	void encryptionIsNonDeterministicDueToFreshIv() {
		String plaintext = "same-input";
		assertThat(encryptor.encrypt(plaintext)).isNotEqualTo(encryptor.encrypt(plaintext));
	}

	@Test
	void decryptRejectsTamperedCiphertext() {
		String ciphertext = encryptor.encrypt("integrity-check-me");
		char leading = ciphertext.charAt(0) == 'A' ? 'B' : 'A';
		String tampered = leading + ciphertext.substring(1);

		assertThatThrownBy(() -> encryptor.decrypt(tampered))
				.isInstanceOf(RuntimeException.class);
	}

	@Test
	void decryptRejectsGarbageInput() {
		assertThatThrownBy(() -> encryptor.decrypt("not-base64!!"))
				.isInstanceOf(RuntimeException.class);
		assertThatThrownBy(() -> encryptor.decrypt("aGVsbG8"))
				.isInstanceOf(RuntimeException.class)
				.hasMessageContaining("Invalid encrypted payload");
	}

	@Test
	void maskNeverExposesRawValueAndShowsLastFour() {
		assertThat(AesGcmEncryptor.mask("super-secret-1234")).isEqualTo("****1234");
		assertThat(AesGcmEncryptor.mask("abcd")).isEqualTo("****abcd");
		assertThat(AesGcmEncryptor.mask("ab")).isEqualTo("****ab");
		assertThat(AesGcmEncryptor.mask(null)).isNull();
		assertThat(AesGcmEncryptor.mask("super-secret-1234")).doesNotContain("super-secret");
	}

	@Test
	void exchangeCredentialEncryptorComponentRoundTripsViaSettingsProperties() {
		SettingsProperties properties = new SettingsProperties(
				2.0, 20.0, 4, SEED, RiskProfile.CONSERVATIVE,
				QuoteCurrency.USDT, PositionSizingMode.FIXED_PERCENT);
		ExchangeCredentialEncryptor component = new ExchangeCredentialEncryptor(properties);

		component.encrypt(null);
		assertThat(component.encrypt("api-secret-value")).satisfies(encoded -> {
			assertThat(component.decrypt(encoded)).isEqualTo("api-secret-value");
			assertThat(component.mask("api-secret-9999")).isEqualTo("****9999");
		});
	}
}