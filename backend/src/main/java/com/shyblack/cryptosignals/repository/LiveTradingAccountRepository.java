package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.LiveTradingAccount;
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

public interface LiveTradingAccountRepository extends JpaRepository<LiveTradingAccount, UUID> {

	Optional<LiveTradingAccount> findByUserAndExchange(User user, ExchangeName exchange);

	Optional<LiveTradingAccount> findFirstByUser(User user);

	/**
	 * All accounts with the credential eagerly fetched.
	 *
	 * <p>The execution router must read {@code credential.status} as one of its
	 * account gates, and {@code credential} is {@code LAZY}. Without this the
	 * gate would throw {@code LazyInitializationException} outside the owning
	 * session — a failure that would be reported as a routing error rather than
	 * the account state it is checking.
	 */
	/**
	 * Enabled accounts with the kill switch off.
	 *
	 * <p>Used by reconciliation to refresh balances. Distinct from
	 * {@link #findAllWithCredential()}: the router deliberately reads the
	 * <i>unfiltered</i> list so a refusal is recorded with its reason rather than
	 * the account silently vanishing from consideration.
	 */
	List<LiveTradingAccount> findByEnabledTrueAndKillSwitchActiveFalse();

	@Query("select a from LiveTradingAccount a join fetch a.credential join fetch a.user")
	List<LiveTradingAccount> findAllWithCredential();

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from LiveTradingAccount a where a.id = :id")
	Optional<LiveTradingAccount> findByIdForUpdate(@Param("id") UUID id);
}
