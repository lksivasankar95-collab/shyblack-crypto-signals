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
 * Persisted historical OHLCV research candle. Times are UTC. In production this
 * table is created partition-by-range on {@code open_time} (monthly) by
 * {@code db/research/V1__research_market_data.sql}; Hibernate maps it read/write
 * but does not own the partitioning.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
		name = "market_candle",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_market_candle_symbol_tf_time",
						columnNames = {"symbol", "timeframe", "open_time"})
		},
		indexes = {
				@Index(name = "ix_market_candle_symbol_tf_time", columnList = "symbol,timeframe,open_time"),
				@Index(name = "ix_market_candle_dataset", columnList = "dataset_version")
		})
public class MarketCandle extends BaseEntity {

	@Column(nullable = false, length = 20)
	private String symbol;

	@Column(nullable = false, length = 10)
	private String timeframe;

	@Column(name = "open_time", nullable = false)
	private Instant openTime;

	@Column(name = "close_time")
	private Instant closeTime;

	@Column(nullable = false, precision = 24, scale = 10)
	private BigDecimal open;

	@Column(nullable = false, precision = 24, scale = 10)
	private BigDecimal high;

	@Column(nullable = false, precision = 24, scale = 10)
	private BigDecimal low;

	@Column(nullable = false, precision = 24, scale = 10)
	private BigDecimal close;

	@Column(precision = 30, scale = 10)
	private BigDecimal volume;

	@Column(name = "quote_volume", precision = 30, scale = 10)
	private BigDecimal quoteVolume;

	private Long trades;

	@Column(name = "source_dataset", length = 100)
	private String sourceDataset;

	@Column(name = "source_file", length = 300)
	private String sourceFile;

	@Column(name = "dataset_version", length = 100)
	private String datasetVersion;
}
