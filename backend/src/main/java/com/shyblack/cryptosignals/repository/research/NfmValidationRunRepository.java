package com.shyblack.cryptosignals.repository.research;

import com.shyblack.cryptosignals.entity.research.NfmValidationRun;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NfmValidationRunRepository extends JpaRepository<NfmValidationRun, UUID> {

	Optional<NfmValidationRun> findByRunId(UUID runId);

	boolean existsByRunId(UUID runId);

	List<NfmValidationRun> findAllByOrderByCreatedAtDesc();

	List<NfmValidationRun> findAllByStrategyIdAndStrategyVersionOrderByCreatedAtDesc(String strategyId,
			String strategyVersion);
}
