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
 * Optional historical liquidation aggregate. Binance publishes no USDT-M
 * historical liquidation dataset, so this table is expected to be EMPTY for the
 * frozen window unless a third-party source is licensed. Absence means
 * liquidation state stays UNKNOWN — never inferred or fabricated.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
		name = "market_liquidation",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_market_liquidation_symbol_ts", columnNames = {"symbol", "ts"})
		},
		indexes = {
				@Index(name = "ix_market_liquidation_symbol_ts", columnList = "symbol,ts")
		})
public class MarketLiquidation extends BaseEntity {

	@Column(nullable = false, length = 20)
	private String symbol;

	@Column(nullable = false)
	private Instant ts;

	@Column(name = "long_volume", precision = 30, scale = 10)
	private BigDecimal longVolume;

	@Column(name = "short_volume", precision = 30, scale = 10)
	private BigDecimal shortVolume;

	@Column(name = "source_dataset", length = 100)
	private String sourceDataset;

	@Column(name = "source_file", length = 300)
	private String sourceFile;

	@Column(name = "dataset_version", length = 100)
	private String datasetVersion;
}
