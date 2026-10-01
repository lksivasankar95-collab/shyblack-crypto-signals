package com.shyblack.cryptosignals.repository.research;

import com.shyblack.cryptosignals.entity.research.EventCollectionCheckpoint;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventCollectionCheckpointRepository extends JpaRepository<EventCollectionCheckpoint, UUID> {

	Optional<EventCollectionCheckpoint> findBySourceAndDatasetVersion(String source, String datasetVersion);
}
