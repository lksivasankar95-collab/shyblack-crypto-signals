package com.shyblack.cryptosignals.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-GCM authenticated encryption utility. A 256-bit key is derived from the
 * configured seed via SHA-256. Ciphertext is stored as Base64 of the 12-byte
 * IV prepended to the cipher text, so each call encrypts with a fresh IV
 * (non-deterministic output).
 */
public final class AesGcmEncryptor {

	public static final int IV_BYTES = 12;
	public static final int TAG_BITS = 128;
	public static final int KEY_BYTES = 32;

	private static final String ALGORITHM = "AES/GCM/NoPadding";
	private static final String KEY_ALGORITHM = "AES";
	private static final String DIGEST = "SHA-256";

	private final SecretKey key;
	private final SecureRandom random = new SecureRandom();

	public AesGcmEncryptor(String keySeed) {
		this.key = deriveKey(keySeed);
	}

	public String encrypt(String plaintext) {
		if (plaintext == null) {
			return null;
		}
		try {
			byte[] iv = new byte[IV_BYTES];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance(ALGORITHM);
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
			byte[] combined = new byte[IV_BYTES + ciphertext.length];
			System.arraycopy(iv, 0, combined, 0, IV_BYTES);
			System.arraycopy(ciphertext, 0, combined, IV_BYTES, ciphertext.length);
			return Base64.getEncoder().encodeToString(combined);
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to encrypt value", ex);
		}
	}

	public String decrypt(String encoded) {
		if (encoded == null) {
			return null;
		}
		try {
			byte[] combined = Base64.getDecoder().decode(encoded);
			if (combined.length < IV_BYTES + TAG_BITS / 8) {
				throw new IllegalArgumentException("Invalid encrypted payload: too short");
			}
			byte[] iv = Arrays.copyOfRange(combined, 0, IV_BYTES);
			byte[] ciphertext = Arrays.copyOfRange(combined, IV_BYTES, combined.length);
			Cipher cipher = Cipher.getInstance(ALGORITHM);
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			byte[] plaintext = cipher.doFinal(ciphertext);
			return new String(plaintext, StandardCharsets.UTF_8);
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("Invalid encrypted payload", ex);
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to decrypt value", ex);
		}
	}

	/**
	 * Returns {@code ****} plus the last four characters of the raw value,
	 * or {@code null} for null input. Values shorter than four characters are
	 * fully masked apart from the leading asterisks.
	 */
	public static String mask(String raw) {
		if (raw == null) {
			return null;
		}
		String value = raw.trim();
		if (value.isEmpty()) {
			return "****";
		}
		int start = Math.max(0, value.length() - 4);
		return "****" + value.substring(start);
	}

	private static SecretKey deriveKey(String seed) {
		try {
			byte[] seedBytes = seed == null || seed.isBlank()
					? new byte[0]
					: seed.getBytes(StandardCharsets.UTF_8);
			byte[] keyBytes = MessageDigest.getInstance(DIGEST).digest(seedBytes);
			if (keyBytes.length < KEY_BYTES) {
				throw new IllegalStateException("Derived key too short");
			}
			return new SecretKeySpec(Arrays.copyOf(keyBytes, KEY_BYTES), KEY_ALGORITHM);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}
}