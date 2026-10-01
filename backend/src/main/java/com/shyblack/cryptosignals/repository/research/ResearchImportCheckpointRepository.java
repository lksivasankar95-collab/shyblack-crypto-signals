package com.shyblack.cryptosignals.repository.research;

import com.shyblack.cryptosignals.entity.research.ResearchImportCheckpoint;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResearchImportCheckpointRepository extends JpaRepository<ResearchImportCheckpoint, UUID> {

	Optional<ResearchImportCheckpoint> findBySourceFile(String sourceFile);
}
