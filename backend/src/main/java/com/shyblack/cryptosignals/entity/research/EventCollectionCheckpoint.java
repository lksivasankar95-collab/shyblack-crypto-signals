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
 * Per-source, per-dataset collection checkpoint. Records the last fully
 * processed month so a stopped/partial collection resumes instead of restarting.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
		name = "research_event_collection_checkpoint",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_event_collection_source_dataset",
						columnNames = {"source", "dataset_version"})
		})
public class EventCollectionCheckpoint extends BaseEntity {

	@Column(nullable = false, length = 40)
	private String source;

	@Column(name = "dataset_version", nullable = false, length = 100)
	private String datasetVersion;

	private Instant windowStart;
	private Instant windowEnd;

	/** Last fully processed month, "YYYY-MM". */
	@Column(name = "last_completed_month", length = 7)
	private String lastCompletedMonth;

	/** RUNNING | COMPLETED | PARTIAL | FAILED | TIMEOUT */
	@Column(length = 20)
	private String status;

	@Column(name = "records_collected")
	private Long recordsCollected;
}
