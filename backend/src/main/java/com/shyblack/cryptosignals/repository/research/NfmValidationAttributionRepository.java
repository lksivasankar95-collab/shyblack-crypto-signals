package com.shyblack.cryptosignals.repository.research;

import com.shyblack.cryptosignals.entity.research.NfmValidationAttribution;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NfmValidationAttributionRepository
		extends JpaRepository<NfmValidationAttribution, UUID> {

	boolean existsByRunIdAndSignalId(UUID runId, UUID signalId);

	List<NfmValidationAttribution> findByRunIdOrderByCreatedAtAsc(UUID runId);

	long countByRunId(UUID runId);
}
