package com.shyblack.cryptosignals.entity.research;

import com.shyblack.cryptosignals.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Historical open-interest observation (5-minute cadence from Binance Vision
 * {@code daily/metrics}). UTC. Missing observations are simply absent — they are
 * never coerced to zero.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
		name = "market_open_interest",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_market_oi_symbol_ts", columnNames = {"symbol", "ts"})
		},
		indexes = {
				@Index(name = "ix_market_oi_symbol_ts", columnList = "symbol,ts"),
				@Index(name = "ix_market_oi_dataset", columnList = "dataset_version")
		})
public class MarketOpenInterest extends BaseEntity {

	@Column(nullable = false, length = 20)
	private String symbol;

	@Column(nullable = false)
	private Instant ts;

	@Column(name = "sum_open_interest", precision = 30, scale = 10)
	private BigDecimal sumOpenInterest;

	@Column(name = "sum_open_interest_value", precision = 30, scale = 10)
	private BigDecimal sumOpenInterestValue;

	@Column(name = "taker_long_short_vol_ratio", precision = 18, scale = 8)
	private BigDecimal takerLongShortVolRatio;

	@Column(name = "source_dataset", length = 100)
	private String sourceDataset;

	@Column(name = "source_file", length = 300)
	private String sourceFile;

	@Column(name = "dataset_version", length = 100)
	private String datasetVersion;
}
