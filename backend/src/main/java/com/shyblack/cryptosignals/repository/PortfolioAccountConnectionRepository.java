package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.PortfolioAccountConnection;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PortfolioAccountConnectionRepository
		extends JpaRepository<PortfolioAccountConnection, UUID> {

	Optional<PortfolioAccountConnection> findByUserAndAccountModeAndAccountCategory(
			User user, AccountMode accountMode, AccountCategory accountCategory);

	List<PortfolioAccountConnection> findByUserAndAccountMode(User user, AccountMode accountMode);

	List<PortfolioAccountConnection> findByUser(User user);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from PortfolioAccountConnection c where c.id = :id")
	Optional<PortfolioAccountConnection> findByIdForUpdate(@Param("id") UUID id);
}