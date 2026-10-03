package com.shyblack.cryptosignals.entity;

import com.shyblack.cryptosignals.entity.enums.PaperCapitalEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Immutable audit trail for paper-account capital movements — one row per
 * INITIAL / ADD / REDUCE / RESET. Never updated once written: this table is
 * the ledger of record for "why is the balance what it is".
 *
 * <p>Deliberately tied to {@link Portfolio} with {@code AccountType.PAPER} so
 * a LIVE account can never acquire a row here; capital management is a
 * simulated-funds concern only and must never touch real balances.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "paper_capital_events",
		indexes = {
				@Index(name = "ix_paper_capital_portfolio", columnList = "portfolio_id"),
				@Index(name = "ix_paper_capital_type", columnList = "event_type")
		})
public class PaperCapitalEvent extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "portfolio_id", nullable = false)
	private Portfolio portfolio;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false)
	private PaperCapitalEventType eventType;

	/** Signed movement applied to available capital. ADD is positive, REDUCE negative. */
	@Column(name = "amount", nullable = false, precision = 19, scale = 8)
	private BigDecimal amount;

	/** totalBalance (available + invested) before the movement. */
	@Column(name = "previous_balance", nullable = false, precision = 19, scale = 8)
	private BigDecimal previousBalance;

	/** totalBalance after the movement. */
	@Column(name = "new_balance", nullable = false, precision = 19, scale = 8)
	private BigDecimal newBalance;

	@Column(length = 500)
	private String reason;
}
