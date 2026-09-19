package com.shyblack.cryptosignals.entity;

import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.FuturesMarginMode;
import com.shyblack.cryptosignals.entity.enums.FuturesPositionMode;
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
 * A user's live Binance USDT-M FUTURES account. Kept fully separate from the
 * SPOT {@link LiveTradingAccount} entity so Futures state (leverage, margin
 * mode, position mode, funding, liquidation) is never mixed with Spot.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
		name = "futures_trading_accounts",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_futures_accounts_user_exchange",
				columnNames = {"user_id", "exchange"}))
public class FuturesTradingAccount extends BaseEntity {

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

	/** Master activation switch — off by default. */
	@Column(nullable = false)
	private boolean enabled = false;

	@Column(nullable = false)
	private boolean killSwitchActive = false;

	/** Explicit user acknowledgement stored on the backend (Phase 34 safety gate). */
	@Column(nullable = false)
	private boolean acknowledged = false;

	@Column(nullable = false)
	private String marginAsset = "USDT";

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private FuturesMarginMode marginMode = FuturesMarginMode.ISOLATED;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private FuturesPositionMode positionMode = FuturesPositionMode.ONE_WAY;

	/** Configured max leverage cap per trade (backend-enforced). */
	@Column(nullable = false)
	private int maxLeverage = 3;

	@Column(precision = 19, scale = 8)
	private BigDecimal maxNotionalPerTrade;

	@Column(nullable = false)
	private int maxActivePositions = 2;

	@Column(precision = 7, scale = 4)
	private BigDecimal dailyLossLimitPct;

	@Column(precision = 19, scale = 8)
	private BigDecimal sessionStartEquity;

	@Column(precision = 19, scale = 8)
	private BigDecimal realizedPnlToday = BigDecimal.ZERO;

	private LocalDate sessionDate;

	// Cached Futures wallet / margin metrics from the last sync.
	@Column(precision = 19, scale = 8) private BigDecimal walletBalance;
	@Column(precision = 19, scale = 8) private BigDecimal availableBalance;
	@Column(precision = 19, scale = 8) private BigDecimal marginBalance;
	@Column(precision = 19, scale = 8) private BigDecimal unrealizedPnl;
	@Column(precision = 19, scale = 8) private BigDecimal cachedRealizedPnl;
	@Column(precision = 19, scale = 8) private BigDecimal usedMargin;
	@Column(precision = 19, scale = 8) private BigDecimal maintenanceMargin;
	@Column(precision = 19, scale = 8) private BigDecimal totalFundingPaid = BigDecimal.ZERO;

	@Column private Instant lastValidatedAt;
	@Column(length = 200) private String lastValidationMessage;

	@Version
	@Column(nullable = false)
	private Long version = 0L;
}
