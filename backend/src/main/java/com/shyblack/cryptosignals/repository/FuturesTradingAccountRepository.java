package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.FuturesTradingAccount;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FuturesTradingAccountRepository extends JpaRepository<FuturesTradingAccount, UUID> {

	Optional<FuturesTradingAccount> findByUserAndExchange(User user, ExchangeName exchange);

	Optional<FuturesTradingAccount> findFirstByUser(User user);

	List<FuturesTradingAccount> findByEnabledTrueAndKillSwitchActiveFalse();

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from FuturesTradingAccount a where a.id = :id")
	Optional<FuturesTradingAccount> findByIdForUpdate(@Param("id") UUID id);
}
