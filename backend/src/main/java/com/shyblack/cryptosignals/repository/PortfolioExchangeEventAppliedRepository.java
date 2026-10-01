package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.PortfolioExchangeEventApplied;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioExchangeEventAppliedRepository
		extends JpaRepository<PortfolioExchangeEventApplied, UUID> {

	boolean existsByUserAndAccountCategoryAndEventTypeAndEventIdentity(
			User user, AccountCategory accountCategory, String eventType, String eventIdentity);

	List<PortfolioExchangeEventApplied> findByUserAndAccountCategory(
			User user, AccountCategory accountCategory);

	long countByUserAndAccountCategory(User user, AccountCategory accountCategory);

	void deleteByAppliedAtBefore(Instant cutoff);
}