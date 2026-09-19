package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.Portfolio;
import com.shyblack.cryptosignals.entity.Position;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.PositionStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PositionRepository extends JpaRepository<Position, UUID> {

	List<Position> findByPortfolio(Portfolio portfolio);

	List<Position> findByPortfolioAndStatus(Portfolio portfolio, PositionStatus status);

	List<Position> findByPortfolio_UserOrderByCreatedAtDesc(User user);

	List<Position> findByPortfolio_UserAndStatusOrderByCreatedAtDesc(User user, PositionStatus status);

	List<Position> findByPortfolio_UserAndPortfolio_AccountTypeOrderByCreatedAtDesc(User user, AccountType accountType);

	List<Position> findByPortfolio_UserAndPortfolio_AccountTypeAndStatusOrderByCreatedAtDesc(
			User user, AccountType accountType, PositionStatus status);

	Optional<Position> findByPortfolioAndSignalId(Portfolio portfolio, UUID signalId);

	List<Position> findByStatus(PositionStatus status);

	List<Position> findByStatusAndPortfolio_AccountType(PositionStatus status, AccountType accountType);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Position p where p.id = :id")
	Optional<Position> findByIdForUpdate(@Param("id") UUID id);
}
