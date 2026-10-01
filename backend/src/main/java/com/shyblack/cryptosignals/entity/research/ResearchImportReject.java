package com.shyblack.cryptosignals.entity.research;

import com.shyblack.cryptosignals.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Quarantine for rows rejected during import (invalid/short/duplicate-conflict). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
		name = "research_import_reject",
		indexes = {
				@Index(name = "ix_research_reject_dataset", columnList = "dataset_version"),
				@Index(name = "ix_research_reject_file", columnList = "source_file")
		})
public class ResearchImportReject extends BaseEntity {

	@Column(name = "dataset_version", length = 100)
	private String datasetVersion;

	@Column(name = "source_file", length = 300)
	private String sourceFile;

	@Column(name = "line_number")
	private Long lineNumber;

	@Column(length = 300)
	private String reason;

	@Column(name = "raw_line", length = 2000)
	private String rawLine;
}
