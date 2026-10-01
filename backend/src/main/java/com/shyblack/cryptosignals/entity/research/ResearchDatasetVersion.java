package com.shyblack.cryptosignals.entity.research;

import com.shyblack.cryptosignals.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Provenance + reproducibility anchor for an imported research dataset. The
 * {@code name} is the dataset version stamped onto every imported row and (via
 * strategy params) into the backtest configuration hash.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
		name = "research_dataset_version",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_research_dataset_name", columnNames = {"name"})
		})
public class ResearchDatasetVersion extends BaseEntity {

	@Column(nullable = false, length = 150)
	private String name;

	@Column(name = "window_start")
	private Instant windowStart;

	@Column(name = "window_end")
	private Instant windowEnd;

	@Column(length = 1000)
	private String sources;

	/** BUILDING | READY | FAILED */
	@Column(length = 30)
	private String status = "BUILDING";

	/** Aggregate checksum of the imported files, for reproducibility. */
	@Column(length = 128)
	private String checksum;

	@Column(length = 2000)
	private String notes;
}
