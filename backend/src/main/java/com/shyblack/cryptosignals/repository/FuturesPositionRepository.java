package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.FuturesPosition;
import com.shyblack.cryptosignals.entity.FuturesTradingAccount;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.FuturesPositionStatus;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FuturesPositionRepository extends JpaRepository<FuturesPosition, UUID> {

	Optional<FuturesPosition> findByAccountAndSymbolAndPositionSideAndStatus(
			FuturesTradingAccount account, String symbol, PositionSide positionSide,
			FuturesPositionStatus status);

	List<FuturesPosition> findByAccount_UserOrderByCreatedAtDesc(User user);

	List<FuturesPosition> findByAccountAndStatusOrderByCreatedAtDesc(
			FuturesTradingAccount account, FuturesPositionStatus status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from FuturesPosition p where p.id = :id")
	Optional<FuturesPosition> findByIdForUpdate(@Param("id") UUID id);
}
