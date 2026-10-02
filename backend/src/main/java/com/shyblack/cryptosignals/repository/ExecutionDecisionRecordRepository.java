package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.ExecutionDecisionRecord;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.service.execution.ExecutionOutcome;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionDecisionRecordRepository
		extends JpaRepository<ExecutionDecisionRecord, UUID> {

	/**
	 * Primary duplicate lookup, keyed on the same columns as the table's unique
	 * constraint.
	 *
	 * <p>The pre-check is an optimisation. The authoritative guarantee is the
	 * constraint plus the {@code DataIntegrityViolationException} catch, because a
	 * lookup-then-insert pair is not atomic on its own.
	 */
	Optional<ExecutionDecisionRecord> findByUserIdAndSignalIdAndAccountModeAndAccountCategory(
			UUID userId, UUID signalId,
			com.shyblack.cryptosignals.entity.enums.AccountMode accountMode,
			com.shyblack.cryptosignals.entity.enums.AccountCategory accountCategory);

	List<ExecutionDecisionRecord> findBySignalIdOrderByDecidedAtAsc(UUID signalId);

	List<ExecutionDecisionRecord> findByUserIdOrderByDecidedAtDesc(UUID userId);

	/**
	 * Attempts whose exchange outcome was never established.
	 *
	 * <p>These are the only orders that may safely be reconciled, and they are
	 * never safe to blindly re-send.
	 */
	List<ExecutionDecisionRecord> findByOutcome(ExecutionOutcome outcome);

	boolean existsByUserIdAndSignalIdAndAccountModeAndAccountCategory(
			UUID userId, UUID signalId,
			com.shyblack.cryptosignals.entity.enums.AccountMode accountMode,
			com.shyblack.cryptosignals.entity.enums.AccountCategory accountCategory);
}