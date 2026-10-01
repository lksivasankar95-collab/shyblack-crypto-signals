package com.shyblack.cryptosignals.entity.research;

import com.shyblack.cryptosignals.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-file import checkpoint. A file is imported at most once per checksum;
 * a RUNNING row for the same file prevents overlapping imports (non-overlap).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
		name = "research_import_checkpoint",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_research_checkpoint_file", columnNames = {"source_file"})
		})
public class ResearchImportCheckpoint extends BaseEntity {

	@Column(name = "dataset_version", length = 100)
	private String datasetVersion;

	@Column(name = "source_file", nullable = false, length = 300)
	private String sourceFile;

	@Column(length = 30)
	private String kind;

	@Column(length = 128)
	private String checksum;

	/** RUNNING | COMPLETED | FAILED */
	@Column(length = 30)
	private String status;

	@Column(name = "rows_imported")
	private Long rowsImported;
}
