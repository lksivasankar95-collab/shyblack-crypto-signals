package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.BacktestRun;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.BacktestStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BacktestRunRepository extends JpaRepository<BacktestRun, UUID> {

	List<BacktestRun> findByUserOrderByCreatedAtDesc(User user);

	Optional<BacktestRun> findByIdAndUser(UUID id, User user);

	List<BacktestRun> findByStatusOrderByCreatedAtAsc(BacktestStatus status);

	long countByStatus(BacktestStatus status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from BacktestRun r where r.id = :id")
	Optional<BacktestRun> findByIdForUpdate(@Param("id") UUID id);
}
