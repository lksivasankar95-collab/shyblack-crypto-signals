package com.shyblack.cryptosignals.entity;

import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.PositionStatus;
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
@Table(name = "positions")
public class Position extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "portfolio_id", nullable = false)
	private Portfolio portfolio;

	@Column(nullable = false)
	private String symbol;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PositionSide side;

	@Column(nullable = false, precision = 19, scale = 8)
	private BigDecimal size;

	@Column(nullable = false, precision = 19, scale = 8)
	private BigDecimal entryPrice;

	@Column(precision = 19, scale = 8)
	private BigDecimal currentPrice;

	@Column(precision = 19, scale = 8)
	private BigDecimal margin;

	@Column(precision = 19, scale = 8)
	private BigDecimal liquidationPrice;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PositionStatus status = PositionStatus.OPEN;

	private Instant closedAt;
}
