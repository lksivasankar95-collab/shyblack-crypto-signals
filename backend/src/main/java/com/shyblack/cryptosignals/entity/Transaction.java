package com.shyblack.cryptosignals.entity;

import com.shyblack.cryptosignals.entity.enums.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "transactions")
public class Transaction extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "portfolio_id", nullable = false)
	private Portfolio portfolio;

	@Column(nullable = false)
	private String symbol;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TransactionType type;

	@Column(nullable = false, precision = 19, scale = 8)
	private BigDecimal quantity;

	@Column(nullable = false, precision = 19, scale = 8)
	private BigDecimal price;

	@Column(nullable = false)
	private Instant executedAt;

	private String notes;
}
