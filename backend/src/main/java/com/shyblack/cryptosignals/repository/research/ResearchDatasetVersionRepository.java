package com.shyblack.cryptosignals.repository.research;

import com.shyblack.cryptosignals.entity.research.ResearchDatasetVersion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResearchDatasetVersionRepository extends JpaRepository<ResearchDatasetVersion, UUID> {

	Optional<ResearchDatasetVersion> findByName(String name);
}
