package com.shyblack.cryptosignals.repository.research;

import com.shyblack.cryptosignals.entity.research.NfmValidationDetail;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NfmValidationDetailRepository extends JpaRepository<NfmValidationDetail, UUID> {

	List<NfmValidationDetail> findByRunIdAndKindOrderByOrdinalAsc(UUID runId, String kind);

	List<NfmValidationDetail> findByRunIdOrderByKindAscOrdinalAsc(UUID runId);

	long deleteByRunId(UUID runId);
}
