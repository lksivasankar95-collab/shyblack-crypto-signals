package com.shyblack.cryptosignals.entity;

import com.shyblack.cryptosignals.entity.enums.EntryType;
import com.shyblack.cryptosignals.entity.enums.MarketRegime;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.SignalGrade;
import com.shyblack.cryptosignals.entity.enums.SignalStatus;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
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
@Table(name = "signals")
public class Signal extends BaseEntity {

	@Column(nullable = false)
	private String symbol;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SignalStatus status = SignalStatus.DRAFT;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PositionSide side;

	@Column(nullable = false)
	private Integer confidence;

	@Column(nullable = false, precision = 19, scale = 8)
	private BigDecimal entryPrice;

	@Column(nullable = false, precision = 19, scale = 8)
	private BigDecimal targetPrice;

	@Column(nullable = false, precision = 19, scale = 8)
	private BigDecimal stopLoss;

	private String strategy;

	@Column(precision = 7, scale = 4)
	private BigDecimal strategyWinRate;

	@Column(nullable = false, precision = 5, scale = 2)
	private BigDecimal suggestedRiskPercent = new BigDecimal("2.00");

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by_user_id")
	private User createdBy;

	private Instant closedAt;

	@Column(length = 2000)
	private String technicalSummary;

	@Column(length = 1000)
	private String disclaimer;

	// --- Spot Morning Plan fields ---

	@Column
	private Integer score;

	@Enumerated(EnumType.STRING)
	@Column
	private SignalGrade signalGrade;

	@Enumerated(EnumType.STRING)
	@Column
	private EntryType entryType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TradingMode tradingMode = TradingMode.SPOT;

	@Enumerated(EnumType.STRING)
	@Column
	private MarketRegime marketRegime;

	@Column(precision = 19, scale = 8)
	private BigDecimal targetPrice2;

	@Column(precision = 19, scale = 8)
	private BigDecimal targetPrice3;

	@Column(precision = 10, scale = 4)
	private BigDecimal riskReward;
}
