package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.FuturesTradingAccount;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FuturesTradingAccountRepository extends JpaRepository<FuturesTradingAccount, UUID> {

	Optional<FuturesTradingAccount> findByUserAndExchange(User user, ExchangeName exchange);

	Optional<FuturesTradingAccount> findFirstByUser(User user);

	/**
	 * All accounts with the credential and user eagerly fetched.
	 *
	 * <p>{@code credential} is {@code LAZY} and the execution router reads
	 * {@code credential.status} as an account gate, so it must be fetched in the
	 * same query or the gate would fail with a lazy-initialization error instead of
	 * reporting the account state.
	 */
	/**
	 * Enabled accounts with the kill switch off.
	 *
	 * <p>Used by reconciliation to refresh balances. The router reads the
	 * unfiltered list instead, so a refusal is recorded with its reason rather than
	 * the account silently disappearing.
	 */
	List<FuturesTradingAccount> findByEnabledTrueAndKillSwitchActiveFalse();

	@Query("select a from FuturesTradingAccount a join fetch a.credential join fetch a.user")
	List<FuturesTradingAccount> findAllWithCredential();

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from FuturesTradingAccount a where a.id = :id")
	Optional<FuturesTradingAccount> findByIdForUpdate(@Param("id") UUID id);
}
