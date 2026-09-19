package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.Portfolio;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {

	List<Portfolio> findByUser(User user);

	List<Portfolio> findByUserAndAccountType(User user, AccountType accountType);

	Optional<Portfolio> findFirstByUserAndAccountType(User user, AccountType accountType);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Portfolio p where p.id = :id")
	Optional<Portfolio> findByIdForUpdate(@Param("id") UUID id);
}
