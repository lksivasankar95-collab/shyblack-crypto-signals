package com.shyblack.cryptosignals.entity;

import com.shyblack.cryptosignals.entity.enums.AccountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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

	@Column(nullable = false)
	private String quoteCurrency = "USDT";

	@Column(nullable = false, precision = 19, scale = 8)
	private BigDecimal totalBalance = BigDecimal.ZERO;

	@Column(nullable = false, precision = 19, scale = 8)
	private BigDecimal availableBalance = BigDecimal.ZERO;

	@Column(nullable = false, precision = 19, scale = 8)
	private BigDecimal invested = BigDecimal.ZERO;
}
