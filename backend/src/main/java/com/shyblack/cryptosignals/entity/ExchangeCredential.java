package com.shyblack.cryptosignals.entity;

import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.security.AesGcmEncryptor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An exchange API credential for a single user. {@code apiKey} and
 * {@code apiSecret} hold AES-GCM ciphertext (Base64) — raw secrets are never
 * persisted and never serialized.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "exchange_credentials",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_exchange_credentials_user_exchange",
				columnNames = {"user_id", "exchange"}))
public class ExchangeCredential extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ExchangeName exchange;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ExchangeConnectionStatus status = ExchangeConnectionStatus.NOT_CONNECTED;

	/** Encrypted at rest (AES-GCM, Base64). */
	@Column(nullable = false, length = 2000)
	private String apiKey;

	/** Encrypted at rest (AES-GCM, Base64). */
	@Column(nullable = false, length = 2000)
	private String apiSecret;

	private String label;

	private String displayName;

	/**
	 * Defensive masking of the value stored at rest (already ciphertext).
	 * Never exposes the raw secret.
	 */
	@Transient
	public String maskedApiKey() {
		return AesGcmEncryptor.mask(apiKey);
	}

	@Transient
	public String maskedSecret() {
		return AesGcmEncryptor.mask(apiSecret);
	}
}