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

/** Historical funding-rate settlement. UTC. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
		name = "market_funding_rate",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_market_funding_symbol_time", columnNames = {"symbol", "funding_time"})
		},
		indexes = {
				@Index(name = "ix_market_funding_symbol_time", columnList = "symbol,funding_time"),
				@Index(name = "ix_market_funding_dataset", columnList = "dataset_version")
		})
public class MarketFundingRate extends BaseEntity {

	@Column(nullable = false, length = 20)
	private String symbol;

	@Column(name = "funding_time", nullable = false)
	private Instant fundingTime;

	@Column(name = "funding_interval_hours")
	private Integer fundingIntervalHours;

	@Column(name = "last_funding_rate", precision = 18, scale = 10)
	private BigDecimal lastFundingRate;

	@Column(name = "mark_price", precision = 24, scale = 10)
	private BigDecimal markPrice;

	@Column(name = "source_dataset", length = 100)
	private String sourceDataset;

	@Column(name = "source_file", length = 300)
	private String sourceFile;

	@Column(name = "dataset_version", length = 100)
	private String datasetVersion;
}
