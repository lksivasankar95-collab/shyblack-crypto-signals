package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.LiveTradingAccount;
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

public interface LiveTradingAccountRepository extends JpaRepository<LiveTradingAccount, UUID> {

	Optional<LiveTradingAccount> findByUserAndExchange(User user, ExchangeName exchange);

	Optional<LiveTradingAccount> findFirstByUser(User user);

	List<LiveTradingAccount> findByEnabledTrueAndKillSwitchActiveFalse();

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from LiveTradingAccount a where a.id = :id")
	Optional<LiveTradingAccount> findByIdForUpdate(@Param("id") UUID id);
}
