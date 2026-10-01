package com.shyblack.cryptosignals.repository.research;

import com.shyblack.cryptosignals.entity.research.ResearchImportReject;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResearchImportRejectRepository extends JpaRepository<ResearchImportReject, UUID> {

	long countByDatasetVersion(String datasetVersion);
}
