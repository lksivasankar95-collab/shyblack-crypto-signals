package com.shyblack.cryptosignals.entity;

import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A user's live exchange trading account. One record per user per exchange.
 * References a {@link ExchangeCredential} for the signed API path.
 *
 * `enabled` and `killSwitchActive` gate every real order and default OFF —
 * live trading is opt-in and remains off until explicitly activated.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
		name = "live_trading_accounts",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_live_accounts_user_exchange",
				columnNames = {"user_id", "exchange"}))
public class LiveTradingAccount extends BaseEntity {

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ExchangeName exchange;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "credential_id", nullable = false)
	private ExchangeCredential credential;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ExchangeConnectionStatus connectionStatus = ExchangeConnectionStatus.NOT_CONNECTED;

	/** Master gate — no orders are ever placed while false. */
	@Column(nullable = false)
	private boolean enabled = false;

	/** Emergency stop — blocks new entries but reconciliation + manual close remain live. */
	@Column(nullable = false)
	private boolean killSwitchActive = false;

	@Column(nullable = false)
	private String quoteCurrency = "USDT";

	@Column(precision = 19, scale = 8)
	private BigDecimal maxNotionalPerTrade;

	@Column(nullable = false)
	private int maxActivePositions = 3;

	@Column(precision = 7, scale = 4)
	private BigDecimal dailyLossLimitPct;

	@Column(precision = 19, scale = 8)
	private BigDecimal sessionStartEquity;

	private LocalDate sessionDate;

	/** Cached balance from last successful sync — never a source of truth for order sizing. */
	@Column(precision = 19, scale = 8)
	private BigDecimal cachedAvailableBalance;

	@Column(precision = 19, scale = 8)
	private BigDecimal cachedTotalBalance;

	@Column
	private Instant lastValidatedAt;

	@Column(length = 200)
	private String lastValidationMessage;

	@Version
	@Column(nullable = false)
	private Long version = 0L;
}
