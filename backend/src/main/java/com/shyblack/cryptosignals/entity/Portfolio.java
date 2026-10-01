package com.shyblack.cryptosignals.entity;

import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "portfolios")
public class Portfolio extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AccountType accountType;

	/**
	 * Read-model category of this portfolio. Added by the unified Portfolio architecture and
	 * intentionally nullable: existing rows hold SQL NULL and load unchanged. NULL means "not yet
	 * categorised" — a legacy paper row is presented as {@link AccountCategory#MAIN} by the read
	 * model, never silently rewritten. Paper SPOT and FUTURES keep sharing this single row; the
	 * category split is a query-layer partition, not a physical account split.
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "account_category", length = 20)
	private AccountCategory accountCategory;

	/**
	 * Exchange backing this portfolio. Null for a simulated account, which has no exchange; the
	 * read model reports {@code PAPER} for those. Null is never defaulted to a fabricated exchange.
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "exchange", length = 20)
	private ExchangeName exchange;

	@Column(nullable = false)
	private String quoteCurrency = "USDT";

	/** Configured starting balance; never mutated after initialization (except by reset). */
	@Column(nullable = false, precision = 19, scale = 8)
	private BigDecimal initialBalance = BigDecimal.ZERO;

	/** Total balance including invested capital (available + invested). */
	@Column(nullable = false, precision = 19, scale = 8)
	private BigDecimal totalBalance = BigDecimal.ZERO;

	/** Free cash available for new positions. */
	@Column(nullable = false, precision = 19, scale = 8)
	private BigDecimal availableBalance = BigDecimal.ZERO;

	/** Notional deployed into open positions. */
	@Column(nullable = false, precision = 19, scale = 8)
	private BigDecimal invested = BigDecimal.ZERO;

	@Column(nullable = false, precision = 19, scale = 8)
	private BigDecimal realizedPnl = BigDecimal.ZERO;

	@Column(nullable = false, precision = 19, scale = 8)
	private BigDecimal totalFees = BigDecimal.ZERO;

	@Column(nullable = false)
	private int totalTrades = 0;

	@Column(nullable = false)
	private int winningTrades = 0;

	@Column(nullable = false)
	private int losingTrades = 0;

	@Version
	@Column(nullable = false)
	private Long version = 0L;
}
