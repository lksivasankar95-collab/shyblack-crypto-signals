package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.Portfolio;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {

	List<Portfolio> findByUser(User user);

	List<Portfolio> findByUserAndAccountType(User user, AccountType accountType);
}
